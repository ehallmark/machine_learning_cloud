#
# To be used by projects that make use of CMakeified hdf-java
#

#
# Find the HDFJAVA includes and get all installed hdf-java library settings from
# HDFJAVA-config.cmake file : Requires a CMake compatible hdf-java-3.3.2 or later
# for this feature to work. The following vars are set if hdf-java is found.
#
# HDFJAVA_FOUND               - True if found, otherwise all other vars are undefined
# HDFJAVA_VERSION_STRING      - full version (e.g. 3.3.2)
# HDFJAVA_VERSION_MAJOR       - major part of version (e.g. 3)
# HDFJAVA_VERSION_MINOR       - minor part (e.g. 3)
#
# Target names that are valid (depending on enabled options)
# will be the following
#
#
# To aid in finding HDFJAVA as part of a subproject set
# HDFJAVA_ROOT_DIR_HINT to the location where hdf-java-config.cmake lies

INCLUDE (SelectLibraryConfigurations)
INCLUDE (FindPackageHandleStandardArgs)

# The HINTS option should only be used for values computed from the system.
set (_HDFJAVA_HINTS
    $ENV{HOME}/.local
    $ENV{HDFJAVA_ROOT}
    $ENV{HDFJAVA_ROOT_DIR_HINT}
)
# Hard-coded guesses should still go in PATHS. This ensures that the user
# environment can always override hard guesses.
set (_HDFJAVA_PATHS
    $ENV{HOME}/.local
    $ENV{HDFJAVA_ROOT}
    $ENV{HDFJAVA_ROOT_DIR_HINT}
    /usr/lib/hdf-java
    /usr/share/hdf-java
    /usr/local/hdf-java
    /usr/local/hdf-java/share
)

FIND_PATH (HDFJAVA_ROOT_DIR "hdf-java-config.cmake"
    HINTS ${_HDFJAVA_HINTS}
    PATHS ${_HDFJAVA_PATHS}
    PATH_SUFFIXES
        cmake/hdf-java
        lib/cmake/hdf-java
        share/cmake/hdf-java
)

FIND_PATH (HDFJAVA_LIBRARY "jarhdf5-3.3.2.jar"
    HINTS ${_HDFJAVA_HINTS}
    PATHS ${_HDFJAVA_PATHS}
    PATH_SUFFIXES
        lib
)

if (HDFJAVA_ROOT_DIR)
  set (HDFJAVA_FOUND "YES")
  INCLUDE (${HDFJAVA_ROOT_DIR}/hdf-java-config.cmake)
  set (HDFJAVA_LIBRARIES "${HDFJAVA_LIBRARY}")
  set (HDFJAVA_INCLUDE_DIRS
        ${HDFJAVA_LIBRARY}/jarhdf-3.3.2.jar
        ${HDFJAVA_LIBRARY}/jarhdf5-3.3.2.jar
  )

endif ()
