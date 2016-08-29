package server.tools;

import seeding.Database;

import java.sql.SQLException;

/**
 * Created by ehallmark on 7/27/16.
 */
public class AbstractPatent implements Comparable<AbstractPatent>{
    protected String name;
    protected double similarity;
    protected String referringName;
    protected String title;

    public AbstractPatent(String name, double similarity, String referringName) throws SQLException {
        this.name = name;
        this.similarity=similarity;
        this.title= Database.getTitleFromDB(name);
        this.referringName=referringName;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public double getSimilarity() {
        return similarity;
    }

    public String getReferringName() {
        return referringName;
    }

    public void flipSimilarity() { similarity*=-1.0; }

    @Override
    public int compareTo(AbstractPatent o) {
        return Double.compare(similarity, o.similarity);
    }
}
