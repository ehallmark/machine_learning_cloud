package server.tools.excel;

import jxl.write.Number;
import jxl.write.WritableCell;
import jxl.write.WritableCellFormat;

/**
 * Created by Evan on 1/27/2017.
 */
public class ExcelCell {
    private WritableCellFormat format;
    private Object content;
    private boolean isNumber;
    public ExcelCell(WritableCellFormat format, Object content, boolean isNumber) {
        this.format=format;
        this.isNumber=isNumber;
        this.content=content;
    }

    public Object getContent() { return content; }
    public WritableCellFormat getFormat() { return format; }
    public boolean isNumber() { return isNumber; }
}
