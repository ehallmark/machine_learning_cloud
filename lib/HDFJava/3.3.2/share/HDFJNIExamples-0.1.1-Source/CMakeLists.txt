cmake_minimum_required(VERSION 3.1.0)
project ( HDFJNIExamples Java )

#-----------------------------------------------------------------------------
# Instructions for use : Normal Build
#
# For standard build of HDFJNI Examples.
#
#-----------------------------------------------------------------------------

#-----------------------------------------------------------------------------
# Define some CMake variables for use later in the project
#-----------------------------------------------------------------------------
set (HDFJNI_RESOURCES_DIR       ${HDFJNIExamples_SOURCE_DIR}/config/cmake)
set (HDFJNI_NATIVE_DIR          ${PROJECT_SOURCE_DIR}/native)

find_package (Java)
#-----------------------------------------------------------------------------
# Include some macros for reusable code
#-----------------------------------------------------------------------------
set (CMAKE_MODULE_PATH ${HDFJNI_RESOURCES_DIR})
include (${HDFJNI_RESOURCES_DIR}/UseJava.cmake)

#-----------------------------------------------------------------------------
# Setup output Directories
#-----------------------------------------------------------------------------
set (CMAKE_RUNTIME_OUTPUT_DIRECTORY
    ${PROJECT_BINARY_DIR}/bin CACHE PATH "Single Directory for all Executables."
)
set (CMAKE_LIBRARY_OUTPUT_DIRECTORY
    ${PROJECT_BINARY_DIR}/bin CACHE PATH "Single Directory for all Libraries"
)
set (CMAKE_ARCHIVE_OUTPUT_DIRECTORY
    ${PROJECT_BINARY_DIR}/bin CACHE PATH "Single Directory for all static libraries."
)
set (CMAKE_JAVA_TARGET_OUTPUT_DIR
    ${PROJECT_BINARY_DIR}/bin CACHE PATH "Single Directory for all java jars."
)

#-----------------------------------------------------------------------------
# Include the main src and config directories
#-----------------------------------------------------------------------------
set (HDFJNI_INCLUDE_DIRECTORIES
    ${HDFJNIExamples_BINARY_DIR}
    ${JAVA_INCLUDE_PATH}
    ${JAVA_INCLUDE_PATH2}
)
INCLUDE_DIRECTORIES (${HDFJNI_INCLUDE_DIRECTORIES})
set (CMAKE_JAVA_INCLUDE_PATH "")

set (MAKE_SYSTEM)
if (CMAKE_BUILD_TOOL MATCHES "make")
  set (MAKE_SYSTEM 1)
endif (CMAKE_BUILD_TOOL MATCHES "make")

set (CFG_INIT "/${CMAKE_CFG_INTDIR}")
if (MAKE_SYSTEM)
  set (CFG_INIT "")
endif (MAKE_SYSTEM)

#-----------------------------------------------------------------------------
# HDF-JAVA support
#-----------------------------------------------------------------------------
set (CMAKE_MODULE_PATH ${HDFJNI_RESOURCES_DIR} ${CMAKE_MODULE_PATH})

find_package (HDFJAVA NAMES ${HDFJAVA_PACKAGE_NAME})
if (HDFJAVA_FOUND)
  message (STATUS "HDFJAVA found: ${HDFJAVA_INCLUDE_DIRS}")
  set (CMAKE_JAVA_INCLUDE_PATH "${CMAKE_JAVA_INCLUDE_PATH};${HDFJAVA_INCLUDE_DIRS}")
else (HDFJAVA_FOUND)
  message (FATAL_ERROR " HDFJAVA is Required for HDFJNI Examples")
endif (HDFJAVA_FOUND)

#-----------------------------------------------------------------------------
# Dashboard and Testing Settings
#-----------------------------------------------------------------------------
option (BUILD_TESTING "Build HDFJNI Unit Testing" OFF)
if (BUILD_TESTING)
  set (DART_TESTING_TIMEOUT 1200
      CACHE INTEGER
      "Timeout in seconds for each test (default 1200=20minutes)"
  )
  enable_testing ()
  include (CTest)
  include (${HDFJNIExamples_SOURCE_DIR}/CTestConfig.cmake)
  configure_file (${HDFJNI_RESOURCES_DIR}/CTestCustom.cmake ${HDFJNIExamples_BINARY_DIR}/CTestCustom.ctest @ONLY)
endif (BUILD_TESTING)

#-----------------------------------------------------------------------------
# Add the HDFJNI Examples Target to the build
#-----------------------------------------------------------------------------
add_subdirectory (${HDFJNIExamples_SOURCE_DIR}/jnative   jnative)
