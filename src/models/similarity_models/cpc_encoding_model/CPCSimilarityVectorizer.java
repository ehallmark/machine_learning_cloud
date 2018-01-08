package models.similarity_models.cpc_encoding_model;

import data_pipeline.pipeline_manager.DefaultPipelineManager;
import models.similarity_models.Vectorizer;
import models.similarity_models.combined_similarity_model.CombinedSimilarityVAEPipelineManager;
import models.similarity_models.paragraph_vectors.WordFrequencyPair;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;
import tools.MinHeap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Evan on 10/28/2017.
 */
public class CPCSimilarityVectorizer implements Vectorizer {
    //private static final File vectorMapFile = new File(Constants.DATA_FOLDER+"signature_model_vector_map-depth4.jobj");
    //private static final File bestModelFile = new File("data/word_to_cpc_deep_nn_model_data/word_to_cpc_encoder_2017-11-17T07:37:15.921");
    private Map<String,INDArray> data;
    private boolean binarize;
    private boolean normalize;
    private boolean probability;
    private DefaultPipelineManager<?,INDArray> pipelineManager;
    private Function<Void,Collection<String>> getAllAssetsFunction;
    public CPCSimilarityVectorizer(DefaultPipelineManager<?,INDArray> pipelineManager, boolean binarize, boolean normalize, boolean probability, Function<Void,Collection<String>> getAllAssetsFunction) {
        this.binarize=binarize;
        this.probability=probability;
        this.getAllAssetsFunction=getAllAssetsFunction;
        this.normalize=normalize;
        this.pipelineManager=pipelineManager;
        getLookupTable();
    }

    public CPCSimilarityVectorizer(Map<String,INDArray> data, boolean binarize, boolean normalize, boolean probability) {
        this.binarize=binarize;
        this.probability=probability;
        this.normalize=normalize;
        this.data=data;
    }

    public INDArray vectorFor(String item) {
        if(item==null||item.isEmpty()) return null;
        INDArray vec = getLookupTable().get(item);
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
        return in.gt(0.0);
    }

    private INDArray normalize(INDArray in) {
        return in.div(in.norm2Number());
    }

    private INDArray probability(INDArray in) {
        return Transforms.sigmoid(in,true);
    }

    public synchronized Map<String,INDArray> getLookupTable() {
        if (data == null) {
            data = pipelineManager.loadPredictions();
        }
        return data;
    }

    public static void main(String[] args) throws Exception {
        updateLatest();
    }

    public static void updateLatest() {
        // update cpc
        CPCVAEPipelineManager cpcvaePipelineManager = new CPCVAEPipelineManager(CPCVAEPipelineManager.MODEL_NAME);
        CombinedSimilarityVAEPipelineManager combinedPipelineManager = CombinedSimilarityVAEPipelineManager.getOrLoadManager();
        Collection<CPCSimilarityVectorizer> vectorizers = Arrays.asList(
                new CPCSimilarityVectorizer(cpcvaePipelineManager,false,false,false,v->Database.getAllPatentsAndApplications()),
                new CPCSimilarityVectorizer(combinedPipelineManager,false,false,false,v->Database.getAllFilings())
        );
        vectorizers.forEach(vectorizer->{
            try {
                vectorizer.update();
            }catch(Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void update() throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);

        // test restore model
        System.out.println("Restoring model...");
        pipelineManager.runPipeline(false,false,false,false,-1,false,false);


        Map<String,INDArray> previous = pipelineManager.loadPredictions();
        Collection<String> latestAssets = null;
        if(previous!=null&&previous.size()>0) {
            List<String> unknownAssets = getAllAssetsFunction.apply(null)
                    .parallelStream()
                    .filter(asset->!previous.containsKey(asset))
                    .collect(Collectors.toList());
            if(unknownAssets.isEmpty()) {
                return;
            }
            latestAssets = unknownAssets.stream().flatMap(asset->new FilingToAssetMap().getApplicationDataMap().getOrDefault(asset,new FilingToAssetMap().getPatentDataMap().getOrDefault(asset,Collections.singleton(asset))).stream())
                    .distinct().collect(Collectors.toList());
        }


        List<String> allAssets = new ArrayList<>(latestAssets==null?(Database.getAllPatentsAndApplications()):latestAssets);
        List<String> allAssignees = new ArrayList<>(latestAssets==null?Database.getAssignees():latestAssets.stream().map(asset->Database.assigneeFor(asset)).filter(assignee->assignee!=null).distinct().collect(Collectors.toList()));
        List<String> allClassCodes = new ArrayList<>(latestAssets==null?Database.getClassCodes():latestAssets.stream().flatMap(asset->Database.classificationsFor(asset).stream()).filter(cpc->cpc!=null).distinct().collect(Collectors.toList()));

        System.out.println("Testing encodings");
        if(latestAssets==null) {
            // not updating
            data = pipelineManager.predict(allAssets,allAssignees,allClassCodes);
        } else {
            // updating
            data = pipelineManager.updatePredictions(allAssets,allAssignees,allClassCodes);
        }
        System.out.println("Num patent vectors found: "+data.size());
        System.out.println("Saving results...");
        if(data!=null&&data.size()>0) {
            pipelineManager.savePredictions(data);
            System.out.println("Finished saving.");
        }
    }

}
