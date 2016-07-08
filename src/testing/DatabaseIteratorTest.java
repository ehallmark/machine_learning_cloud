package testing;

import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import seeding.DatabaseIterator;

import java.util.Set;

/**
 * Created by ehallmark on 6/28/16.
 */
public class DatabaseIteratorTest {

    public DatabaseIteratorTest() throws Exception {
        DatabaseIterator iterator = new DatabaseIterator(false);
        DatabaseIterator unClassifiedIterator = new DatabaseIterator(true);
        Set<String> set = iterator.getPatents();
        int initialSize = set.size();
        System.out.println("Number of training patents: "+iterator.numPatents());
        System.out.println("Number of testing patents: "+unClassifiedIterator.numPatents());
        System.out.println("Number of training technologies: "+iterator.numTechnologies());
        System.out.println("Number of testing technologies: "+unClassifiedIterator.numTechnologies());
        if(set.removeAll(unClassifiedIterator.getPatents())) {
            System.out.println("FAILED!!!!");
            System.out.println("DUPLICATES: "+(initialSize-set.size()));
        } else {
            System.out.println("PASSED!!!");
        }

    }

    public static void main(String[] args) {
       try {
           new DatabaseIteratorTest();
       } catch(Exception e) {
           e.printStackTrace();
       }

    }
}
