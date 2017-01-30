package value_estimation;

import analysis.Patent;
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

/**
 * Created by Evan on 1/27/2017.
 */
public class PriorArtEvaluator extends Evaluator {
    private static final File file = new File("prior_art_value_model.jobj");

    @Override
    protected Map<String,Double> loadModel() {
        return (Map<String,Double>)Database.tryLoadObject(file);
    }

    private static Map<String,Double> runModel(ParagraphVectors paragraphVectors){
        final int SAMPLE_SIZE = 200;
        final int WINDOW_SIZE = 36;
        System.out.println("Starting to load prior art evaluator...");
        Map<LocalDate,Set<String>> dateToPatentsMap = Collections.synchronizedMap((Map<LocalDate,Set<String>>)Database.tryLoadObject(new File("pubdate_to_patent_map.jobj")));

        // group dates by month
        Map<LocalDate,Set<String>> groupedDateToPatentMap = groupMapByMonth(dateToPatentsMap,SAMPLE_SIZE);

        List<INDArray> monthVectors = new ArrayList<>(groupedDateToPatentMap.size());
        Map<String,List<INDArray>> patentToWindowDataMap = new HashMap<>();
        List<LocalDate> datesToIndex = new ArrayList<>(groupedDateToPatentMap.size());
        SortedSet<LocalDate> sortedDates = new TreeSet<>(groupedDateToPatentMap.keySet());
        sortedDates.forEach(date->{
            System.out.println("Computing vector for: "+date);
            Set<String> patentSet = groupedDateToPatentMap.get(date);
            datesToIndex.add(date);
            monthVectors.add(SimilarPatentFinder.computeAvg(new SimilarPatentFinder(patentSet,null,paragraphVectors.getLookupTable()).getPatentList()));
        });

        sortedDates.forEach(date->{
            Set<String> patentSet = groupedDateToPatentMap.get(date);
            int dateIdx = datesToIndex.indexOf(date);
            if(dateIdx-WINDOW_SIZE<0) dateIdx=WINDOW_SIZE;
            if(dateIdx+WINDOW_SIZE>monthVectors.size()) dateIdx=monthVectors.size()-WINDOW_SIZE;
            List<INDArray> subList = monthVectors.subList(dateIdx-WINDOW_SIZE,dateIdx+WINDOW_SIZE);
            patentSet.forEach(patent->{
                System.out.println("adding window for for: "+patent);
                patentToWindowDataMap.put(patent,subList);
            });

        });

        Collection<String> assignees = Database.getAssignees();
        WeightLookupTable<VocabWord> lookupTable = paragraphVectors.getLookupTable();
        SimilarPatentFinder patentFinder = new SimilarPatentFinder(Database.getValuablePatents(),null,lookupTable);
        // compute scores for each patent
        Map<String,Double> model = new HashMap<>();
        patentFinder.getPatentList().forEach(patent->{
            List<INDArray> data = patentToWindowDataMap.get(patent.getName());
            double totalScore = 0.0;
            for(int i = 0; i < data.size()-1; i+=2) {
                double simT = Transforms.cosineSim(patent.getVector(),data.get(i))+1.0;
                double simT2 = Transforms.cosineSim(patent.getVector(),data.get(i+1))+1.0;
                // calculate growth
                double growth = (simT2-simT)/simT;
                double timeFactor = (double)i-WINDOW_SIZE;
                totalScore+=(timeFactor*growth);
            }
            System.out.println("Score for patent "+patent.getName()+": "+totalScore);
            model.put(patent.getName(),totalScore);
        });
        assignees.forEach(assignee->{
            Collection<String> assigneePatents = Database.selectPatentNumbersFromAssignee(assignee);
            double score = 0.0;
            double toDivide = 0.0;
            INDArray assigneeVec = lookupTable.vector(assignee);
            if(assigneeVec!=null) {
                for (String patent : assigneePatents) {
                    if(!model.containsKey(patent)) continue;
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
