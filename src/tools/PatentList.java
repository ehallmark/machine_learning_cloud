package tools;

import analysis.Patent;
import server.AbstractPatent;

import java.util.ArrayList;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PatentList extends ArrayList<AbstractPatent> {
    private String sortedBy;
    public PatentList(int capacity, Patent.Type type) {
        super(capacity);
        sortedBy=type.toString().toLowerCase();
    }
    public String getSortedBy() {
        return sortedBy;
    }
}
