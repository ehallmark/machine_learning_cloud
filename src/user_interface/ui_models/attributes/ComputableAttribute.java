package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.meta_attributes.MetaComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class ComputableAttribute<T> extends AbstractAttribute<T> {
    protected Map<String,T> patentDataMap;
    protected Map<String,T> applicationDataMap;

    public ComputableAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        super(filterTypes);
    }

    public T attributesFor(Collection<String> portfolio, int limit) {
        return portfolio.stream().map(item->{
            if(Database.isApplication(item)) {
                return getApplicationDataMap().getOrDefault(item,null);
            } else {
                return getPatentDataMap().getOrDefault(item,null);
            }
        }).filter(item->item!=null).findAny().orElse(null);
    }

    public synchronized Map<String,T> getPatentDataMap() {
        if(patentDataMap==null) patentDataMap = (Map<String,T>) Database.tryLoadObject(dataFileFrom(Constants.PATENT_DATA_FOLDER,getName(),getType()));
        return patentDataMap;
    }
    public synchronized Map<String,T> getApplicationDataMap() {
        if(applicationDataMap==null) applicationDataMap = (Map<String,T>) Database.tryLoadObject(dataFileFrom(Constants.APPLICATION_DATA_FOLDER,getName(),getType()));
        return applicationDataMap;
    }

    public void initMaps() {
        getPatentDataMap();
        getApplicationDataMap();
        if(patentDataMap==null) this.patentDataMap= Collections.synchronizedMap(new HashMap<String, T>());
        if(applicationDataMap==null) this.applicationDataMap = Collections.synchronizedMap(new HashMap<String, T>());
    }

    public void handlePatentData(String item, Map<String,Object> data) {
        if(patentDataMap==null) throw new RuntimeException("Must init patent data map");
        T val = handleIncomingData(item,data,false);
        if(val!=null) {
            patentDataMap.put(item, val);
        }
    }

    public Collection<MetaComputableAttribute> getNecessaryMetaAttributes() {
        return Collections.emptyList();
    }
    public T handleIncomingData(String name, Map<String,Object> data, boolean isApplication) {
        return null;
    }

    public String getAssociatedField() {
        throw new UnsupportedOperationException("Must define each associated field separately on the concrete classes.");
    }

    public String getAssociation() {
        return Constants.NAME; // Default
    }

    public void handleApplicationData(String item, Map<String,Object> data) {
        if(applicationDataMap==null) throw new RuntimeException("Must init application data map");
        T val = handleIncomingData(item,data,true);
        if(val!=null) {
            applicationDataMap.put(item,val);
        }
    }

    public void save() {
        if(patentDataMap!=null) Database.saveObject(patentDataMap, dataFileFrom(Constants.PATENT_DATA_FOLDER,getName(),getType()));
        if(applicationDataMap!=null) Database.saveObject(applicationDataMap, dataFileFrom(Constants.APPLICATION_DATA_FOLDER,getName(),getType()));
    }

    public static File dataFileFrom(String folder, String attrName, String attrType) {
        return new File(Constants.DATA_FOLDER+folder+attrName+"_"+attrType+"_map.jobj");
    }
}
