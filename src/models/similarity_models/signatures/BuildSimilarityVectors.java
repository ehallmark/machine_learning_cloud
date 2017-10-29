package models.similarity_models.signatures;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 10/28/2017.
 */
public class BuildSimilarityVectors {
    private static final File vectorMapFile = new File(Constants.DATA_FOLDER+"signature_model_vector_map.jobj");
    public static void main(String[] args) throws Exception {
        // test restore model
        System.out.println("Restoring model test");
        SignatureSimilarityModel clone = SignatureSimilarityModel.restoreAndInitModel(SignatureSimilarityModel.MAX_CPC_DEPTH);
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
            vectorMap.put(assignee,assigneeVec);
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Vectorized "+cnt.get()+" assignees.");
            }
        });
        System.out.println("Total vectors: "+vectorMap.size());
        System.out.println("Saving results...");
        Database.trySaveObject(vectorMap,vectorMapFile);
        System.out.println("Finished saving.");
        Random rand = new Random();
        for(int i = 0; i < 10; i++) {
            String randomAsset = clone.getAllAssets().get(rand.nextInt(clone.getAllAssets().size()));
            INDArray vector = vectorMap.get(randomAsset);
            if(vector!=null) {
                // find closest
                Collection<String> closest = vectorMap.entrySet().parallelStream()
                        .filter(e->!randomAsset.equals(e.getKey()))
                        .sorted((e1,e2)->{
                            return Double.compare(Transforms.cosineSim(vector, e2.getValue()),Transforms.cosineSim(vector,e1.getValue()));
                        }).map(e->e.getKey())
                        .limit(5)
                        .collect(Collectors.toList());
                System.out.println("Closest to "+randomAsset+": "+String.join("; ",closest));
            }
        }
    }
}
