package ui_models.filters;

import ui_models.portfolios.items.Item;

/**
 * Created by ehallmark on 5/10/17.
 */
public class ThresholdFilter implements AbstractFilter {
    private double threshold;
    public ThresholdFilter(double threshold) {
        this.threshold=threshold;
    }
    @Override
    public boolean shouldKeepItem(Item item) {
        return item.getSimilarity()>threshold;
    }
}
