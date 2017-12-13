package test;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ehallmark on 12/13/17.
 */
public class ExcelTest {
    public static void main(String[] args) throws Exception {
        File excelFile = new File("ipel.csv");
        File outputFile = new File("ipel-output.csv");

        BufferedReader reader = new BufferedReader(new FileReader(excelFile));
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

        Map<String,Set<String>> map = new HashMap<>();
        AtomicReference<String> currentPatent = new AtomicReference<>(null);
        reader.lines().skip(1).forEach(line->{
            System.out.println("line: "+line);
            String[] cells = line.split(",",2);
            String asset = cells[0];
            String name = asset.replace("\"","");
            if(cells.length>1) {
                if(asset.replaceAll("[^0-9]","").length()>5) {
                    name = cells[1].replace("\"", "");
                    currentPatent.set(asset);
                }
            }
            map.putIfAbsent(name,new HashSet<>());
            map.get(name).add(currentPatent.get());
        });

        map.entrySet().forEach(e->{
            try {
                writer.write("\"" + e.getKey() + "\","+e.getValue().size()+",\""+String.join("; ",e.getValue())+"\"\n");
            } catch(Exception ex) {

            }
        });

        reader.close();
        writer.flush();
        writer.close();
    }
}
