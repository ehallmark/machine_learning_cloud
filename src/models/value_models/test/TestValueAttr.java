package models.value_models.test;

import seeding.Database;
import models.value_models.PageRankEvaluator;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;

import java.util.Collection;

/**
 * Created by ehallmark on 5/15/17.
 */
public class TestValueAttr {
    public static void main(String[] args) {

        Database.initializeDatabase();
        ValueAttr attr = new PageRankEvaluator();
        Collection<String> patents = Database.getValuablePatents();
        patents.forEach(patent->{
            System.out.print("Patent "+patent+": ");
            System.out.println(attr.evaluate(patent));
        });
    }
}
