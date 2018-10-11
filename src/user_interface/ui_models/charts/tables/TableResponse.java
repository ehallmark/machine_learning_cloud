package user_interface.ui_models.charts.tables;

import org.nd4j.linalg.primitives.Pair;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.aggregations.Type;

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
    public RecursiveTask<Pair<List<Map<String, String>>, List<Double>>> computeAttributesTask;
    public String type;
    public String title;
    public Set<String> numericAttrNames;
    public final ReentrantLock lock;
    public Type collectorType;
    public AbstractAttribute groupByAttribute;
    public AbstractAttribute attribute;
    public TableResponse() {
        this.lock = new ReentrantLock();
    }
}