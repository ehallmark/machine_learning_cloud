package tools;

import server.tools.AbstractPatent;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PatentList implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<AbstractPatent> patents;
    private String name1;
    private String name2;
    public PatentList(List<AbstractPatent> patentList, String name1, String name2) {
        this.patents=patentList;
        this.name1=name1;
        this.name2=name2;
        Collections.sort(patents);
        Collections.reverse(patents);

    }

    public String getName1() {
        return name1;
    }

    public String getName2() {
        return name2;
    }

    public List<AbstractPatent> getPatents() {
        return patents;
    }

}
