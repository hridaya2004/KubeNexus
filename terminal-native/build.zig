const std = @import("std");
const builtin = @import("builtin");
const ndk = @import("src/ndk.zig");

fn parseMinSdk(src: []const u8) ?u32 {
    const needle = "minSdk";
    var i: usize = 0;
    while (std.mem.indexOfPos(u8, src, i, needle)) |pos| {
        var j = pos + needle.len;
        while (j < src.len and (src[j] == ' ' or src[j] == '\t' or src[j] == '=')) : (j += 1) {}
        const start = j;
        while (j < src.len and src[j] >= '0' and src[j] <= '9') : (j += 1) {}
        if (j > start) {
            if (std.fmt.parseInt(u32, src[start..j], 10)) |v| return v else |_| {}
        }
        i = pos + needle.len;
    }
    return null;
}

fn resolveAndroidApiLevel(b: *std.Build) u32 {
    if (b.option(u32, "android-api", "Android API level (defaults to $ANDROID_API_LEVEL, $ANDROID_MIN_SDK, or gradle minSdk)")) |v| return v;
    if (b.graph.environ_map.get("ANDROID_API_LEVEL")) |s| {
        return std.fmt.parseInt(u32, std.mem.trim(u8, s, " \t\r\n"), 10) catch @panic("invalid ANDROID_API_LEVEL: expected integer");
    }
    if (b.graph.environ_map.get("ANDROID_MIN_SDK")) |s| {
        return std.fmt.parseInt(u32, std.mem.trim(u8, s, " \t\r\n"), 10) catch @panic("invalid ANDROID_MIN_SDK: expected integer");
    }
    if (readGradleMinSdk(b)) |v| return v;
    std.debug.panic("cannot determine Android API level: pass -Dandroid-api, set ANDROID_API_LEVEL, or ensure android/app/build.gradle.kts contains `minSdk = <n>`", .{});
}

fn readGradleMinSdk(b: *std.Build) ?u32 {
    const root = b.build_root.path orelse ".";
    const candidates: []const []const u8 = &.{
        b.pathResolve(&.{ root, "../android/app/build.gradle.kts" }),
        b.pathResolve(&.{ root, "android/app/build.gradle.kts" }),
        "../android/app/build.gradle.kts",
        "android/app/build.gradle.kts",
    };
    for (candidates) |path| {
        const content = std.Io.Dir.cwd().readFileAlloc(b.graph.io, path, b.allocator, .limited(1 << 20)) catch continue;
        defer b.allocator.free(content);
        if (parseMinSdk(content)) |v| return v;
    }
    return null;
}

fn parseTargetQuery(triple: []const u8, default_api: u32) std.Target.Query {
    var query = std.Target.Query.parse(.{ .arch_os_abi = std.mem.trim(u8, triple, " \t") }) catch |err| {
        std.debug.panic("invalid -Dtarget '{s}': {s}", .{ triple, @errorName(err) });
    };
    if (query.android_api_level == null) query.android_api_level = default_api;
    return query;
}

fn defaultTargetQueries(b: *std.Build, default_api: u32) []const std.Target.Query {
    const specs = [_][]const u8{ "aarch64-linux-android", "arm-linux-androideabi", "x86_64-linux-android", "x86-linux-android" };
    const targets = b.allocator.alloc(std.Target.Query, specs.len) catch @panic("OOM");
    for (specs, targets) |spec, *target| target.* = parseTargetQuery(spec, default_api);
    return targets;
}

fn resolveBuildTargets(b: *std.Build) []const std.Target.Query {
    const default_api = resolveAndroidApiLevel(b);
    const maybe_target = b.option(
        []const u8,
        "target",
        "Comma-separated Android target triples (default: arm64-v8a, armeabi-v7a, x86_64, x86 at gradle minSdk)",
    ) orelse return defaultTargetQueries(b, default_api);

    var targets = std.ArrayList(std.Target.Query).empty;
    var it = std.mem.splitScalar(u8, maybe_target, ',');
    while (it.next()) |triple| {
        if (std.mem.trim(u8, triple, " \t").len == 0) continue;
        targets.append(b.allocator, parseTargetQuery(triple, default_api)) catch @panic("OOM");
    }
    if (targets.items.len == 0) std.debug.panic("no valid targets in -Dtarget '{s}'", .{maybe_target});
    return targets.toOwnedSlice(b.allocator) catch @panic("OOM");
}

fn ndkPrebuiltTag() []const u8 {
    const os_part = switch (builtin.os.tag) {
        .macos => "darwin",
        .linux => "linux",
        .windows => "windows",
        else => @panic("Unsupported host OS for Android NDK prebuilt toolchain"),
    };

    const arch_part = switch (builtin.cpu.arch) {
        .x86_64 => "x86_64",
        .aarch64 => if (builtin.os.tag == .macos) "x86_64" else "aarch64",
        else => @panic("Unsupported host architecture for Android NDK prebuilt toolchain"),
    };

    return std.fmt.comptimePrint("{s}-{s}", .{ os_part, arch_part });
}

fn isNdkDir(b: *std.Build, candidate: []const u8) bool {
    const toolchains_path = b.pathJoin(&.{ candidate, "toolchains", "llvm" });
    if (std.Io.Dir.openDirAbsolute(b.graph.io, toolchains_path, .{})) |dir| {
        dir.close(b.graph.io);
        return true;
    } else |_| return false;
}

fn findLatestNdk(b: *std.Build, ndk_dir: []const u8) ?[]const u8 {
    var best: ?[]const u8 = null;
    if (std.Io.Dir.openDirAbsolute(b.graph.io, ndk_dir, .{ .iterate = true })) |dir| {
        defer dir.close(b.graph.io);
        var iter = dir.iterate();
        while (iter.next(b.graph.io) catch null) |entry| {
            if (entry.kind != .directory) continue;
            const candidate = b.pathJoin(&.{ ndk_dir, entry.name });
            if (!isNdkDir(b, candidate)) continue;
            if (best == null or std.mem.order(u8, entry.name, std.fs.path.basename(best.?)) == .gt) {
                best = candidate;
            }
        }
    } else |_| return null;
    return best;
}

fn resolveNdkHome(b: *std.Build, ndk_root: []const u8) []const u8 {
    if (ndk_root.len == 0) return ndk_root;

    if (isNdkDir(b, ndk_root)) return ndk_root;
    if (findLatestNdk(b, ndk_root)) |latest| return latest;

    return ndk_root;
}

fn resolveSdkRoot(b: *std.Build) []const u8 {
    if (b.graph.environ_map.get("ANDROID_HOME")) |sdk| return sdk;
    if (b.graph.environ_map.get("ANDROID_SDK_ROOT")) |sdk| return sdk;
    if (b.graph.environ_map.get("HOME")) |home| return b.pathJoin(&.{ home, "Android", "Sdk" });
    std.debug.panic("cannot locate Android SDK: set ANDROID_HOME or ANDROID_SDK_ROOT", .{});
}

fn buildNativeLibrary(
    b: *std.Build,
    target: std.Build.ResolvedTarget,
    optimize: std.builtin.OptimizeMode,
) *std.Build.Step.Compile {
    const ghostty_dep = b.dependency("ghostty", .{
        .target = target,
        .optimize = optimize,
        .simd = false,
    });

    const ndk_root = b.graph.environ_map.get("ANDROID_NDK_HOME") orelse
        b.graph.environ_map.get("ANDROID_NDK_ROOT") orelse blk: {
        break :blk b.pathJoin(&.{ resolveSdkRoot(b), "ndk" });
    };
    const ndk_home = resolveNdkHome(b, ndk_root);
    if (!isNdkDir(b, ndk_home)) {
        std.debug.panic("Android NDK not found at '{s}': set ANDROID_NDK_HOME or install an NDK under <sdk>/ndk", .{ndk_home});
    }

    const android_target = ndk.getAndroidTriple(target.result) catch {
        std.debug.panic("target must be Android", .{});
    };
    std.debug.assert(target.result.os.tag == .linux);
    const android_api_version: u32 = target.result.os.version_range.linux.android;

    const ndk_sysroot = b.pathJoin(&.{
        ndk_home,
        "toolchains",
        "llvm",
        "prebuilt",
        ndkPrebuiltTag(),
        "sysroot",
    });

    const libc_config = ndk.createLibC(
        b,
        android_target,
        android_api_version,
        ndk_sysroot,
    );

    const include_dir = b.pathJoin(&.{ ndk_sysroot, "usr", "include" });
    const target_include_dir = b.pathJoin(&.{ include_dir, android_target });

    const root_module = b.createModule(.{
        .root_source_file = b.path("src/ghostty_bridge.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
        // Unstripped: AGP strips the shipped copy and keeps the symbols for Play.
        .strip = false,
        // Unwind tables and frame pointers let Android's unwinder walk native frames, so a
        // crash report shows the whole stack rather than stopping at the first native frame,
        // which would leave the debug symbols shipped to Play with nothing to symbolicate.
        .unwind_tables = .async,
        .omit_frame_pointer = false,
    });
    root_module.addIncludePath(b.path("src"));
    root_module.addImport("ghostty-vt", ghostty_dep.module("ghostty-vt"));
    root_module.addIncludePath(.{ .cwd_relative = include_dir });
    root_module.addIncludePath(.{ .cwd_relative = target_include_dir });

    const api_dir = b.fmt("{d}", .{android_api_version});
    // Recent NDK headers require __ANDROID_MIN_SDK_VERSION__ (Zig's
    // translate-c uses an unversioned triple and doesn't define it) and use
    // `[_Nullable N]` annotations that Zig's translate-c cannot parse, so
    // provide/neutralize them for all C code in this module. Values derive
    // from the resolved target, never from hardcoded versions.
    root_module.addCMacro("__ANDROID_MIN_SDK_VERSION__", api_dir);
    root_module.addCMacro("_Nullable", "");
    root_module.addCMacro("_Nonnull", "");
    root_module.addCMacro("_Null_unspecified", "");

    const lib_dir = b.pathJoin(&.{ ndk_sysroot, "usr", "lib", android_target, api_dir });
    root_module.addLibraryPath(.{ .cwd_relative = lib_dir });
    root_module.linkSystemLibrary("log", .{});
    root_module.linkSystemLibrary("z", .{});
    root_module.link_libc = true;

    const lib = b.addLibrary(.{
        .linkage = .dynamic,
        // ABI-suffixed so the default install step does not collide when
        // several targets are built in one invocation. The jni step below
        // renames the artifact back to libghostty_jni.so per ABI directory.
        .name = b.fmt("ghostty_jni_{s}", .{ndk.getOutputDir(target.result) catch unreachable}),
        .root_module = root_module,
    });
    lib.link_function_sections = true;
    lib.link_data_sections = true;
    lib.link_gc_sections = true;
    lib.link_eh_frame_hdr = true;
    // Google Play requires 16 KB-aligned LOAD segments for apps targeting Android 15+.
    // LLD only defaults to that on arm64 (64 KB); x86_64 and x86 would otherwise get 4 KB.
    lib.link_z_max_page_size = 16384;
    lib.link_z_common_page_size = 16384;
    lib.lto = .thin;
    root_module.strip = false;

    lib.setLibCFile(libc_config);
    lib.version_script = b.path("src/version-script.map");

    b.installArtifact(lib);
    return lib;
}

pub fn build(b: *std.Build) void {
    const optimize = b.standardOptimizeOption(.{});
    const build_targets = resolveBuildTargets(b);

    const native_step = b.step("native", "Build native JNI library");
    const jni_step = b.step("jni", "Build native library and copy to jniLibs");

    for (build_targets) |target_query| {
        const resolved_target = b.resolveTargetQuery(target_query);
        const native_lib = buildNativeLibrary(b, resolved_target, optimize);
        native_step.dependOn(&native_lib.step);

        const abi_name = ndk.getOutputDir(resolved_target.result) catch unreachable;
        const jni_lib_dir = b.fmt("../android/app/src/main/jniLibs/{s}", .{abi_name});

        const mkdir_jni_libs = b.addSystemCommand(&.{ "mkdir", "-p", jni_lib_dir });
        mkdir_jni_libs.step.dependOn(&native_lib.step);

        const copy_to_jni_libs = b.addSystemCommand(&.{"cp"});
        copy_to_jni_libs.step.dependOn(&mkdir_jni_libs.step);
        copy_to_jni_libs.addFileArg(native_lib.getEmittedBin());
        _ = copy_to_jni_libs.addArg(b.fmt("{s}/libghostty_jni.so", .{jni_lib_dir}));

        jni_step.dependOn(&copy_to_jni_libs.step);
    }
}
