cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             opusjni

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/opus_jni.c )

include_directories( src/main/cpp )

# one day we will be able to replace those lines with find_library(opus-lib opus)
add_library(opus SHARED IMPORTED)
string(REPLACE "/app/" "/libopus/" OPUS_TARGET "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libopus.so")
string(REGEX REPLACE "/[^/]+/obj/" "/*/obj/local/" OPUS_TARGET ${OPUS_TARGET})
file(GLOB OPUS_TARGET ${OPUS_TARGET})
include_directories(../libopus/src/main/cpp/opus/include)
set_target_properties(opus PROPERTIES IMPORTED_LOCATION ${OPUS_TARGET})

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target libraries.
                       opusjni opus

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )
 
