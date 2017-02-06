package seeding;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/16/16.
 */
public class GetEtsiPatentsList {

    public static Map<String,Collection<String>> getETSIPatentMap() throws Exception {
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
        Map<String,Collection<String>> map = new HashMap<>();
        for(int i = 0; i < sheet.getRows(); i++) {
            Cell[] row = sheet.getRow(i);
            if(row.length < 2) continue;
            Cell p = row[1];
            if(row.length < 11 || !(p!=null&&p.getContents().trim().startsWith("US")&&p.getContents().contains("B")))continue;
            String name = p.getContents().trim().split("\\s+")[0].replaceFirst("US","");
            Cell t = row[10];
            if (t != null && t.getContents() != null) {
                for(String key : t.getContents().split("\\|")) {
                    key=key.replaceAll("\\."," ").trim().replaceAll("\\s+"," ").replaceAll("\\s+"," ");
                    int idxSpace = key.indexOf("(");
                    if(idxSpace >= 0) key = key.substring(0,idxSpace).trim();
                    if(key.length()==0)continue;
                    if (!map.containsKey(key)) map.put(key, new HashSet<>());
                    Collection<String> values = map.get(key);
                    values.add(name);
                }
            }
        }
        return map;
    }
    public static List<String> getExcelList(File excelFile, int colIdx, int offset) throws Exception {
        assert offset >= 0 : "Offset must be positive!";
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
        List<String> list = new ArrayList<>();
        for(int i = offset; i < sheet.getRows(); i++) {
            Cell[] row = sheet.getRow(i);
            if(row.length < colIdx+1) continue;
            if(row[colIdx]==null) continue;
            Cell p = row[colIdx];
            if(p.getContents()==null)continue;
            String name = p.getContents().trim();
            if(name.length()>0) {
                list.add(name);
            }
        }
        return list;
    }

    public static void loadAndPrintExcelReservoir(String filename, int colIdx, int offset) throws Exception {
        List<String> list = getExcelList(new File(filename),colIdx,offset);

        StringJoiner sj = new StringJoiner(" ");

        AtomicInteger cnt = new AtomicInteger(0);
        list.forEach(p->{
            sj.add(p.replaceFirst("US","").replaceAll(",","").replaceAll("\\.","").trim());
            cnt.getAndIncrement();
        });

        System.out.println("Read from: "+filename);
        System.out.println(sj.toString());
        System.out.println("Total count: "+cnt.get());

    }

    public static void main(String[] args) throws Exception {
        //Database.setupSeedConn();

        loadAndPrintExcelReservoir("orange_reservoir.xls",0,1);
        loadAndPrintExcelReservoir("ricoh_reservoir.xls",1,1);

    }
}
