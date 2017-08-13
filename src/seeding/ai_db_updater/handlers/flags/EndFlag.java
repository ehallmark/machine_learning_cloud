package seeding.ai_db_updater.handlers.flags;

import lombok.Getter;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Created by Evan on 8/6/2017.
 */
public abstract class EndFlag extends Flag {
    protected Map<Flag,String> dataMap = new HashMap<>();
    public final Map<String,Flag> flagMap = new HashMap<>();
    public List<Map<String,Object>> dataQueue = new ArrayList<>();
    @Getter
    public boolean isArray = false;
    public EndFlag(String localName) {
        super(localName,null,null,null,defaultCompareFunction,null,null);
    }

    public EndFlag(Collection<String> localNames) {
        super(null,null,null,null,multiCompareFunction(localNames),null,null);
    }

    public abstract void save();

    public Map<Flag,String> getDataMap() {
        return dataMap;
    }

    public Map<String,Object> getTransform(Collection<String> flagsToIngest) {
        Map<String,Object> transform = new HashMap<>(dataMap.size());
        Collection<Flag> flags = new HashSet<>(dataMap.keySet());
        flags.forEach(flag->{
            if(flagsToIngest!=null && !flagsToIngest.contains(flag.dbName)) {
               if(dbName==null || !flagsToIngest.contains(dbName+"."+flag.dbName)) {
                   return;
               }
            }
            String data = dataMap.get(flag);
            Object result;
            if(flag.isForeign()) {
                result = data;
            } else {
                result = flag.apply(data);
            }
            if (result != null) {
                transform.put(flag.dbName, result);
            }
        });
        return transform;
    }

    public void resetDataMap() {
        int size = 10;
        if(dataMap!=null) {
            dataMap.size();
            dataMap.clear();
        }
        dataMap = new HashMap<>(size);
    }

    @Override
    public EndFlag getEndFlag() {
        return this;
    }

}