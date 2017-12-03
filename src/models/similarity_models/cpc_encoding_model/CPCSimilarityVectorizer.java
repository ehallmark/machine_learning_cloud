package models.similarity_models.cpc_encoding_model;

import ch.qos.logback.classic.Level;
import data_pipeline.models.TrainablePredictionModel;
import data_pipeline.pipeline_manager.PipelineManager;
import lombok.Getter;
import models.similarity_models.Vectorizer;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import models.similarity_models.paragraph_vectors.WordFrequencyPair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;
import org.nd4j.linalg.primitives.Pair;
import tools.MinHeap;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Evan on 10/28/2017.
 */
public class CPCSimilarityVectorizer implements Vectorizer {
    //private static final File vectorMapFile = new File(Constants.DATA_FOLDER+"signature_model_vector_map-depth4.jobj");
    //private static final File bestModelFile = new File("data/word_to_cpc_deep_nn_model_data/word_to_cpc_encoder_2017-11-17T07:37:15.921");
    private static final PipelineManager pipelineManager = new CPCVAEPipelineManager(CPCVAEPipelineManager.MODEL_NAME);
    private static Map<String,INDArray> DATA;
    private Map<String,INDArray> data;
    private boolean binarize;
    private boolean normalize;
    private boolean probability;
    public CPCSimilarityVectorizer(boolean binarize, boolean normalize, boolean probability) {
        this(getLookupTable(),binarize, normalize, probability);
    }

    public CPCSimilarityVectorizer(Map<String,INDArray> data, boolean binarize, boolean normalize, boolean probability) {
        this.binarize=binarize;
        this.probability=probability;
        this.normalize=normalize;
        this.data=data;
    }

    public INDArray vectorFor(String item) {
        if(item==null||item.isEmpty()) return null;
        INDArray vec = data.get(item);
        if(vec==null) return null;
        if(binarize) vec = binarize(vec);
        if(probability) vec = probability(vec);
        if(normalize) vec = normalize(vec);
        return vec;
    }

    public List<WordFrequencyPair<String,Double>> similarTo(String item, int limit) {
        return this.similarTo(vectorFor(item),item,limit);
    }

    public List<WordFrequencyPair<String,Double>> similarTo(INDArray vec, int limit) {
        return this.similarTo(vec,null,limit);
    }

    public List<WordFrequencyPair<String,Double>> similarTo(INDArray vec, String item, int limit) {
        MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(limit);
        if(vec!=null) {
            data.entrySet().parallelStream().forEach(e -> {
                if(item!=null&&e.getKey().equals(item)) return;
                double sim = Transforms.cosineSim(e.getValue(), vec);
                synchronized (heap) {
                    heap.add(new WordFrequencyPair<>(e.getKey(), sim));
                }
            });
        }

        List<WordFrequencyPair<String,Double>> similar = new ArrayList<>();
        while(!heap.isEmpty()) {
            similar.add(0,heap.remove());
        }
        return similar;
    }

    private INDArray binarize(INDArray in) {
        return in.gte(0.0);
    }

    private INDArray normalize(INDArray in) {
        return in.div(in.norm2Number());
    }

    private INDArray probability(INDArray in) {
        return Transforms.sigmoid(in,true);
    }

    public synchronized static Map<String,INDArray> getLookupTable() {
        //boolean notUpdatedYet = true;
        if (DATA == null) {
            //if(notUpdatedYet) { // TODO remove this (after update)
            //    DATA = (Map<String, INDArray>) Database.tryLoadObject(vectorMapFile);
            //} else {
                DATA = (Map<String, INDArray>) Database.tryLoadObject(pipelineManager.getPredictionsFile());
            //}
        }
        return DATA;
    }

    public static void main(String[] args) throws Exception {
        updateLatest(null);
    }

    public static void updateLatest(Collection<String> latestAssets) throws Exception {
        // test restore model
        String modelName = CPCVAEPipelineManager.MODEL_NAME;
        int cpcDepth = CPCVAEPipelineManager.MAX_CPC_DEPTH;

        System.out.println("Restoring model: "+modelName);
        TrainablePredictionModel<INDArray,?> clone = new CPCVariationalAutoEncoderNN((CPCVAEPipelineManager)pipelineManager,modelName,cpcDepth);
        clone.loadBestModel();

        List<String> allAssets = new ArrayList<>(latestAssets==null?(Database.getAllPatentsAndApplications()):latestAssets);
        List<String> allAssignees = new ArrayList<>(latestAssets==null?Database.getAssignees():latestAssets.stream().map(asset->Database.assigneeFor(asset)).filter(assignee->assignee!=null).collect(Collectors.toList()));
        List<String> allClassCodes = new ArrayList<>(latestAssets==null?Database.getClassCodes():latestAssets.stream().flatMap(asset->Database.classificationsFor(asset).stream()).filter(cpc->cpc!=null).collect(Collectors.toList()));

        System.out.println("Testing encodings");
        if(latestAssets==null) {
            // not updating
            DATA = clone.predict(allAssets,allAssignees,allClassCodes, null);
        } else {
            // updating
            DATA = getLookupTable();
            DATA.putAll(clone.predict(allAssets,allAssignees,allClassCodes, DATA));
        }
        System.out.println("Num patent vectors found: "+DATA.size());
        System.out.println("Saving results...");
        Database.trySaveObject(DATA,pipelineManager.getPredictionsFile());
        System.out.println("Finished saving.");
    }

}
