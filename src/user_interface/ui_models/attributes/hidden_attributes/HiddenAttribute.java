package user_interface.ui_models.attributes.hidden_attributes;

import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collections;
import java.util.Map;

/**
 * Created by Evan on 8/11/2017.
 */
public abstract class HiddenAttribute<T> extends ComputableAttribute<T> {
    public HiddenAttribute() {
        super(Collections.emptyList());
    }

    @Override
    public String getType() {
        return "hidden";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        throw new UnsupportedOperationException("Hidden attribute not shown, so there should be no field type");
    }

    public abstract T handleIncomingData(String name, Map<String,Object> allData, Map<String, T> myData, boolean isApplication);

    @Override
    public void handlePatentData(String item, Map<String,Object> allData) {
        if(patentDataMap==null) throw new RuntimeException("Must init patent data map");
        T val = handleIncomingData(item,allData,patentDataMap,false);
        if(val!=null) {
            patentDataMap.put(item, val);
        }
    }

    @Override
    public void handleApplicationData(String item, Map<String,Object> allData) {
        if(applicationDataMap==null) throw new RuntimeException("Must init application data map");
        T val = handleIncomingData(item,allData,applicationDataMap,true);
        if(val!=null) {
            applicationDataMap.put(item,val);
        }
    }
}
