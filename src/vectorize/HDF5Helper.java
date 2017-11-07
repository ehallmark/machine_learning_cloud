package vectorize;


import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;

/**
 * Created by ehallmark on 11/6/17.
 */
public class HDF5Helper {
    static {

    }
    public static int createFile(String fname) {
        int file_id = -1;
        try {
            file_id = H5.H5Fcreate(fname, HDF5Constants.H5F_ACC_TRUNC,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to create file:" + fname);
        }
        return file_id;
    }

    public static int openFile(String fname) {
        int file_id = -1;
        try {
            file_id = H5.H5Fopen(fname, HDF5Constants.H5F_ACC_RDWR,
                    HDF5Constants.H5P_DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to open file:" + fname);
        }
        return file_id;
    }

    public static int openGroup(int file_id, String gName) {
        int group_id = -1;
        try {
            group_id = H5.H5Gopen(file_id, gName,
                    HDF5Constants.H5P_DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to open group:" + gName);
        }
        return group_id;
    }

    public static int createGroup(int file_id, String gName) {
        int group_id = -1;
        // Create a group in the file.
        try {
            if (file_id >= 0) {
                group_id = H5.H5Gcreate(file_id, gName,
                        HDF5Constants.H5P_DEFAULT,
                        HDF5Constants.H5P_DEFAULT,
                        HDF5Constants.H5P_DEFAULT);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return group_id;
    }

    public static int createDataspace(long[] dims) {
        // Create the data space for the  3D dataset.
        int dataspace_id = -1;
        try {
            long[] max_dims = dims.clone();
            max_dims[0] = HDF5Constants.H5S_UNLIMITED;
            dataspace_id = H5.H5Screate_simple(dims.length, dims, max_dims);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return dataspace_id;
    }

    public static int createDataset(int group_id, int dataspace_id, int dcpl_id, String dName, int HDF5DataType) {
        int dataset_id = -1;
        try {
            if ((group_id >= 0) && (dataspace_id >= 0)) {
                dataset_id = H5.H5Dcreate(group_id, dName,
                        HDF5DataType, dataspace_id,
                        HDF5Constants.H5P_DEFAULT,
                        dcpl_id,
                        HDF5Constants.H5P_DEFAULT);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return dataset_id;
    }

    public static int openDataset(int file_id, String dName) {
        int dataset_id = -1;
        // create 2D 32-bit (4 bytes) integer dataset of 20 by 10
        try {
            dataset_id = H5.H5Dopen(file_id, dName,
                    HDF5Constants.H5P_DEFAULT);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return dataset_id;
    }

    public static void writeDataset(int dataset_id, Object dataset, int HDF5DataType) throws Exception {
        H5.H5Dwrite(dataset_id, HDF5DataType,HDF5Constants.H5S_ALL,HDF5Constants.H5S_ALL,HDF5Constants.H5P_DEFAULT,dataset);
    }

    public static void readDataset(int dataset_id, Object dataset, int HDF5DataType) throws Exception {
        H5.H5Dread(dataset_id, HDF5DataType,HDF5Constants.H5S_ALL,HDF5Constants.H5S_ALL,HDF5Constants.H5P_DEFAULT,dataset);
    }

}
