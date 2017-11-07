cmake_minimum_required (VERSION 3.1.0)
project (HDFJNIExamples_NATIVE_H4 Java)

set (CMAKE_VERBOSE_MAKEFILE 1)

INCLUDE_DIRECTORIES (${HDFJAVA_LIB_DIR})

set (HDFJNI_EXAMPLES
    HDF4FileCreate
    HDF4GroupCreate
    HDF4DatasetCreate
)

if (WIN32 AND NOT CYGWIN)
  set (CMAKE_JAVA_INCLUDE_FLAG_SEP ";")
else (WIN32 AND NOT CYGWIN)
  set (CMAKE_JAVA_INCLUDE_FLAG_SEP ":")
endif (WIN32 AND NOT CYGWIN)

set (CMAKE_JAVA_CLASSPATH ".")
foreach (CMAKE_INCLUDE_PATH ${CMAKE_JAVA_INCLUDE_PATH})
  set (CMAKE_JAVA_CLASSPATH "${CMAKE_JAVA_CLASSPATH}${CMAKE_JAVA_INCLUDE_FLAG_SEP}${CMAKE_INCLUDE_PATH}")
endforeach (CMAKE_INCLUDE_PATH)

foreach (HCP_JAR ${CMAKE_JAVA_INCLUDE_PATH})
  get_filename_component (_HCP_FILE ${HCP_JAR} NAME)
  set (HDFJNI_CLASSJARS "${_HCP_FILE} ${HDFJNI_CLASSJARS}")
endforeach (HCP_JAR ${CMAKE_JAVA_INCLUDE_PATH})

foreach (example ${HDFJNI_EXAMPLES})
  file (WRITE ${PROJECT_BINARY_DIR}/Manifest.txt
  "Main-Class: jnative.h4.${example}
Class-Path: ${HDFJNI_CLASSJARS}
"
  )
  ADD_JAR (${example} ${PROJECT_BINARY_DIR}/Manifest.txt ${example}.java)
  get_target_property (${example}_JAR_FILE ${example} JAR_FILE)
endforeach (example ${HDFJNI_EXAMPLES})

if (BUILD_TESTING)
  macro (ADD_H4_TEST resultfile resultcode)
    add_test (
        NAME jnative-h4-${resultfile}
        COMMAND "${CMAKE_COMMAND}"
            -D "TEST_TESTER=${CMAKE_Java_RUNTIME}"
            -D "TEST_PROGRAM=jnative.h4.${resultfile}"
            -D "TEST_ARGS:STRING=${ARGN}"
            -D "TEST_CLASSPATH:STRING=${CMAKE_JAVA_CLASSPATH}${CMAKE_JAVA_INCLUDE_FLAG_SEP}${${resultfile}_JAR_FILE}"
            -D "TEST_LIBRARY_DIRECTORY=${HDFJAVA_LIBRARY}"
            -D "TEST_FOLDER=${HDFJNIExamples_NATIVE_BINARY_DIR}"
            -D "TEST_OUTPUT=${HDFJNIExamples_NATIVE_H4_BINARY_DIR}/${resultfile}.out"
            -D "TEST_EXPECT=${resultcode}"
            -D "TEST_SKIP_COMPARE=TRUE"
            -P "${HDFJNI_RESOURCES_DIR}/runTest.cmake"
    )
    if (NOT "${last_test}" STREQUAL "")
      set_tests_properties (jnative-h4-${resultfile} PROPERTIES DEPENDS ${last_test})
    endif (NOT "${last_test}" STREQUAL "")
    set (last_test "jnative-h4-${resultfile}")
  endmacro (ADD_H4_TEST file)

  foreach (example ${HDFJNI_EXAMPLES})
    add_test (
        NAME jnative-h4-${example}-clearall-objects
        COMMAND    ${CMAKE_COMMAND}
            -E remove
            ${HDFJNIExamples_NATIVE_BINARY_DIR}/${example}.hdf
            ${example}.out
            ${example}.out.err
    )
    if (NOT "${last_test}" STREQUAL "")
      set_tests_properties (jnative-h4-${example}-clearall-objects PROPERTIES DEPENDS ${last_test})
    endif (NOT "${last_test}" STREQUAL "")
    set (last_test "jnative-h4-${example}-clearall-objects")
    ADD_H4_TEST (${example} 0)
  endforeach (example ${HDFJNI_EXAMPLES})

endif (BUILD_TESTING)
