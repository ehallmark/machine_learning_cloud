#-----------------------------------------------------------------------------
# HDFJAVA Version file for install directory
#-----------------------------------------------------------------------------

set (PACKAGE_VERSION 3.3.2)

if ("${PACKAGE_FIND_VERSION_MAJOR}" EQUAL 3)

  # exact match for version .3
  if ("${PACKAGE_FIND_VERSION_MINOR}" EQUAL 3)

    # compatible with any version 3.3.x
    set (PACKAGE_VERSION_COMPATIBLE 1)

    if ("${PACKAGE_FIND_VERSION_PATCH}" EQUAL 2)
      set (PACKAGE_VERSION_EXACT 1)

      if ("${PACKAGE_FIND_VERSION_TWEAK}" EQUAL )
        # not using this yet
      endif ()

    endif ()

  endif ()
endif ()


