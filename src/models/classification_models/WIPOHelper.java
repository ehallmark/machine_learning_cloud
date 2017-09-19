package models.classification_models;

import seeding.Database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 6/18/2017.
 */
public class WIPOHelper {
    public static final String DESIGN_TECHNOLOGY = "Design";
    public static final String PLANT_TECHNOLOGY = "Plants";

    public static final File definitionFile = new File("data/wipo_definition_map.jobj");

    private static Map<String, String> DEF_MAP;

    public synchronized static List<String> getOrderedClassifications() {
        if (DEF_MAP == null) getDefinitionMap();
        return DEF_MAP.values().stream().distinct().sorted().collect(Collectors.toList());
    }

    public static synchronized Map<String, String> getDefinitionMap() {
        if (DEF_MAP == null) DEF_MAP = (Map<String, String>) Database.tryLoadObject(definitionFile);
        return DEF_MAP;
    }

}
