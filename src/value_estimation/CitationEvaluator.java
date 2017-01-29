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

/**
 * Created by Evan on 1/27/2017.
 */
public class CitationEvaluator extends Evaluator {
    private static final File file = new File("citation_value_model.jobj");

    @Override
    protected Map<String,Double> loadModel() {
        return (Map<String,Double>)Database.tryLoadObject(file);
    }

    private static Map<String,Double> runModel(WeightLookupTable<VocabWord> lookupTable){
        System.out.println("Starting to load citation evaluator...");
        List<String> patents = new ArrayList<>(Database.getValuablePatents());
        Collection<String> assignees = Database.getAssignees();
        Map<String,Set<String>> patentToReferencedByMap = (Map<String,Set<String>>)Database.tryLoadObject(new File("patent_to_referenced_by_map.jobj"));
        Map<String,Double> model = new HashMap<>();
        System.out.println("Calculating scores for patents...");
        Map<String,Double> oldScores = new HashMap<>();
        patents.forEach(patent->{
            if(patentToReferencedByMap.containsKey(patent)) {
                oldScores.put(patent,(double)patentToReferencedByMap.get(patent).size());
            } else {
                oldScores.put(patent,0d);
            }
        });
        System.out.println("Updating scores again...");
        patents.forEach(patent->{
            if(patentToReferencedByMap.containsKey(patent)) {
                double score = oldScores.get(patent);
                Set<String> references = patentToReferencedByMap.get(patent);
                double bonus = 0.0;
                for (String ref : references) {
                    if(oldScores.containsKey(ref)) {
                        bonus += oldScores.get(ref);
                    } else {
                        bonus += 1.0;
                    }
                }
                model.put(patent,score+Math.sqrt(bonus));
            } else {
                model.put(patent,0d);
            }
        });

        System.out.println("Calculating scores for assignees...");
        assignees.forEach(assignee->{
            System.out.println("Updating assignee: "+assignee);
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
        System.out.println("Starting to run model.");
        Map<String,Double> map = runModel(ParagraphVectorModel.loadParagraphsModel().getLookupTable());
        System.out.println("Finished... Now writing model to file...");
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        oos.writeObject(map);
        oos.flush();
        oos.close();
        System.out.println("Finished successfully.");
    }
}
