package value_estimation;

import analysis.SimilarPatentFinder;
import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;
import server.tools.excel.ExcelWritable;
import tools.PortfolioList;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
        final LocalDate START_DATE = LocalDate.of(2010,1,1);
        final int SAMPLE_SIZE = 500;
        System.out.println("Starting to load classification evaluator...");
        List<String> classifications = new ArrayList<>(Database.getClassCodes());
        List<String> patents = new ArrayList<>(Database.getValuablePatents());
        Map<LocalDate,Set<String>> dateToPatentsMap = Collections.synchronizedMap((Map<LocalDate,Set<String>>)Database.tryLoadObject(new File("pubdate_to_patent_map.jobj")));
        SortedSet<LocalDate> sortedDates = new TreeSet<>(dateToPatentsMap.keySet());
        sortedDates.removeIf(date->date.compareTo(START_DATE)>=0);
        Collection<String> assignees = Database.getAssignees();
        WeightLookupTable<VocabWord> lookupTable = paragraphVectors.getLookupTable();
        SimilarPatentFinder classCodeFinder = new SimilarPatentFinder(Database.getClassCodes(),null,lookupTable);

        List<Map<String,Double>> classScores = new ArrayList<>();


        for(LocalDate date : sortedDates) {

            System.out.println("Starting date: "+date);
            List<String> patentGroup = new ArrayList<>(dateToPatentsMap.get(date));
            // sample patentGroup
            Collections.shuffle(patentGroup,new Random(System.currentTimeMillis()));
            List<String> sample = patentGroup.subList(0,Math.max(patentGroup.size(),SAMPLE_SIZE));
            SimilarPatentFinder sampleFinder = new SimilarPatentFinder(sample,null,lookupTable);
            INDArray avg = SimilarPatentFinder.computeAvg(sampleFinder.getPatentList());
            Map<String,Double> scores = new HashMap<>();
            if(avg!=null) {
                classCodeFinder.getPatentList().forEach(classCode->{
                    scores.put(classCode.getName(),1.0+Transforms.cosineSim(classCode.getVector(),avg));
                });
            }
            classScores.add(scores);
        }

        System.out.println("Calculating analysis for evaluator...");
        Map<String,Double> model = new HashMap<>();
        for(String patent: patents) {
            model.put(patent,0.0);
        }
        for(int i = 0; i < classScores.size()-1; i+=2) {
            Map<String,Double> scoreT = classScores.get(i);
            Map<String,Double> scoreT2 = classScores.get(i+1);

            for(int j = 0; j < scoreT.size(); j++) {
                String clazz = classifications.get(j);
                Double p1 = scoreT.get(clazz);
                Double p2 = scoreT2.get(clazz);
                if(p1>0) {
                    double score = (p2-p1)/p1;
                    model.put(clazz,model.get(clazz)+score*Math.pow(i,1.5));
                }
            }
        }
        patents.forEach(patent->{
            System.out.println("Patent: "+patent);
            double score = 0.0;
            double toDivide = 0.0;
            INDArray patentVec = lookupTable.vector(patent);
            if(patentVec!=null) {
                PortfolioList list = classCodeFinder.findSimilarPatentsTo(patent, patentVec, new HashSet<>(), 0.9, 10, PortfolioList.Type.class_codes);
                for(ExcelWritable x : list.getPortfolio()) {
                    String clazz = x.getName();
                    if(clazz!=null) {
                        score += (x.getSimilarity()*model.get(clazz));
                        toDivide+=x.getSimilarity();
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
