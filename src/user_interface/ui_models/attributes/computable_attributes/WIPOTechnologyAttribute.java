package user_interface.ui_models.attributes.computable_attributes;

import models.classification_models.WIPOHelper;
import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 6/18/2017.
 */
public class WIPOTechnologyAttribute extends ComputableFilingAttribute<String> {
    private static final File wipoTechnologiesFile = new File(Constants.DATA_FOLDER+"patentWIPOTechnologiesByFilingMap.jobj");
    public WIPOTechnologyAttribute() {
        super(wipoTechnologiesFile,Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude, AbstractFilter.FilterType.AdvancedKeyword, AbstractFilter.FilterType.Regexp, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
    }

    @Override
    public String getName() {
        return Constants.WIPO_TECHNOLOGY;
    }

    @Override
    public String getType() {
        return "text";
    }

    @Override
    public Collection<String> getAllValues() {
        return WIPOHelper.getOrderedClassifications();
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }

    @Override
    public Map<String,Object> getNestedFields() {
        Map<String,Object> fields = new HashMap<>();
        Map<String,String> rawType = new HashMap<>();
        rawType.put("type","keyword");
        fields.put("raw",rawType);
        return fields;
    }
}
