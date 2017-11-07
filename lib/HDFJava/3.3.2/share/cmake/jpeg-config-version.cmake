#-----------------------------------------------------------------------------
# JPEG Version file for install directory
#-----------------------------------------------------------------------------

set (PACKAGE_VERSION 8.4)

if ("${PACKAGE_FIND_VERSION_MAJOR}" EQUAL 8.4)

  # exact match for version 8.4.0
  if ("${PACKAGE_FIND_VERSION_MINOR}" EQUAL 0)

    # compatible with any version 8.4.0.x
    set (PACKAGE_VERSION_COMPATIBLE 1)

    if ("${PACKAGE_FIND_VERSION_PATCH}" EQUAL )
      set (PACKAGE_VERSION_EXACT 1)

      if ("${PACKAGE_FIND_VERSION_TWEAK}" EQUAL )
        # not using this yet
      endif ()

    endif ()

  endif ()
endif ()


