package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
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
        this.patentDataMap= Collections.synchronizedMap(new HashMap<String, T>());
        this.applicationDataMap = Collections.synchronizedMap(new HashMap<String, T>());
    }

    public void addDataToPatentMap(String item, T val) {
        if(patentDataMap==null) throw new RuntimeException("Must init patent data map");
        patentDataMap.put(item,val);
    }

    public void addDataToApplicationMap(String item, T val) {
        if(applicationDataMap==null) throw new RuntimeException("Must init application data map");
        applicationDataMap.put(item,val);
    }

    public void save() {
        if(patentDataMap!=null) Database.saveObject(patentDataMap, dataFileFrom(Constants.PATENT_DATA_FOLDER,getName(),getType()));
        if(applicationDataMap!=null) Database.saveObject(applicationDataMap, dataFileFrom(Constants.APPLICATION_DATA_FOLDER,getName(),getType()));
    }

    public static File dataFileFrom(String folder, String attrName, String attrType) {
        return new File(Constants.DATA_FOLDER+folder+attrName+"_"+attrType+"_map.jobj");
    }
}
