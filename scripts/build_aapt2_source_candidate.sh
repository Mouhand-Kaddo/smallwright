#!/usr/bin/env bash
# Replay the pinned local ARM64 AAPT2 source build without fetching inputs.
set -euo pipefail

repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo"

if [[ ( $# -ne 2 && $# -ne 3 ) || $1 != --output || ( $# -eq 3 && $3 != --resume ) ]]; then
  printf 'Usage: %s --output toolchains/IGNORED-DIRECTORY [--resume]\n' "$0" >&2
  exit 2
fi
output=$2
resume=${3:-}
case $output in
  toolchains/*) ;;
  *) printf 'Output must be under toolchains/\n' >&2; exit 2 ;;
esac
output=$(realpath -m "$output")
case $output in
  "$repo"/toolchains/*) ;;
  *) printf 'Output escapes repository toolchains/\n' >&2; exit 2 ;;
esac
if [[ $resume == --resume ]]; then
  [[ -f "$output/.aapt2-replay" ]] || {
    printf 'No replay marker in existing output: %s\n' "$output" >&2
    exit 2
  }
elif [[ -e $output || -L $output ]]; then
  printf 'Output already exists: %s\n' "$output" >&2
  exit 2
fi

sdk="$repo/toolchains/android-sdk"
cmake="$sdk/cmake/3.31.4/bin/cmake"
ninja="$sdk/cmake/3.31.4/bin/ninja"
toolchain="$sdk/ndk/28.2.13676358/build/cmake/android.toolchain.cmake"
sources="$repo/toolchains/aapt2-deps"
wrapper="$repo/toolchains/aapt2-source-c4edf85"
for input in "$cmake" "$ninja" "$toolchain"; do
  [[ -f $input ]] || { printf 'Missing tool: %s\n' "$input" >&2; exit 1; }
done

check_commit() {
  local name=$1 expected=$2 actual
  actual=$(git -C "$sources/$name" rev-parse HEAD)
  [[ $actual == "$expected" ]] || {
    printf '%s commit mismatch: %s\n' "$name" "$actual" >&2
    exit 1
  }
  if [[ $name != zlib && -n $(git -C "$sources/$name" status --porcelain) ]]; then
    printf '%s source checkout is dirty\n' "$name" >&2
    exit 1
  fi
}
check_commit expat 79dde19a8fe1dfb580d39de37854221a6de5f09b
check_commit fmtlib d50695f04854e474766da7d713bf8242f4ceb4f8
check_commit googletest cbeab5d01811d23ea488efc07f2c31f7c53024e2
check_commit abseil-cpp 2bf8472a92b84fe4617eeb50053e7a942edcb1b3
check_commit protobuf 31fc4250f78631cf8f4f1cc48b200acf11e9c884
check_commit zlib a6f47e8bd7c1ba412c4b5a3539aa77fe628d0172
check_commit libpng 812ea249a49e26a13fa080821dc094f33c0f6608
python3 scripts/prepare_aapt2_source_candidate.py "$wrapper" --verify

sysroot="$output/sysroot"
build="$output/build"
host="$output/host"
mkdir -p "$sysroot" "$build" "$host" "$output/sources/zlib"
if [[ $resume != --resume ]]; then
  printf '%s\n' 'Pinned local AAPT2 source-build replay' > "$output/.aapt2-replay"
# The pinned zlib CMake build renames zconf.h. Archive a clean committed copy
# so the original checkout is neither modified nor relied on for that header.
  git -C "$sources/zlib" archive HEAD | tar -xf - -C "$output/sources/zlib"
fi

android=(
  -G Ninja
  "-DCMAKE_MAKE_PROGRAM=$ninja"
  "-DCMAKE_TOOLCHAIN_FILE=$toolchain"
  -DANDROID_ABI=arm64-v8a
  -DANDROID_PLATFORM=android-31
  "-DCMAKE_INSTALL_PREFIX=$sysroot"
)
configure_android() {
  local name=$1 src=$2
  shift 2
  "$cmake" -S "$src" -B "$build/$name" "${android[@]}" "$@"
  "$cmake" --build "$build/$name" --parallel 1
  "$cmake" --install "$build/$name"
}

configure_android expat "$sources/expat/expat" \
  -DEXPAT_SHARED_LIBS=OFF -DEXPAT_BUILD_DOCS=OFF \
  -DEXPAT_BUILD_EXAMPLES=OFF -DEXPAT_BUILD_TESTS=OFF \
  -DEXPAT_BUILD_TOOLS=OFF -DEXPAT_BUILD_PKGCONFIG=OFF
configure_android fmtlib "$sources/fmtlib" \
  -DBUILD_SHARED_LIBS=OFF -DFMT_TEST=OFF -DFMT_DOC=OFF -DCMAKE_BUILD_TYPE=Release
configure_android googletest "$sources/googletest" \
  -DBUILD_SHARED_LIBS=OFF -DBUILD_GMOCK=OFF -Dgtest_build_tests=OFF
configure_android abseil "$sources/abseil-cpp" \
  -DBUILD_SHARED_LIBS=OFF -DABSL_BUILD_TESTING=OFF \
  -DABSL_PROPAGATE_CXX_STD=ON -DCMAKE_CXX_STANDARD=17
configure_android protobuf "$sources/protobuf" \
  "-DCMAKE_PREFIX_PATH=$sysroot" -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=ONLY \
  "-Dabsl_DIR=$sysroot/lib/cmake/absl" \
  -Dprotobuf_ABSL_PROVIDER=package -Dprotobuf_BUILD_TESTS=OFF \
  -Dprotobuf_BUILD_SHARED_LIBS=OFF -Dprotobuf_BUILD_PROTOC_BINARIES=OFF \
  -Dprotobuf_BUILD_LIBPROTOC=OFF -Dprotobuf_WITH_ZLIB=OFF
configure_android zlib "$output/sources/zlib" -DBUILD_SHARED_LIBS=OFF
configure_android libpng "$sources/libpng" \
  -DPNG_SHARED=OFF -DPNG_STATIC=ON -DPNG_TESTS=OFF \
  -DPNG_EXECUTABLES=OFF -DPNG_TOOLS=OFF \
  "-DZLIB_LIBRARY=$sysroot/lib/libz.a" \
  "-DZLIB_INCLUDE_DIR=$sysroot/include"

"$cmake" -S "$sources/protobuf" -B "$host/protobuf" -G Ninja \
  "-DCMAKE_MAKE_PROGRAM=$ninja" \
  "-DABSL_ROOT_DIR=$sources/abseil-cpp" \
  -DCMAKE_CXX_STANDARD=17 -Dprotobuf_ABSL_PROVIDER=module \
  -Dprotobuf_BUILD_TESTS=OFF -Dprotobuf_WITH_ZLIB=OFF \
  -Dprotobuf_BUILD_SHARED_LIBS=OFF
"$cmake" --build "$host/protobuf" --target protoc --parallel 1

# pkg-config may be present on the host, but it must see only ARM64 metadata.
PKG_CONFIG_LIBDIR="$sysroot/lib/pkgconfig:$sysroot/share/pkgconfig" \
"$cmake" -S "$wrapper" -B "$build/aapt2" "${android[@]}" \
  -DANDROID_BUILD_TOOLS_PATCH_VENDOR=OFF \
  "-DCMAKE_EXE_LINKER_FLAGS=-Wl,-Map=$output/aapt2-link.map" \
  "-DCMAKE_PREFIX_PATH=$sysroot" "-DCMAKE_FIND_ROOT_PATH=$sysroot" \
  -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=ONLY \
  "-DProtobuf_DIR=$sysroot/lib/cmake/protobuf" \
  "-DProtobuf_PROTOC_EXECUTABLE=$host/protobuf/protoc" \
  "-DGTest_DIR=$sysroot/lib/cmake/GTest" \
  "-DEXPAT_LIBRARY=$sysroot/lib/libexpat.a" \
  "-DProtobuf_LIBRARY=$sysroot/lib/libprotobuf.a"
PKG_CONFIG_LIBDIR="$sysroot/lib/pkgconfig:$sysroot/share/pkgconfig" \
  "$cmake" --build "$build/aapt2" --target aapt2 --parallel 1

binary="$build/aapt2/vendor/aapt2"
strip="$sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
"$strip" -o "$output/aapt2-stripped" "$binary"
sha256sum "$binary" "$output/aapt2-stripped" "$output/aapt2-link.map"
