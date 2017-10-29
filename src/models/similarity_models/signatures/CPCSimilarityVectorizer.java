package models.similarity_models.signatures;

import lombok.Getter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;
import org.deeplearning4j.berkeley.Pair;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 10/28/2017.
 */
public class CPCSimilarityVectorizer {
    private static final File vectorMapFile = new File(Constants.DATA_FOLDER+"signature_model_vector_map-depth4.jobj");
    private static Pair<Map<String,Integer>,INDArray> data;

    @Getter
    private Map<String,Integer> assetToIdxMap;
    @Getter
    private Map<Integer,String> idxToAssetMap;
    @Getter
    private INDArray matrix;
    public CPCSimilarityVectorizer() {
        if(data==null) getLookupTable();
        this.matrix=data.getSecond();
        this.assetToIdxMap=data.getFirst();
        this.idxToAssetMap=Collections.synchronizedMap(assetToIdxMap.entrySet().parallelStream().collect(Collectors.toMap(e->e.getValue(),e->e.getKey())));
    }

    public INDArray vectorFor(String item) {
        Integer row = assetToIdxMap.get(item);
        if(row==null) return null;
        return matrix.getRow(row);
    }


    public synchronized static Pair<Map<String,Integer>,INDArray> getLookupTable() {
        if(data==null) {
            data=(Pair<Map<String,Integer>,INDArray>)Database.tryLoadObject(vectorMapFile);
        }
        return data;
    }
    public static void main(String[] args) throws Exception {
        // test restore model
        System.out.println("Restoring model test");
        SignatureSimilarityModel clone = SignatureSimilarityModel.restoreAndInitModel(SignatureSimilarityModel.MAX_CPC_DEPTH,false);
        clone.setBatchSize(5000);
        List<String> allAssets = new ArrayList<>(Database.getAllPatentsAndApplications());

        System.out.println("Testing encodings");
        Map<String,INDArray> vectorMap = clone.encode(allAssets);
        System.out.println("Num patent vectors found: "+vectorMap.size());
        System.out.println("Starting assignees...");
        AtomicInteger cnt = new AtomicInteger(0);
        Database.getAssignees().parallelStream().forEach(assignee->{
            List<INDArray> vectors = new ArrayList<>(Stream.of(
                    Database.selectPatentNumbersFromExactAssignee(assignee),
                    Database.selectApplicationNumbersFromExactAssignee(assignee)
            ).flatMap(assets->assets.stream()).map(asset->{
                return vectorMap.get(asset);
            }).filter(vec->vec!=null).collect(Collectors.toList()));

            if(vectors.isEmpty()) return;

            if(vectors.size()>1000) {
                Collections.shuffle(vectors);
                vectors = vectors.subList(0,1000);
            }

            INDArray assigneeVec = Nd4j.vstack(vectors).mean(0);
            double norm = assigneeVec.norm2Number().doubleValue();
            if(norm>0) {
                assigneeVec = assigneeVec.divi(norm);
                vectorMap.put(assignee, assigneeVec);
                if (cnt.getAndIncrement() % 10000 == 9999) {
                    System.out.println("Vectorized " + cnt.get() + " assignees.");
                }
            }
        });
        System.out.println("Total vectors: "+vectorMap.size());
        Map<String,Integer> assetToIdxMap = Collections.synchronizedMap(new HashMap<>(vectorMap.size()));
        INDArray matrix = Nd4j.create(vectorMap.size(),SignatureSimilarityModel.VECTOR_SIZE);
        AtomicInteger idx = new AtomicInteger(0);
        vectorMap.entrySet().parallelStream().forEach(e->{
            int i = idx.getAndIncrement();
            assetToIdxMap.put(e.getKey(),i);
            matrix.putRow(i,e.getValue());
        });

        Pair<Map<String,Integer>,INDArray> data = new Pair<>(assetToIdxMap,matrix);
        System.out.println("Saving results...");
        Database.trySaveObject(data,vectorMapFile);
        System.out.println("Finished saving.");
    }
}
