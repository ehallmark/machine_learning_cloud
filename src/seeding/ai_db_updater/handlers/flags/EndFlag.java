package seeding.ai_db_updater.handlers.flags;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Created by Evan on 8/6/2017.
 */
public abstract class EndFlag extends Flag {
    protected Map<Flag,String> dataMap = new HashMap<>();
    public final Map<String,Flag> flagMap = new HashMap<>();
    public EndFlag(String localName) {
        super(localName,null,null,null,defaultCompareFunction,null,null);
    }

    public abstract void save();
    public Map<Flag,String> getDataMap() {
        return dataMap;
    }

    public Map<Flag,Object> getTransform() {
        Map<Flag,Object> transform = new HashMap<>(dataMap.size());
        Collection<Flag> flags = new HashSet<>(dataMap.keySet());
        flags.forEach(flag->{
            Object result = flag.apply(dataMap.get(flag));
            if(result!=null) {
                transform.put(flag, result);
            }
        });
        return transform;
    }

    public void resetDataMap() {
        dataMap = new HashMap<>();
    }

    @Override
    public EndFlag getEndFlag() {
        return this;
    }
}