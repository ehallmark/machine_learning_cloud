package models.dl4j_neural_nets.phrase_tokenization;

import org.apache.commons.lang.ArrayUtils;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.eclipse.jetty.util.ArrayUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Set;

/**
 * Created by ehallmark on 12/18/16.
 */
public class SetupPatentSentenceTokensTable {
    private static final String patentDBUrl = "jdbc:postgresql://192.168.1.148/patentdb?user=postgres&password=&tcpKeepAlive=true";
    private static Set<String> phrases;

    private static void setupFromIterator(LabelAwareSentenceIterator iterator, String docType, Connection insertConn, PreparedStatement insertStatement) throws Exception {
        int cnt = 0;
        while(iterator.hasNext()) {
            String sentence = iterator.nextSentence();
            String label = iterator.currentLabel();

            String[] tokens = sentence.split("\\s+");
            int idx;
            tokens=ArrayUtil.removeNulls(tokens);
            while((idx = ArrayUtils.indexOf(tokens,""))>=0) {
                tokens = (String[])ArrayUtils.remove(tokens,idx);
            }

            if(tokens.length < 5) continue;

            if (!phrases.isEmpty()) {
                // check trigrams and bigrams
                for (int i = 0; i < tokens.length - 2; i++) {
                    String w1 = tokens[i];
                    String w2 = tokens[i + 1];
                    String w3 = tokens[i + 2];
                    // Check for existing phrase
                    if (phrases.contains(w1 + "_" + w2 + "_" + w3)) {
                        tokens = (String[])ArrayUtils.remove(tokens,i);
                        tokens = (String[])ArrayUtils.remove(tokens,i);
                        tokens[i]=w1 + "_" + w2 + "_" + w3;
                        i--;
                        continue;
                    } else if (phrases.contains(w1 + "_" + w2)) {
                        tokens = (String[])ArrayUtils.remove(tokens,i);
                        tokens[i]=w1 + "_" + w2;
                        i--;
                        continue;
                    }
                }
            }

            insertStatement.setString(1,label);
            insertStatement.setArray(2,insertConn.createArrayOf("varchar",tokens));
            insertStatement.setString(3,docType);
            insertStatement.executeUpdate();

            if(cnt%10000==0) {
                insertConn.commit();
                System.out.println(docType+ " sentences sequences so far: "+cnt);
            }
            cnt++;
        }
        insertConn.commit();
    }

    public static void main(String[] args) throws Exception {
        phrases = PhraseDeterminator.load();
        Connection insertConn = DriverManager.getConnection(patentDBUrl);
        insertConn.setAutoCommit(false);
        PreparedStatement insertStatement = insertConn.prepareStatement("insert into patent_sentence_tokens (pub_doc_number,tokens,doc_type) values (?,?,?)");

        /* LabelAwareSentenceIterator descriptionSentenceIterator = DatabaseIteratorFactory.DescriptionSentenceIterator();
        setupFromIterator(descriptionSentenceIterator,"description",insertConn,insertStatement);

        LabelAwareSentenceIterator claimSentenceIterator = DatabaseIteratorFactory.ClaimSentenceIterator();
        setupFromIterator(claimSentenceIterator,"claim",insertConn,insertStatement);


        LabelAwareSentenceIterator abstractSentenceIterator = DatabaseIteratorFactory.AbstractSentenceIterator();
        setupFromIterator(abstractSentenceIterator,"abstract",insertConn,insertStatement);

        */

        insertConn.close();
    }
}
