package client_projects;

import seeding.Database;
import user_interface.ui_models.attributes.RemainingLifeAttribute;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;

/**
 * Created by ehallmark on 7/10/17.
 */
public class SIE {
    public static void main(String[] args) throws Exception {
        Database.initializeDatabase();
        File file = new File("data/sie_patents.csv");
        BufferedReader reader = new BufferedReader(new FileReader(file));

        RemainingLifeAttribute attr = new RemainingLifeAttribute();
        reader.lines().forEach(line-> {
            System.out.println(line+","+attr.attributesFor(Arrays.asList(line),1));
        });
    }


}
