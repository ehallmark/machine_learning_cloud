package seeding.google.elasticsearch.attributes;

import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import user_interface.server.SimilarPatentServer;

import java.util.Arrays;

public class Enc extends SimilarityAttribute {
    @Override
    public String getName() {
        return Attributes.ENC;
    }

    public Enc() {
        super(Arrays.asList(Constants.TEXT_SIMILARITY,Constants.PATENT_SIMILARITY, SimilarPatentServer.DATASETS_TO_SEARCH_IN_FIELD));
    }

    @Override
    public SimilarityAttribute clone() {
        return dup();
    }

    @Override
    public SimilarityAttribute dup() {
        return new Enc();
    }
}