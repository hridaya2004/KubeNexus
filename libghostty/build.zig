const std = @import("std");

const Abi = struct {
    name: []const u8,
    zig: []const u8,
    ndk: []const u8,
};

const abis = [_]Abi{
    .{ .name = "arm64-v8a", .zig = "aarch64-linux-android", .ndk = "aarch64-linux-android" },
    .{ .name = "armeabi-v7a", .zig = "arm-linux-androideabi", .ndk = "arm-linux-androideabi" },
    .{ .name = "x86_64", .zig = "x86_64-linux-android", .ndk = "x86_64-linux-android" },
    .{ .name = "x86", .zig = "x86-linux-android", .ndk = "i686-linux-android" },
};

pub fn build(b: *std.Build) void {
    const optimize = b.standardOptimizeOption(.{});
    const sysroot = b.option([]const u8, "sysroot", "NDK sysroot: <ndk>/toolchains/llvm/prebuilt/<host>/sysroot") orelse
        std.debug.panic("missing -Dsysroot; build through `make jni`", .{});
    const api = b.option(u32, "api", "Android API level, the app's minSdk") orelse
        std.debug.panic("missing -Dapi; build through `make jni`", .{});

    for (abis) |abi| {
        const lib = buildLibrary(b, abi, sysroot, api, optimize);
        const install = b.addInstallArtifact(lib, .{ .dest_dir = .{ .override = .{ .custom = abi.name } } });
        b.getInstallStep().dependOn(&install.step);
    }
}

fn buildLibrary(
    b: *std.Build,
    abi: Abi,
    sysroot: []const u8,
    api: u32,
    optimize: std.builtin.OptimizeMode,
) *std.Build.Step.Compile {
    var query = std.Target.Query.parse(.{ .arch_os_abi = abi.zig }) catch unreachable;
    query.android_api_level = api;
    const target = b.resolveTargetQuery(query);

    const ghostty = b.dependency("ghostty", .{
        .target = target,
        .optimize = optimize,
        .simd = false,
    });

    const include_dir = b.pathJoin(&.{ sysroot, "usr", "include" });
    const target_include_dir = b.pathJoin(&.{ include_dir, abi.ndk });
    const lib_dir = b.fmt("{s}/usr/lib/{s}/{d}", .{ sysroot, abi.ndk, api });

    const module = b.createModule(.{
        .root_source_file = b.path("src/ghostty_bridge.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
        .strip = false,
        .unwind_tables = .async,
        .omit_frame_pointer = false,
    });
    module.addImport("ghostty-vt", ghostty.module("ghostty-vt"));
    module.addIncludePath(b.path("src"));
    module.addIncludePath(.{ .cwd_relative = include_dir });
    module.addIncludePath(.{ .cwd_relative = target_include_dir });
    module.addLibraryPath(.{ .cwd_relative = lib_dir });
    module.linkSystemLibrary("log", .{});
    module.linkSystemLibrary("z", .{});
    module.addCMacro("__ANDROID_MIN_SDK_VERSION__", b.fmt("{d}", .{api}));
    module.addCMacro("_Nullable", "");
    module.addCMacro("_Nonnull", "");
    module.addCMacro("_Null_unspecified", "");

    const lib = b.addLibrary(.{
        .linkage = .dynamic,
        .name = "ghostty_jni",
        .root_module = module,
    });
    lib.setLibCFile(libcFile(b, abi, include_dir, target_include_dir, lib_dir));
    lib.version_script = b.path("src/version-script.map");
    lib.link_function_sections = true;
    lib.link_data_sections = true;
    lib.link_gc_sections = true;
    lib.link_eh_frame_hdr = true;
    lib.link_z_max_page_size = 16384;
    lib.link_z_common_page_size = 16384;
    lib.lto = .thin;
    return lib;
}
fn libcFile(
    b: *std.Build,
    abi: Abi,
    include_dir: []const u8,
    sys_include_dir: []const u8,
    crt_dir: []const u8,
) std.Build.LazyPath {
    const contents = b.fmt(
        \\include_dir={s}
        \\sys_include_dir={s}
        \\crt_dir={s}
        \\msvc_lib_dir=
        \\kernel32_lib_dir=
        \\gcc_dir=
        \\
    , .{ include_dir, sys_include_dir, crt_dir });
    return b.addWriteFiles().add(b.fmt("libc-{s}.conf", .{abi.name}), contents);
}
