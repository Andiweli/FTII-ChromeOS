cmake_minimum_required(VERSION 3.22.1)

project(ft2_clone_chromeos LANGUAGES C CXX)

# Produce ELF load segments that also work on Android devices using 16 KiB pages.
# This keeps the APK forward-compatible while remaining valid on current ChromeOS.
set(ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES ON)

set(FT2_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../..")
set(SDL2_ROOT "${FT2_ROOT}/third_party/SDL2")

# SDL2 is built from the exact 2.32.10 source version used by upstream FT2.
set(SDL_SHARED ON CACHE BOOL "Build SDL2 as a shared library" FORCE)
set(SDL_STATIC OFF CACHE BOOL "Do not build an unused static SDL2" FORCE)
set(SDL_TEST OFF CACHE BOOL "Do not build SDL2 tests" FORCE)
add_subdirectory("${SDL2_ROOT}" "${CMAKE_CURRENT_BINARY_DIR}/SDL2")
target_link_options(SDL2 PRIVATE "-Wl,-z,max-page-size=16384")

file(GLOB FT2_SOURCES CONFIGURE_DEPENDS
    "${FT2_ROOT}/src/*.c"
    "${FT2_ROOT}/src/gfxdata/*.c"
    "${FT2_ROOT}/src/mixer/*.c"
    "${FT2_ROOT}/src/scopes/*.c"
    "${FT2_ROOT}/src/modloaders/*.c"
    "${FT2_ROOT}/src/smploaders/*.c"
)

add_library(main SHARED ${FT2_SOURCES})

target_include_directories(main PRIVATE
    "${FT2_ROOT}/src"
    "${SDL2_ROOT}/include"
)

target_compile_definitions(main PRIVATE
    FT2_CHROMEOS=1
    _FILE_OFFSET_BITS=64
    _GNU_SOURCE=1
)

target_compile_options(main PRIVATE
    -fvisibility=hidden
    $<$<CONFIG:Release>:-O3>
    $<$<CONFIG:Release>:-ffast-math>
)

target_link_options(main PRIVATE "-Wl,-z,max-page-size=16384")

find_library(ANDROID_LIBRARY android REQUIRED)
find_library(LOG_LIBRARY log REQUIRED)

target_link_libraries(main PRIVATE
    SDL2
    m
    ${ANDROID_LIBRARY}
    ${LOG_LIBRARY}
)
