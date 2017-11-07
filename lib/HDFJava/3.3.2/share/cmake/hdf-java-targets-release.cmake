#----------------------------------------------------------------
# Generated CMake target import file for configuration "Release".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "jhdf" for configuration "Release"
set_property(TARGET jhdf APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(jhdf PROPERTIES
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libjhdf.3.3.2.dylib"
  IMPORTED_SONAME_RELEASE "@rpath/libjhdf.3.3.2.dylib"
  )

list(APPEND _IMPORT_CHECK_TARGETS jhdf )
list(APPEND _IMPORT_CHECK_FILES_FOR_jhdf "${_IMPORT_PREFIX}/lib/libjhdf.3.3.2.dylib" )

# Import target "jhdf5" for configuration "Release"
set_property(TARGET jhdf5 APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(jhdf5 PROPERTIES
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libjhdf5.3.3.2.dylib"
  IMPORTED_SONAME_RELEASE "@rpath/libjhdf5.3.3.2.dylib"
  )

list(APPEND _IMPORT_CHECK_TARGETS jhdf5 )
list(APPEND _IMPORT_CHECK_FILES_FOR_jhdf5 "${_IMPORT_PREFIX}/lib/libjhdf5.3.3.2.dylib" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
