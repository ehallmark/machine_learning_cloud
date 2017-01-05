package seeding;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;

/**
 * Created by ehallmark on 10/15/16.
 */
public class FilterExcelFile {
    public static void main(String[] args) throws Exception {
        //List<String> assigneesToRemove = Arrays.asList("International Business Machines","Ibm","Microsoft","Oracle","Sap","Salesforce","Avaya");
        Database.setupGatherConn();
        List<String> assetsToRemove = Database.getGatherSalesforceAssets();
        Database.close();
        System.out.println(String.join(" ",assetsToRemove));
    }
}
