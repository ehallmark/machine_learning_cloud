package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeyphrasePredictionPipelineManager;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.kmeans.AssetKMeans;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 11/21/2017.
 */
public class KMeansStage extends Stage<Set<MultiStem>>  {
    private Word2Vec word2Vec;
    private Map<String,MultiStem> stemToMultiStemMap;
    public KMeansStage(Collection<MultiStem> multiStems, Word2Vec word2Vec, Model model) {
        super(model);
        this.word2Vec=word2Vec;
        this.data = multiStems==null? Collections.emptySet() : new HashSet<>(multiStems);
        this.stemToMultiStemMap = this.data.stream().collect(Collectors.toMap(s->s.toString(),s->s));
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 2
            System.out.println("Num keywords before kmeans stage: " + data.size());

            System.out.println("Computing multi stem encoding map...");
            Map<MultiStem,INDArray> multiStemToEncodingsMap = KeyphrasePredictionPipelineManager.buildNewKeywordToLookupTableMapHelper(word2Vec,data);
            Map<String,INDArray> labelToEncodingMap = multiStemToEncodingsMap.entrySet().parallelStream().collect(Collectors.toMap(e->e.getKey().toString(),e->e.getValue()));
            AssetKMeans kMeansModel = new AssetKMeans(labelToEncodingMap, data.size()/10);

            System.out.println("Starting to fit k means...");
            Map<String,List<String>> clusters = kMeansModel.clusterAssets();
            data = clusters.keySet().stream()
                        .map(name->stemToMultiStemMap.get(name))
                        .filter(stem->stem!=null).collect(Collectors.toSet());

            System.out.println("Num keywords after kmeans stage: " + data.size());

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
