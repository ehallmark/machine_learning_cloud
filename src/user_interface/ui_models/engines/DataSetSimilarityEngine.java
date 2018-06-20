package user_interface.ui_models.engines;

import j2html.tags.Tag;
import org.nd4j.linalg.primitives.Pair;
import seeding.google.elasticsearch.Attributes;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 2/28/17.
 */
public class DataSetSimilarityEngine extends AbstractSimilarityEngine {
    private DatasetAttribute datasetAttribute = DatasetAttribute.getDatasetAttribute();

    public DataSetSimilarityEngine(String tableName) {
        super(tableName, Attributes.PUBLICATION_NUMBER_FULL, Attributes.ENC, false);
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        Collection<String> labels = SimilarPatentServer.extractArray(req, getId());
        if(labels==null) return Collections.emptyList();
        Collection<String> patents = labels.stream().flatMap(label->{
            Pair<String,Set<String>> p = DatasetAttribute.createDatasetFor(label);
            if(p==null) return Stream.empty();
            return p.getSecond().stream();
        }).collect(Collectors.toList());
        patents = patents.stream().limit(1000).distinct().collect(Collectors.toList());
        System.out.println("Found "+patents.size()+" patents...");
        return patents;
    }


    @Override
    public String getName() {
        return SimilarPatentServer.DATASETS_TO_SEARCH_IN_FIELD;
    }

    @Override
    public String getId() {
        return SimilarPatentServer.DATASETS_TO_SEARCH_IN_FIELD;
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div().with(
               datasetAttribute.getFilterTag(getName(),getId())
        );
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }

    @Override
    public AbstractSimilarityEngine dup() {
        return new DataSetSimilarityEngine(tableName);
    }
}
