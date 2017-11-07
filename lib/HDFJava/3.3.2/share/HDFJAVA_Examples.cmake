cmake_minimum_required(VERSION 3.2.2 FATAL_ERROR)
###############################################################################################################
# This script will build and run the examples from a compressed file
# Execute from a command line:
#     ctest -S HDFJAVA_Examples.cmake,HDFJNIExamples-0.1.1-Source -C Release -VV -O test.log
###############################################################################################################

set(INSTALLDIR "/Users/ehallmark/Downloads/CMake-hdfjava-3.3.2/HDF_Group/HDFJAVA/3.3.2")
set(CTEST_CMAKE_GENERATOR "Unix Makefiles")
set(CTEST_SOURCE_NAME ${CTEST_SCRIPT_ARG})
set(CTEST_DASHBOARD_ROOT ${CTEST_SCRIPT_DIRECTORY})
set(CTEST_BUILD_CONFIGURATION "Release")
#set(CTEST_USE_TAR_SOURCE "${CTEST_SCRIPT_ARG}")

###############################################################################################################
#     Adjust the following SET Commands as needed
###############################################################################################################
if(WIN32)
  set(ENV{HDFJAVA_DIR} "${INSTALLDIR}/cmake")
  set(CTEST_BINARY_NAME ${CTEST_SOURCE_NAME}\\build)
  set(CTEST_SOURCE_DIRECTORY "${CTEST_DASHBOARD_ROOT}\\${CTEST_SOURCE_NAME}")
  set(CTEST_BINARY_DIRECTORY "${CTEST_DASHBOARD_ROOT}\\${CTEST_BINARY_NAME}")
else()
  set(ENV{HDFJAVA_DIR} "${INSTALLDIR}/share/cmake")
  set(ENV{LD_LIBRARY_PATH} "${INSTALLDIR}/lib")
  set(CTEST_BINARY_NAME ${CTEST_SOURCE_NAME}/build)
  set(CTEST_SOURCE_DIRECTORY "${CTEST_DASHBOARD_ROOT}/${CTEST_SOURCE_NAME}")
  set(CTEST_BINARY_DIRECTORY "${CTEST_DASHBOARD_ROOT}/${CTEST_BINARY_NAME}")
endif()

###############################################################################################################
# For any comments please contact cdashhelp@hdfgroup.org
#
###############################################################################################################

#-----------------------------------------------------------------------------
# MAC machines need special option
#-----------------------------------------------------------------------------
if(APPLE)
  # Compiler choice
  execute_process(COMMAND xcrun --find cc OUTPUT_VARIABLE XCODE_CC OUTPUT_STRIP_TRAILING_WHITESPACE)
  execute_process(COMMAND xcrun --find c++ OUTPUT_VARIABLE XCODE_CXX OUTPUT_STRIP_TRAILING_WHITESPACE)
  set(ENV{CC} "${XCODE_CC}")
  set(ENV{CXX} "${XCODE_CXX}")
  set(BUILD_OPTIONS "${BUILD_OPTIONS} -DCTEST_USE_LAUNCHERS:BOOL=ON -DCMAKE_BUILD_WITH_INSTALL_RPATH:BOOL=OFF")
endif()

#-----------------------------------------------------------------------------
set(CTEST_CMAKE_COMMAND "\"${CMAKE_COMMAND}\"")
## --------------------------
if(CTEST_USE_TAR_SOURCE)
  ## Uncompress source if tar or zip file provided
  ## --------------------------
  if(WIN32)
    message(STATUS "extracting... [${CMAKE_EXECUTABLE_NAME} x ${CTEST_USE_TAR_SOURCE}.zip]")
    execute_process(COMMAND ${CMAKE_EXECUTABLE_NAME} -E tar -xvf ${CTEST_DASHBOARD_ROOT}\\${CTEST_USE_TAR_SOURCE}.zip RESULT_VARIABLE rv)
  else()
    message(STATUS "extracting... [${CMAKE_EXECUTABLE_NAME} -E tar -xvf ${CTEST_USE_TAR_SOURCE}.tar]")
    execute_process(COMMAND ${CMAKE_EXECUTABLE_NAME} -E tar -xvf ${CTEST_DASHBOARD_ROOT}/${CTEST_USE_TAR_SOURCE}.tar RESULT_VARIABLE rv)
  endif()

  if(NOT rv EQUAL 0)
    message(STATUS "extracting... [error-(${rv}) clean up]")
    file(REMOVE_RECURSE "${CTEST_SOURCE_DIRECTORY}")
    message(FATAL_ERROR "error: extract of ${CTEST_SOURCE_NAME} failed")
  endif()
endif()

#-----------------------------------------------------------------------------
## Clear the build directory
## --------------------------
set(CTEST_START_WITH_EMPTY_BINARY_DIRECTORY TRUE)
if (EXISTS "${CTEST_BINARY_DIRECTORY}" AND IS_DIRECTORY "${CTEST_BINARY_DIRECTORY}")
  ctest_empty_binary_directory(${CTEST_BINARY_DIRECTORY})
else ()
  file(MAKE_DIRECTORY "${CTEST_BINARY_DIRECTORY}")
endif ()

# Use multiple CPU cores to build
include(ProcessorCount)
ProcessorCount(N)
if(NOT N EQUAL 0)
  if(NOT WIN32)
    set(CTEST_BUILD_FLAGS -j${N})
  endif()
  set(ctest_test_args ${ctest_test_args} PARALLEL_LEVEL ${N})
endif()
set (CTEST_CONFIGURE_COMMAND
    "${CTEST_CMAKE_COMMAND} -C \"${CTEST_SOURCE_DIRECTORY}/config/cmake/cacheinit.cmake\" -DCMAKE_BUILD_TYPE:STRING=${CTEST_BUILD_CONFIGURATION} ${BUILD_OPTIONS} \"-G${CTEST_CMAKE_GENERATOR}\" \"${CTEST_SOURCE_DIRECTORY}\""
)

#-----------------------------------------------------------------------------
## -- set output to english
set($ENV{LC_MESSAGES}  "en_EN")

#-----------------------------------------------------------------------------
configure_file(${CTEST_SOURCE_DIRECTORY}/config/cmake/CTestCustom.cmake ${CTEST_BINARY_DIRECTORY}/CTestCustom.cmake)
ctest_read_custom_files ("${CTEST_BINARY_DIRECTORY}")
## NORMAL process
## --------------------------
ctest_start (Experimental)
ctest_configure (BUILD "${CTEST_BINARY_DIRECTORY}")
ctest_build (BUILD "${CTEST_BINARY_DIRECTORY}" APPEND)
ctest_test (BUILD "${CTEST_BINARY_DIRECTORY}" APPEND ${ctest_test_args} RETURN_VALUE res)
if(res GREATER 0)
  message (FATAL_ERROR "tests FAILED")
endif()
#-----------------------------------------------------------------------------
##############################################################################################################
message(STATUS "DONE")
