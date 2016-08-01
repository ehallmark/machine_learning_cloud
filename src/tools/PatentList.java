package tools;

import server.AbstractPatent;

import java.util.ArrayList;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PatentList extends ArrayList<AbstractPatent> {
    public String sortedBy;
    public PatentList(int capacity, String type) {
        super(capacity);
        sortedBy=type;
    }
}
