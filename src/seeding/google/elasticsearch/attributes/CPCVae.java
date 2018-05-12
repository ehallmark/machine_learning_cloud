package seeding.google.elasticsearch.attributes;

import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class CPCVae extends SimilarityAttribute {
    public CPCVae() {
        super(Arrays.asList(Constants.CPC_SIMILARITY, Constants.ASSIGNEE_SIMILARITY, Constants.PATENT_SIMILARITY, SimilarPatentServer.DATASETS_TO_SEARCH_IN_FIELD));
    }

    @Override
    public String getName() {
        return Attributes.CPC_VAE;
    }

    @Override
    public SimilarityAttribute dup() {
        return new CPCVae();
    }

    @Override
    public SimilarityAttribute clone() {
        return dup();
    }
}
