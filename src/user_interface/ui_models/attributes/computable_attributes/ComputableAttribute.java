package user_interface.ui_models.attributes.computable_attributes;

import j2html.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class ComputableAttribute<T> extends AbstractAttribute {
    protected Map<String,T> patentDataMap;
    protected Map<String,T> applicationDataMap;
    protected boolean modifiedPatents = false;
    protected boolean modifiedApps = false;
   // protected LocalDate saveDate;

    private static Map<String,Map<String,?>> allPatentDataMaps = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,Map<String,?>> allApplicationDataMaps = Collections.synchronizedMap(new HashMap<>());

    public ComputableAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        super(filterTypes);
    }

    public void setPatentDataMap(Map<String,T> patentDataMap) {
        this.patentDataMap=patentDataMap;
        this.modifiedPatents=true;
    }

    public void setApplicationDataMap(Map<String,T> applicationDataMap) {
        this.applicationDataMap=applicationDataMap;
        this.modifiedApps=true;
    }


    public T attributesFor(Collection<String> portfolio, int limit) {
        if(applicationDataMap==null||patentDataMap==null) initMaps();
        String item = portfolio.stream().filter(i->i!=null).findAny().orElse(null);
        if(item==null) return null;
        boolean probablyApplication = Database.isApplication(item);
        if(probablyApplication) {
            return applicationDataMap.getOrDefault(item, patentDataMap.get(item));
        } else {
            return patentDataMap.getOrDefault(item, applicationDataMap.get(item));
        }
    }

    public synchronized Map<String,T> getPatentDataMap() {
        if(patentDataMap==null) {
            synchronized (ComputableAttribute.class) {
                if (allPatentDataMaps.containsKey(getName())) {
                    patentDataMap = (Map<String, T>) allPatentDataMaps.get(getName());
                } else {
                    File dataFile = dataFileFrom(Constants.PATENT_DATA_FOLDER, getName(), getType());
                    patentDataMap = (Map<String, T>) Database.tryLoadObject(dataFile);
                    if(patentDataMap == null) {
                        // check for backup file
                        patentDataMap = (Map<String,T>) Database.tryLoadObject(new File(dataFile.getAbsolutePath()+"-backup"));
                        if(patentDataMap==null) {
                            patentDataMap = Collections.synchronizedMap(new HashMap<>());
                        } else {
                            System.out.println("WARNING:: LOADED BACKUP FILE FOR : "+dataFile.getName());
                        }
                    }
                    allPatentDataMaps.put(getName(), patentDataMap);
                    modifiedPatents=false;
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
                    File dataFile = dataFileFrom(Constants.APPLICATION_DATA_FOLDER, getName(), getType());
                    applicationDataMap = (Map<String, T>) Database.tryLoadObject(dataFile);
                    if(applicationDataMap == null) {
                        // check for backup file
                        applicationDataMap = (Map<String,T>) Database.tryLoadObject(new File(dataFile.getAbsolutePath()+"-backup"));
                        if(applicationDataMap==null) {
                            applicationDataMap = Collections.synchronizedMap(new HashMap<>());
                        } else {
                            System.out.println("WARNING:: LOADED BACKUP FILE FOR : "+dataFile.getName());
                        }
                    }
                    allApplicationDataMaps.put(getName(),applicationDataMap);
                    modifiedApps=false;
                }
            }
        }
        return applicationDataMap;
    }

    public void initMaps() {
        getPatentDataMap();
        getApplicationDataMap();
    }

    public T handleIncomingData(String item, Map<String, Object> data, Map<String,T> myData, boolean isApplication) {
        if(item==null)return null;
        return attributesFor(Arrays.asList(item),1);
    }

    public void handlePatentData(String item, Map<String,Object> allData) {
        T val = handleIncomingData(item,allData,patentDataMap,false);
        if(val!=null) {
            if(parent!=null) {
                Object _allData = allData.get(parent.getName());
                if(_allData instanceof List) {
                    if(((List)_allData).size()>0) {
                        allData = (Map<String, Object>) (((List) _allData).get(0));
                    } else {
                        allData=null;
                    }
                } else {
                    allData = (Map<String, Object>) _allData;
                }
            }
            if(allData!=null) {
                modifiedPatents=true;
                allData.put(getName(), val);
            }
        }
    }

    public void handleApplicationData(String item, Map<String,Object> allData) {
        T val = handleIncomingData(item,allData,applicationDataMap,true);
        if(val!=null) {
            if(parent!=null) {
                allData = (Map<String,Object>)allData.get(parent.getName());
            }
            if(allData!=null) {
                modifiedApps=true;
                allData.put(getName(), val);
            }
        }
    }

    public void save() {
        if(patentDataMap!=null && modifiedPatents) synchronized (patentDataMap) { safeSaveFile(patentDataMap, dataFileFrom(Constants.PATENT_DATA_FOLDER,getName(),getType())); }
        if(applicationDataMap!=null && modifiedApps) synchronized (applicationDataMap) { safeSaveFile(applicationDataMap, dataFileFrom(Constants.APPLICATION_DATA_FOLDER,getName(),getType())); }
        modifiedApps = false;
        modifiedPatents = false;
    }

    protected static void safeSaveFile(Object obj, File file) {
        try {
            File backup;
            if(file.exists()) {
                backup = new File(file.getAbsolutePath()+"-backup");
                if(backup.exists()) backup.delete();
                backup = new File(backup.getAbsolutePath());
                // copy to backup
                FileUtils.copyFile(file, backup);
            }
            // write contents
            Database.trySaveObject(obj, file);

        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("  ... While saving: "+file.getName());
        }
    }

    public static File dataFileFrom(String folder, String attrName, String attrType) {
        return new File(Constants.DATA_FOLDER+folder+attrName+"_"+attrType+"_map.jobj");
    }
}
