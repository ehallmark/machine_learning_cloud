package tools;

import seeding.Database;
import server.tools.AbstractAssignee;
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
    private String name1;
    private String name2;
    private Type portfolioType;
    private double avgSimilarity;
    public enum Type { patents, assignees, class_codes }

    static {
        colWidthsMap=new HashMap<>();
    }
    //
    public PortfolioList(List<ExcelWritable> portfolioList, String name1, String name2, Type portfolioType) {
        this.name1=name1;
        this.name2=name2;
        this.portfolioType=portfolioType;
        this.portfolio=portfolioList;
    }

    public void flipAvgSimilarity() {
        avgSimilarity=avgSimilarity*-1.0;
    }

    public double getAvgSimilarity() {
        return avgSimilarity;
    }

    @Override
    public int compareTo(PortfolioList o) {
        return Double.compare(o.avgSimilarity,avgSimilarity);
    }

    public String getName1() {
        return name1;
    }

    public String getName2() {
        return name2;
    }

    public List<ExcelWritable> getPortfolio() {
        return portfolio;
    }

    public int[] getColWidths(List<String> attributes) {
        int[] colWidths = new int[attributes.size()];
        for(int i = 0; i < attributes.size(); i++) {
            String attr = attributes.get(i);
            if(attr!=null&&colWidthsMap.containsKey(attr)) {
                colWidths[i] = (int) ((double)colWidthsMap.get(attr));
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
                sheetName= "- AbstractAssignee List";
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

    public void filterPortfolioSize(int limit, boolean isPatent) {
        portfolio=portfolio.stream()
                .filter(obj->{
                    if(obj instanceof AbstractPatent) {
                        return Database.getAssetCountFor(((AbstractPatent)obj).getAssignee()) <= limit;
                    } else if (obj instanceof AbstractAssignee) {
                        return Database.getAssetCountFor(obj.getName()) <= limit;
                    } else {
                        return Database.selectPatentNumbersFromClassCode(obj.getName()).size() <= limit;
                    }
                }).collect(Collectors.toList());
    }

    public void init() {
        portfolio=portfolio.stream().filter(obj->!Database.isExpired(obj.getName())).collect(Collectors.toList());
        Collections.sort(portfolio);
        Collections.reverse(portfolio);

        if(portfolio.size()>0) {
            this.avgSimilarity = portfolio.stream().collect(Collectors.averagingDouble(p -> (Double)p.getAttributeValue("similarity")));
        }
        else this.avgSimilarity=0.0d;
    }
}
