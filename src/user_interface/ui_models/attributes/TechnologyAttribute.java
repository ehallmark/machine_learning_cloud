package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import models.classification_models.ClassificationAttr;
import user_interface.ui_models.attributes.meta_attributes.MetaComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/17/2017.
 */
public class TechnologyAttribute extends ComputableAttribute<String> {
    public TechnologyAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
    }

    @Override
    public String getName() {
        return Constants.TECHNOLOGY;
    }

    @Override
    public String getType() {
        return "keyword";
    }

    @Override
    public Collection<String> getAllValues() {
        return SimilarPatentServer.getTechTagger().getClassifications().stream().sorted().collect(Collectors.toList());
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }

    @Override
    public String handleIncomingData(String item, Map<String, Object> data, boolean isAppplication) {
        if(item==null)return null;
        return Database.getItemToTechnologyMap().get(item);
    }
}
