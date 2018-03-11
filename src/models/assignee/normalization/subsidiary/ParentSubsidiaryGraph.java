package models.assignee.normalization.subsidiary;

import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.stream.IntStream;

public class ParentSubsidiaryGraph {
    public static final File rawDataFile = new File(Constants.DATA_FOLDER+"company_subsidiary_data.jobj");
    public static final File parentToChildrenMapFile = new File(Constants.DATA_FOLDER+"company_parent_to_subsidiary_map.jobj");
    public static final File childToParentMapFile = new File(Constants.DATA_FOLDER+"company_subsidiary_to_parent_map.jobj");
    private static final String CATEGORIES = "categories";
    private static final String SUBSIDIARIES = "subsidiaries";
    private static final String PARENT = "parent";
    private static final String TITLE = "title";

    private static String parseParent(Object obj) {
        if(obj==null) return null;
        String str = obj.toString().trim();
        while(str.startsWith("{{")||str.startsWith("[[")) {
            str = str.substring(2,str.length()).trim();
        }
        int idx1 = str.indexOf("|");
        int idx2 = str.indexOf("]]");
        int idx3 = str.indexOf("}}");
        int idx = IntStream.of(idx1,idx2,idx3).filter(i->i>=0).min().orElse(-1);
        if(idx>=0) {
            return str.substring(0,idx).trim();
        }
        if(str.isEmpty())return null;
        return str;
    }

    static List<String> parseSubsidiaries(Object obj) {
        if(obj==null) return null;
        String str = obj.toString().trim();
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        if(str.startsWith("''")&&str.endsWith("''")&&!str.contains("{{")&&!str.contains("[[")) {
            str = str.replace("'","").trim();
            if(str.length()>0) {
                results.add(str);
                return results;
            }
        }
        if((str.startsWith("[[")||str.contains("  ")||str.contains("; "))&&!str.contains("|")&&!str.contains("{{")) {
            String[] parts;
            if(str.contains("; ")) {
                parts = str.split("; ");
            } else if(!str.contains("  ")) {
                parts = str.split("]]");
            } else {
                parts = str.split("( {2,})");
            }
            for(String part : parts) {
                if(part.startsWith(",")) part = part.substring(1).trim();
                if(part.endsWith(",")) part = part.substring(0,part.length()-1).trim();
                part = part.replace("[[", "").replace("]]", "").trim();
                int p = part.indexOf("(");
                int q = part.indexOf(")");
                if(p>=0 && q>p) {
                    part = (p>0?part.substring(0,p):"")+(q<part.length()-1?part.substring(q+1):"");
                    part = part.trim();
                }
                if(part.length()>0) {
                    results.add(part);
                }
            }
        } else
        if(results.isEmpty())return null;
        return results;
    }

    public static void main(String[] args) {
        Map<String,Map<String,Object>> data = (Map<String,Map<String,Object>>) Database.tryLoadObject(rawDataFile);
        Map<String,String> childToParentMap = Collections.synchronizedMap(new HashMap<>());
        data.entrySet().parallelStream().forEach(e->{
            String title = e.getKey().toUpperCase();
            if(title.contains("WIKIPEDIA:")||title.startsWith("DRAFT:")) return;
            Object parent = e.getValue().get(PARENT);
            Object subsidiary = e.getValue().get(SUBSIDIARIES);
            String parentStr = parseParent(parent);
            if(parentStr!=null) {
                parentStr = parentStr.toUpperCase();
                //System.out.println("Parent of "+title+": "+parentStr);
                childToParentMap.put(title,parentStr);
            }
            List<String> subsidiaryList = parseSubsidiaries(subsidiary);
            if(subsidiaryList!=null) {
                //System.out.println("Subsidiaries of "+title+": "+subsidiaryList);
                subsidiaryList.forEach(child->{
                    childToParentMap.putIfAbsent(child.toUpperCase(),title);
                });
            } else if(subsidiary!=null&&subsidiary.toString().trim().length()>0){
               // System.out.println("Raw of "+title+": "+subsidiary);
            }
        });
        Map<String,Set<String>> parentToChildrenMap = Collections.synchronizedMap(new HashMap<>());
        childToParentMap.entrySet().forEach(e->{
            parentToChildrenMap.putIfAbsent(e.getValue(),Collections.synchronizedSet(new HashSet<>()));
            parentToChildrenMap.get(e.getValue()).add(e.getKey());
        });
        System.out.println("Child to parent map size: "+childToParentMap.size());
        System.out.println("Parent to child map size: "+parentToChildrenMap.size());

        Database.trySaveObject(parentToChildrenMap,parentToChildrenMapFile);
        Database.trySaveObject(childToParentMap,childToParentMapFile);
    }
}
