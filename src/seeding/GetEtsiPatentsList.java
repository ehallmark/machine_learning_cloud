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
    private static final File FULL_ETSI_FILE = new File("data/etsi_patents.xls");
    private static final File ETSI_FILE_2G = new File("data/etsi_2g_filter.xls");
    private static final File ETSI_FILE_3G = new File("data/etsi_3g_filter.xls");
    private static final File ETSI_FILE_4G = new File("data/etsi_4g_filter.xls");

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
        Map<String,Collection<String>> map2G = get2GPatentMap();

        Map<String,Collection<String>> map3G = get3GPatentMap();

        Map<String,Collection<String>> map4G = get4GPatentMap();

        BufferedWriter writer = new BufferedWriter(new FileWriter(new File("standards_by_tech.csv")));
        // headers
        writer.write("Standard,2G (GSM) Applicable,3G (UMTS) Applicable, 4G (LTE) Applicable,Declared US Assets\n");
        writer.flush();
        Set<String> allStandards = new HashSet<>();
        allStandards.addAll(map2G.keySet());
        allStandards.addAll(map3G.keySet());
        allStandards.addAll(map4G.keySet());
        allStandards.forEach(standard->{
            try {
                Set<String> patents = new HashSet<>();
                boolean is2G = false;
                boolean is3G = false;
                boolean is4G = false;
                if(map2G.containsKey(standard)) {
                    is2G=true;
                    patents.addAll(map2G.get(standard));
                }
                if(map3G.containsKey(standard)) {
                    is3G=true;
                    patents.addAll(map3G.get(standard));
                }
                if(map4G.containsKey(standard)) {
                    is4G=true;
                    patents.addAll(map4G.get(standard));
                }
                StringJoiner line = new StringJoiner(",", "", "\n");
                line.add(standard).add(boolToString(is2G)).add(boolToString(is3G)).add(boolToString(is4G)).add(String.join(" ", patents));
                writer.write(line.toString());
                writer.flush();
            } catch(Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static String boolToString(boolean isTrue) {
        if(isTrue) return "Yes";
        else return "No";
    }

}
