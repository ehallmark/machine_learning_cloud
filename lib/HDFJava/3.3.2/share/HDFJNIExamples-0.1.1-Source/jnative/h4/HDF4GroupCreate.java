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
 * Description: this example shows how to create HDF4 groups using the
 * "HDF Native Package (Java)". The example creates the group structure:
 * 
 * <pre>
 *     "/" (root)
 *         g1
 *             g11
 *             g12
 *         g2
 *             g21
 *             g22
 * </pre>
 * 
 * </p>
 */
public class HDF4GroupCreate {
    private static String fname = "HDF4GroupCreate.hdf";

    public static void main(String args[]) throws Exception {
        int file_id = -1;
        int subvgroup_id = -1;
        int vgroup_id1 = -1;
        int vgroup_id2 = -1;

        // Create a new file using default properties.
        try {
            file_id = HDFLibrary.Hopen(fname, HDFConstants.DFACC_CREATE);
            // Initialize the V interface.
            if (file_id >= 0)
                HDFLibrary.Vstart(file_id);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to create file:" + fname);
            return;
        }

        try {
            // Create the vgroup.  Note that the vgroup reference number is set
            // to -1 for creating and the access mode is "w" for writing.
            if (file_id >= 0) {
                vgroup_id1 = HDFLibrary.Vattach (file_id, -1, "w");
                if (vgroup_id1 >= 0) {
                    HDFLibrary.Vsetname(vgroup_id1, "g1");
                    subvgroup_id = HDFLibrary.Vattach (vgroup_id1, -1, "w");
                    if (subvgroup_id >= 0) {
                        HDFLibrary.Vsetname(subvgroup_id, "g11");
                        HDFLibrary.Vinsert(vgroup_id1, subvgroup_id);
                        if (subvgroup_id >= 0)
                            HDFLibrary.Vdetach(subvgroup_id);
                    }
                    subvgroup_id = HDFLibrary.Vattach (vgroup_id1, -1, "w");
                    if (subvgroup_id >= 0) {
                        HDFLibrary.Vsetname(subvgroup_id, "g12");
                        HDFLibrary.Vinsert(vgroup_id1, subvgroup_id);
                        if (subvgroup_id >= 0)
                            HDFLibrary.Vdetach(subvgroup_id);
                    }
                }
                vgroup_id2 = HDFLibrary.Vattach (file_id, -1, "w");
                if (vgroup_id2 >= 0) {
                    HDFLibrary.Vsetname(vgroup_id2, "g2");
                    subvgroup_id = HDFLibrary.Vattach (vgroup_id2, -1, "w");
                    if (subvgroup_id >= 0) {
                        HDFLibrary.Vsetname(subvgroup_id, "g21");
                        HDFLibrary.Vinsert(vgroup_id2, subvgroup_id);
                        if (subvgroup_id >= 0)
                            HDFLibrary.Vdetach(subvgroup_id);
                    }
                    subvgroup_id = HDFLibrary.Vattach (vgroup_id2, -1, "w");
                    if (subvgroup_id >= 0) {
                        HDFLibrary.Vsetname(subvgroup_id, "g22");
                        HDFLibrary.Vinsert(vgroup_id2, subvgroup_id);
                        if (subvgroup_id >= 0)
                            HDFLibrary.Vdetach(subvgroup_id);
                    }
                }
            }
            
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // Close the groups.
        try {
            if (vgroup_id2 >= 0)
                HDFLibrary.Vdetach(vgroup_id2);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (vgroup_id1 >= 0)
                HDFLibrary.Vdetach(vgroup_id1);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // Close the file.
        try {
            if (file_id >= 0) {
                HDFLibrary.Vend (file_id);
                HDFLibrary.Hclose(file_id);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
