package dl4j_neural_nets.tests;

import similarity_models.paragraph_vectors.SimilarPatentFinder;
import similarity_models.paragraph_vectors.WordFrequencyPair;
import com.google.common.util.concurrent.AtomicDouble;
import dl4j_neural_nets.iterators.datasets.ParagraphVectorDataSetIterator;
import org.apache.commons.io.FileUtils;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.GetEtsiPatentsList;
import tools.MinHeap;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/27/16.
 */
public class ModelEvaluator {
    private double score;
    private String stats;
    public String evaluateModel(DataSetIterator iterator, MultiLayerNetwork net) {
        Evaluation evaluation = new Evaluation();
        while(iterator.hasNext()){
            DataSet t = iterator.next();
            INDArray features = t.getFeatureMatrix();
            INDArray labels = t.getLabels();
            INDArray predicted = net.output(features,false);
            evaluation.eval(labels, predicted);
        }
        iterator.reset();
        return evaluation.stats();
    }

    public String getStats() {
        return stats;
    }

    public double getScore() {
        return score;
    }

    public String evaluateWordVectorModel(WeightLookupTable<VocabWord> lookupTable, String modelName) throws Exception {
        Map<String,List<String>> transactionMap = new HashMap<>();
        transactionMap.put("0", FileUtils.readLines(new File("unvaluable_patents.csv")));
        transactionMap.put("1",FileUtils.readLines(new File("valuable_patents.csv")));

        StringJoiner join = new StringJoiner("\n");
       /* Map<String,List<String>> gatherTechMap = Database.getGatherTechMap();
        Database.setupSeedConn();
        Map<String,List<String>> gatherValueMap = Database.getGatherRatingsMap();*/
        //Database.setupSeedConn();

        boolean fallBackToWordVectors = false;
        AtomicDouble scoreCounter = new AtomicDouble(0d);

        /*join.add("Transaction Probability ("+modelName+")");
        join.add(evaluateModel(transactionMap,fallBackToWordVectors,lookupTable));
        join.add("----------------------------");

        scoreCounter.addAndGet(score);

        /*join.add("Gather Technologies ("+modelName+")");
        join.add(evaluateModel(gatherTechMap,fallBackToWordVectors,lookupTable));
        join.add("----------------------------");

        scoreCounter.addAndGet(score);

        join.add("Gather Valuation ("+modelName+")");
        join.add(evaluateModel(gatherValueMap,fallBackToWordVectors,lookupTable));
        join.add("----------------------------");

        scoreCounter.addAndGet(score);*/

        join.add("ETSI Standards ("+modelName+")");
        join.add(evaluateModel(GetEtsiPatentsList.getETSIPatentMap(),fallBackToWordVectors,lookupTable));
        join.add("----------------------------");

        scoreCounter.addAndGet(score);

        int numModels = 4;
        score = scoreCounter.get()/numModels;
        stats=join.toString();
        return join.toString();
    }

    public String evaluateModel(Map<String,Collection<String>> classificationToPatentMap, boolean fallbackToWordVectors, WeightLookupTable<VocabWord> lookupTable) {
        Evaluation evaluation = new Evaluation();
        Map<String,INDArray> classificationsFeatureMap = new HashMap<>();
        Map<String,List<String>> classificationToPatentMapForTesting = new HashMap<>();
        List<String> orderedLabels = new ArrayList<>(classificationToPatentMap.keySet());
        classificationToPatentMap.forEach((klass,_values)->{
            Collection<String> patents;
            if(fallbackToWordVectors) {
                patents = _values;
            } else {
                patents = _values.stream().filter(patent -> lookupTable.vector(patent) != null).collect(Collectors.toList());
            }
            if(patents.size() <= 2) return;
            Collections.shuffle((List<String>)patents,new Random(0));
            int splitIdx = (int)Math.floor(0.75*patents.size());

            List<INDArray> features = ((List<String>)patents).subList(0,splitIdx).stream().map(p->{
                try {
                    return lookupTable.vector(p);
                } catch(Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }).filter(p->p!=null).collect(Collectors.toList());

            if(features.isEmpty()) return;

            classificationsFeatureMap.put(klass, Nd4j.vstack(features).mean(0));
            classificationToPatentMapForTesting.put(klass,((List<String>)patents).subList(splitIdx,patents.size()));
        });
        DataSetIterator iterator = new ParagraphVectorDataSetIterator(classificationToPatentMapForTesting,orderedLabels,1,fallbackToWordVectors,lookupTable);
        while(iterator.hasNext()){
            DataSet t = iterator.next();
            INDArray features = t.getFeatureMatrix();
            INDArray labels = t.getLabels();
            INDArray predicted = getPrediction(features,classificationsFeatureMap, orderedLabels);
            evaluation.eval(labels, predicted);
        }
        score=evaluation.f1();
        return evaluation.stats();
    }


    private INDArray getPrediction(INDArray featuresMatrix, Map<String,INDArray> classificationsFeatureMap, List<String> orderedLabels) {
        List<MinHeap<WordFrequencyPair<String,Double>>> heaps = new ArrayList<>(featuresMatrix.rows());
        for(int i = 0; i < featuresMatrix.rows(); i++) {
            heaps.add(new MinHeap<>(1));
        }
        for(int c = 0; c < orderedLabels.size(); c++) {
            String label = orderedLabels.get(c);
            if(classificationsFeatureMap.containsKey(label)) {
                INDArray classificationFeatures = classificationsFeatureMap.get(label);
                for (int i = 0; i < heaps.size(); i++) {
                    INDArray features = featuresMatrix.getRow(i);
                    if(features!=null && classificationFeatures!=null) {
                        heaps.get(i).add(new WordFrequencyPair<>(label, Transforms.cosineSim(features, classificationFeatures)));
                    }
                }
            }
        }

        INDArray predictions = Nd4j.zeros(featuresMatrix.rows(),orderedLabels.size());
        for(int i = 0; i < heaps.size(); i++) {
            MinHeap<WordFrequencyPair<String,Double>> heap = heaps.get(i);
            String prediction = heap.remove().getFirst();
            predictions.putScalar(i,orderedLabels.indexOf(prediction),1.0d);
        }
        return predictions;
    }

    public static void main(String[] args) throws Exception {
        WordVectors vec = WordVectorSerializer.readParagraphVectorsFromText("wordvectorexample2.txt");
        new ModelEvaluator().evaluateWordVectorModel(vec.lookupTable(),"Example");
    }
}
