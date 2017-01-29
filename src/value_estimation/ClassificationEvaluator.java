package value_estimation;

import analysis.SimilarPatentFinder;
import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;
import tools.PortfolioList;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.*;

/**
 * Created by Evan on 1/27/2017.
 */
public class ClassificationEvaluator extends Evaluator {
    private static final File file = new File("classification_value_model.jobj");

    @Override
    protected Map<String,Double> loadModel() {
        return (Map<String,Double>)Database.tryLoadObject(file);
    }

    public Map<String,Double> getMap() {
        return model;
    }

    private static Map<String,Double> runModel(ParagraphVectors paragraphVectors){
        System.out.println("Starting to load classification evaluator...");
        List<String> classifications = new ArrayList<>(Database.getClassCodes());
        List<String> patents = new ArrayList<>(Database.getValuablePatents());
        Collection<String> assignees = Database.getAssignees();
        WeightLookupTable<VocabWord> lookupTable = paragraphVectors.getLookupTable();
        // sort patents
        List<Set<String>> similarWordList = new ArrayList<>(classifications.size());
        Collections.sort(patents);
        final int periodSize = 10000;
        final int numPeriods = 30;
        patents=patents.subList(patents.size()-(periodSize*numPeriods),patents.size());
        double threshHold = 0.90;
        List<List<Double>> classScores = new ArrayList<>(patents.size()/periodSize+1);

        // get relevance to each class code
        for(int c = 0; c < classifications.size(); c++) {
            String clazz = classifications.get(c);
            Set<String> similar = new HashSet<>(paragraphVectors.similarWordsInVocabTo(clazz,threshHold));
            System.out.println("Similar words to "+clazz+": "+similar.size());
            similarWordList.add(similar);
        }
        for(int i = 0; i < patents.size()-periodSize; i+= periodSize) {
            List<Double> scores = new ArrayList<>(classifications.size());
            classScores.add(scores);
            for(int c = 0; c < classifications.size(); c++) {
                scores.add(0.0);
            }
            List<String> patentBatch = patents.subList(i,i+periodSize);

            // get relevance to each class code
            for(int c = 0; c < classifications.size(); c++) {
                String clazz = classifications.get(c);
                Set<String> similar = similarWordList.get(c);
                if(similar!=null&&!similar.isEmpty()) {
                    double score = 0.0;
                    for(String patent : patentBatch) {
                        if(similar.contains(patent)) score+=1.0;
                    }
                    System.out.println("Score for "+clazz+": "+score);
                    scores.set(c,score);
                }
            }
        }
        System.out.println("Calculating analysis for evaluator...");
        Map<String,Double> model = new HashMap<>();
        for(String patent: patents) {
            model.put(patent,0.0);
        }
        for(int i = 0; i < classScores.size()-1; i+=2) {
            List<Double> scoreT = classScores.get(i);
            List<Double> scoreT2 = classScores.get(i+1);
            for(int j = 0; j < scoreT.size(); j++) {
                Double p1 = scoreT.get(j);
                Double p2 = scoreT2.get(j);
                if(p1>threshHold) {
                    double score = (p2-p1)/p1;
                    String clazz = classifications.get(j);
                    model.put(clazz,model.get(clazz)+score*Math.pow(i,1.5));
                }
            }
        }
        SimilarPatentFinder classCodeFinder = new SimilarPatentFinder(Database.getClassCodes(),null,lookupTable);
        patents.forEach(patent->{
            double score = 0.0;
            double toDivide = 0.0;
            INDArray patentVec = lookupTable.vector(patent);
            if(patentVec!=null) {
                PortfolioList list = classCodeFinder.findSimilarPatentsTo(patent, patentVec, new HashSet<>(), threshHold, 10, PortfolioList.Type.class_codes);
                for(String clazz : list.getPortfolioAsStrings()) {
                    INDArray classVec = lookupTable.vector(clazz);
                    if(classVec!=null&&patentVec!=null) {
                        double sim = Transforms.cosineSim(classVec, patentVec);
                        if (sim>threshHold){
                            score += (sim*model.get(clazz));
                            toDivide+=sim;
                        }
                    }
                }
            }
            if(toDivide>0) {
                score = score/toDivide;
            } else score=0.0;
            model.put(patent,score);
        });
        assignees.forEach(assignee->{
            Collection<String> assigneePatents = Database.selectPatentNumbersFromAssignee(assignee);
            double score = 0.0;
            double toDivide = 0.0;
            INDArray assigneeVec = lookupTable.vector(assignee);
            if(assigneeVec!=null) {
                for (String patent : assigneePatents) {
                    INDArray patentVec = lookupTable.vector(patent);
                    if (patentVec != null) {
                        double weight = Math.max(0.2, Transforms.cosineSim(patentVec, assigneeVec));
                        score += model.get(patent) * weight;
                        toDivide += weight;
                    }
                }
            }
            if(toDivide>0) {
                score = score/toDivide;
            } else score=0.0;
            model.put(assignee,score);
        });
        System.out.println("Finished evaluator...");
        return model;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting to load lookupTable...");
        ParagraphVectors paragraphVectors = ParagraphVectorModel.loadParagraphsModel();
        System.out.println("Finished.");
        Map<String,Double> map = runModel(paragraphVectors);
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        oos.writeObject(map);
        oos.flush();
        oos.close();
    }
}
