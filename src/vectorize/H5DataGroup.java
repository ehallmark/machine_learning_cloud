package vectorize;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 11/6/17.
 */
public class H5DataGroup implements Serializable {
    private static final long[] chunk_dims = {32,32};
    private static final long serialVersionUID = 1L;
    protected String fName;
    protected String gName;
    protected int file_id;
    protected int dcpl_id;
    protected int group_id;
    private File instanceFile;
    private Map<String,H5DataSet> nameToDataSetMap;
    private transient boolean closed;
    public H5DataGroup(String fName, String gName) throws Exception {
        this.fName=fName;
        this.gName=gName;
        this.nameToDataSetMap = Collections.synchronizedMap(new HashMap<>());
        this.file_id=-1;
        this.group_id=-1;
        this.dcpl_id=-1;
        this.closed=false;
        this.instanceFile = createFile(fName,gName);
        this.init();
    }

    private static File createFile(String fName, String gName) {
        return new File(fName+"_"+gName+"_instance.jobj");
    }

    private synchronized void init() throws Exception {
        closed = false;
        file_id = HDF5Helper.createFile(fName);
        if(file_id>=0) {
            group_id = HDF5Helper.createGroup(file_id, gName);
            dcpl_id = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);
            if(dcpl_id>=0) {
                // chunk size
                H5.H5Pset_chunk(dcpl_id, 2, chunk_dims);
            }
        }
    }

    private synchronized void load() throws Exception {
        closed = false;
        file_id = HDF5Helper.openFile(fName);
        if(file_id>=0) {
            group_id = HDF5Helper.openGroup(file_id, gName);
            for(H5DataSet dataSet : nameToDataSetMap.values()) {
                dataSet.load();
            }
        }
    }

    public static synchronized H5DataGroup load(String filename, String groupname) throws Exception {
        H5DataGroup dataGroup = (H5DataGroup) Database.tryLoadObject(createFile(filename,groupname));
        dataGroup.nameToDataSetMap.values().forEach(ds->ds.setDataGroup(dataGroup));
        dataGroup.load();
        return dataGroup;
    }

    public synchronized void save() throws Exception {
        Database.trySaveObject(this, instanceFile);
    }

    public synchronized H5DataSet createDataSet(String dsName, long[] dims, Object dataset, int dataType) throws Exception{
        if(nameToDataSetMap.containsKey(dsName)) throw new RuntimeException("Dataset ("+dsName+") already exists.");
        if(group_id>=0) {
            int dataspace_id = -1;
            dataspace_id = HDF5Helper.createDataspace(dims);
            if (dataspace_id >= 0) {
                int dataset_id = -1;
                dataset_id = HDF5Helper.createDataset(group_id, dataspace_id, dcpl_id, dsName, dataType);
                if (dataset_id >= 0) {
                    // write data
                    H5DataSet ds = new H5DataSet(this, dsName, dataspace_id, dataset_id, dims, dataType);
                    ds.writeData(dataset);
                    nameToDataSetMap.put(dsName, ds);
                    return ds;
                }
            }
        }
        return null;
    }

    public synchronized void close() throws Exception {
        if(!closed) {
            nameToDataSetMap.values().forEach(ds -> {
                try {
                    ds.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            if (group_id >= 0) H5.H5Gclose(group_id);
            if (file_id >= 0) H5.H5Fclose(file_id);
            closed=true;
        }
    }

    public H5DataSet getDataSet(String dsName) {
        return nameToDataSetMap.get(dsName);
    }

    private static double[][] randomData(long[] dims, boolean stochastic) {
        int n = (int)dims[0];
        int k = (int)dims[1];
        double[][] data = new double[n][];
        for(int i = 0; i < n; i++) {
            if(stochastic) data[i] = Nd4j.rand(new int[]{k}).gti(0.5).data().asDouble();
            else data[i] = Nd4j.rand(new int[]{k}).data().asDouble();
        }
        return data;
    }
    public static void main(String[] args) throws Exception{
        long[] dims = new long[]{100,100};
        H5DataGroup dataSet = new H5DataGroup("testDataset","g1");
        dataSet.createDataSet("training",dims, randomData(dims,true), HDF5Constants.H5T_NATIVE_DOUBLE);
        double[][] data = dataSet.getDataSet("training").getData();
        for(int i = 0; i < data.length; i++) {
            System.out.println("1) "+i+": "+ Arrays.toString(data[i]));
        }
        dataSet.save();
        dataSet.close();
        // now open
        dataSet = load("testDataset","g1");
        H5DataSet ds = dataSet.getDataSet("training");
        long[] newDims = new long[]{150,100};
        ds.appendData(randomData(newDims,false),newDims);
        data = dataSet.getDataSet("training").getData();
        for(int i = 0; i < data.length; i++) {
            System.out.println("2) "+i+": "+ Arrays.toString(data[i]));
        }
        System.out.println("Num datapoints after reopening: "+dataSet.getDataSet("training").getData().length);
    }
}
