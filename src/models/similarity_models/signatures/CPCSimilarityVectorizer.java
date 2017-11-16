package models.similarity_models.signatures;

import lombok.Getter;
import models.similarity_models.Vectorizer;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Evan on 10/28/2017.
 */
public class CPCSimilarityVectorizer implements Vectorizer {
    private static final File vectorMapFile = new File(Constants.DATA_FOLDER+"signature_model_vector_map-depth4.jobj");
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
        if(DATA==null) {
            DATA=(Map<String,INDArray>)Database.tryLoadObject(vectorMapFile);
        }
        return DATA;
    }

    public static void main(String[] args) throws Exception {
        updateLatest(null);
    }

    public static void updateLatest(Collection<String> latestAssets) throws Exception {
        // test restore model
        System.out.println("Restoring model test");
        SignatureSimilarityModel clone = SignatureSimilarityModel.restoreAndInitModel(SignatureSimilarityModel.MAX_CPC_DEPTH,false);
        clone.setBatchSize(1000);
        List<String> allAssets = new ArrayList<>(latestAssets==null?(Database.getAllPatentsAndApplications()):latestAssets);

        System.out.println("Testing encodings");
        if(latestAssets==null) {
            // not updating
            DATA = clone.encode(allAssets);
        } else {
            // updating
            DATA = getLookupTable();
            DATA.putAll(clone.encode(allAssets));
        }
        System.out.println("Num patent vectors found: "+DATA.size());
        System.out.println("Starting assignees...");
        AtomicInteger cnt = new AtomicInteger(0);
        Database.getAssignees().parallelStream().forEach(assignee->{
            List<INDArray> vectors = new ArrayList<>(Stream.of(
                    Database.selectPatentNumbersFromExactAssignee(assignee),
                    Database.selectApplicationNumbersFromExactAssignee(assignee)
            ).flatMap(assets->assets.stream()).map(asset->{
                return DATA.get(asset);
            }).filter(vec->vec!=null).collect(Collectors.toList()));

            if(vectors.isEmpty()) return;

            if(vectors.size()>1000) {
                Collections.shuffle(vectors);
                vectors = vectors.subList(0,1000);
            }

            INDArray assigneeVec = Nd4j.vstack(vectors).mean(0);
            DATA.put(assignee, assigneeVec);
            if (cnt.getAndIncrement() % 10000 == 9999) {
                System.out.println("Vectorized " + cnt.get() + " assignees.");
            }
        });
        System.out.println("Total vectors: "+DATA.size());
        System.out.println("Saving results...");
        Database.trySaveObject(DATA,vectorMapFile);
        System.out.println("Finished saving.");
    }

}
