cmake_minimum_required(VERSION 3.16)
project(schnorr256k1_bench C)

# The C layer: libsecp256k1 and libschnorr256k1, both built from the submodules in third_party/
# with their own build flags, the way each ships. Host build:
#   cmake -S c -B build/c -DCMAKE_BUILD_TYPE=Release && cmake --build build/c
# Android (NDK): scripts/run-android-c.sh.

set(CMAKE_C_STANDARD 11)
if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

set(THIRD_PARTY ${CMAKE_CURRENT_SOURCE_DIR}/../third_party)

# libsecp256k1 as ACINQ's secp256k1-kmp builds it (native/build.gradle.kts, CMAKE_DEFAULT_OPTS).
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SECP256K1_ENABLE_MODULE_ECDH ON CACHE BOOL "" FORCE)
set(SECP256K1_ENABLE_MODULE_MUSIG ON CACHE BOOL "" FORCE)
set(SECP256K1_ENABLE_MODULE_RECOVERY ON CACHE BOOL "" FORCE)
set(SECP256K1_ENABLE_MODULE_SCHNORRSIG ON CACHE BOOL "" FORCE)
set(SECP256K1_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
set(SECP256K1_BUILD_CTIME_TESTS OFF CACHE BOOL "" FORCE)
set(SECP256K1_BUILD_EXHAUSTIVE_TESTS OFF CACHE BOOL "" FORCE)
set(SECP256K1_BUILD_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory(${THIRD_PARTY}/secp256k1 secp256k1 EXCLUDE_FROM_ALL)

set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_BENCH OFF CACHE BOOL "" FORCE)
add_subdirectory(${THIRD_PARTY}/libschnorr256k1 libschnorr256k1 EXCLUDE_FROM_ALL)

function(git_short dir out)
  execute_process(COMMAND git -C ${dir} rev-parse --short HEAD
                  OUTPUT_VARIABLE v OUTPUT_STRIP_TRAILING_WHITESPACE ERROR_QUIET)
  if(NOT v)
    set(v "unknown")
  endif()
  set(${out} ${v} PARENT_SCOPE)
endfunction()
git_short(${THIRD_PARTY}/secp256k1 SECP_VER)
git_short(${THIRD_PARTY}/libschnorr256k1 SCHNORR_VER)
git_short(${CMAKE_CURRENT_SOURCE_DIR}/.. HARNESS_VER)

add_executable(bench main.c vectors.c backend_libsecp256k1.c backend_libschnorr256k1.c)
target_include_directories(bench PRIVATE
  ${THIRD_PARTY}/secp256k1/include
  ${THIRD_PARTY}/libschnorr256k1/include)
target_link_libraries(bench PRIVATE secp256k1 schnorr256k1)
target_compile_definitions(bench PRIVATE
  LIBSECP256K1_VERSION="${SECP_VER}"
  LIBSCHNORR256K1_VERSION="${SCHNORR_VER}"
  BENCH_HARNESS_VERSION="${HARNESS_VER}"
  BENCH_COMPILER_FLAGS="each library with its own CMake flags, ${CMAKE_SYSTEM_PROCESSOR}, ${CMAKE_BUILD_TYPE}")
