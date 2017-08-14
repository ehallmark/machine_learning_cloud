package user_interface.ui_models.attributes.computable_attributes;

import j2html.tags.Tag;
import lombok.Getter;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.util.*;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class ComputableAttribute<T> extends AbstractAttribute<T> {
    protected Map<String,T> patentDataMap;
    protected Map<String,T> applicationDataMap;
    private static Map<String,Map<String,?>> allPatentDataMaps = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,Map<String,?>> allApplicationDataMaps = Collections.synchronizedMap(new HashMap<>());

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
        if(patentDataMap==null) {
            synchronized (ComputableAttribute.class) {
                if (allPatentDataMaps.containsKey(getName())) {
                    patentDataMap = (Map<String, T>) allPatentDataMaps.get(getName());
                } else {
                    patentDataMap = (Map<String, T>) Database.tryLoadObject(dataFileFrom(Constants.PATENT_DATA_FOLDER, getName(), getType()));
                    if(patentDataMap == null) {
                        patentDataMap = Collections.synchronizedMap(new HashMap<>());
                    }
                    allPatentDataMaps.put(getName(), patentDataMap);
                }
            }
        }
        return patentDataMap;
    }
    public synchronized Map<String,T> getApplicationDataMap() {
        if(applicationDataMap==null) {
            synchronized (ComputableAttribute.class) {
                if(allApplicationDataMaps.containsKey(getName())) {
                    applicationDataMap = (Map<String,T>) allApplicationDataMaps.get(getName());
                } else {
                    applicationDataMap = (Map<String, T>) Database.tryLoadObject(dataFileFrom(Constants.APPLICATION_DATA_FOLDER, getName(), getType()));
                    if(applicationDataMap == null) {
                        applicationDataMap = Collections.synchronizedMap(new HashMap<>());
                    }
                    allApplicationDataMaps.put(getName(),applicationDataMap);
                }
            }
        }
        return applicationDataMap;
    }

    public void initMaps() {
        getPatentDataMap();
        getApplicationDataMap();
        if(patentDataMap==null) this.patentDataMap= Collections.synchronizedMap(new HashMap<String, T>());
        if(applicationDataMap==null) this.applicationDataMap = Collections.synchronizedMap(new HashMap<String, T>());
    }

    public T handleIncomingData(String item, Map<String, Object> data, Map<String,T> myData, boolean isAppplication) {
        if(item==null)return null;
        return attributesFor(Arrays.asList(item),1);
    }

    public void handlePatentData(String item, Map<String,Object> allData) {
        T val = handleIncomingData(item,allData,patentDataMap,false);
        if(val!=null) {
            allData.put(getName(), val);
        }
    }

    public void handleApplicationData(String item, Map<String,Object> allData) {
        T val = handleIncomingData(item,allData,applicationDataMap,true);
        if(val!=null) {
            allData.put(getName(), val);
        }
    }

    public synchronized void save() {
        //if(patentDataMap!=null && patentDataMap.size()>0) Database.trySaveObject(patentDataMap, dataFileFrom(Constants.PATENT_DATA_FOLDER,getName(),getType()));
        //if(applicationDataMap!=null && applicationDataMap.size()>0) Database.trySaveObject(applicationDataMap, dataFileFrom(Constants.APPLICATION_DATA_FOLDER,getName(),getType()));
    }

    public static File dataFileFrom(String folder, String attrName, String attrType) {
        return new File(Constants.DATA_FOLDER+folder+attrName+"_"+attrType+"_map.jobj");
    }
}
