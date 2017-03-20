package analysis.genetics.keyword_analysis;

import analysis.SimilarPatentFinder;
import analysis.WordFrequencyPair;
import analysis.patent_view_api.Patent;
import analysis.patent_view_api.PatentAPIHandler;
import analysis.tech_tagger.TechTagger;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;
import server.SimilarPatentServer;
import tools.MinHeap;
import tools.PortfolioList;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 3/9/17.
 */
public class GatherKeywordTechTagger extends TechTagger {
    private static Map<String,List<INDArray>> technologyToKeywordMap;
    private static WeightLookupTable<VocabWord> lookupTable;
    static {
        technologyToKeywordMap= (Map<String,List<INDArray>>)Database.tryLoadObject(WordFrequencyCalculator.technologyToTopKeyWordsMapFile);
        try {
            SimilarPatentServer.loadLookupTable();
        }catch(Exception e) {
            e.printStackTrace();
        }
        lookupTable= SimilarPatentServer.getLookupTable();
    }

    @Override
    public int size() {
        return technologyToKeywordMap.size();
    }

    @Override
    public double getTechnologyValueFor(Collection<String> items, String technology, PortfolioList.Type type) {
        if(!technologyToKeywordMap.containsKey(technology) || !type.equals(PortfolioList.Type.patents)) {
            return 1d;
        }
        List<INDArray> keywordVectorsForTech = technologyToKeywordMap.get(technology);
        int topN = keywordVectorsForTech.size()/10;
        return scoreKeywordHeap(keywordVectorsForTech,requestVectorFromPatents(items),topN);
    }


    @Override
    public List<Pair<String, Double>> getTechnologiesFor(Collection<String> items, PortfolioList.Type type, int n) {
        INDArray meanVec = requestVectorFromPatents(items);
        MinHeap<WordFrequencyPair<String,Double>> technologyHeap = new MinHeap<>(n);
        technologyToKeywordMap.forEach((tech,vecs) -> {
            int topN = vecs.size()/10;
            technologyHeap.add(new WordFrequencyPair<>(tech,scoreKeywordHeap(vecs,meanVec,topN)));
        });
        List<Pair<String,Double>> predictions = new ArrayList<>(n);
        while(!technologyHeap.isEmpty()) {
            predictions.add(0,technologyHeap.remove().toPair());
        }
        return predictions;
    }

    @Override
    public Collection<String> getAllTechnologies() {
        return new HashSet<>(technologyToKeywordMap.keySet());
    }

    private INDArray getAvgVectorFromWords(String[] words, String name) {
        SimilarPatentFinder finder = new SimilarPatentFinder(Arrays.asList(words),name,lookupTable);
        if(!finder.getPatentList().isEmpty()) {
            INDArray avg = finder.computeAvg();
            return avg;
        } else return null;
    }

    private double scoreKeywordHeap(List<INDArray> keywordVectorsForTech, INDArray meanVec, int topN) {
        if(meanVec==null)return 1d;
        if(topN <= 0) return 1d;
        MinHeap<Double> keywordHeap = new MinHeap<>(topN);
        keywordVectorsForTech.forEach(keywordTechVec->{
            keywordHeap.add(Transforms.cosineSim(meanVec,keywordTechVec));
        });
        List<Double> values = new ArrayList<>(topN);
        while(!keywordHeap.isEmpty()) {
            values.add(keywordHeap.remove());
        }
        return 3.0+ (2.0 * values.stream().collect(Collectors.averagingDouble(d->d)));
    }

    private INDArray requestVectorFromPatents(Collection<String> items) {
        Collection<Patent> patents = PatentAPIHandler.requestAllPatents(items);
        List<INDArray> patentVectors = new ArrayList<>(patents.size());
        patents.forEach(patent->{
            List<INDArray> vectors = new ArrayList<>(2);
            if(patent==null) return;
            if(patent.getAbstract()!=null) {
                String[] abstractWords = patent.getAbstract().split("\\s+");
                INDArray vec = getAvgVectorFromWords(abstractWords,patent.getPatentNumber()+"_abstract");
                if(vec!=null) {
                    vectors.add(vec);
                }
            }
            if(patent.getAbstract()!=null) {
                String[] titleWords = patent.getInventionTitle().split("\\s+");
                INDArray vec = getAvgVectorFromWords(titleWords,patent.getPatentNumber()+"_title");
                if(vec!=null) {
                    vectors.add(vec);
                }
            }
            if(vectors.isEmpty()) return;
            INDArray meanVec = Nd4j.vstack(vectors).mean(0);
            patentVectors.add(meanVec);
        });
        if(patentVectors.isEmpty()) return null;
        return Nd4j.vstack(patentVectors).mean(0);
    }
}
