package dl4j_neural_nets.tools;

import dl4j_neural_nets.phrase_tokenization.PhraseDeterminator;
import org.apache.commons.lang.ArrayUtils;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.eclipse.jetty.util.ArrayUtil;

import java.util.Set;

/**
 * Created by ehallmark on 12/30/16.
 */
public class PhrasePreprocessor implements SentencePreProcessor {
    private static Set<String> phrases;
    static {
        try {
            phrases = PhraseDeterminator.load();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String preProcess(String sentence) {
        if(sentence==null)return "";
        String[] tokens = sentence.toLowerCase().replaceAll("[^a-z ]","").split("\\s+");
        int idx;
        tokens= ArrayUtil.removeNulls(tokens);
        while((idx = ArrayUtils.indexOf(tokens,""))>=0) {
            tokens = (String[])ArrayUtils.remove(tokens,idx);
        }

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
