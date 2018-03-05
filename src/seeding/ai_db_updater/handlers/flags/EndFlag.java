package seeding.ai_db_updater.handlers.flags;

import lombok.Getter;
import org.nd4j.linalg.primitives.Pair;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Created by Evan on 8/6/2017.
 */
public abstract class EndFlag extends Flag {
    protected Map<Flag,String> dataMap = new HashMap<>();
    public final Map<String,Flag> flagMap = new HashMap<>();
    public List<Map<String,Object>> dataQueue = new ArrayList<>();
    private List<Pair<String,Object>> additionalData = new ArrayList<>();
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

    public void addData(String name, Object data) {
        additionalData.add(new Pair<>(name,data));
    }

    public Map<String,Object> getTransform(Collection<String> flagsToIngest) {
        Map<String,Object> transform = new HashMap<>(dataMap.size());
        // add additional data
        additionalData.forEach(pair->{
            String name = pair.getFirst();
            Object data = pair.getSecond();
            //System.out.println("Additional data "+name+": "+data);
            transform.put(name,data);
        });
        additionalData.clear();
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
                if(result instanceof LocalDate) {
                    result = ((LocalDate) result).format(DateTimeFormatter.ISO_DATE);
                }
                transform.put(flag.dbName, result);
            }
        });
        return transform;
    }

    public void resetDataMap() {
        int size = 10;
        if(dataMap!=null) {
            size=dataMap.size();
            dataMap.clear();
        }
        dataMap = new HashMap<>(size);
        additionalData.clear();
    }

    @Override
    public EndFlag getEndFlag() {
        return this;
    }

}