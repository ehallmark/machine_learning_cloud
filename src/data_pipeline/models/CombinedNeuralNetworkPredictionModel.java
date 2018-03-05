package data_pipeline.models;

import data_pipeline.helpers.CombinedModel;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import seeding.Database;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 11/8/17.
 */
public abstract class CombinedNeuralNetworkPredictionModel<T,N extends Model> extends BaseTrainablePredictionModel<T,CombinedModel<N>> {
    protected CombinedNeuralNetworkPredictionModel(String modelName) {
        super(modelName);
    }


    public abstract File getModelBaseDirectory();


    @Override
    protected void saveNet(CombinedModel<N> net, File file) throws IOException {
        if(net.getNameToNetworkMap()!=null) {
            Database.trySaveObject(net,file);
            net.getNameToNetworkMap().forEach((name,model)->{
                try {
                    ModelSerializer.writeModel(model, file.getAbsolutePath() + name, true);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @Override
    protected void restoreFromFile(File modelFile) {
        if(modelFile!=null&&modelFile.exists()) {
            this.net = (CombinedModel)Database.tryLoadObject(modelFile);
            if(this.net==null) this.net = (CombinedModel)tryLoadObjectOld(modelFile);
            if(net!=null) {
                Map<String,N> map = Collections.synchronizedMap(new HashMap<>());
                if(net.getNetworkNames()!=null) {
                    net.getNetworkNames().forEach(name->{
                        try {
                            File file = new File(modelFile.getAbsolutePath() + name);
                            if(net.getClazz().equals(MultiLayerNetwork.class)) {
                                map.put(name, (N)ModelSerializer.restoreMultiLayerNetwork(file, true));
                            } else {
                                map.put(name, (N)ModelSerializer.restoreComputationGraph(file,true));
                            }
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
                net.setNameToNetworkMap(map);
                this.isSaved.set(true);
            }
        } else {
            System.out.println("WARNING: Model file does not exist: "+modelFile.getAbsolutePath());
        }
    }

    public static Object tryLoadObjectOld(File file) {
        System.out.println("Starting to load file: "+file.getName()+"...");
        try {
            ObjectInputStream ois = new DecompressibleInputStream(new BufferedInputStream(new FileInputStream(file)));
            Object toReturn = ois.readObject();
            ois.close();
            return toReturn;
        } catch(Exception e) {
            e.printStackTrace();
            //throw new RuntimeException("Unable to open file: "+file.getPath());
            return null;
        }
    }

}
class DecompressibleInputStream extends ObjectInputStream {

    public DecompressibleInputStream(InputStream in) throws IOException {
        super(in);
    }


    protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
        ObjectStreamClass resultClassDescriptor = super.readClassDescriptor(); // initially streams descriptor
        Class localClass = Class.forName(resultClassDescriptor.getName()); // the class in the local JVM that this descriptor represents.
        if (localClass == null) {
            System.out.println("No local class for " + resultClassDescriptor.getName());
            return resultClassDescriptor;
        }
        ObjectStreamClass localClassDescriptor = ObjectStreamClass.lookup(localClass);
        if (localClassDescriptor != null) { // only if class implements serializable
            final long localSUID = localClassDescriptor.getSerialVersionUID();
            final long streamSUID = resultClassDescriptor.getSerialVersionUID();
            if (streamSUID != localSUID) { // check for serialVersionUID mismatch.
                final StringBuffer s = new StringBuffer("Overriding serialized class version mismatch: ");
                s.append("local serialVersionUID = ").append(localSUID);
                s.append(" stream serialVersionUID = ").append(streamSUID);
                Exception e = new InvalidClassException(s.toString());
                System.out.println("Potentially Fatal Deserialization Operation. " + e);
                resultClassDescriptor = localClassDescriptor; // Use local class descriptor for deserialization
            }
        }
        return resultClassDescriptor;
    }
}