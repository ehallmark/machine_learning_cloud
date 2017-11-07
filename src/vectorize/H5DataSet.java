package vectorize;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import lombok.Setter;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Created by ehallmark on 11/6/17.
 */
public class H5DataSet implements Serializable {
    private static final long serialVersionUID = 1L;
    private int dataset_id;
    private int dataspace_id;
    private long[] dims;
    private int dataType;
    private String dsName;
    @Setter
    private transient H5DataGroup dataGroup;
    public H5DataSet(H5DataGroup dataGroup, String dsName, int dataspace_id, int dataset_id, long[] dims, int dataType) {
        this.dataspace_id=dataspace_id;
        this.dsName=dsName;
        this.dataset_id=dataset_id;
        this.dims=dims;
        this.dataGroup=dataGroup;
        this.dataType=dataType;
    }

    public void close() throws Exception {
        if(dataset_id>=0) H5.H5Dclose(dataset_id);
        if(dataspace_id>=0) H5.H5Sclose(dataspace_id);
    }

    public void load() throws Exception {
        dataset_id = HDF5Helper.openDataset(dataGroup.group_id,dsName);
        dataspace_id = H5.H5Dget_space(dataset_id);
    }

    public void writeData(Object data) throws Exception {
        HDF5Helper.writeDataset(dataset_id, data, dataType);
    }

    public void appendData(Object data, long[] newDims) throws Exception {
        long[] offset = new long[]{dims[0],0};
        this.dims[0]+=newDims[0];

        H5.H5Dset_extent(dataset_id,dims);

        // get new dataspace
        H5.H5Sclose(dataspace_id);
        dataspace_id = H5.H5Dget_space(dataset_id);

        //Allocate some memory space to hold the new data in memory during I/O
        int memspace_id = HDF5Helper.createDataspace(newDims);
        H5.H5Sselect_hyperslab(dataspace_id, HDF5Constants.H5S_SELECT_SET, offset, null, newDims, null);
        H5.H5Dwrite(dataset_id, dataType, memspace_id, dataspace_id, HDF5Constants.H5P_DEFAULT, data);
    }

    public double[][] getData() throws Exception{
        double[][] data = new double[(int)dims[0]][(int)dims[1]];
        HDF5Helper.readDataset(dataset_id, data, dataType);
        return data;
    }
}
