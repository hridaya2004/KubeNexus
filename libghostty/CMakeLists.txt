# KubeNexus terminal library, built through CMake.
#
# For every ABI in KUBENEXUS_ABIS this builds Ghostty's libghostty-vt static
# library through Ghostty 1.4.0's own CMake support (which invokes
# `zig build -Demit-lib-vt` for the Android target) and compiles
# libghostty_jni.so against it with the NDK toolchain, keeping the flags of
# the previous Zig build.
#
#   make jni-zig
#
# or directly, per ABI:
#
#   cmake -S libghostty -B build/native/zig/<abi> \
#     -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
#     -DANDROID_ABI=<abi> -DANDROID_PLATFORM=android-35
#   cmake --build build/native/zig/<abi>
#   cmake --install build/native/zig/<abi>
#
# The Ghostty source is FetchContent'd at the pin in build.zig.zon. To build
# against a local checkout, pass -DFETCHCONTENT_SOURCE_DIR_GHOSTTY=<path>; a
# vendored copy under libghostty/zig-pkg/ is used automatically when present.

cmake_minimum_required(VERSION 3.19)

# Build the bridge with size optimisation, matching the old Zig ReleaseSmall
# build. Ghostty itself is still built with ReleaseSmall below.
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE MinSizeRel CACHE STRING "Build type" FORCE)
endif()

project(kubenexus_terminal LANGUAGES C)

# ABIs the app ships (see Makefile ABIS and android/app/build.gradle.kts).
# arm64-v8a, armeabi-v7a and x86_64.
set(KUBENEXUS_ABIS arm64-v8a armeabi-v7a x86_64 CACHE STRING
    "Android ABIs to build libghostty_jni for")

# The NDK toolchain builds one ABI per configure; the bridge target is named
# after it so its output stays unique in the build tree.
if(ANDROID_ABI)
    set(KUBENEXUS_ABIS "${ANDROID_ABI}")
endif()

# minSdk version, from android/gradle/libs.versions.toml.
set(MIN_SDK "35" CACHE STRING "Android minSdk version")

find_program(ZIG_EXECUTABLE zig REQUIRED)
message(STATUS "Found zig: ${ZIG_EXECUTABLE}")

# Pin of the Ghostty source, kept in sync with libghostty/build.zig.zon.
file(READ "${CMAKE_CURRENT_SOURCE_DIR}/build.zig.zon" KUBENEXUS_ZON)
string(REGEX MATCH "github\\.com/ghostty-org/ghostty[^#]*#([a-f0-9]+)"
    _GHOSTTY_PIN_MATCH "${KUBENEXUS_ZON}")
if(NOT _GHOSTTY_PIN_MATCH)
    message(FATAL_ERROR
        "Could not read the pinned Ghostty commit from build.zig.zon")
endif()
set(GHOSTTY_PIN "${CMAKE_MATCH_1}")

# Use the vendored Ghostty checkout when present so local builds do not need
# a network round trip; CI fetches the pinned commit instead.
file(GLOB KUBENEXUS_GHOSTTY_CACHE "${CMAKE_CURRENT_SOURCE_DIR}/zig-pkg/ghostty-*")
if(KUBENEXUS_GHOSTTY_CACHE AND NOT FETCHCONTENT_SOURCE_DIR_GHOSTTY)
    list(GET KUBENEXUS_GHOSTTY_CACHE 0 FETCHCONTENT_SOURCE_DIR_GHOSTTY)
    set(FETCHCONTENT_SOURCE_DIR_GHOSTTY
        "${FETCHCONTENT_SOURCE_DIR_GHOSTTY}" CACHE PATH
        "Local checkout of the Ghostty source")
endif()

include(FetchContent)
# Keep Ghostty's own native build target out of the default build; only the
# per-ABI target we ask for should run. EXCLUDE_FROM_ALL needs CMake 3.28+.
set(_KUBENEXUS_FETCH_EXCLUDE "")
if(CMAKE_VERSION VERSION_GREATER_EQUAL "3.28")
    set(_KUBENEXUS_FETCH_EXCLUDE EXCLUDE_FROM_ALL)
endif()
FetchContent_Declare(
    ghostty
    GIT_REPOSITORY https://github.com/ghostty-org/ghostty.git
    GIT_TAG "${GHOSTTY_PIN}"
    ${_KUBENEXUS_FETCH_EXCLUDE})
FetchContent_MakeAvailable(ghostty)

# ghostty_vt_add_target() appends its own optimize flag (ReleaseFast by
# default) and Zig rejects a repeated -Doptimize. Point it at the project's
# ReleaseSmall profile; the helper reads this variable from the caller's scope.
set(_GHOSTTY_ZIG_OPT_FLAG "-Doptimize=ReleaseSmall")

foreach(abi IN LISTS KUBENEXUS_ABIS)
    # The ".${MIN_SDK}" suffix is Zig's Android API level, so Ghostty is built
    # against the same NDK API level as the bridge and the app's minSdk.
    if(abi STREQUAL "arm64-v8a")
        set(zig_target aarch64-linux-android.${MIN_SDK})
    elseif(abi STREQUAL "armeabi-v7a")
        set(zig_target arm-linux-androideabi.${MIN_SDK})
    elseif(abi STREQUAL "x86_64")
        set(zig_target x86_64-linux-android.${MIN_SDK})
    else()
        message(FATAL_ERROR "Unsupported Android ABI: ${abi}")
    endif()

    # Ghostty 1.4.0's CMake support builds libghostty-vt.a and headers for
    # this Android target and exposes them as ghostty-vt-static-<abi>.
    ghostty_vt_add_target(NAME "${abi}" ZIG_TARGET "${zig_target}")

    # The JNI bridge, compiled with the NDK for the same ABI.
    add_library(kubenexus_terminal_${abi} SHARED src/ghostty_bridge.c)
    set_target_properties(kubenexus_terminal_${abi} PROPERTIES
        OUTPUT_NAME ghostty_jni)

    target_compile_definitions(kubenexus_terminal_${abi} PRIVATE
        GHOSTTY_STATIC
        _Nullable=
        _Nonnull=
        _Null_unspecified=
        __ANDROID_MIN_SDK_VERSION__=${MIN_SDK})

    target_compile_options(kubenexus_terminal_${abi} PRIVATE
        -std=c11 -Wall -Wextra)

    target_link_libraries(kubenexus_terminal_${abi}
        PRIVATE ghostty-vt-static-${abi})

    target_link_options(kubenexus_terminal_${abi} PRIVATE
        -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/src/version-script.map
        -Wl,-z,max-page-size=16384
        -Wl,-z,common-page-size=16384
        -Wl,--gc-sections)

    install(TARGETS kubenexus_terminal_${abi} DESTINATION "${abi}")
endforeach()
