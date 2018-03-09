package user_interface.ui_models.attributes.dataset_lookup;

import elasticsearch.DatasetIndex;
import j2html.tags.Tag;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.tools.AjaxMultiselect;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 12/23/2017.
 */
public class DatasetAttribute extends TermsLookupAttribute implements AjaxMultiselect {

    private List<Pair<String,Set<String>>> currentDatasets;

    @Override
    public List<String> termsFor(String asset) {
        return currentDatasets.stream().map(ds->{
            String label = ds.getFirst();
            if(label!=null&&label.length()>0) {
                if(ds.getSecond()!=null&&ds.getSecond().contains(asset)) {
                    return label;
                }
            }
            return null;
        }).filter(l->l!=null).collect(Collectors.toList());
    }

    @Override
    public String getTermsIndex() {
        return DatasetIndex.INDEX;
    }

    @Override
    public String getTermsType() {
        return DatasetIndex.TYPE;
    }

    @Override
    public String getTermsPath() {
        return DatasetIndex.DATA_FIELD;
    }

    @Override
    public String getTermsName() {
        return Constants.NAME;
    }

    @Override
    public String getName() {
        return Constants.DATASET_NAME;
    }

    public static Pair<String,Set<String>> createDatasetFor(String label) {
        // need to get latest folder name for this dataset and assets
        String[] tmp = label.split("_",2);
        if(tmp.length==2) {
            List<String> assets = DatasetIndex.get(label);
            if(assets == null) assets = Collections.emptyList();
            String user = tmp[1];
            String file = tmp[0];
            String datasetName = SimilarPatentServer.datasetNameFrom(user, file);
            return new Pair<>(datasetName, new HashSet<>(assets));
        }
        return null;
    }

    public Tag getFilterTag(String name, String id) {
        return AbstractIncludeFilter.ajaxMultiSelect(name,ajaxUrl(),id);
    }


    @Override
    public void extractRelevantInformationFromParams(Request params) {
        currentDatasets = Collections.synchronizedList(new ArrayList<Pair<String, Set<String>>>());
        AbstractIncludeFilter filter = new AbstractIncludeFilter(this, AbstractFilter.FilterType.Include,getFieldType(),null);
        filter.extractRelevantInformationFromParams(params);
        if(filter.isActive()) {
            Collection<String> labels = ((AbstractIncludeFilter)filter).getLabels();
            if(labels!=null) {
                labels.forEach(label->{
                    Pair<String,Set<String>> ds = createDatasetFor(label);
                    if(ds!=null) {
                        currentDatasets.add(ds);
                    }
                });
            }
        }
    }

    @Override
    public AbstractAttribute dup() {
        return new DatasetAttribute();
    }

    @Override
    public String ajaxUrl() {
        return Constants.DATASET_NAME_AJAX_URL;
    }
}
