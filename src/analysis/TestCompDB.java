package analysis;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import dl4j_neural_nets.vectorization.GloVeModel;
import org.apache.commons.io.FileUtils;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;
import seeding.GetEtsiPatentsList;
import tools.Emailer;
import tools.MinHeap;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/8/16.
 */
public class TestCompDB {
    private static Map<String,INDArray> randomBaseMap;
    private static Map<String,Set<String>> technologyToTestPatentsMap;
    private static List<Patent> testPatents;
    private static Set<String> testPatentNames;

    public static String RunTest(Map<String,List<String>> compDBMap, WeightLookupTable<VocabWord> lookupTable, String name) throws Exception {
        final int seed = 41;
        randomBaseMap=new HashMap<>();
        testPatents = new ArrayList<>();
        technologyToTestPatentsMap=new HashMap<>();
        testPatentNames=new HashSet<>();
        Random rand = new Random(seed);
        final int invTestRatio = 4;
        System.out.println("Starting to split testing and base data...");
        // split test train
        Set<String> baseNames = new HashSet<>();
        compDBMap.entrySet().stream().forEach(e->{
            if(e.getValue().size()<= 2) return;
            try {
                List<String> validSet = e.getValue().stream().filter(v->{
                    if(testPatentNames.contains(v))return false;
                    try{
                        return lookupTable.vector(v) != null;

                    } catch(Exception ex) {
                        ex.printStackTrace();
                        return false;
                    }
                }).distinct().collect(Collectors.toList());
                if(validSet.size()<=2) return;
                Set<Patent> test = Sets.newHashSet(new Patent(validSet.get(0), lookupTable.vector(validSet.get(0))));
                List<Patent> base = Lists.newArrayList(new Patent(validSet.get(1), lookupTable.vector(validSet.get(1))));
                for (int i = 2; i < validSet.size(); i++) {
                    Patent p = new Patent(validSet.get(i), lookupTable.vector(validSet.get(i)));
                    if (rand.nextInt(invTestRatio) == 0) test.add(p);
                    else base.add(p);
                    System.out.println(p.getName());
                }
                Set<String> testPatentNames = test.stream().map(b->b.getName()).collect(Collectors.toSet());
                testPatents.addAll(test);
                testPatentNames.addAll(testPatentNames);
                // compute avg
                INDArray thisAvg = Nd4j.create(base.size(),base.stream().findFirst().get().getVector().columns());
                for(int i = 0; i < base.size(); i++) {
                    thisAvg.putRow(i, base.get(i).getVector());
                }
                INDArray avg = thisAvg.mean(0);
                randomBaseMap.put(e.getKey(), avg);
                technologyToTestPatentsMap.put(e.getKey(), testPatentNames);
                baseNames.addAll(base.stream().map(p->p.getName()).collect(Collectors.toList()));
            } catch(Exception ex) {
                ex.printStackTrace();
            }
        });

        StringJoiner toEmail = new StringJoiner("\n");
        toEmail.add("-- Comparison Test for "+name+" --");
        //toEmail.add("Total number of patents considered: "+(baseNames.size()+testPatents.size()));
        //toEmail.add("Number of patents used to train model: "+baseNames.size());
        //toEmail.add("Number of patents used for testing: "+testPatents.size());
        //toEmail.add("Number of distinct classifications: "+technologyToTestPatentsMap.size());
        System.out.println("Training model... Done");
        System.out.println("Starting to run tests...");
        for(int i : new int[]{1,2,3,5,10}) {
            System.out.println("Starting test when n="+i);
            test(i,toEmail);
        }
        return toEmail.toString();
    }

    public static void main(String[] args) throws Exception{
        /*Map<String,Pair<Float,INDArray>> googleVocab = BuildVocabVectorMap.readVocabMap(new File(Constants.BETTER_VOCAB_VECTOR_FILE));
        googleVocab.clear();
        SimilarPatentFinder.clearAllCaches();
        System.gc();System.gc();System.gc();*/
        //Map<String,Pair<Float,INDArray>> myVocab = BuildVocabVectorMap.readVocabMap(new File("my_custom_vocab_map_file.vocab"));
        //startVocabTest(myVocab,"2st Model (epoch 2)");


        /*ParagraphVectors descriptionVectors = ParagraphVectorModel.loadDescriptionModel();
        setParagraphVectors(descriptionVectors);
        startVocabTest(null,"Paragraph Vectors Description Model");
        */
        /*
        for(int epoch = 9; epoch <= 12; epoch++) {
            String modelName = "Paragraph Vectors Claim Model (" + epoch + ")";
            String filePath = ParagraphVectorModel.claimsParagraphVectorFile.getAbsolutePath() + epoch;
            RunModelTest(modelName, ParagraphVectorModel.loadModel(filePath));
        }
        */

        RunModelTest("GloDV", WordVectorSerializer.loadTxtVectors(GloVeModel.gloVeFile));
    }

    public static void RunModelTest(String modelName,WordVectors... pVectorsList) throws Exception {
        for(WordVectors paragraphVectors : pVectorsList) {
            StringJoiner toEmail = new StringJoiner("\n");
            toEmail.add(startVocabTest(paragraphVectors.lookupTable(), modelName));
            new Emailer(toEmail.toString());
        }
    }

    private static String startVocabTest(WeightLookupTable<VocabWord> lookupTable, String modelName) throws Exception {
        Map<String,List<String>> transactionMap = new HashMap<>();
        transactionMap.put("0",FileUtils.readLines(new File("unvaluable_patents.csv")));
        transactionMap.put("1",FileUtils.readLines(new File("valuable_patents.csv")));

        StringJoiner join = new StringJoiner("\n");
        Map<String,List<String>> gatherTechMap = Database.getGatherTechMap();
        Database.setupSeedConn();
        Map<String,List<String>> gatherValueMap = Database.getGatherRatingsMap();
        Database.setupSeedConn();

        join.add(RunTest(transactionMap,lookupTable, "Transaction Probability ("+modelName+")"));
        join.add(RunTest(gatherTechMap,lookupTable, "Gather Technologies ("+modelName+")"));
        join.add(RunTest(gatherValueMap,lookupTable, "Gather Valuation ("+modelName+")"));
        //join.add(RunTest(GetEtsiPatentsList.getETSIPatentMap(),lookupTable, "ETSI Standards ("+modelName+")"));
        return join.toString();
    }

    private static void test(int n,StringJoiner toEmail) {
        AtomicInteger overallCorrect = new AtomicInteger(0);
        getTestPredictions(n).forEach(p->{
            for (String prediction : p.getFirst()) {
                if (technologyToTestPatentsMap.get(prediction).contains(p.getSecond())) {
                    overallCorrect.getAndIncrement();
                    break;
                }
            }
        });
        toEmail.add("--");
        toEmail.add("Number of allowed predictions: "+n);
        //toEmail.add("Number correctly predicted: "+overallCorrect.get()+" out of "+testPatents.size()+" test patents");
        toEmail.add("Model Accuracy: ");
        toEmail.add("%"+((new Double(overallCorrect.get())/testPatents.size())*100.0));
    }

    private static List<Pair<List<String>,String>> getTestPredictions(int n) {
        List<MinHeap<WordFrequencyPair<String,Double>>> heaps = new ArrayList<>(testPatents.size());
        for(int i = 0; i < testPatents.size(); i++) {
            heaps.add(new MinHeap<>(n));
        }
        randomBaseMap.entrySet().forEach(e->{
            System.out.println(e.getKey());
            for(int i = 0; i < heaps.size(); i++) {
                INDArray vec = testPatents.get(i).getVector();
                heaps.get(i).add(new WordFrequencyPair<>(e.getKey(), Transforms.cosineSim(vec,e.getValue())));
            }
        });
        List<Pair<List<String>,String>> toReturn = new ArrayList<>();
        for(int i = 0; i < heaps.size(); i++) {
            MinHeap<WordFrequencyPair<String,Double>> heap = heaps.get(i);
            List<String> predictions = new ArrayList<>();
            while (!heap.isEmpty()) {
                predictions.add(0, heap.remove().getFirst());
            }
            toReturn.add(new Pair<>(predictions,testPatents.get(i).getName()));
        }
        return toReturn;
    }

}
