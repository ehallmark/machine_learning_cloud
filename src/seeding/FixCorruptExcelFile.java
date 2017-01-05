package seeding;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Created by ehallmark on 10/15/16.
 */
public class FixCorruptExcelFile {
    public static void main(String[] args) throws Exception {
        File corruptFile = new File("corrupt_file.xls");
        File correctFile = new File("correct_file.xls");
        File newFileCsv = new File("fixed_file.csv");
        BufferedWriter bw = new BufferedWriter(new FileWriter(newFileCsv));
        int colIdx = 1;
        int offset = 3;
        List<String> assigneesFromCorrupt = GetEtsiPatentsList.getExcelList(corruptFile,colIdx,offset);
        for(String assignee : assigneesFromCorrupt) {
            System.out.println(assignee);
        }

        Workbook wb = null;
        try {
            wb = Workbook.getWorkbook(correctFile);
        } catch(BiffException be) {
            be.printStackTrace();
            throw new RuntimeException("--- Try converting to Excel 97-2004 (.xls) format ---");
        }
        Sheet sheet = wb.getSheet(0);

        for(int i = offset; i < sheet.getRows(); i++) {
            Cell[] row = sheet.getRow(i);
            if(row.length < colIdx+1) continue;
            if(row[colIdx]==null) continue;
            Cell p = row[colIdx];
            if(p.getContents()==null)continue;
            String name = p.getContents().trim();
            System.out.println("name => "+name);
            if(name.length()>0 && assigneesFromCorrupt.contains(name)) {
                StringJoiner sj = new StringJoiner(",","","\n");
                for(int j = colIdx; j < row.length; j++) {
                    Cell c = row[j];
                    String str;
                    if(c!=null&&c.getContents()!=null) str = c.getContents();
                    else str="";
                    sj.add(str);
                }
                bw.write(sj.toString());
                bw.flush();
            }
        }
        bw.flush();
        bw.close();
    }
}
