package server.tools;

/**
 * Created by ehallmark on 11/19/16.
 */
public interface ExcelWritable {
    String[] getDataAsRow(boolean valuePrediction, int tagLimit);
}
