package seeding.ai_db_updater.handlers.flags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Created by Evan on 8/6/2017.
 */
public abstract class EndFlag extends Flag {
    protected Map<Flag,String> dataMap = new HashMap<>();

    public EndFlag(String localName) {
        super(localName,null,null,null,defaultCompareFunction,null);
    }

    public abstract void save();
    public Map<Flag,String> getDataMap() {
        return dataMap;
    }

}