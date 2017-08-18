package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.hidden_attributes.HiddenAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;

/**
 * Created by Evan on 8/11/2017.
 */
public class LapsedAttribute extends ComputableAttribute<Set<String>> {
    public LapsedAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolTrue,AbstractFilter.FilterType.BoolFalse));
    }

    @Override
    public String getName() {
        return Constants.LAPSED;
    }

    @Override
    public String getType() {
        return "boolean";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }

    @Override
    public synchronized Map<String,Set<String>> getPatentDataMap() {
        patentDataMap = super.getPatentDataMap();
        if(patentDataMap!=null&&patentDataMap.isEmpty()) {
            patentDataMap.put(Constants.LAPSED,new HashSet<>());
        }
        return patentDataMap;
    }

    @Override
    public synchronized Map<String,Set<String>> getApplicationDataMap() {
        applicationDataMap = super.getApplicationDataMap();
        if(applicationDataMap!=null&&applicationDataMap.isEmpty()) {
            applicationDataMap.put(Constants.LAPSED,new HashSet<>());
        }
        return applicationDataMap;
    }
}
