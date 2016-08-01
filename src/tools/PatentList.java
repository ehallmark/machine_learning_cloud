package tools;

import server.AbstractPatent;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PatentList extends ArrayList<AbstractPatent> implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sortedBy;
    public PatentList(int capacity, String type) {
        super(capacity);
        sortedBy=type;
    }
}
