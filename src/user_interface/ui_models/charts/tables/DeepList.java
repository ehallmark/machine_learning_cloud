package user_interface.ui_models.charts.tables;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Evan on 12/16/2017.
 */
public class DeepList<T> extends ArrayList<T> {
    private boolean cache = false;
    private Integer hash = null;

    public DeepList(List<T> list) {
        super(list);
        setCacheHashCode();
    }

    public DeepList() {
        super();
    }

    public void setCacheHashCode() {
        cache = true;
    }

    @Override
    public int hashCode() {
        if(cache) {
            if(hash==null) {
                hash = Arrays.deepHashCode(toArray());
            }
            return hash;
        }
        return Arrays.deepHashCode(toArray());
    }

    @Override
    public boolean equals(Object other) {
        List<T> l2 = (List<T>)other;
        for(int i = 0; i < Math.min(l2.size(),size()); i++) {
            if(!get(i).equals(l2.get(i))) return false;
        }
        return true;
    }
}
