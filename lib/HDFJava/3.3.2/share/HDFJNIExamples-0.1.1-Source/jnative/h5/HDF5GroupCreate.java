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

package jnative.h5;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;

/**
 * <p>
 * Title: HDF Native Package (Java) Example
 * </p>
 * <p>
 * Description: this example shows how to create HDF5 groups using the
 * "HDF Native Package (Java)". The example created the group structure:
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
public class HDF5GroupCreate {
    private static String fname = "HDF5GroupCreate.h5";

    public static void main(String args[]) throws Exception {
        int file_id = -1;
        int subgroup_id = -1;
        int group_id1 = -1;
        int group_id2 = -1;

        // Create a new file using default properties.
        try {
            file_id = H5.H5Fcreate(fname, HDF5Constants.H5F_ACC_TRUNC,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to create file:" + fname);
            return;
        }

        // Create a group in the file.
        try {
            if (file_id >= 0) {
                group_id1 = H5.H5Gcreate(file_id, "g1",
                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
                if (group_id1 >= 0) {
                    subgroup_id = H5.H5Gcreate(group_id1, "g11",
                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
                    try {
                        if (subgroup_id >= 0)
                            H5.H5Gclose(subgroup_id);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    subgroup_id = H5.H5Gcreate(group_id1, "g12",
                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
                    try {
                        if (subgroup_id >= 0)
                            H5.H5Gclose(subgroup_id);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                group_id2 = H5.H5Gcreate(file_id, "g2",
                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
                if (group_id2 >= 0) {
                    subgroup_id = H5.H5Gcreate(group_id2, "g21",
                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
                    try {
                        if (subgroup_id >= 0)
                            H5.H5Gclose(subgroup_id);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    subgroup_id = H5.H5Gcreate(group_id2, "g22",
                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
                    try {
                        if (subgroup_id >= 0)
                            H5.H5Gclose(subgroup_id);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // Close the groups.
        try {
            if (group_id2 >= 0)
                H5.H5Gclose(group_id2);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (group_id1 >= 0)
                H5.H5Gclose(group_id1);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // Close the file.
        try {
            if (file_id >= 0)
                H5.H5Fclose(file_id);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
