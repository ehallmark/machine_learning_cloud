package ui_models.attributes.value.test;

import seeding.Database;
import ui_models.attributes.value.PageRankEvaluator;
import ui_models.attributes.value.ValueAttr;

import java.util.Collection;

/**
 * Created by ehallmark on 5/15/17.
 */
public class TestValueAttr {
    public static void main(String[] args) {
        ValueAttr attr = new PageRankEvaluator();
        Collection<String> patents = Database.getValuablePatents();
        patents.forEach(patent->{
            System.out.print("Patent "+patent+": ");
            System.out.println(attr.evaluate(patent));
        });
    }
}
