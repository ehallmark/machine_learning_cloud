package user_interface.ui_models.excel;

import jxl.Workbook;
import jxl.format.*;
import jxl.format.Alignment;
import jxl.format.Border;
import jxl.format.BorderLineStyle;
import jxl.format.Colour;
import jxl.format.VerticalAlignment;
import jxl.write.*;
import jxl.write.Number;
import user_interface.server.SimilarPatentServer;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import java.util.stream.Stream;

/**
 * Created by Evan on 1/27/2017.
 */
public class ExcelHandler {
    private static Map<String,WritableCellFormat> CellFormatMap = new HashMap<>();
    public static final double CELL_DEFAULT_HEIGHT = 24;

    public static WritableCellFormat getDefaultFormat() {
        return CellFormatMap.get("dataStyle");
    }
    public static WritableCellFormat getNumberFormat() {
        return CellFormatMap.get("valueFormat");
    }

    private static void setupExcelFormats() throws Exception {
        // title style
        WritableFont cellFont = new WritableFont(WritableFont.ARIAL, 12);
        cellFont.setBoldStyle(WritableFont.BOLD);
        cellFont.setUnderlineStyle(UnderlineStyle.SINGLE);
        WritableCellFormat cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        CellFormatMap.put("titleStyle",cellFormat);

        // disclaimer style
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        cellFont.setBoldStyle(WritableFont.BOLD);
        cellFont.setColour(Colour.DARK_RED);
        cellFormat = new WritableCellFormat(cellFont);
        CellFormatMap.put("disclaimerStyle",cellFormat);

        // Data style
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN, Colour.BLACK);
        cellFormat.setWrap(true);
        CellFormatMap.put("dataStyle",cellFormat);


        // Data style number
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        NumberFormat decimalNo = new NumberFormat("0");
        cellFormat = new WritableCellFormat(decimalNo);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN, Colour.BLACK);
        cellFormat.setFont(cellFont);
        //write to datasheet
        CellFormatMap.put("valueFormat",cellFormat);

        // header style
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        cellFont.setBoldStyle(WritableFont.BOLD);
        cellFont.setColour(Colour.WHITE);
        cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setBackground(Colour.DARK_BLUE);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN, Colour.BLACK);
        cellFormat.setWrap(true);
        CellFormatMap.put("headerStyle",cellFormat);

    }

    public static void writeDefaultSpreadSheetToRaw(HttpServletResponse raw, String sheetName, String sheetTitle, List<List<Object>> data, List<String> headers) throws Exception {
        WritableWorkbook workbook = Workbook.createWorkbook(raw.getOutputStream());

        createSheetWithTemplate(workbook, sheetName, sheetTitle, data, headers);

        long t0 = System.currentTimeMillis();

        workbook.write();
        workbook.close();

        raw.getOutputStream().flush();
        raw.getOutputStream().close();

        long t1 = System.currentTimeMillis();
        System.out.println("Time to write sheet to outputstream: "+(t1-t0)/1000+ " seconds");
    }

    private static int[] computeColWidths(List<List<Object>> data, List<String> attributes) {
        Stream<int[]> stream = data.parallelStream().limit(10).map(row->{
            int[] rowWidths = new int[attributes.size()];
            for(int i = 0; i < attributes.size(); i++) {
                rowWidths[i] = charToPixelLength(row.get(i).toString().length());
            }
            return rowWidths;
        });
        int[] widths = stream.reduce((w1,w2)->{
            for(int i = 0; i < w1.length; i++) {
                w1[i]=(w1[i]+w2[i])/2;
            }
            return w1;
        }).get();
        return widths;
    }

    private static int charToPixelLength(int numChars) { return numChars + 8; }

    private static WritableSheet createSheetWithTemplate(WritableWorkbook workbook, String sheetName, String sheetTitle, List<List<Object>> data, List<String> headers) throws Exception{
        try {
            setupExcelFormats();
        } catch(Exception e) {
            e.printStackTrace();
        }
        workbook.setColourRGB(Colour.DARK_BLUE, 52, 89, 133);
        WritableSheet sheet = workbook.createSheet(sheetName, workbook.getNumberOfSheets());
        sheet.getSettings().setShowGridLines(false);

        double rowHeightMultiplier = 1.3;
        sheet.getSettings().setDefaultRowHeight((int)Math.round(255.0*rowHeightMultiplier));

        // Create gutter
        int col = 0;
        int width = 3;
        sheet.setColumnView(col, width);
        sheet.addCell(new Label(col, 0, ""));

        long t0 = System.currentTimeMillis();
        int[] colWidths = computeColWidths(data, headers);
        for(int i = 0; i < colWidths.length; i++) {
            sheet.setColumnView(1+i, colWidths[i]);
        }
        long t1 = System.currentTimeMillis();
        System.out.println("Time to compute col widths: "+(t1-t0)/1000+ " seconds");

        // gtt logo
        String pathToImage = "images/brand.png";
        File logoFile = new File(pathToImage);
        BufferedImage logoImage = ImageIO.read(logoFile);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(logoImage, "PNG", baos);
        WritableImage img = new WritableImage(1.0,0.3,30.0*new Double(logoImage.getWidth()) / sheet.getColumnView(1).getSize(),
                1.2 * new Double(logoImage.getHeight()) / CELL_DEFAULT_HEIGHT,baos.toByteArray());
        sheet.addImage(img);

        // Create the label, specifying content and format

        // sheet title
        int row = 3;
        int height = 27 * 20;
        sheet.setRowView(row, height);
        sheet.addCell(new Label(1, row, sheetTitle, CellFormatMap.get("titleStyle")));

        row++;
        // disclaimer
        sheet.addCell(new Label(1, row, "Prepared at the Direction of Counsel", CellFormatMap.get("disclaimerStyle")));
        row++;
        sheet.addCell(new Label(1, row, "Privileged and Confidential Work Product", CellFormatMap.get("disclaimerStyle")));
        row++;

        writeHeadersAndData(sheet,  data, row, headers);

        return sheet;
    }

    private static void writeHeadersAndData(WritableSheet sheet, List<List<Object>> data, int rowOffset, List<String> attributes) throws Exception {
        System.out.println("Starting sheet with "+data.size()+ " elements");

        //int headerRow = 6 + rowOffset;
        int headerRow = rowOffset;

        // headers
        int headerHeight = 50 * 20;
        sheet.setRowView(headerRow, headerHeight);
        for (int c = 0; c < attributes.size(); c++) {
            sheet.addCell(new Label(1 + c, headerRow, SimilarPatentServer.humanAttributeFor(attributes.get(c)), CellFormatMap.get("headerStyle")));
        }

        for (int r = 0; r < data.size(); r++) {
            List<Object> rowData = data.get(r);
            for (int c = 0; c < attributes.size(); c++) {
                int col = c + 1;
                int row = headerRow + 1 + r;
                WritableCell cell;
                Object excelCell = rowData.get(c);

                if(excelCell==null) {
                    cell = new Label(col, row, "", getDefaultFormat());
                } else if (isNumber(excelCell)) {
                    cell = new Number(col, row, Double.valueOf(excelCell.toString()), getNumberFormat());
                } else {
                    cell = new Label(col, row, excelCell.toString(), getDefaultFormat());
                }
                sheet.addCell(cell);
            }
        }
    }

    private static boolean isNumber(Object content) {
        if(content instanceof Number) return true;
        try {
            Double.valueOf(content.toString());
            return true;
        } catch(Exception e) {
            return false;
        }
    }

}