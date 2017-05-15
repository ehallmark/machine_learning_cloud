package ui_models.attributes.value.test;

import graphical_models.page_rank.PageRank;
import graphical_models.page_rank.PageRankHelper;
import seeding.Database;
import ui_models.attributes.value.PageRankEvaluator;
import ui_models.attributes.value.ValueAttr;

import java.util.Collection;
import java.util.Map;

/**
 * Created by ehallmark on 5/15/17.
 */
public class TestValueAttr {
    public static void main(String[] args) {
        Map<String,Float> map = new PageRank.Loader().loadRankTable(PageRankHelper.file);
        map.forEach((k,v)->{
            System.out.println(k+": "+v);
        });

        Database.initializeDatabase();
        ValueAttr attr = new PageRankEvaluator();
        Collection<String> patents = Database.getValuablePatents();
        patents.forEach(patent->{
            System.out.print("Patent "+patent+": ");
            System.out.println(attr.evaluate(patent));
        });
    }
}
