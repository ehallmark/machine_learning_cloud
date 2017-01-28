package server.tools.excel;

import java.util.List;

/**
 * Created by Evan on 1/27/2017.
 */
public class ExcelSheet {
    private String name;
    private List<ExcelRow> rows;
    public ExcelSheet(String name, List<ExcelRow> rows) {
        this.name=name;
        this.rows=rows;
    }

    public List<ExcelRow> getRows() { return rows; }

    public String getName() { return name; }

}
