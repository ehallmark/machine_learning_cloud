package user_interface.ui_models.attributes.computable_attributes;

import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 7/20/17.
 */
public abstract class ComputableFilingAttribute<T> extends ComputableAttribute<T> {
    protected static final AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
    protected static final Map<String,Map<String,?>> filingMapCache = Collections.synchronizedMap(new HashMap<>());

    protected Map<String,T> map;
    protected final File file;
    public ComputableFilingAttribute(File file, Collection<AbstractFilter.FilterType> filterTypes) {
        super(filterTypes);
        this.file=file;
    }

    @Override
    public T attributesFor(Collection<String> items, int limit, Boolean isApp) {
        String item = items.stream().findAny().get();

        String filing;
        if(isApp==null) filing = assetToFilingMap.getPatentDataMap().getOrDefault(item,assetToFilingMap.getApplicationDataMap().get(item));
        else if(isApp) filing = assetToFilingMap.getApplicationDataMap().get(item);
        else filing = assetToFilingMap.getPatentDataMap().get(item);

        if(filing==null) return null;
        return handleFiling(filing);
    }

    protected T handleFiling(String filing) {

        if(map==null) {
            synchronized (ComputableFilingAttribute.class) {
                map = loadMap();
            }
        }

        return map.get(filing);
    }

    @Override
    public Map<String,T> getPatentDataMap() {
        return null;
    }

    @Override
    public Map<String,T> getApplicationDataMap() {
        return null;
    }

    @Override
    public T handleIncomingData(String item, Map<String, Object> data, Map<String,T> myData, boolean isApplication) {
        return null;
    }

    @Override
    public void save() {
        saveMap(this.map);
    }

    public void saveMap(Map<String,T> map) {
        if(map!=null&&map.size()>0) {
            synchronized (ComputableFilingAttribute.class) {
                this.map = map;
                filingMapCache.put(getFullName(), this.map);
            }
            Database.trySaveObject(this.map, file);
        }
    }

    public  Map<String,T> loadMap() {
        if (map == null) {
            synchronized (ComputableFilingAttribute.class) {
                map = (Map<String, T>) filingMapCache.get(getFullName());
                if (map == null) {
                    map = (Map<String, T>) Database.tryLoadObject(file);
                    if(map==null) {
                        map = Collections.synchronizedMap(new HashMap<>());
                    }
                    filingMapCache.put(getFullName(), map);
                }
            }
        }
        return map;
    }
}
