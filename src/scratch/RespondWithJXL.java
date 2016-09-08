package scratch;

import jxl.Workbook;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

import javax.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;

import static spark.Spark.get;

/**
 * Created by ehallmark on 9/8/16.
 */
public class RespondWithJXL {


    public static void server() {
        get("/download", (req, res) -> {
            HttpServletResponse raw = res.raw();
            res.header("Content-Disposition", "attachment; filename=download.xls");
            res.type("application/force-download");
            try {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                WritableWorkbook workbook = Workbook.createWorkbook(os);
                workbook.createSheet("Sheet1", 0);
                workbook.createSheet("Sheet2", 1);
                workbook.createSheet("Sheet3", 2);
                workbook.write();
                workbook.close();
                raw.getOutputStream().write(os.toByteArray());
                raw.getOutputStream().flush();
                raw.getOutputStream().close();
            } catch (Exception e) {

                e.printStackTrace();
            }
            return raw;
        });
    }

    public static void main(String[] args) {
        server();
    }
}
