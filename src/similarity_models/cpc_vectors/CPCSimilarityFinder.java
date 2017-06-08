package similarity_models.cpc_vectors;

import dl4j_neural_nets.vectorization.CPCAutoEncoderModel;
import graphical_models.classification.CPCKMeans;
import lombok.Getter;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import similarity_models.AbstractSimilarityModel;
import similarity_models.BaseSimilarityModel;
import similarity_models.paragraph_vectors.Patent;
import tools.MinHeap;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.PortfolioList;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by Evan on 6/8/2017.
 */
public class CPCSimilarityFinder extends BaseSimilarityModel {
    private static final File file = new File("data/cpc_similarity_finder_lookup_table.jobj");
    private static Map<String,INDArray> LOOKUP_TABLE;
    public CPCSimilarityFinder(Collection<String> candidateSet, String name) {
        super(candidateSet,name,getLookupTable());
    }

    public static Map<String,INDArray> getLookupTable() {
        if(LOOKUP_TABLE==null) {
            LOOKUP_TABLE=(Map<String,INDArray>) Database.tryLoadObject(file);
        }
        return LOOKUP_TABLE;
    }
    public static void main(String[] args) throws IOException {
        Database.initializeDatabase();

        int cpcDepth = CPCKMeans.DEFAULT_CPC_DEPTH;
        List<String> orderedClassifications = CPCAutoEncoderModel.getOrderedClassifications();

        MultiLayerNetwork model = CPCAutoEncoderModel.getModel();

        Map<String,INDArray> toSave = new HashMap<>();

        Database.getCopyOfAllPatents().forEach(patent->{
            INDArray vec = Nd4j.create(CPCKMeans.classVectorForPatents(Arrays.asList(patent),orderedClassifications,cpcDepth));
            if(vec!=null) {
                // encode
                INDArray encoding = model.activate(vec,false);
                if(encoding!=null && encoding.length()== Constants.VECTOR_LENGTH) {
                    System.out.println("Valid patent encoding");
                    toSave.put(patent, encoding);
                }
            }
        });

        System.out.println("Starting assignees");
        Database.getAssignees().forEach(assignee->{
            Collection assets = Database.selectPatentNumbersFromExactAssignee(assignee);
            if(assets==null||assets.isEmpty()) return;

            INDArray vec = Nd4j.create(CPCKMeans.classVectorForPatents(assets,orderedClassifications,cpcDepth));
            if(vec!=null) {
                // encode
                INDArray encoding = model.activate(vec,false);
                if(encoding!=null && encoding.length()== Constants.VECTOR_LENGTH) {
                    System.out.println("Valid assignee encoding");
                    toSave.put(assignee, encoding);
                }
            }
        });
        Database.trySaveObject(toSave,file);
    }
}
