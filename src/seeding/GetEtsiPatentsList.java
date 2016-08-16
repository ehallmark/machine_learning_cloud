package seeding;

import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/16/16.
 */
public class GetEtsiPatentsList {

    public static void main(String[] args) throws Exception {
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
        Set<String> usPatents = Arrays.asList(sheet.getColumn(1)).stream().filter(p->p!=null&&p.getContents().trim().startsWith("US")&&p.getContents().contains("B")).map(p->p.getContents().trim().split("\\s+")[0].replaceFirst("US","")).collect(Collectors.toSet());

        StringJoiner sj = new StringJoiner("\",\"","Arrays.asList(\"","\");");
        for(String patent: usPatents) {
            if(patent!=null&&patent.trim().length()>0)sj.add(patent);
        }
        System.out.println(sj.toString());
    }
}
