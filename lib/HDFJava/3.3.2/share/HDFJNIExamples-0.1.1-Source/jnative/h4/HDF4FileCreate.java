/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                      *
 *                                                                           *
 * This file is part of the HDF Java Products distribution.                  *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the files COPYING and Copyright.html. *
 * COPYING can be found at the root of the source code distribution tree.    *
 * Or, see http://hdfgroup.org/products/hdf-java/doc/Copyright.html.         *
 * If you do not have access to either file, you may request a copy from     *
 * help@hdfgroup.org.                                                        *
 ****************************************************************************/

package jnative.h4;

import hdf.hdflib.HDFConstants;
import hdf.hdflib.HDFLibrary;

/**
 * <p>
 * Title: HDF Native Package (Java) Example
 * </p>
 * <p>
 * Description: this example shows how to create an empty HDF4 file using the
 * "HDF Native Package (Java)".
 * </p>
 */
public class HDF4FileCreate {
    private static String fname = "HDF4FileCreate.hdf";

    public static void main(String args[]) throws Exception {
        int file_id = -1;

        // Create a new file using default properties.
        try {
            file_id = HDFLibrary.Hopen(fname, HDFConstants.DFACC_CREATE);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to create file:" + fname);
            return;
        }

        // Close the file.
        try {
            if (file_id >= 0)
                HDFLibrary.Hclose(file_id);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
