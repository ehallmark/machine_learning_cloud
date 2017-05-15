package seeding.patent_view_api;

import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by ehallmark on 5/1/17.
 */
public class PrintGatherTechnologies {
    public static void main(String[] args) {
        Set<String> gatherTechnologies = ((Map<String, Collection<String>>)Database.getGatherTechMap()).keySet();
        System.out.println(String.join("\n",gatherTechnologies));
    }
}
