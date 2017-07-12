package models.similarity_models.paragraph_vectors;


import models.dl4j_neural_nets.vectorization.ParagraphVectorModel;
import seeding.ai_db_updater.tools.RelatedAssetsGraph;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import models.similarity_models.BaseSimilarityModel;
import user_interface.ui_models.portfolios.items.Item;

import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 7/26/16.
 */
public class SimilarPatentFinder extends BaseSimilarityModel {
    private static final File file = new File("data/similar_patent_finder_lookup_table.jobj");
    private static Map<String,INDArray> LOOKUP_TABLE;
    protected static ParagraphVectors paragraphVectors;

    public static WeightLookupTable<VocabWord> getWeightLookupTable() {
        if(paragraphVectors==null) loadLookupTable();
        return paragraphVectors.getLookupTable();
    }

    private static void loadLookupTable() {
        if(paragraphVectors!=null)return;
        boolean testing = false;
        try {
            if(testing==true) {
                paragraphVectors = ParagraphVectorModel.loadTestParagraphsModel();
            } else {
                paragraphVectors = ParagraphVectorModel.loadParagraphsModel();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public SimilarPatentFinder(Collection<Item> candidateSet, String name) {
        super(candidateSet,name,getLookupTable());
    }

    public synchronized static Map<String,INDArray> getLookupTable() {
        if(LOOKUP_TABLE==null) {
            LOOKUP_TABLE=(Map<String,INDArray>)Database.tryLoadObject(file);
        }
        return LOOKUP_TABLE;
    }
    public static void main(String[] args) {
        RelatedAssetsGraph relatedAssetsGraph = RelatedAssetsGraph.get();
        WeightLookupTable<VocabWord> lookupTable = getWeightLookupTable();
        Map<String,INDArray> toSave = Collections.synchronizedMap(new HashMap<>());
        Database.getAllPatentsAndApplications().parallelStream().forEach(patent->{
            INDArray vec = lookupTable.vector(patent);
            if(vec!=null) toSave.put(patent,vec);
        });
        Database.getAssignees().parallelStream().forEach(assignee->{
            INDArray vec = lookupTable.vector(assignee);
            if(vec!=null) toSave.put(assignee,vec);
        });
        Database.trySaveObject(toSave,file);
    }
}
