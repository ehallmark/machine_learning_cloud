package seeding.google.elasticsearch.attributes;

import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;

import java.util.Arrays;
import java.util.Collection;

public class RnnEnc extends SimilarityAttribute {
    @Override
    public String getName() {
        return Attributes.RNN_ENC;
    }

    public RnnEnc() {
        super(Arrays.asList(Constants.TEXT_SIMILARITY, Constants.ASSIGNEE_SIMILARITY,Constants.PATENT_SIMILARITY, SimilarPatentServer.DATASETS_TO_SEARCH_IN_FIELD));
    }

    @Override
    public SimilarityAttribute clone() {
        return null;
    }

    @Override
    public AbstractScriptAttribute dup() {
        return new RnnEnc();
    }
}
