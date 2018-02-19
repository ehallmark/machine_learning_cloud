package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.kmeans.AssetKMeans;
import models.similarity_models.combined_similarity_model.Word2VecToCPCIterator;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        this.stemToMultiStemMap = this.data.stream().collect(Collectors.toMap(s->s.getBestPhrase(),s->s));
        this.docCounts=docCounts;
    }

    @Override
    public Set<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 2
            System.out.println("Num keywords before kmeans stage: " + data.size());

            System.out.println("Computing multi stem encoding map...");
            Map<String,INDArray> multiStemToEncodingsMap = computeMultiStemToEncodingMap(stemToBestPhraseMap,word2Vec,net);
            AssetKMeans kMeansModel = new AssetKMeans(multiStemToEncodingsMap, data.size()/10);

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


    public static Map<String,INDArray> computeMultiStemToEncodingMap(Map<String,String> stemToBestPhraseMap, Word2Vec word2Vec, MultiLayerNetwork net) {
        int batchSize = 1000;
        AtomicInteger idx = new AtomicInteger(0);
        List<Map.Entry<String,String>> entries = new ArrayList<>(stemToBestPhraseMap.entrySet());
        List<List<Map.Entry<String,String>>> entryBatches = new ArrayList<>();
        for(int i = 0; i < entries.size(); i+= batchSize) {
            int endIdx = Math.min(i+batchSize,entries.size());
            if(endIdx>i) {
                entryBatches.add(entries.subList(i, endIdx));
            }
        }
        System.gc();
        return entryBatches.stream().flatMap(batch->{
            List<Pair<String,INDArray>> vecPairs = batch.stream().map(e->new Pair<>(e.getKey(),Word2VecToCPCIterator.getPhraseVector(word2Vec,e.getValue(),1)))
                    .filter(p->p.getSecond()!=null).collect(Collectors.toList());
            if(vecPairs.isEmpty())return Stream.empty();
            INDArray mat = Nd4j.create(vecPairs.size(),word2Vec.getLayerSize()*3);
            for(int i = 0; i < vecPairs.size(); i++) {
                mat.putRow(i,vecPairs.get(i).getSecond());
            }
            INDArray encoding = net.activateSelectedLayers(0,net.getnLayers()-1,mat);
            List<Pair<String,INDArray>> pairs = new ArrayList<>();
            for(int i = 0; i < vecPairs.size(); i++) {
                pairs.add(new Pair<>(vecPairs.get(i).getFirst(),encoding.getRow(i)));
                if (idx.getAndIncrement() % 10000 == 9999) {
                    System.out.println("Finished: " + idx.get());
                    System.gc();
                }
            }
            return pairs.stream();
        }).filter(p->p!=null).collect(Collectors.toMap(p->p.getFirst(),p->p.getSecond()));
    }
}
