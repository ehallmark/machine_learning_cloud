package excel;

import java.util.List;

/**
 * Created by Evan on 1/27/2017.
 */
public class ExcelRow {
    private List<ExcelCell> cells;
    private double height;
    public ExcelRow(List<ExcelCell> cells, double height) {
        this.cells=cells;
        this.height=height;
    }

    public List<ExcelCell> getCells() {
        return cells;
    }

    public double getHeight() {
        return height;
    }
}
