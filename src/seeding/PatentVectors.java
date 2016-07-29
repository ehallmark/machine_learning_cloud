package seeding;

/**
 * Created by ehallmark on 7/20/16.
 */
public class PatentVectors {
    private Double[] titleWordVectors;
    private Double[] abstractWordVectors;
    private Double[] descriptionWordVectors;
    private String pubDocNumber;
    private Integer pubDate;

    public PatentVectors(String pubDocNumber, Integer pubDate) {
        this.pubDocNumber=pubDocNumber;
        this.pubDate=pubDate;
    }

    public void setTitleWordVectors(Double[] v) {
        titleWordVectors=v;
    }

    public void setAbstractWordVectors(Double[] v) { abstractWordVectors=v; }

    public void setDescriptionWordVectors(Double[] v) {
        descriptionWordVectors=v;
    }

    public Double[] getTitleWordVectors() { return titleWordVectors; }

    public Double[] getAbstractWordVectors() {
        return abstractWordVectors;
    }

    public Double[] getDescriptionWordVectors() {
        return descriptionWordVectors;
    }

    public String getPubDocNumber() {
        return pubDocNumber;
    }

    public Integer getPubDate() {
        return pubDate;
    }

    public boolean isValid() {
        return ((descriptionWordVectors!=null || abstractWordVectors!=null || titleWordVectors!=null) && pubDate!=null && pubDocNumber!=null);
    }

}
