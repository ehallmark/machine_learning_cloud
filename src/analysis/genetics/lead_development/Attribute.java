package analysis.genetics.lead_development;

/**
 * Created by Evan on 2/25/2017.
 */
public abstract class Attribute {
    public String name;
    public double importance;
    public Attribute(String name, double importance) {
        this.name=name;
        this.importance=importance;
    }
    @Override
    public boolean equals(Object other) {
        return other.hashCode()==hashCode();
    }

    public abstract double scoreAssignee(String assignee);

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
