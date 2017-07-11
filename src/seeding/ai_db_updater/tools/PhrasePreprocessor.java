package seeding.ai_db_updater.tools;
import org.apache.commons.lang3.ArrayUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Set;

/**
 * Created by ehallmark on 12/30/16.
 */
public class PhrasePreprocessor  {
    private static File file = new File("phrases_set.obj");
    private static Set<String> phrases;
    static {
        try {
            if(file.exists()) {
                ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
                phrases = (Set<String>) ois.readObject();
                ois.close();
            }else {
                System.out.println("---------------------------------------------------------");
                System.out.println("------------------------ WARNING ------------------------");
                System.out.println("------- NO PHRASES LOADED FOR PHRASE TOKENIZATION -------");
                System.out.println("----------- DEFAULTING TO SIMPLE WORD TOKENS ------------");
                System.out.println("---------------------------------------------------------");
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public String preProcess(String sentence) {
        if(phrases==null) return sentence; // no preprocessing

        if(sentence==null)return "";
        String[] tokens = sentence.toLowerCase().replaceAll("[^a-z ]","").split("\\s+");
        tokens=ArrayUtils.removeAllOccurences(tokens,null);
        tokens=ArrayUtils.removeAllOccurences(tokens,"");
        if(tokens.length < 3) return String.join(" ",tokens);

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
        if(tokens.length == 0) return "";
        return String.join(" ", tokens);
    }

}
