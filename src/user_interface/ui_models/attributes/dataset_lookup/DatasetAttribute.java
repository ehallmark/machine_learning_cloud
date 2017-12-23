package user_interface.ui_models.attributes.dataset_lookup;

import elasticsearch.DatasetIndex;
import j2html.tags.Tag;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;

import java.util.Collections;

/**
 * Created by Evan on 12/23/2017.
 */
public class DatasetAttribute extends TermsLookupAttribute {
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

    @Override
    public Tag getFilterTag(String name, String id) {
        return SimilarPatentServer.technologySelectWithCustomClass(name, id, "dataset-multiselect", Collections.emptyList());
    }
}
