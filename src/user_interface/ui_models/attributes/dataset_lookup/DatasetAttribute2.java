package user_interface.ui_models.attributes.dataset_lookup;

import elasticsearch.DatasetIndex;
import j2html.tags.Tag;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
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

    @Override
    public String getName() {
        return Constants.DATASET2_NAME;
    }

    @Override
    public AbstractAttribute dup() {
        return new DatasetAttribute2();
    }
}
