package models.similarity_models;

import org.nd4j.linalg.api.ndarray.INDArray;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Collection;

/**
 * Created by ehallmark on 5/9/17.
 */
public interface AbstractSimilarityModel {
    int numItems();
    Item[] getItemList();
}
