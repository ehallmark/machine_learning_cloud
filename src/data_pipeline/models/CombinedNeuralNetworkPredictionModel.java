package data_pipeline.models;

import data_pipeline.helpers.CombinedModel;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import seeding.Database;

import java.io.File;
import java.io.IOException;
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
    protected void restoreFromFile(File modelFile) throws IOException {
        if(modelFile!=null&&modelFile.exists()) {
            this.net = (CombinedModel)Database.tryLoadObject(modelFile);
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

}
