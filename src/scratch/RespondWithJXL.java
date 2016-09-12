package scratch;

import jxl.Workbook;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import javax.swing.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
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

        get("/graph", (req, res)->{
            Component c = TreeDrawing.getSampleTree(); // the component you would like to print to a BufferedImage
            JFrame frame = new JFrame();
            frame.setBackground(Color.WHITE);
            frame.setUndecorated(true);
            frame.getContentPane().add(c);
            frame.pack();
            BufferedImage bi = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = bi.createGraphics();
            c.print(graphics);
            graphics.dispose();
            frame.dispose();
            res.type("image/png");

            OutputStream out = res.raw().getOutputStream();
            ImageIO.write(bi, "png", out);
            out.close();
            res.status(200);
            return res.body();
        });
    }

    public static void main(String[] args) {
        server();
    }
}
