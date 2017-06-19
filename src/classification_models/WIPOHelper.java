package classification_models;

import seeding.Database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 6/18/2017.
 */
public class WIPOHelper {
    public static final File mapFile = new File("data/wipo_patent_map.jobj");
    public static final File definitionFile = new File("data/wipo_definition_map.jobj");

    public static Map<String,String> getDefinitionMap() {
        return (Map<String,String>) Database.tryLoadObject(definitionFile);
    }

    public static Map<String,String> getWIPOMap() {
        return (Map<String,String>) Database.tryLoadObject(mapFile);
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> definitionMap = Collections.synchronizedMap(new HashMap<>());
        {
            BufferedReader reader = new BufferedReader(new FileReader(new File("wipo_field.tsv")));
            reader.lines().parallel().forEach(line -> {
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
            File file = new File("wipo.tsv");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            Map<String, String> patentToWIPOMap = Collections.synchronizedMap(new HashMap<>());
            reader.lines().parallel().forEach(line -> {
                String[] fields = line.split("\t");
                String patent = fields[0];
                String wipo = fields[1];
                if(definitionMap.containsKey(wipo)) {
                    try {
                        if(Integer.valueOf(patent) > 6800000) {
                            System.out.println(patent + ": " + wipo + " -> " + definitionMap.get(wipo));
                            patentToWIPOMap.put(patent, wipo);
                        }
                    } catch(Exception e) {

                    }
                }
            });
            Database.trySaveObject(patentToWIPOMap, mapFile);
            reader.close();
        }
    }
}
