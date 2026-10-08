#!/usr/bin/env python3
"""Writes the in-app attribution for the native libraries into android/config.

Covers the Go engine (every linked module's licence and NOTICE text, read from the modules
themselves), the Kubernetes client libraries, and the pinned Ghostty and Zig versions. Every APK
build runs it through the Makefile, and CI fails if the committed files are out of date.
"""

import json
import pathlib
import re
import subprocess
import sys

REPO = pathlib.Path(__file__).resolve().parents[1]
ENGINE = REPO / "k8s-engine"
CONFIG = REPO / "android" / "config"
ZIG_ZON = REPO / "terminal-native" / "build.zig.zon"

LICENSE_FILE = re.compile(r"(?i)^(licen[cs]e|copying)")
NOTICE_FILE = re.compile(r"(?i)^notice")
KUBERNETES = ("k8s.io/", "sigs.k8s.io/")


def run(*args: str) -> str:
    return subprocess.run(args, cwd=ENGINE, check=True, capture_output=True, text=True).stdout


def module_dir(path: str, version: str) -> pathlib.Path:
    info = json.loads(run("go", "mod", "download", "-json", f"{path}@{version}"))
    return pathlib.Path(info["Dir"])


def is_pure_apache(text: str) -> bool:
    """Apache-2.0 text only, with no MIT/BSD/ISC portions that carry their own notices."""
    return (
        "Apache License" in text
        and "Permission is hereby granted" not in text
        and "Redistribution and use" not in text
        and "Permission to use, copy, modify" not in text
    )


def linked_modules() -> list[tuple[str, str]]:
    out = run(
        "go", "list", "-deps",
        "-f", "{{if not .Standard}}{{with .Module}}{{if not .Main}}{{.Path}} {{.Version}}{{end}}{{end}}{{end}}",
        "./pkg/client",
    )
    return sorted({tuple(line.split()) for line in out.splitlines() if line.strip()})


def gomobile_version() -> str:
    return run("go", "list", "-m", "-f", "{{.Version}}", "golang.org/x/mobile").strip()


def write_json(path: pathlib.Path, data: dict) -> None:
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    if not path.exists() or path.read_text() != text:
        path.write_text(text)
        print(f"updated {path.relative_to(REPO)}")


def update_json(path: pathlib.Path, **fields) -> None:
    write_json(path, {**json.loads(path.read_text()), **fields})


def zig_pins() -> tuple[str, str]:
    zon = ZIG_ZON.read_text()
    zig = re.search(r'minimum_zig_version = "([^"]+)"', zon).group(1)
    commit = re.search(r"ghostty-org/ghostty#([0-9a-f]+)", zon).group(1)
    version = re.search(r'"ghostty-(\d[^"]*?)-[A-Za-z0-9_-]{44}"', zon).group(1)
    return zig, f"{version}+{commit[:7]}"


def main() -> None:
    modules = linked_modules()
    apache_only, sections = [], []
    for path, version in modules:
        directory = module_dir(path, version)
        files = sorted(p.name for p in directory.iterdir() if p.is_file())
        licenses = [f for f in files if LICENSE_FILE.match(f)]
        notices = [f for f in files if NOTICE_FILE.match(f)]
        if not licenses:
            sys.exit(f"{path}@{version} has no licence file")
        texts = [(directory / f).read_text(errors="replace").strip() for f in licenses]
        body = []
        if all(is_pure_apache(t) for t in texts):
            apache_only.append(f"{path} {version}")
        else:
            body.extend(texts)
        body.extend((directory / f).read_text(errors="replace").strip() for f in notices)
        if body:
            sections.append(f"{path} {version}\n\n" + "\n\n".join(body))

    content = (
        "Go modules compiled into the Kubernetes engine (libkubenexus_client.so).\n\n"
        "Licensed under the Apache License, Version 2.0, with the full text shown under that "
        "licence:\n\n" + "\n".join(f"  {m}" for m in apache_only) + "\n\n"
        "The licences and notices below are reproduced from the modules themselves.\n\n"
        + "\n\n\n".join("=" * 72 + "\n" + s for s in sections) + "\n"
    )
    write_json(CONFIG / "licenses" / "lic_go-module-notices.json", {
        "content": content,
        "hash": "go-module-notices",
        "name": "Go module licences and notices",
        "url": "https://pkg.go.dev",
    })
    write_json(CONFIG / "libraries" / "lib_native_go_modules.json", {
        "uniqueId": "dev.hridaya.kubenexus:native-go-modules",
        "name": "Go modules in the Kubernetes engine",
        "description": "Third-party Go modules compiled into the Go engine alongside client-go: "
        + ", ".join(p for p, _ in modules if not p.startswith(KUBERNETES))
        + ".",
        "website": "https://pkg.go.dev",
        "licenses": ["Apache-2.0", "go-module-notices"],
        "developers": [{"name": "The Go module authors"}],
        "tag": "native",
    })

    mobile = gomobile_version()
    go_license = (module_dir("golang.org/x/mobile", mobile) / "LICENSE").read_text().strip()
    write_json(CONFIG / "licenses" / "lic_go-bsd-3-clause.json", {
        "content": go_license + "\n",
        "hash": "go-bsd-3-clause",
        "name": "BSD 3-Clause License (The Go Authors)",
        "spdxId": "BSD-3-Clause",
        "url": "https://go.dev/LICENSE",
    })
    write_json(CONFIG / "libraries" / "lib_native_go_runtime.json", {
        "uniqueId": "dev.hridaya.kubenexus:native-go-runtime",
        "name": "Go runtime, standard library and gomobile bindings",
        "artifactVersion": mobile,
        "description": "The Go runtime and standard library, and gomobile's Java/Go binding glue "
        "(golang.org/x/mobile), compiled into the Kubernetes engine.",
        "website": "https://go.dev",
        "licenses": ["go-bsd-3-clause"],
        "developers": [{"name": "The Go Authors", "organisationUrl": "https://go.dev"}],
        "tag": "native",
    })

    client_go = run("go", "list", "-m", "-f", "{{.Version}}", "k8s.io/client-go").strip()
    update_json(
        CONFIG / "libraries" / "lib_native_kubernetes_go_client.json",
        artifactVersion=client_go.removeprefix("v"),
        description=", ".join(p for p, _ in modules if p.startswith(KUBERNETES))
        + ". Used unmodified from upstream.",
    )

    zig, ghostty = zig_pins()
    update_json(CONFIG / "libraries" / "lib_native_libghostty.json", artifactVersion=ghostty)
    update_json(CONFIG / "libraries" / "lib_native_zig_std.json", artifactVersion=zig)


if __name__ == "__main__":
    main()
