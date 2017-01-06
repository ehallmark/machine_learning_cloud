package server.tools;

import jxl.Workbook;
import jxl.format.Alignment;
import jxl.format.Border;
import jxl.format.BorderLineStyle;
import jxl.format.*;
import jxl.format.Colour;
import jxl.format.VerticalAlignment;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.*;
import tools.PatentList;
import tools.TreeGui;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.URL;
import java.util.*;

import static spark.Spark.get;

/**
 * Created by ehallmark on 9/8/16.
 */
public class RespondWithJXL {

    private static final double CELL_DEFAULT_HEIGHT = 24;
    private static Map<String,WritableCellFormat> CellFormatMap = new HashMap<>();


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


    private static WritableSheet createSheetWithTemplate(WritableWorkbook workbook, String sheetName, String sheetTitle, String[] headers, String[][] data, int[] colWidths, int assigneeCol, java.util.List<String> toHighlight, boolean gatherValue) throws Exception{
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


        if(data!=null&&headers!=null)writeHeadersAndData(sheet, headers, data, assigneeCol, toHighlight, 0,gatherValue);

        return sheet;
    }
    private static void writeHeadersAndData(WritableSheet sheet, String[] headers, String[][] data, int assigneeCol, java.util.List<String> toHighlight, int rowOffset, boolean gatherValue) throws Exception {
        writeHeadersAndData(sheet,null,headers,data,assigneeCol,toHighlight,rowOffset,gatherValue);
    }

    private static void writeHeadersAndData(WritableSheet sheet, String preTitle, String[] headers, String[][] data, int assigneeCol, java.util.List<String> toHighlight, int rowOffset, boolean gatherValue) throws Exception {
        int headerRow = 6 + rowOffset;

        if(preTitle!=null) {
            // sheet title
            int height = 27 * 20;
            sheet.setRowView(headerRow, height);
            sheet.addCell(new Label(1, headerRow, preTitle, CellFormatMap.get("titleStyle")));
            headerRow++;
        }

        // headers
        int headerHeight = 50 * 20;
        sheet.setRowView(headerRow, headerHeight);
        for (int c = 0; c < headers.length; c++) {
            sheet.addCell(new Label(1 + c, headerRow, headers[c], CellFormatMap.get("headerStyle")));
        }


        for (int r = 0; r < data.length; r++) {
            CellFormat defaultFormat = CellFormatMap.get("dataStyle");
            CellFormat percentageFormat = CellFormatMap.get("dataStylePercentage");
            final int thisRow = r;
            if (toHighlight.stream().anyMatch(highlight -> data[thisRow][assigneeCol].toUpperCase().startsWith(highlight))) {
                defaultFormat = CellFormatMap.get("dataStyleHighlighted");
                percentageFormat = CellFormatMap.get("dataStylePercentageHighlighted");
            }
            for (int c = 0; c < data[r].length; c++) {
                CellFormat format = defaultFormat;
                boolean isNumber = false;
                Double num = null;
                try {
                    num = (double) Integer.valueOf(data[r][c]);
                    isNumber = true;
                } catch (Exception e) {
                    try {
                        num = Double.valueOf(data[r][c]);
                        isNumber = true;
                        if((!gatherValue)||num<=1.0)format = percentageFormat;
                    } catch (Exception e2) {

                    }
                }
                int col = c + 1;
                int row = headerRow + 1 + r;

                WritableCell cell;
                if (isNumber) {
                    cell = new Number(col, row, num, format);
                } else {
                    cell = new Label(col, row, data[r][c].toUpperCase(), format);
                }
                sheet.addCell(cell);
            }
        }
    }

    private static WritableSheet setupPatentList(WritableWorkbook workbook, String sheetPrefix, PatentList patentList, java.util.List<String> toHighlight, int tagLimit, boolean gatherValue, int tagIdx) throws Exception {
        String[][] data = new String[patentList.getPatents().size()][];
        String[] headers = gatherValue ? new String[]{"Patent", "Relevance", "Value", "Assignee", "Tag Count", "Primary Tag", "Additional Tags", "Invention Title"}
            : new String[]{"Patent", "Relevance", "Assignee", "Tag Count", "Primary Tag", "Additional Tags", "Invention Title"};
        java.util.List<AbstractPatent> patents = patentList.getPatents();
        if(gatherValue) Collections.sort(patents, (p1,p2)->Double.compare(p2.getGatherValue(),p1.getGatherValue()));
        for (int i = 0; i < patentList.getPatents().size(); i++) {
            data[i] = patentList.getPatents().get(i).getDataAsRow(gatherValue,tagLimit);
        }

        String sheetName = sheetPrefix+ "- Patent List";
        String sheetTitle = sheetPrefix+ " - Patent List ("+data.length+" patents)";
        int assigneeColIdx = 2;
        int[] colWidths = gatherValue ? new int[]{25, 25, 25, 75, 25, 25, 50, 75} : new int[]{25, 25, 75, 25, 25, 50, 75};
        // patents are in first column - make sure they start with 'US'
        for(int i = 0; i < data.length; i++) {
            if(!data[i][0].startsWith("US")) data[i][0] = "US"+data[i][0];
        }
        WritableSheet sheet = createSheetWithTemplate(workbook, sheetName, sheetTitle, headers, data, colWidths, assigneeColIdx, toHighlight, gatherValue);
        return sheet;
    }

    private static WritableSheet setupSearchTerms(WritableWorkbook workbook, PatentList patentList, String sheetPrefix, boolean gatherValue, int tagIdx) throws Exception {
        final String sheetName = sheetPrefix+"- Search Terms";
        final String sheetTitle = "Search Terms and Results";
        final String[] headers = gatherValue ? new String[]{"Search Term","Results (No. of Assets)","Average Relevance","Average Value","No. Assets 4+","No. Assets 3+",  "No. Assets below 3"}
            : new String[]{"Search Term","Results (No. of Patents)","Average Relevance"};
        final int[] colWidths = gatherValue ? new int[]{25,25,25,25,25,25,25}   :
                new int[]{25,25,25};
        WritableSheet sheet = createSheetWithTemplate(workbook, sheetName, sheetTitle, null, null, colWidths, 0, new ArrayList<>(),gatherValue);
        String[][] data = new String[patentList.getTags().size()][];
        for(int i = 0; i < patentList.getTags().size(); i++) {
            data[i] = patentList.getTags().get(i).getDataAsRow(gatherValue,0);
        }
        writeHeadersAndData(sheet, sheetPrefix, headers, data, 0, new ArrayList<>(), 0, gatherValue);
        return sheet;
    }

    private static WritableSheet setupAssigneeQuantityList(WritableWorkbook workbook, String sheetPrefix, PatentList patentList, java.util.List<String> toHighlight, int tagLimit, boolean gatherValue, int tagIdx) throws Exception {
        String sheetName = sheetPrefix+ "- Assignee List";
        String sheetTitle = sheetPrefix+ " - Assignee Quantity List ("+patentList.getAssignees().size()+" assignees)";
        String[][] data = new String[patentList.getAssignees().size()][];
        for(int i = 0; i < data.length; i++) {
            data[i] = patentList.getAssignees().get(i).getDataAsRow(gatherValue,tagLimit);
        }
        String[] headers = gatherValue ? new String[]{"Assignee","Patent Qty.","Avg. Relevance", "Avg. Value", "Tag Count", "Primary Tag", "Additional Tags"}
                : new String[]{"Assignee","Patent Qty.","Avg. Relevance", "Tag Count", "Primary Tag", "Additional Tags"};
        int assigneeIdx = 0;
        int[] colWidths = gatherValue ? new int[]{75,25,25,25,25,25,50} : new int[]{75,25,25,25,25,50};
        WritableSheet sheet = createSheetWithTemplate(workbook, sheetName, sheetTitle, headers, data, colWidths, assigneeIdx, toHighlight,gatherValue);
        return sheet;
    }

    private static void buildCoverPage(WritableWorkbook workbook, String clientName, String[] EMData, String[] SAMData) throws Exception{
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


    public static void writeDefaultSpreadSheetToRaw(HttpServletResponse raw, PatentList patentList, java.util.List<String> toHighlight, String clientName, String[] EMData, String[] SAMData, int tagLimit, boolean gatherValue, int tagIdx) throws Exception {
        setupExcelFormats();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        WritableWorkbook workbook = Workbook.createWorkbook(os);

        // cover
        buildCoverPage(workbook,clientName,EMData,SAMData);


        setupAssigneeQuantityList(workbook, patentList.getName1(), patentList, toHighlight, tagLimit, gatherValue,tagIdx);
        setupPatentList(workbook, patentList.getName1(), patentList, toHighlight, tagLimit, gatherValue,tagIdx);
        setupSearchTerms(workbook, patentList, patentList.getName1(), gatherValue,tagIdx);


        workbook.write();
        workbook.close();

        raw.getOutputStream().write(os.toByteArray());
        raw.getOutputStream().flush();
        raw.getOutputStream().close();
    }



    private static void server() {

        get("/download", (req, res) -> {
            HttpServletResponse raw = res.raw();
            res.header("Content-Disposition", "attachment; filename=download.xls");
            res.type("application/force-download");
            try {
               // writeDefaultSpreadSheetToRaw(raw,"Focused Search",null,null,null);
            } catch (Exception e) {

                e.printStackTrace();
            }
            return raw;
        });

        get("/graph.gif", (req, res)->{
            ///Panel c = TreeDrawing.getSampleTree(); // the component you would like to print to a BufferedImage
            //BufferedImage bi = getImage(c);

            // Drawing Examples
            TreeGui<String> c = TreeDrawing.getSampleTree();
            c.draw();
            boolean success = c.writeToOutputStream(res.raw().getOutputStream());
            System.out.println("Created " + c.getClass().getSimpleName() + " : " + success);

            res.type("image/gif");
            OutputStream out = res.raw().getOutputStream();
            //ImageIO.write(bufferedImage, "png", out);
            out.close();
            res.status(200);

            return res.body();

        });
    }

    public static BufferedImage getImage(Panel c) {
        BufferedImage bi;
        try {
            bi = new BufferedImage(c.getWidth(),c.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g =GraphicsEnvironment.getLocalGraphicsEnvironment().createGraphics(bi);
            //c.update(g2d);
            g.setColor(c.getForeground());
            g.setFont(c.getFont());
            g.setBackground(c.getBackground());
            c.print(g);
            g.dispose();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return bi;
    }


    public static void main(String[] args) {
        server();
    }
}
