package ui_models.portfolios;

import lombok.Getter;
import seeding.Database;
import ui_models.filters.AbstractFilter;
import excel.ExcelHandler;
import ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PortfolioList implements Comparable<PortfolioList> {
    @Getter
    private List<Item> portfolio;
    private double avgSimilarity;
    private List<String> attributes;
    public enum Type { patents, assignees }

    public PortfolioList(List<Item> portfolioList) {
        this.portfolio=portfolioList;
    }

    @Override
    public int compareTo(PortfolioList o) {
        return Double.compare(o.avgSimilarity,avgSimilarity);
    }

    public List<String> getTokens() {
        return portfolio.stream().map(p->p.getName()).collect(Collectors.toList());
    }

    public void applyFilter(AbstractFilter filter) {
        portfolio=portfolio.stream().filter(obj->filter.shouldKeepItem(obj)).collect(Collectors.toList());
    }

    public void init(Comparator<Item> comparator, int limit) {
        portfolio = portfolio.stream().sorted(comparator.reversed()).limit(limit).collect(Collectors.toList());
        if (portfolio.size() > 0) {
            this.avgSimilarity = portfolio.stream().collect(Collectors.averagingDouble(obj -> obj.getSimilarity()));
        } else this.avgSimilarity = 0.0d;
    }
}
