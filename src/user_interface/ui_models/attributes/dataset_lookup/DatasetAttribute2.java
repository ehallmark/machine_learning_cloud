package user_interface.ui_models.attributes.dataset_lookup;

import elasticsearch.DatasetIndex;
import j2html.tags.Tag;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;

import java.util.*;
import java.util.stream.Collectors;

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
        return new DatasetAttribute2(getTermsName());
    }

    public static DatasetAttribute getOldDatasetAttribute() {
        return new DatasetAttribute2(Constants.NAME);
    }

    public static DatasetAttribute getDatasetAttribute() {
        return new DatasetAttribute2(Attributes.PUBLICATION_NUMBER_FULL);
    }
}
