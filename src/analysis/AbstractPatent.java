package analysis;

/**
 * Created by ehallmark on 7/27/16.
 */
public class AbstractPatent {
    protected String name;
    protected String assignee;
    protected double similarity;
    public AbstractPatent(String name, double similarity, String assignee) {
        this.name = name;
        this.similarity=similarity;
        this.assignee=assignee;
    }
    public AbstractPatent() {

    }

}
