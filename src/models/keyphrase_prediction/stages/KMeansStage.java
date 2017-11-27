package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.kmeans.DistanceFunctions;
import models.kmeans.KMeans;
import models.similarity_models.word2vec_to_cpc_encoding_model.Word2VecToCPCEncodingNN;
import models.similarity_models.word2vec_to_cpc_encoding_model.Word2VecToCPCIterator;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Created by Evan on 11/21/2017.
 */
public class KMeansStage extends Stage<Set<MultiStem>>  {
    private Map<String,String> stemToBestPhraseMap;
    private Word2Vec word2Vec;
    private MultiLayerNetwork net;
    private Map<String,MultiStem> stemToMultiStemMap;
    private Map<MultiStem,AtomicLong> docCounts;
    public KMeansStage(Collection<MultiStem> multiStems, Map<String,String> stemToBestPhraseMap, Map<MultiStem,AtomicLong> docCounts, Word2Vec word2Vec, MultiLayerNetwork net, Model model) {
        super(model);
        this.stemToBestPhraseMap=stemToBestPhraseMap;
        this.word2Vec=word2Vec;
        this.net=net;
        this.data = multiStems==null? Collections.emptySet() : new HashSet<>(multiStems);
        this.stemToMultiStemMap = this.data.stream().collect(Collectors.toMap(s->s.toString(),s->s));
        this.docCounts=docCounts;
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 2
            System.out.println("Num keywords before kmeans stage: " + data.size());
            System.out.println("Reading dictionary...");

            KMeans kMeansModel = new KMeans(data.size()/4, DistanceFunctions.COSINE_DISTANCE_FUNCTION);
            int numKMeansEpochs = 40;
            Map<String,INDArray> multiStemToEncodingsMap = computeMultiStemToEncodingMap(stemToBestPhraseMap,word2Vec,net);

            kMeansModel.fit(multiStemToEncodingsMap,numKMeansEpochs,false);

            List<Set<String>> clusters = kMeansModel.getClusters();
            data = clusters.stream().map(cluster->{
                return cluster.stream()
                        .map(name->stemToMultiStemMap.get(name))
                        .filter(m->m!=null)
                        .map(m->new Pair<>(m,docCounts.getOrDefault(m,new AtomicLong(0)).get()))
                        .sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond()))
                        .map(p->p.getFirst())
                        .findFirst().orElse(null);

            }).filter(stem->stem!=null).collect(Collectors.toSet());

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


    public static Map<String,INDArray> computeMultiStemToEncodingMap(Map<String,String> stemToBestPhraseMap, Word2Vec word2Vec, MultiLayerNetwork net) {
        return stemToBestPhraseMap.entrySet().parallelStream().map(e->{
            String phrase = e.getValue();
            INDArray vec = Word2VecToCPCIterator.getPhraseVector(word2Vec,phrase);
            if(vec==null) return null;
            INDArray encoding = net.activateSelectedLayers(0,net.getnLayers()-1,vec);
            return new Pair<>(e.getKey(),encoding);
        }).filter(p->p!=null).collect(Collectors.toMap(p->p.getFirst(),p->p.getSecond()));
    }
}
