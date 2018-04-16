package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;

public class CPCVae extends SimilarityAttribute {
    @Override
    public String getName() {
        return Attributes.CPC_VAE;
    }

    @Override
    public AbstractScriptAttribute dup() {
        return new CPCVae();
    }
}
