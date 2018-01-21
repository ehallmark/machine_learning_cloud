package user_interface.ui_models.charts.tables;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Evan on 12/16/2017.
 */
public class TableResponse {
    public List<String> headers;
    public List<String> nonHumanAttrs;
    public RecursiveTask<List<Map<String,String>>> computeAttributesTask;
    public String type;
    public String title;
    public Set<String> numericAttrNames;
    public final ReentrantLock lock;

    public TableResponse() {
        this.lock = new ReentrantLock();
    }
}