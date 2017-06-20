package classification_models;

import seeding.Database;
import similarity_models.class_vectors.vectorizer.ClassVectorizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 6/18/2017.
 */
public class WIPOHelper {
    public static final File mapFile = new File("data/wipo_patent_map.jobj");
    public static final File definitionFile = new File("data/wipo_definition_map.jobj");
    public static final File mapFileWithAssignees = new File("data/wipo_patent_with_assignee_map.jobj");

    private static Map<String,String> DEF_MAP;
    private static Map<String,String> WIPO_MAP;
    private static Map<String,String> WIPO_MAP_WITH_ASSIGNEES;

    public synchronized static List<String> getOrderedClassifications() {
        if(DEF_MAP==null) getDefinitionMap();
        return DEF_MAP.values().stream().distinct().sorted().collect(Collectors.toList());
    }

    public static synchronized Map<String,String> getDefinitionMap() {
        if(!definitionFile.exists()) {
            try {
                main(null);
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }
        if(DEF_MAP==null) DEF_MAP = (Map<String,String>) Database.tryLoadObject(definitionFile);
        return DEF_MAP;
    }

    public static synchronized Map<String,String> getWIPOMapWithAssignees() {
        if(!mapFileWithAssignees.exists()) {
            try {
                main(null);
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }
        if(WIPO_MAP_WITH_ASSIGNEES == null) WIPO_MAP_WITH_ASSIGNEES = (Map<String,String>) Database.tryLoadObject(mapFileWithAssignees);
        return WIPO_MAP_WITH_ASSIGNEES;
    }

    public static synchronized Map<String,String> getWIPOMap() {
        if(!mapFile.exists()) {
            try {
                main(null);
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }
        if(WIPO_MAP == null) WIPO_MAP = (Map<String,String>) Database.tryLoadObject(mapFile);
        return WIPO_MAP;
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> definitionMap = Collections.synchronizedMap(new HashMap<>());
        {
            BufferedReader reader = new BufferedReader(new FileReader(new File("data/wipo_field.tsv")));
            reader.lines().skip(1).parallel().forEach(line -> {
                String[] fields = line.split("\t");
                String wipo = fields[0];
                String title = fields[2];
                if(!wipo.startsWith("D")) {
                    System.out.println(wipo + ": " + title);
                    definitionMap.put(wipo, title);
                }
            });
            Database.trySaveObject(definitionMap, definitionFile);
        }

        {
            File file = new File("data/wipo.tsv");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            Map<String, String> patentToWIPOMap = Collections.synchronizedMap(new HashMap<>());
            reader.lines().skip(1).parallel().forEach(line -> {
                String[] fields = line.split("\t");
                String patent = fields[0];
                String wipo = fields[1];
                if(definitionMap.containsKey(wipo)) {
                    try {
                        if(Integer.valueOf(patent) > 6800000) {
                            //System.out.println(patent + ": " + wipo + " -> " + definitionMap.get(wipo));
                            patentToWIPOMap.put(patent, wipo);
                        }
                    } catch(Exception e) {

                    }
                }
            });

            Database.trySaveObject(patentToWIPOMap, mapFile);
            reader.close();

            System.out.println("Adding assignees...");
            // add assignees
            Database.getAssignees().parallelStream().forEach(assignee->{
                Collection<String> patents = Database.selectPatentNumbersFromExactAssignee(assignee);
                if(patents.isEmpty()) return;
                Map.Entry<String,Long> entry = patents.stream().map(patent->patentToWIPOMap.get(patent)).filter(wipo->wipo!=null)
                        .collect(Collectors.groupingBy(wipo->wipo,Collectors.counting())).entrySet()
                        .stream().max((e1,e2)->e1.getValue().compareTo(e2.getValue())).orElse(null);
                if(entry==null) return;
                patentToWIPOMap.put(assignee,entry.getKey());
            });

            Database.trySaveObject(patentToWIPOMap, mapFileWithAssignees);
        }
    }
}
