# Third-Party Notices — KubeNexus

KubeNexus is free and open-source software that bundles and links the components
below. This file satisfies the attribution obligations of the MIT License and
sections 4(a), 4(c) and 4(d) of the Apache License, Version 2.0.

The root `LICENSE` (Apache-2.0 plus the trademark reservation) and `NOTICE` are in place;
see "Outstanding obligations" at the bottom for the rest.

---

## 1. Ghostty (terminal emulator core)

The terminal rendering engine is **Ghostty**, compiled into the app as native
code via its `libghostty` library.

- **Upstream:** <https://github.com/ghostty-org/ghostty>
- **Pinned commit:** `683d8db643b95cf229bfb5fe9fab9ae677920343`
- **Vendored version:** `1.3.2-dev`
- **License:** MIT
- **Copyright:** Copyright (c) 2024 Mitchell Hashimoto, Ghostty contributors

```
MIT License

Copyright (c) 2024 Mitchell Hashimoto, Ghostty contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

**Embedding is explicitly supported upstream.** Ghostty's README invites third
parties to "use `libghostty` to build a terminal emulator or embed a terminal
into their own applications," and points to
[Ghostling](https://github.com/ghostty-org/ghostling) as a reference
implementation. No separate permission or license exception is required.

**Trademark note.** The MIT License grants copyright rights only; it grants no
trademark rights. See [TRADEMARK-NOTICE.md](TRADEMARK-NOTICE.md) §2 before using
the name "Ghostty" anywhere user-facing.

## 2. Kubernetes client libraries

KubeNexus's Go core (`k8s-engine/`) is built on the official Kubernetes Go
client libraries.

- **Upstream:** <https://github.com/kubernetes>
- **License:** Apache License, Version 2.0
- **Copyright:** Copyright The Kubernetes Authors

| Module | Version |
|---|---|
| `k8s.io/api` | v0.36.3 |
| `k8s.io/apimachinery` | v0.36.3 |
| `k8s.io/client-go` | v0.36.3 |
| `k8s.io/streaming` | v0.36.3 |
| `k8s.io/klog/v2` | v2.140.0 (indirect) |
| `k8s.io/kube-openapi` | v0.0.0-20260317180543-43fb72c5454a (indirect) |
| `k8s.io/utils` | v0.0.0-20260210185600-b8788abfbbc2 (indirect) |
| `sigs.k8s.io/yaml` | v1.6.0 |
| `sigs.k8s.io/json` | v0.0.0-20250730193827-2d320260d730 (indirect) |
| `sigs.k8s.io/randfill` | v1.0.0 (indirect) |
| `sigs.k8s.io/structured-merge-diff/v6` | v6.3.3 (indirect) |

The full Apache License, Version 2.0 text is reproduced in
[LICENSE-Apache-2.0.txt](LICENSE-Apache-2.0.txt), and must ship in your
distribution (Apache-2.0 §4(a)).

### 2.1 No fork — client-go is consumed unmodified from upstream

`k8s-engine/go.mod` depends on `k8s.io/client-go` directly. No source file from the module has
been modified, which was verified on 26 September 2026: a personal mirror of the repository was
compared tag-for-tag against upstream and resolved to the same commit
(`7a46b3e8b7afc2ae403f4ff7deaa5d10bb727073`) with zero commits of its own.

Consequences:

- Apache-2.0 §4(b) — the duty to mark modified files — does not arise, and cannot arise while
  there is no fork.
- The build does not depend on any personal GitHub account, so it cannot be broken by a repository
  being deleted, renamed, or suspended, and a floating tag cannot be swapped for modified code.
- `go get -u`, Dependabot and `govulncheck` behave normally.

Verified after the change: `go list -m k8s.io/client-go` resolves to `k8s.io/client-go v0.36.3`,
`go build ./...` and `go vet ./...` are clean, `gomobile bind` regenerates `kubenexus.aar`, and
the release APK builds.

### 2.2 Patch currency

KubeNexus pins the `v0.36.3` **release tag**, which is immutable, so there is no silent drift.
Patch level does not advance on its own, though — track
[k8s.io/client-go releases](https://github.com/kubernetes/client-go/releases) and bump
deliberately.

One upstream merge shortly after this pin referenced `fix-cve-2026-78662`, which bumps
`golang.org/x/crypto` to v0.56.0. **This does not affect KubeNexus:**
`golang.org/x/crypto` does not appear in `k8s-engine/go.mod` or `go.sum`, so it is not in the
verified build graph. Re-check if you later add a dependency that pulls it in.

### 2.3 Trademark note

Apache-2.0 §6 is explicit:

> *"This License does not grant permission to use the trade names, trademarks,
> service marks, or product names of the Licensor, except as required for
> reasonable and customary use in describing the origin of the Work and
> reproducing the content of the NOTICE file."*

So the Apache License gives you **no** right to the word "Kubernetes". The name
is a **registered trademark of LF Projects, LLC**, governed by a separate
trademark policy. See [TRADEMARK-NOTICE.md](TRADEMARK-NOTICE.md) §1 — this is the
single highest-risk item in this document.

## 3. What the native libraries actually contain

Checked against the built libraries (`llvm-nm` on `libghostty_jni.so`, `go list -deps` for the
Go engine):

| Library | Linked in | Attributed as |
|---|---|---|
| `libghostty_jni.so` | Ghostty (`libghostty-vt`) | Ghostty, MIT |
| | Wuffs (image decoding; 165 `wuffs_*` symbols) | Wuffs, MIT (dual MIT / Apache-2.0) |
| | Parts of the Zig standard library | Zig standard library, MIT |
| `libkubenexus_client.so` (gomobile AAR) | Go runtime and standard library, gomobile's Java/Go glue (`golang.org/x/mobile`, which also ships as the `go.*` classes) | Go runtime, standard library and gomobile bindings, BSD-3-Clause |
| | `k8s.io/*`, `sigs.k8s.io/*` | Kubernetes Go client libraries, Apache-2.0 |
| | 27 other Go modules under Apache-2.0, MIT, BSD-2, BSD-3 and ISC | Go modules in the Kubernetes engine, with each module's own licence and NOTICE text |

`libghostty/zig-pkg/` (fetched by Zig from `build.zig.zon`, not committed) also contains
`aro`, `translate_c` and `uucode` (build tools), zlib's C sources, `afl++`, fonts and colour
themes. None of those are in the shipped library: there are no zlib, `inflate`/`deflate`, `afl`
or font symbols in `libghostty_jni.so`. Two things to watch when bumping the Ghostty pin:

- **`afl++` is GPL-2.0.** If a future Ghostty ever linked it into the JNI library, the whole
  distribution would become GPL and could no longer be Apache-2.0. GPL-2.0-only is deliberately
  not an allowed licence in `app/build.gradle.kts`, and §5 has a symbol check.
- **Nerd Fonts is `.lazy`** and absent from the binary, so the SIL OFL's requirement to ship
  its text with the font files does not currently apply.

## 4. Transitive Go and Kotlin dependencies

### 4.1 In-app attribution (implemented)

KubeNexus renders this information in-app at **Settings → About → Open source licenses**, backed
by [AboutLibraries](https://github.com/mikepenz/AboutLibraries) 15.2.0 (Apache-2.0). The Gradle
plugin collects licence metadata at build time into `res/raw/aboutlibraries`, and the app renders it
with a Material 3 `LibrariesContainer`.

This matters for a hard reason, not just convenience: the native libraries are statically linked,
and MIT, BSD and ISC all require their notices to travel with every binary copy. A notice that lives
only in this repository does **not** satisfy that.

The metadata comes from two places, because Gradle only ever sees half of what ships:

| Source | Covers | Mechanism |
|---|---|---|
| Gradle resolution | Android/JVM artifacts | automatic |
| `android/config/libraries/` | the native components in §3 | declared; the Go ones generated |

### 4.2 Hand-declared native components

`android/config/libraries/` declares the six native components from §3. All of them except
Wuffs are kept current by `scripts/generate_notices.py` (`make notices`), which every APK build
runs; CI fails if the committed files are out of date:

- `lib_native_go_runtime.json` and `lib_native_go_modules.json`, with their licence texts
  `lic_go-bsd-3-clause.json` and `lic_go-module-notices.json`, are generated in full. The script
  lists the modules the Go client package links, reads each one's `LICENSE` and `NOTICE` files
  from the module cache, and reproduces every licence that is not plain Apache-2.0, plus every
  NOTICE file.
- `lib_native_kubernetes_go_client.json` gets its version and module list from `go.mod`, and
  the Ghostty and Zig entries get their versions from `libghostty/build.zig.zon`.
- The Ghostty, Wuffs and Zig licence texts are copied by hand from their `LICENSE` files.

**When you add a dependency that ships code, declare it there.** `strictMode = FAIL` in
`app/build.gradle.kts` stops a release for an unreviewed licence id, but it cannot tell you a
native component or a copyright notice is missing.

### 4.3 Hand-supplied licence texts

MIT, BSD and ISC texts carry the project's own copyright line, which the SPDX templates replace with
a `<year> <copyright holders>` placeholder; shipping the template would satisfy nothing. So
`android/config/licenses/` holds those texts with the real notices: `mit-with-copyrights`
(Ghostty), `wuffs-mit`, `zig-mit`, `go-bsd-3-clause` and the generated `go-module-notices`.
Apache-2.0 resolves by SPDX id. Only licences a shipped component uses are listed in
`additionalLicenses`, so the screen shows no stray texts.

A known limitation: `BSD-3-Clause` still shows the SPDX template for Gradle-resolved dependencies
that use it. That is AboutLibraries' behaviour for third-party POMs and is outside this repo's
control.

### 4.4 Kotlin stdlib

AboutLibraries 15.2.0 is published against kotlin-stdlib 2.4.10. The project builds with Kotlin
2.4.21, so the stdlib resolves to that newer version and no `resolutionStrategy.force` is needed.

### 4.5 Kotlin / Android dependencies (first-party, no third-party SDKs)

Every declared dependency resolves to Google/Jetpack first-party artifacts or small open-source
libraries: AndroidX (Core, Lifecycle, Activity, Navigation, Room, DataStore, Compose, Material3, Adaptive,
Window Size Class), Kotlin stdlib/coroutines/serialization, Hilt (Apache-2.0), snakeyaml-engine
(Apache-2.0, kubeconfig parsing), AboutLibraries (Apache-2.0), LeakCanary (Apache-2.0,
**debug-only**), `org.json` (tests only), `javax.inject`.

One exception is not open source: **Google Play In-App Updates** (`com.google.android.play:app-update`
and `app-update-ktx`, with `core-common`), under the Play Core Software Development Kit Terms of
Service, and the `play-services-basement` and `play-services-tasks` libraries it pulls in, under the
Android Software Development Kit License. Both licences are allowed in `app/build.gradle.kts`
(`PCSDKToS`, `ASDKL`) for this library only. It asks the Play Store app on the device whether an
update exists. Google lists the data that check uses (device metadata, app version, installed
modules) in the Data safety section of its In-App Updates guide; account for it in the Data
safety answers below.

**No advertising, analytics, or crash-reporting SDKs are present.** This is worth keeping true:
it is what keeps the submitted Data safety answers narrow — a single declared type, App activity
→ Other user-generated content, for pod logs the user chooses to upload to dpaste.org. Adding
one SDK will invalidate them, as would removing the dpaste.org upload, adding any SDK that phones
home, or adding a hosted sync feature.

### 4.6 Generating a compliance export

For an auditor, or for a Play review, produce a checkable report:

```bash
make licenses                          # -> legal/compliance/
make licenses LICENSES_DIR=/tmp/out    # anywhere else
make licenses-clean
```

It prints the library count, the distinct licences in use, every native component, and — the part
that actually needs a human — the contents of the report's `ARTIFACTS WITHOUT LICENSE` and
`UNKNOWN LICENSES` sections, which should both be empty. `LICENSES_DIR` accepts a relative or
absolute path, and the target fails rather than reporting success if no report was written.

Nothing here is committed. The report is derived entirely from the build, and the licences that
actually ship come from `res/raw/aboutlibraries`, which the plugin generates during the Android
build — not from this export. Regenerating it whenever you want it keeps dependency bumps from
producing a diff of generated files. The output is renamed to `kubenexus-licenses.txt` and
`.csv` so a copy that travels on its own still says what it is.

## 5. Outstanding obligations

| # | Obligation | Status |
|---|---|---|
| 1 | Root `LICENSE` for KubeNexus itself | **Done** — `LICENSE` (Apache-2.0 + trademark reservation) |
| 2 | Root `NOTICE` file | **Done** — `NOTICE` |
| 3 | `legal/LICENSE-Apache-2.0.txt` | **Done** — verbatim Apache-2.0 |
| 4 | Ghostty, Wuffs and Zig notices reachable in the shipped app | **Done** — licences screen (§4.1) |
| 5 | Per-file modification notices in the `client-go` fork (§4(b)) | **Not required** — fork verified unmodified (§2.1) |
| 6 | Remove the no-op `replace` from `k8s-engine/go.mod` | **Done** — depends on upstream directly (§2.1) |
| 7 | Confirm `afl++` is never linked into a shipped `.so` | **Done** — checked by `make verify-jni` on every CI build |
| 8 | Confirm LeakCanary is excluded from the release variant | **Unverified** — it is behind `debugImplementation`, so correct today |
| 9 | Declare a newly shipped native dependency in `config/libraries/` | **Manual** — see §4.2 |
| 10 | Produce an audit report | **Done** — `make licenses` (§4.6), and attached to every CI build; output is not committed |
| 11 | Go runtime and Go module notices in the licences screen | **Done** — generated on every APK build by `make notices` (§4.2); CI fails if they are stale |

### On obligation 7

`afl++` is GPL-2.0 and sits inside Ghostty's tree. It is a fuzzing harness and must never be linked
into a library we ship. Nothing in the licence screen declares it, because it is not our
dependency, so `make verify-jni` (run by CI before every release build) fails if any shipped
`.so`, including those inside the gomobile AAR, contains `afl_` symbols.

### On obligation 4

MIT requires the notice "be included in all copies or substantial portions of the
Software." Ghostty is statically linked native code inside your AAB, so the
notice must be reachable by the person using the app — not only in your Git
repository. A settings screen such as **Settings → About → Open source licenses**
that renders this file satisfies that. Most developers discover this only when a
Play reviewer or a user asks.

## 6. Verifying before you ship

```bash
# Ghostty and the Zig packages it pulls in are fetched and pinned
ls libghostty/zig-pkg/ | grep -i ghostty

# Confirm the pinned Ghostty commit still matches build.zig.zon
grep -A2 'ghostty' libghostty/build.zig.zon

# Confirm client-go is consumed straight from upstream, with no fork redirect
grep -n 'replace' k8s-engine/go.mod || echo "no replace directives"
go -C k8s-engine list -m k8s.io/client-go

# Confirm no ad/analytics SDK crept in
grep -rniE 'firebase|play-services-ads|crashlytics|mixpanel|appsflyer|adjust|amplitude|sentry' \
  android/gradle/libs.versions.toml android/**/*.kts || echo "clean"
```
