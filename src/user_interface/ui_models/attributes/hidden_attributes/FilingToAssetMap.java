package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.*;

/**
 * Created by Evan on 8/11/2017.
 */
public class FilingToAssetMap extends HiddenAttribute<Collection<String>> {
    private static Map<String,Collection<String>> allDataMap;
    @Override
    public Collection<String> handleIncomingData(String name, Map<String,Object> allData, Map<String, Collection<String>> myData, boolean isApp) {
        Object filing = allData.get(Constants.FILING_NAME);
        if(filing==null) return null;
        if(name!=null) {
            Collection<String> previous = myData.get(filing.toString());
            if(previous==null) {
                previous = Collections.synchronizedSet(new HashSet<>());
                myData.put(filing.toString(), previous);
            }
            previous.add(name);
        }
        return null;
    }

    @Override
    public String getName() {
        return Constants.FILING_NAME+"_to_"+Constants.NAME+"_set";
    }

    public Map<String,Collection<String>> getAllDataMap() {
        if(allDataMap==null) {
            allDataMap = new HashMap<>(getPatentDataMap());
            getApplicationDataMap().entrySet().parallelStream().forEach(e->{
                if(allDataMap.containsKey(e.getKey())) {
                    allDataMap.merge(e.getKey(),e.getValue(), (s1,s2) ->{
                        Collection<String> newSet = Collections.synchronizedSet(new HashSet<>());
                        newSet.addAll(s1);
                        newSet.addAll(s2);
                        return newSet;
                    });
                } else {
                    allDataMap.put(e.getKey(),e.getValue());
                }
            });
        }
        return allDataMap;
    }
}
