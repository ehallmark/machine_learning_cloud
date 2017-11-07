#-----------------------------------------------------------------------------
# HDFJAVA Config file for compiling against hdfjava build/install directory
#-----------------------------------------------------------------------------

####### Expanded from @PACKAGE_INIT@ by configure_package_config_file() #######
####### Any changes to this file will be overwritten by the next CMake run ####
####### The input file was hdfjava-config.cmake.in                            ########

get_filename_component(PACKAGE_PREFIX_DIR "${CMAKE_CURRENT_LIST_DIR}/../../" ABSOLUTE)

macro(set_and_check _var _file)
  set(${_var} "${_file}")
  if(NOT EXISTS "${_file}")
    message(FATAL_ERROR "File or directory ${_file} referenced by variable ${_var} does not exist !")
  endif()
endmacro()

macro(check_required_components _NAME)
  foreach(comp ${${_NAME}_FIND_COMPONENTS})
    if(NOT ${_NAME}_${comp}_FOUND)
      if(${_NAME}_FIND_REQUIRED_${comp})
        set(${_NAME}_FOUND FALSE)
      endif()
    endif()
  endforeach()
endmacro()

####################################################################################

string(TOUPPER hdf-java HDFJAVA_PACKAGE_NAME)

set (${HDFJAVA_PACKAGE_NAME}_VALID_COMPONENTS
    JNI
    JHI4
    JHI5
)

#-----------------------------------------------------------------------------
# User Options
#-----------------------------------------------------------------------------
set (${HDFJAVA_PACKAGE_NAME}_BUILD_TOOLS     )
set (${HDFJAVA_PACKAGE_NAME}_PACKAGE_EXTLIBS      )
set (${HDFJAVA_PACKAGE_NAME}_EXPORT_LIBRARIES jhdf;jhdf5)
set (${HDFJAVA_PACKAGE_NAME}_BUILD_SHARED_LIBS       OFF)
set (${HDFJAVA_PACKAGE_NAME}_ENABLE_JPEG_LIB_SUPPORT ON)
set (${HDFJAVA_PACKAGE_NAME}_ENABLE_Z_LIB_SUPPORT ON)
set (${HDFJAVA_PACKAGE_NAME}_ENABLE_SZIP_SUPPORT  ON)
set (${HDFJAVA_PACKAGE_NAME}_ENABLE_SZIP_ENCODING ON)

#-----------------------------------------------------------------------------
# Directories
#-----------------------------------------------------------------------------
set (${HDFJAVA_PACKAGE_NAME}_INCLUDE_DIR "${PACKAGE_PREFIX_DIR}/include")

set (${HDFJAVA_PACKAGE_NAME}_SHARE_DIR "${PACKAGE_PREFIX_DIR}/share/cmake")
set_and_check (${HDFJAVA_PACKAGE_NAME}_BUILD_DIR "${PACKAGE_PREFIX_DIR}")

if (${HDFJAVA_PACKAGE_NAME}_BUILD_TOOLS)
  set (${HDFJAVA_PACKAGE_NAME}_INCLUDE_DIR_TOOLS "${PACKAGE_PREFIX_DIR}/include")
  set_and_check (${HDFJAVA_PACKAGE_NAME}_TOOLS_DIR "${PACKAGE_PREFIX_DIR}/bin")
endif ()

if (HDFJAVA_BUILD_SHARED_LIBS)
  set (HJAVA_BUILT_AS_DYNAMIC_LIB 1)
else ()
  set (HJAVA_BUILT_AS_STATIC_LIB 1)
endif ()
set (HDFJAVA_LIBRARY "${PACKAGE_PREFIX_DIR}/lib")
set (HDFJAVA_LIBRARIES "${HDFJAVA_LIBRARY}")


#-----------------------------------------------------------------------------
# Version Strings
#-----------------------------------------------------------------------------
set (HDFJAVA_VERSION_STRING 3.3.2)
set (HDFJAVA_VERSION_MAJOR  3)
set (HDFJAVA_VERSION_MINOR  3)

#-----------------------------------------------------------------------------
# Include Directories
#-----------------------------------------------------------------------------
set (HDFJAVA_INCLUDE_DIRS
    ${HDFJAVA_LIBRARY}/jarhdf-3.3.2.jar
    ${HDFJAVA_LIBRARY}/jarhdf5-3.3.2.jar
    ${HDFJAVA_LIBRARY}/slf4j-api-1.7.5.jar
    ${HDFJAVA_LIBRARY}/slf4j-nop-1.7.5.jar
)

#-----------------------------------------------------------------------------
# Don't include targets if this file is being picked up by another
# project which has already built hdfjava as a subproject
#-----------------------------------------------------------------------------
if (NOT TARGET "hdf-java")
  if (${HDFJAVA_PACKAGE_NAME}_ENABLE_JPEG_LIB_SUPPORT AND ${HDFJAVA_PACKAGE_NAME}_PACKAGE_EXTLIBS AND NOT TARGET "jpeg")
    include (${PACKAGE_PREFIX_DIR}/share/cmake/jpeg-targets.cmake)
  endif ()
  if (${HDFJAVA_PACKAGE_NAME}_ENABLE_Z_LIB_SUPPORT AND ${HDFJAVA_PACKAGE_NAME}_PACKAGE_EXTLIBS AND NOT TARGET "zlib")
    include (${PACKAGE_PREFIX_DIR}/share/cmake/zlib-targets.cmake)
  endif ()
  if (${HDFJAVA_PACKAGE_NAME}_ENABLE_SZIP_SUPPORT AND ${HDFJAVA_PACKAGE_NAME}_PACKAGE_EXTLIBS AND NOT TARGET "szip")
    include (${PACKAGE_PREFIX_DIR}/share/cmake/szip-targets.cmake)
  endif ()
  if (${HDFJAVA_PACKAGE_NAME}_ENABLE_HDF4_LIB_SUPPORT AND ${HDFJAVA_PACKAGE_NAME}_PACKAGE_EXTLIBS AND NOT TARGET "hdf4")
    include (${PACKAGE_PREFIX_DIR}/share/cmake/hdf4-targets.cmake)
  endif ()
  if (${HDFJAVA_PACKAGE_NAME}_ENABLE_HDF5_LIB_SUPPORT AND ${HDFJAVA_PACKAGE_NAME}_PACKAGE_EXTLIBS AND NOT TARGET "hdf5")
    include (${PACKAGE_PREFIX_DIR}/share/cmake/hdf5-targets.cmake)
  endif ()
  include (${PACKAGE_PREFIX_DIR}/share/cmake/hdf-java-targets.cmake)
  set (HDFJAVA_LIBRARIES "jhdf;jhdf5")
endif ()

# Handle default component :
if (NOT ${HDFJAVA_PACKAGE_NAME}_FIND_COMPONENTS)
    set (${HDFJAVA_PACKAGE_NAME}_FIND_COMPONENTS JNI)
    set (${HDFJAVA_PACKAGE_NAME}_FIND_REQUIRED_JNI true)
endif ()

## Handle requested components:
list (REMOVE_DUPLICATES ${HDFJAVA_PACKAGE_NAME}_FIND_COMPONENTS)
  foreach (comp IN LISTS ${HDFJAVA_PACKAGE_NAME}_FIND_COMPONENTS)
    set (hdfjava_comp2)
    if (${comp} STREQUAL "JNI")
      set (hdfjava_comp "jhdf5")
      set (hdfjava_comp2 "jhdf")
    elseif (${comp} STREQUAL "JHI4")
      set (hdfjava_comp "jhdf")
    elseif (${comp} STREQUAL "JHI5")
      set (hdfjava_comp "jhdf5")
    endif ()
    list (FIND ${HDFJAVA_PACKAGE_NAME}_EXPORT_LIBRARIES "${hdfjava_comp}" HAVE_COMP)
    if (${HAVE_COMP} LESS 0)
      set (${HDFJAVA_PACKAGE_NAME}_${comp}_FOUND 0)
    else ()
      if (hdfjava_comp2)
        list (FIND ${HDFJAVA_PACKAGE_NAME}_EXPORT_LIBRARIES "${hdfjava_comp2}" HAVE_COMP2)
        if (${HAVE_COMP2} LESS 0)
          set (${HDFJAVA_PACKAGE_NAME}_${comp}_FOUND 0)
        else ()
          set (${HDFJAVA_PACKAGE_NAME}_${comp}_FOUND 1)
          string(TOUPPER ${HDFJAVA_PACKAGE_NAME}_${comp}_LIBRARY COMP_LIBRARY)
          set (${HDFJAVA_PACKAGE_NAME}_LIBRARIES ${${HDFJAVA_PACKAGE_NAME}_LIBRARIES} ${hdfjava_comp2} ${hdfjava_comp})
        endif ()
      else ()
        set (${HDFJAVA_PACKAGE_NAME}_${comp}_FOUND 1)
        string(TOUPPER ${HDFJAVA_PACKAGE_NAME}_${comp}_LIBRARY COMP_LIBRARY)
        set (${HDFJAVA_PACKAGE_NAME}_LIBRARIES ${${HDFJAVA_PACKAGE_NAME}_LIBRARIES} ${hdfjava_comp})
      endif ()
    endif ()
  endforeach ()

check_required_components(${HDFJAVA_PACKAGE_NAME}_FIND_COMPONENTS)

#set (${HDFJAVA_PACKAGE_NAME}_LIBRARIES ${${COMP_LIBRARY}})
