package seeding.ai_db_updater;

import models.similarity_models.Vectorizer;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import models.similarity_models.signatures.CPCSimilarityVectorizer;
import org.nd4j.linalg.api.ndarray.INDArray;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.*;
import user_interface.ui_models.attributes.hidden_attributes.HiddenAttribute;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 7/23/2017.
 */
public class UpdateExtraneousComputableAttributeData {
    public static void main(String[] args) {
        SimilarPatentServer.initialize(true,false);
        Vectorizer vectorizer = new CPCSimilarityVectorizer(false);
        SimilarPatentServer.loadAndIngestAllItemsWithAttributes(SimilarPatentServer.getAllComputableAttributes().stream().filter(a->!(a instanceof HiddenAttribute)).collect(Collectors.toList()),vectorizer);
    }

}
