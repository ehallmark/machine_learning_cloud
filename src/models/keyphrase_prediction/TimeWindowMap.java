package models.keyphrase_prediction;

import org.deeplearning4j.berkeley.Pair;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 9/11/2017.
 */
public class TimeWindowMap<K,V> implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<K,Pair<Integer,V>> dataMap = Collections.synchronizedMap(new HashMap<>());
    private Map<Integer,Collection<K>> keysUpdatedMap = Collections.synchronizedMap(new HashMap<>());

    public synchronized void putAtYear(K key, V val, int year) {
        if(dataMap.containsKey(key)) {
            // remove old key if present
            keysUpdatedMap.get(dataMap.get(key).getFirst()).remove(key);
        }
        dataMap.put(key, new Pair<>(year, val));
        keysUpdatedMap.putIfAbsent(year, Collections.synchronizedCollection(new HashSet<>()));
        keysUpdatedMap.get(year).add(key);
    }


    public Collection<K> keysBetweenYears(int t0, int t1) {
        if(t0>t1) throw new RuntimeException("T1 must be greater than T0");
        return keysUpdatedMap.entrySet().parallelStream().filter(e->e.getKey()>=t0&&e.getKey()<=t1).flatMap(e->e.getValue().stream()).distinct().collect(Collectors.toList());
    }

    // Return value for key if present between t0 and t1
    public V getBetweenYears(K key, int t0, int t1) {
        Pair<Integer,V> pair = dataMap.get(key);
        if (pair != null && pair.getFirst() >= t0 && pair.getFirst() <= t1) {
            return pair.getSecond();
        }
        return null;
    }
}
