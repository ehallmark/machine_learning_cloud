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
public class NoveltyEvaluator extends Evaluator {
    private static final File file = new File("novelty_value_model.jobj");

    @Override
    protected Map<String,Double> loadModel() {
        return (Map<String,Double>)Database.tryLoadObject(file);
    }

    private static Map<String,Double> runModel(ParagraphVectors paragraphVectors){
        final LocalDate START_DATE = LocalDate.of(2010,1,1);
        final int SAMPLE_SIZE = 500;
        System.out.println("Starting to load novelty evaluator...");
        List<String> patents = new ArrayList<>(Database.getValuablePatents());
        Map<LocalDate,Set<String>> dateToPatentsMap = Collections.synchronizedMap((Map<LocalDate,Set<String>>)Database.tryLoadObject(new File("pubdate_to_patent_map.jobj")));
        // group dates and patents by month
        Map<LocalDate,Set<String>> groupedDateToPatentMap = new HashMap<>();
        dateToPatentsMap.forEach((date,assets)->{
            LocalDate firstDayOfMonth =LocalDate.of(date.getYear(),date.getMonthValue(),1);
            if(groupedDateToPatentMap.containsKey(firstDayOfMonth)) {
                groupedDateToPatentMap.get(firstDayOfMonth).addAll(assets);
            } else {
                Set<String> set = new HashSet<>(assets);
                groupedDateToPatentMap.put(firstDayOfMonth,set);
            }
        });
        SortedSet<LocalDate> sortedDates = new TreeSet<>(groupedDateToPatentMap.keySet());
        Collection<String> assignees = Database.getAssignees();
        WeightLookupTable<VocabWord> lookupTable = paragraphVectors.getLookupTable();
        SimilarPatentFinder patentFinder = new SimilarPatentFinder(Database.getValuablePatents(),null,lookupTable);

        List<Map<String,Double>> allScores = new ArrayList<>();
        Map<String,Double> model = new HashMap<>();

        for(LocalDate date : sortedDates) {

        }
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
