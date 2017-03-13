package tools;

import seeding.Database;
import server.tools.AbstractAssignee;
import server.tools.AbstractClassCode;
import server.tools.AbstractPatent;
import server.tools.excel.ExcelHandler;
import server.tools.excel.ExcelWritable;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PortfolioList implements Serializable, Comparable<PortfolioList> {
    private static final long serialVersionUID = 1L;
    private static Map<String,Double> colWidthsMap;
    private List<ExcelWritable> portfolio;
    private Type portfolioType;
    private double avgSimilarity;
    private List<String> attributes;
    public enum Type { patents, assignees, class_codes }

    static {
        colWidthsMap=new HashMap<>();
        colWidthsMap.put("title",75d);
        colWidthsMap.put("assignee",50d);
        colWidthsMap.put("similarity",25d);
    }

    public static PortfolioList abstractPorfolioList(Collection<String> labels, PortfolioList.Type type) {
        switch(type) {
            case assignees: {
                return new PortfolioList(labels.stream().map(label->new AbstractAssignee(label,0d,null)).collect(Collectors.toList()),type);
            } case class_codes: {
                return new PortfolioList(labels.stream().map(label->new AbstractClassCode(label,0d,null)).collect(Collectors.toList()),type);
            } case patents: {
                return new PortfolioList(labels.stream().map(label->new AbstractPatent(label,0d,null)).collect(Collectors.toList()),type);
            }default: {
                return null;
            }
        }
    }
    //
    public PortfolioList(List<ExcelWritable> portfolioList, Type portfolioType) {
        this.portfolioType=portfolioType;
        this.portfolio=portfolioList;
    }

    public void setAttributes(List<String> attrs) {
        this.attributes=attrs;
    }

    public List<String> getAttributes() { return attributes; }

    @Override
    public int compareTo(PortfolioList o) {
        return Double.compare(o.avgSimilarity,avgSimilarity);
    }

    public List<ExcelWritable> getPortfolio() {
        return portfolio;
    }

    public int[] getColWidths(List<String> attributes) {
        int[] colWidths = new int[attributes.size()];
        for(int i = 0; i < attributes.size(); i++) {
            String attr = attributes.get(i);
            if(attr!=null&&colWidthsMap.containsKey(attr)) {
                colWidths[i] = colWidthsMap.get(attr).intValue();
            } else {
                colWidths[i]= (int)ExcelHandler.CELL_DEFAULT_WIDTH;
            }
        }
        return colWidths;
    }

    public String getSheetName() {
        String sheetName="- List";
        switch(portfolioType) {
            case patents: {
                sheetName= "- Patent List";
                break;
            } case assignees: {
                sheetName= "- Assignee List";
                break;
            } case class_codes: {
                sheetName= "- Classification List";
                break;
            }
        }
        return sheetName;
    }

    public String getSheetTitle() { return getSheetName()+" ("+portfolio.size()+" results)"; }

    public void setPortfolio(List<ExcelWritable> portfolio) {
        this.portfolio=portfolio;
    }

    public List<String> getPortfolioAsStrings() {
        return portfolio.stream().map(p->p.getName()).collect(Collectors.toList());
    }

    public void filterPortfolioSize(int limit) {
        portfolio=portfolio.stream()
                .filter(obj->{
                    if(obj instanceof AbstractPatent) {
                        return Database.getAssetCountFor(((AbstractPatent)obj).getAssignee()) <= limit;
                    } else if (obj instanceof AbstractAssignee) {
                        return Database.getAssetCountFor(obj.getName()) <= limit;
                    } else {
                        return Database.selectPatentNumbersFromClassAndSubclassCodes(obj.getName()).size() <= limit;
                    }
                }).collect(Collectors.toList());
    }

    public void init(Comparator<ExcelWritable> comparator, int limit) {
        if(portfolioType.equals(Type.patents))
        portfolio = portfolio.stream().filter(p->(!portfolioType.equals(Type.patents))||(!Database.isExpired(p.getName()))).sorted(comparator.reversed()).limit(limit).collect(Collectors.toList());
        if (portfolio.size() > 0) {
            this.avgSimilarity = portfolio.stream().collect(Collectors.averagingDouble(obj -> obj.getSimilarity()));
        } else this.avgSimilarity = 0.0d;
    }
}
