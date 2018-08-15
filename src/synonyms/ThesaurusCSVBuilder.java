package synonyms;

import com.opencsv.CSVReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

public class ThesaurusCSVBuilder {
    private static Map<String,WordSynonym> loadWordSynonyms() throws Exception {
        CSVReader reader = new CSVReader(new BufferedReader(new FileReader(new File("thesaurus.csv"))));

        Iterator<String[]> iterator = reader.iterator();
        // skip first line
        iterator.next();
        String prevWord = null;
        int idx = 0;
        Map<String,WordSynonym> wordSynonyms = new HashMap<>(100000);
        while(iterator.hasNext()) {
            String[] row = iterator.next();
            String word = row[0].trim().toLowerCase();
            if(prevWord != null && prevWord.equals(word)) {
                idx ++;
            } else {
                idx = 0;
            }
            prevWord = word;
            try {
                int numMeanings = Integer.valueOf(row[1].trim());
                if(idx >= numMeanings) {
                    throw new IllegalStateException("Meaning idx is >= to num meanings");
                }
                String partOfSpeech = row[2].trim().toLowerCase();
                //System.out.println(word + ", " + idx + ", " + partOfSpeech);
                WordSynonym wordSynonym = new WordSynonym(word, partOfSpeech, idx);
                Set<String> genericTerms = new HashSet<>();
                Set<String> similarTerms = new HashSet<>();
                for (int i = 3; i < row.length; i++) {
                    String term = row[i].trim().toLowerCase();
                    String termType;
                    if (term.contains("(")) {
                        termType = term.substring(term.indexOf("(") + 1, term.length() - 1).trim();
                        term = term.substring(0, term.indexOf("(")).trim();
                    } else {
                        termType = "similar term";
                    }
                    if(termType.contains("antonym")) continue;
                    //System.out.println("   " + term + " - " + termType);
                    if(termType.contains("similar")) {
                        similarTerms.add(term);
                    } else {
                        genericTerms.add(term);
                    }
                }
                if(similarTerms.isEmpty()) {
                    for(String term : genericTerms) {
                        wordSynonym.addSynonym(term);
                    }
                } else {
                    for(String term : similarTerms) {
                        wordSynonym.addSynonym(term);
                    }
                }
                wordSynonyms.put(word+"_"+idx, wordSynonym);
            } catch(Exception e) {
                //e.printStackTrace();
                //System.out.println("Failing row: "+String.join(", ", row));
            }

        }

        System.out.println("Num found: "+wordSynonyms.size());
        reader.close();
        return wordSynonyms;
    }

    private static Map<String,WordSynonym> wordSynonymsMap;
    public ThesaurusCSVBuilder() {
        synchronized (ThesaurusCSVBuilder.class) {
            if (wordSynonymsMap == null) {
                try {
                    wordSynonymsMap = loadWordSynonyms();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private List<WordSynonym> allMeaningsOfWord(String word) {
        List<WordSynonym> results = new ArrayList<>();
        int idx = 0;
        while(wordSynonymsMap.containsKey(word+"_"+idx)) {
            results.add(wordSynonymsMap.get(word+"_"+idx));
            idx++;
        }
        return results;
    }


    public static void main(String[] args) throws Exception {
        ThesaurusCSVBuilder thesaurus = new ThesaurusCSVBuilder();
        for(WordSynonym word: thesaurus.allMeaningsOfWord("patent")) {
            System.out.println("Similar word: "+String.join("; ", word.getSynonyms()));
        }

    }
}
