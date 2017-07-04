package ui_models.excel;

import jxl.Workbook;
import jxl.format.*;
import jxl.format.Alignment;
import jxl.format.Border;
import jxl.format.BorderLineStyle;
import jxl.format.Colour;
import jxl.format.VerticalAlignment;
import jxl.write.*;
import jxl.write.Number;
import lombok.Getter;
import server.SimilarPatentServer;
import ui_models.attributes.AbstractAttribute;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.items.Item;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 1/27/2017.
 */
public class ExcelHandler {
    private static Map<String,WritableCellFormat> CellFormatMap = new HashMap<>();
    public static final double CELL_DEFAULT_HEIGHT = 24;
    public static final double CELL_DEFAULT_WIDTH = 25;

    public static WritableCellFormat getDefaultFormat() {
        return CellFormatMap.get("dataStyle");
    }

    public static WritableCellFormat getPercentageFormat() {
        return CellFormatMap.get("dataStylePercentage");
    }

    public static WritableCellFormat getValueFormat() {
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

        // disclaimer style centered
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        cellFont.setBoldStyle(WritableFont.NO_BOLD);
        cellFont.setColour(Colour.DARK_RED);
        cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setAlignment(Alignment.CENTRE);
        CellFormatMap.put("disclaimerStyleCentered",cellFormat);

        // Data style
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN, Colour.BLACK);
        cellFormat.setWrap(true);
        CellFormatMap.put("dataStyle",cellFormat);

        // normal style
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setAlignment(Alignment.LEFT);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        cellFormat.setWrap(false);
        CellFormatMap.put("normalStyle",cellFormat);

        // bold style
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        cellFormat = new WritableCellFormat(cellFont);
        cellFont.setBoldStyle(WritableFont.BOLD);
        cellFormat.setAlignment(Alignment.LEFT);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        cellFormat.setWrap(false);
        CellFormatMap.put("boldStyle",cellFormat);

        // linkStyle
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        cellFont.setUnderlineStyle(UnderlineStyle.SINGLE);
        cellFont.setColour(Colour.BLUE);
        cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN, Colour.BLACK);
        CellFormatMap.put("linkStyle",cellFormat);

        // coverLinkStyle
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        cellFont.setUnderlineStyle(UnderlineStyle.SINGLE);
        cellFont.setColour(Colour.BLUE);
        cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setAlignment(Alignment.LEFT);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        CellFormatMap.put("coverLinkStyle",cellFormat);

        // Data style percentage
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        NumberFormat decimalNo = new NumberFormat("0.00%");
        cellFormat = new WritableCellFormat(decimalNo);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN, Colour.BLACK);
        cellFormat.setFont(cellFont);
        //write to datasheet
        CellFormatMap.put("dataStylePercentage",cellFormat);


        // Data style number
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        decimalNo = new NumberFormat("0.0");
        cellFormat = new WritableCellFormat(decimalNo);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN, Colour.BLACK);
        cellFormat.setFont(cellFont);
        //write to datasheet
        CellFormatMap.put("valueFormat",cellFormat);

        // Data style percentage highlighted
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        decimalNo = new NumberFormat("0.00%");
        cellFormat = new WritableCellFormat(decimalNo);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN, Colour.BLACK);
        cellFormat.setFont(cellFont);
        cellFormat.setBackground(Colour.AQUA);
        //write to datasheet
        CellFormatMap.put("dataStylePercentageHighlighted",cellFormat);

        //dataStyleHighlighted
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN, Colour.BLACK);
        cellFormat.setBackground(Colour.AQUA);
        cellFormat.setWrap(true);
        CellFormatMap.put("dataStyleHighlighted",cellFormat);

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

        // big header style
        cellFont = new WritableFont(WritableFont.ARIAL, 20);
        cellFont.setBoldStyle(WritableFont.NO_BOLD);
        cellFont.setColour(Colour.WHITE);
        cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setBackground(Colour.DARK_BLUE);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        //cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN, Colour.BLACK);
        cellFormat.setWrap(true);
        CellFormatMap.put("bigHeaderStyle",cellFormat);

        // big header style
        cellFont = new WritableFont(WritableFont.ARIAL, 36);
        cellFont.setBoldStyle(WritableFont.NO_BOLD);
        cellFont.setColour(Colour.DARK_BLUE);
        cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setBackground(Colour.WHITE);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        //cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN, Colour.BLACK);
        cellFormat.setWrap(true);
        CellFormatMap.put("inverseBigHeaderStyle",cellFormat);

        // small style
        cellFont = new WritableFont(WritableFont.ARIAL, 10);
        cellFont.setBoldStyle(WritableFont.NO_BOLD);
        cellFont.setColour(Colour.BLACK);
        cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        CellFormatMap.put("smallStyle",cellFormat);

        // summer style
        cellFont = new WritableFont(WritableFont.ARIAL, 12);
        cellFont.setBoldStyle(WritableFont.NO_BOLD);
        cellFont.setColour(Colour.BLACK);
        cellFormat = new WritableCellFormat(cellFont);
        cellFormat.setAlignment(Alignment.LEFT);
        cellFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
        cellFormat.setBorder(Border.ALL, BorderLineStyle.THICK, Colour.BLACK);
        cellFormat.setWrap(true);
        CellFormatMap.put("summaryStyle",cellFormat);
    }

    public static void writeDefaultSpreadSheetToRaw(HttpServletResponse raw, String sheetName, String sheetTitle, List<List<String>> data, List<String> headers) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        WritableWorkbook workbook = Workbook.createWorkbook(os);

        createSheetWithTemplate(workbook, sheetName, sheetTitle, data, headers);

        workbook.write();
        workbook.close();

        raw.getOutputStream().write(os.toByteArray());
        raw.getOutputStream().flush();
        raw.getOutputStream().close();
    }

    private static int[] computeColWidths(List<List<String>> data, List<String> attributes) {
        Stream<int[]> stream = data.parallelStream().map(row->{
            int[] rowWidths = new int[attributes.size()];
            for(int i = 0; i < attributes.size(); i++) {
                rowWidths[i] = charToPixelLength(row.get(i).toString().length());
            }
            return rowWidths;
        });
        int[] widths = stream.reduce((w1,w2)->{
            for(int i = 0; i < w1.length; i++) {
                w1[i]=Math.max(w1[i],w2[i]);
            }
            return w1;
        }).get();
        return widths;
    }

    private static int charToPixelLength(int numChars) { return numChars + 8; }

    private static WritableSheet createSheetWithTemplate(WritableWorkbook workbook, String sheetName, String sheetTitle, List<List<String>> data, List<String> headers) throws Exception{
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

        int[] colWidths = computeColWidths(data, headers);
        for(int i = 0; i < colWidths.length; i++) {
            sheet.setColumnView(1+i, colWidths[i]);
        }

        // gtt logo
        String pathToImage = "images/brand.png";
        File logoFile = new File(pathToImage);
        BufferedImage logoImage = ImageIO.read(logoFile);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(logoImage, "PNG", baos);
        WritableImage img = new WritableImage(1.0,0.3,25.0*new Double(logoImage.getWidth()) / sheet.getColumnView(1).getSize(),
                new Double(logoImage.getHeight()) / CELL_DEFAULT_HEIGHT,baos.toByteArray());
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


        writeHeadersAndData(sheet,  data, row, headers);

        return sheet;
    }

    private static void writeHeadersAndData(WritableSheet sheet, List<List<String>> data, int rowOffset, List<String> attributes) throws Exception {
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
            List<String> rowData = data.get(r);
            List<ExcelCell> excelRow = rowData.stream().map(attr->new ExcelCell(getDefaultFormat(),attr)).collect(Collectors.toList());
            for (int c = 0; c < attributes.size(); c++) {
                int col = c + 1;
                int row = headerRow + 1 + r;
                WritableCell cell;
                ExcelCell excelCell = excelRow.get(c);

                if(excelCell==null) {
                    cell = new Label(col, row, "", ExcelHandler.getDefaultFormat());
                } else if (excelCell.getContent() instanceof Number) {
                    cell = new Number(col, row, Double.valueOf(excelCell.getContent().toString()), excelCell.getFormat());
                } else {
                    cell = new Label(col, row, excelCell.getContent().toString(), excelCell.getFormat());
                }
                sheet.addCell(cell);
            }
        }
    }

    private static void buildCoverPage(WritableWorkbook workbook, String clientName, String[] EMData, String[] SAMData) throws Exception{
        try {
            setupExcelFormats();
        } catch(Exception e) {
            e.printStackTrace();
        }
        workbook.setColourRGB(Colour.DARK_BLUE, 52, 89, 133);
        WritableSheet sheet = workbook.createSheet("Cover", workbook.getNumberOfSheets());
        sheet.getSettings().setShowGridLines(false);

        //double rowHeightMultiplier = 1.3;
        //sheet.getSettings().setDefaultRowHeight((int)Math.round(255.0*rowHeightMultiplier));

        // Create gutter
        int col = 0;
        int width = 3;
        sheet.setColumnView(col, width);
        sheet.addCell(new Label(col, 0, ""));

        int[] colWidths = new int[]{17,17,21,17,30};
        for(int i = 0; i < colWidths.length; i++) {
            sheet.setColumnView(1+i, colWidths[i]);
        }

        sheet.mergeCells(1,0,5,3);
        sheet.mergeCells(1,9,5,9);
        sheet.mergeCells(1,10,5,10);
        sheet.mergeCells(1,12,5,10);
        sheet.mergeCells(1,13,5,10);

        Label label = new Label(1,0,clientName+"- AI Mining Results",CellFormatMap.get("bigHeaderStyle"));
        sheet.addCell(label);

        sheet.addCell(new Label(1,9,"Prepared for",CellFormatMap.get("smallStyle")));
        sheet.setRowView(10, 20*45);
        sheet.addCell(new Label(1,10,clientName,CellFormatMap.get("inverseBigHeaderStyle")));
        sheet.addCell(new Label(1,12,"Prepared at the Direction of Counsel",CellFormatMap.get("disclaimerStyleCentered")));
        sheet.addCell(new Label(1,13,"Privileged and Confidential Work Product",CellFormatMap.get("disclaimerStyleCentered")));


        // hyperlinks
        for(int i = 0; i < EMData.length; i++) {
            String datum = EMData[i].trim();
            if(datum.endsWith(".com")) {
                String[] mailToArr = datum.split("\\s+");
                String mailTo = mailToArr[mailToArr.length-1];
                sheet.addHyperlink(new WritableHyperlink(5,24+i,new URL("mailto:"+mailTo)));
            }
        }
        for(int i = 0; i < SAMData.length; i++) {
            String datum = SAMData[i].trim();
            if(datum.endsWith(".com")) {
                String[] mailToArr = datum.split("\\s+");
                String mailTo = mailToArr[mailToArr.length-1];
                sheet.addHyperlink(new WritableHyperlink(5,31+i,new URL("mailto:"+mailTo)));
            }
        }

        Calendar calendar = Calendar.getInstance();
        String currentYear = String.valueOf(calendar.get(Calendar.YEAR));
        String[] months = new String[]{"January","February","March","April","May","June","July","August","September","October","November","December"};
        String currentMonth = months[calendar.get(Calendar.MONTH)];

        // write contact info
        String[] addressData = new String[]{
                "Global Technology Transfer Group, Inc.",
                "805 SW Broadway, Suite 1580",
                "Portland, Oregon 97205",
                "USA",
                "T: +1.503.243.1853",
                "F: +1.503.243.1858",
                "",
                currentMonth+" "+currentYear
        };

        CellFormat[] addressFormats = new CellFormat[]{
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("normalStyle"),
                null,
                CellFormatMap.get("normalStyle"),
        };

        writeColumn(sheet,addressData,addressFormats,1,26);

        String[] contactData = new String[] {
                "Designated Contacts",
                "",
                EMData[0],
                EMData[1],
                EMData[2],
                "Global Technology Transfer Group",
                EMData[3],
                EMData[4],
                "",
                SAMData[0],
                SAMData[1],
                SAMData[2],
                "Global Technology Transfer Group",
                SAMData[3],
                SAMData[4]
        };

        CellFormat[] contactFormats = new CellFormat[] {
                CellFormatMap.get("titleStyle"),
                null,
                CellFormatMap.get("boldStyle"),
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("coverLinkStyle"),
                null,
                CellFormatMap.get("boldStyle"),
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("normalStyle"),
                CellFormatMap.get("coverLinkStyle")
        };

        writeColumn(sheet,contactData,contactFormats,5,21);


        // gtt logo
        String pathToImage = "images/brand.png";
        File logoFile = new File(pathToImage);
        BufferedImage logoImage = ImageIO.read(logoFile);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(logoImage, "PNG", baos);
        WritableImage img = new WritableImage(1,22,29.0*new Double(logoImage.getWidth()) / sheet.getColumnView(1).getSize(),
                1.15*new Double(logoImage.getHeight()) / CELL_DEFAULT_HEIGHT,baos.toByteArray());
        sheet.addImage(img);

    }

    private static void writeColumn(WritableSheet sheet, String[] data, CellFormat[] formats, int col, int startRow) throws Exception {
        for(int i = 0; i < data.length; i++) {
            if(formats[i]==null||data[i]==null)continue;
            sheet.addCell(new Label(col,startRow+i,data[i],formats[i]));
        }
    }

}

class ExcelCell {
    @Getter
    private WritableCellFormat format;
    @Getter
    private Object content;

    public ExcelCell(WritableCellFormat format, Object content) {
        this.format=format;
        this.content=content;
    }
}