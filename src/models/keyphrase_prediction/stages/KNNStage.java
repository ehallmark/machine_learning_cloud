package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeyphrasePredictionPipelineManager;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.kmeans.UnitCosineKNN;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Created by Evan on 11/21/2017.
 */
public class KNNStage extends Stage<Set<MultiStem>>  {
    private Word2Vec word2Vec;
    private Map<MultiStem,AtomicLong> docCounter;
    public KNNStage(Collection<MultiStem> multiStems, Word2Vec word2Vec, Map<MultiStem,AtomicLong> docCounter, Model model) {
        super(model);
        this.docCounter=docCounter;
        this.word2Vec=word2Vec;
        this.data = multiStems==null? Collections.emptySet() : new HashSet<>(multiStems);
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 2
            System.out.println("Num keywords before knn stage: " + data.size());

            int numIterations = 2;
            for(int i = 0; i < numIterations; i++) {
                System.out.println("Computing multi stem encoding map...");
                Map<MultiStem, INDArray> multiStemToEncodingsMap = KeyphrasePredictionPipelineManager.buildNewKeywordToLookupTableMapHelper(word2Vec, data);
                UnitCosineKNN<MultiStem> knnModel = new UnitCosineKNN<>(multiStemToEncodingsMap);
                System.out.println("Starting to init knn...");
                knnModel.init();

                Map<MultiStem, MultiStem> nearestNeighborMap = knnModel.allItemsToNearestNeighbor();
                data = Collections.synchronizedSet(data.parallelStream().filter(d -> {
                    long score = docCounter.get(d).get();
                    long otherScore = docCounter.get(nearestNeighborMap.get(d)).get();
                    return score >= otherScore;
                }).collect(Collectors.toSet()));
            }

            System.out.println("Num keywords after knn stage: " + data.size());

            Database.saveObject(data, getFile());
            // write to csv for records
            KeywordModelRunner.writeToCSV(data, new File(getFile().getAbsoluteFile() + ".csv"));
        } else {
            try {
                loadData();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return data;
    }


}
