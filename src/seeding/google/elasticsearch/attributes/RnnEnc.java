package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;

public class RnnEnc extends SimilarityAttribute {
    @Override
    public String getName() {
        return Attributes.RNN_ENC;
    }

    @Override
    public AbstractScriptAttribute dup() {
        return new RnnEnc();
    }
}
