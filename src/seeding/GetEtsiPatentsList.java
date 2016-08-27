package seeding;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/16/16.
 */
public class GetEtsiPatentsList {

    public static Map<String,List<String>> getETSIPatentMap() throws Exception {
        File excelFile = new File("etsi_patents.xls");
        if(!excelFile.exists()) throw new RuntimeException("--- File does not exist: "+excelFile.getName()+" ---");
        else System.out.println("--- Reading: "+excelFile.getName()+" ---");
        Workbook wb = null;
        try {
            wb = Workbook.getWorkbook(excelFile);
        } catch(BiffException be) {
            be.printStackTrace();
            throw new RuntimeException("--- Try converting to Excel 97-2004 (.xls) format ---");
        }
        Sheet sheet = wb.getSheet(0);
        Map<String,List<String>> map = new HashMap<>();
        for(int i = 0; i < sheet.getRows(); i++) {
            Cell[] row = sheet.getRow(i);
            if(row.length < 2) continue;
            Cell p = row[1];
            if(row.length < 11 || !(p!=null&&p.getContents().trim().startsWith("US")&&p.getContents().contains("B")))continue;
            String name = p.getContents().trim().split("\\s+")[0].replaceFirst("US","");
            Cell t = row[10];
            if (t != null && t.getContents() != null) {
                for(String key : t.getContents().split("\\|")) {
                    key=key.trim();
                    int idxSpace = key.indexOf("(");
                    if(idxSpace >= 0) key = key.substring(0,idxSpace).trim();
                    if(key.length()==0)continue;
                    if (!map.containsKey(key)) map.put(key, new ArrayList<>());
                    List<String> values = map.get(key);
                    values.add(name);
                }
            }
        }
        return map;
    }

    public static void main(String[] args) throws Exception {
        Map<String,List<String>> map = getETSIPatentMap();
        map.entrySet().forEach(entry->{
            System.out.println(entry.getKey()+" => "+Arrays.toString(entry.getValue().toArray()));
        });
    }
}
