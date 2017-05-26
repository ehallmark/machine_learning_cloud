package ui_models.portfolios;

import seeding.Database;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.items.AbstractAssignee;
import ui_models.portfolios.items.AbstractPatent;
import excel.ExcelHandler;
import ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PortfolioList implements AbstractPortfolio, Comparable<PortfolioList> {
    private static Map<String,Double> colWidthsMap;
    private List<Item> portfolio;
    private Type portfolioType;
    private double avgSimilarity;
    private List<String> attributes;
    public enum Type { patents, assignees }

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
            } case patents: {
                return new PortfolioList(labels.stream().map(label->new AbstractPatent(label,0d,null)).collect(Collectors.toList()),type);
            }default: {
                return null;
            }
        }
    }

    public static PortfolioList asList(String label, PortfolioList.Type type) {
        return abstractPorfolioList(Arrays.asList(label),type);
    }

    //
    public PortfolioList(List<Item> portfolioList, Type portfolioType) {
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

    public List<Item> getPortfolio() {
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
            }
        }
        return sheetName;
    }

    @Override
    public PortfolioList merge(AbstractPortfolio other, int totalLimit) {
        Map<String, Item> map = new HashMap<>();
        Arrays.asList(this,(PortfolioList)other).forEach(portfolioList->{
            portfolioList.getPortfolio().forEach(item -> {
                if (item.getName() == null || item.getName().length() == 0) return;
                Item itemInMap = map.get(item.getName());
                if (itemInMap!=null) {
                    itemInMap.appendTags(item.getTags());
                    itemInMap.setSimilarity(Math.max(item.getSimilarity(), itemInMap.getSimilarity()));
                } else {
                    map.put(item.getName(), item);
                }
            });
        });
        List<Item> merged = map.values().stream().sorted(Comparator.reverseOrder()).limit(totalLimit).collect(Collectors.toList());
        PortfolioList portfolio = new PortfolioList(merged,portfolioType);
        return portfolio;
    }

    public String getSheetTitle() { return getSheetName()+" ("+portfolio.size()+" results)"; }

    public void setPortfolio(List<Item> portfolio) {
        this.portfolio=portfolio;
    }

    public List<String> getTokens() {
        return portfolio.stream().map(p->p.getName()).collect(Collectors.toList());
    }

    public void applyFilter(AbstractFilter filter) {
        portfolio=portfolio.stream().filter(obj->filter.shouldKeepItem(obj)).collect(Collectors.toList());
    }

    public void init(Comparator<Item> comparator, int limit) {
        if(portfolioType.equals(Type.patents))
        portfolio = portfolio.stream().filter(p->(!portfolioType.equals(Type.patents))||(!Database.isExpired(p.getName()))).sorted(comparator.reversed()).limit(limit).collect(Collectors.toList());
        if (portfolio.size() > 0) {
            this.avgSimilarity = portfolio.stream().collect(Collectors.averagingDouble(obj -> obj.getSimilarity()));
        } else this.avgSimilarity = 0.0d;
    }
}
