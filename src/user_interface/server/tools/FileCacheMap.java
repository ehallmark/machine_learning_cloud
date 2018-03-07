package user_interface.server.tools;

import java.util.ArrayDeque;
import java.util.HashMap;

/**
 * Created by ehallmark on 12/15/17.
 */
public class FileCacheMap extends HashMap<String,Object> {
    private final int largeFileLimit = 50;
    private final ArrayDeque<String> largeFileQueue;

    public FileCacheMap() {
        super();
        largeFileQueue = new ArrayDeque<>(largeFileLimit);
    }

    public void putWithLimit(String key, Object value) {
        if(largeFileQueue.size()>=largeFileLimit) {
            this.remove(largeFileQueue.removeLast());
        }
        largeFileQueue.addFirst(key);
        this.put(key,value);
    }
}
