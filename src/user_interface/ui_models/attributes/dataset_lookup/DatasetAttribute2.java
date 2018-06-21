package user_interface.ui_models.attributes.dataset_lookup;

import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.AbstractAttribute;

/**
 * Created by Evan on 12/23/2017.
 */
public class DatasetAttribute2 extends DatasetAttribute {

    public DatasetAttribute2(String termsName) {
        super(termsName);
    }

    @Override
    public String getName() {
        return Constants.DATASET2_NAME;
    }

    @Override
    public AbstractAttribute dup() {
        return new DatasetAttribute2(termsName);
    }


    public static DatasetAttribute2 getDatasetAttribute() {
        return new DatasetAttribute2(Attributes.PUBLICATION_NUMBER_FULL);
    }
}
