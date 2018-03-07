package seeding;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/16/16.
 */
public class GetEtsiPatentsList {
    private static final File FULL_ETSI_FILE = new File(Constants.DATA_FOLDER+"etsi_patents.xls");
    private static final File ETSI_FILE_2G = new File(Constants.DATA_FOLDER+"etsi_2g_filter.xls");
    private static final File ETSI_FILE_3G = new File(Constants.DATA_FOLDER+"etsi_3g_filter.xls");
    private static final File ETSI_FILE_4G = new File(Constants.DATA_FOLDER+"etsi_4g_filter.xls");

    public static String cleanETSIString(String unClean) {
        unClean=unClean.toUpperCase().replaceAll("\\."," ");
        while(unClean.contains("  ")) unClean = unClean.replaceAll("  "," ");
        unClean=unClean.trim();

        int idxSpace = unClean.indexOf("(");
        if(idxSpace >= 0) unClean = unClean.substring(0,idxSpace).trim();
        // trim middle 1 if possible
        String[] split = unClean.split(" ");
        if(split.length>2 && split[1].length()==3 && split[1].startsWith("1")) {
            split[1]=split[1].substring(1);
            unClean=String.join(" ",split);
        }
        if(unClean.contains(","))unClean=unClean.replaceAll(",","");
        return unClean;
    }

    public static Map<String,Collection<String>> getETSIPatentMap(File excelFile) throws IOException {
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
                    key = cleanETSIString(key);
                    if(key.length()==0)continue;
                    if (!map.containsKey(key)) map.put(key, new HashSet<>());
                    Collection<String> values = map.get(key);
                    values.add(name);
                }
            }
        }
        return map;
    }

    public static Map<String,Collection<String>> getETSIPatentMap() throws IOException {
        return getETSIPatentMap(FULL_ETSI_FILE);
    }

    public static Map<String,Collection<String>> get2GPatentMap() throws IOException {
        return getETSIPatentMap(ETSI_FILE_2G);
    }

    public static Map<String,Collection<String>> get3GPatentMap() throws IOException {
        return getETSIPatentMap(ETSI_FILE_3G);
    }

    public static Map<String,Collection<String>> get4GPatentMap() throws IOException {
        return getETSIPatentMap(ETSI_FILE_4G);
    }

    public static Collection<String> get2GPatents() throws IOException {
        Set<String> patents = new HashSet<>();
        get2GPatentMap().values().forEach((patentSet)->patents.addAll(patentSet));
        return patents;
    }

    public static Collection<String> get3GPatents() throws IOException {
        Set<String> patents = new HashSet<>();
        get3GPatentMap().values().forEach((patentSet)->patents.addAll(patentSet));
        return patents;
    }

    public static Collection<String> get4GPatents() throws IOException {
        Set<String> patents = new HashSet<>();
        get4GPatentMap().values().forEach((patentSet)->patents.addAll(patentSet));
        return patents;
    }


    public static List<List<String>> getExcelLists(File excelFile, int offset, int... colIdxs) throws Exception {
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
        List<List<String>> outerList = new ArrayList<>();
        for(int i = offset; i < sheet.getRows(); i++) {
            Cell[] row = sheet.getRow(i);
            List<String> innerList = new ArrayList<>(colIdxs.length);
            for(int colIdx : colIdxs) {
                String name = "";
                if (!(row.length < colIdx + 1) && !(row[colIdx] == null)) {
                    Cell p = row[colIdx];
                    if (p.getContents() == null) continue;
                    name = p.getContents().trim();
                }
                innerList.add(name);
            }
            outerList.add(innerList);
        }
        return outerList;
    }

    public static List<String> getExcelList(File excelFile, int offset, int colIdx, String delim) throws Exception {
        return getExcelLists(excelFile,offset,colIdx).stream().map(list->String.join(delim,list)).collect(Collectors.toList());
    }

    public static List<String> getExcelList(File excelFile, int offset, int colIdx) throws Exception {
        return getExcelList(excelFile,offset,colIdx,"");
    }

    public static void main(String[] args) throws Exception {
        Set<String> technologies = new HashSet<>();
        getExcelList(new File("1402.xls"),9,4).forEach(line->{
            Arrays.stream(line.split(";")).forEach(tech->{
                technologies.add(tech.trim());
            });
        });
        technologies.stream().sorted().forEach(tech->{
            System.out.println(tech.trim());
        });
    }

    private static String boolToString(boolean isTrue) {
        if(isTrue) return "Yes";
        else return "No";
    }

}
