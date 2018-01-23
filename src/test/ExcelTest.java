package test;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 12/13/17.
 */
public class ExcelTest {
    public static void main(String[] args) throws Exception {
        File excelFile = new File("huawei.csv");
        File outputFile = new File("huawei-output.csv");

        BufferedReader reader = new BufferedReader(new FileReader(excelFile));
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

        Map<String,Set<String>> map = new HashMap<>();
        reader.lines().skip(1).forEach(line->{
            System.out.println("line: "+line);
            String[] cells = line.split(",",2);
            if(cells.length<2) return;
            String asset = cells[0].replace("\"","");
            String names = cells[1].replace("\"","");
            Stream.of(names.split(",")).forEach(name->{
                name = name.trim();
                map.putIfAbsent(name,new HashSet<>());
                map.get(name).add(asset);
            });
        });

        map.entrySet().stream().sorted(Comparator.comparing(e->e.getKey())).forEach(e->{
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
