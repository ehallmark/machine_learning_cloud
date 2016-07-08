package seeding;

import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

public class GenerateCitationTableHelper {
    private static DefaultTokenizerFactory tokenizerFactory;
    // Check for paragraphVectors.obj file
    static {
        tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            Database.setupMainConn();
            DatabaseIterator iterator = new DatabaseIterator(false);
            ResultSet results = Database.selectCitationTitleAndAbstract(iterator.getPatents());
            int timeToCommit = 0;
            while(results.next()) {
                String pubDocNumber = results.getString(1);
                String citedPubDocNumber = results.getString(2);
                String citedTitle = results.getString(3);
                String citedAbstract = results.getString(4);
                Database.updateCitation(pubDocNumber,citedPubDocNumber,citedTitle,citedAbstract);
                timeToCommit=(timeToCommit+1)%1000;
                System.out.print(pubDocNumber+' '); System.out.println(citedPubDocNumber);
                if(timeToCommit==0) {
                    System.out.println("COMMIT!");
                    Database.commit();
                }
            }
            System.out.println("DONE!");
            Database.commit();
            Database.close();
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
}
