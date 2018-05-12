package seeding.google.elasticsearch.attributes;

import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class CPCVae extends SimilarityAttribute {
    public CPCVae() {
        super(Collections.singleton(Constants.CPC_SIMILARITY));
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
