package synonyms;

import com.opencsv.CSVReader;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;
import seeding.google.word2vec.Word2VecManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

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

    public List<String> synonymsFor(String word, String... contextWords) {
        if (word == null) return Collections.emptyList();
        word = word.toLowerCase();
        Word2Vec model = Word2VecManager.getOrLoadManager();
        INDArray contextVector;
        if (contextWords.length > 0) {
            INDArray vec = model.getWordVectors(Arrays.asList(contextWords));
            if (vec.shape()[0] > 0) {
                contextVector = Transforms.unitVec(vec.sum(0));
            } else {
                contextVector = null;
            }
        } else {
            contextVector = null;
        }
        List<WordSynonym> wordSynonyms = allMeaningsOfWord(word);
        if (wordSynonyms.isEmpty()) {
            if ((word.endsWith("es") || word.endsWith("ed")) && word.length() > 3 && word.substring(word.length() - 3, word.length() - 2).replaceAll("[aeiou ]", "").length() == 1) {
                wordSynonyms = allMeaningsOfWord(word.substring(0, word.length() - 2));
            } else if (word.endsWith("s")) {
                wordSynonyms = allMeaningsOfWord(word.substring(0, word.length() - 1));
            }
        }
        // sort word synonyms by overall similarity to the context vector
        WordSynonym bestSynonym = wordSynonyms.stream().map(s -> {
            double sim;
            if (s.getSynonyms().size() > 0 && contextVector != null) {
                INDArray vectors = model.getWordVectors(s.getSynonyms());
                if (vectors.shape()[0] > 0) {
                    vectors = Transforms.unitVec(vectors.sum(0));
                    sim = Transforms.cosineSim(vectors, contextVector);
                } else {
                    sim = 0d;
                }
            } else {
                sim = 0d;
            }
            return new Pair<>(s, sim);
        }).max((e1, e2) -> e1.getSecond().compareTo(e2.getSecond())).map(e -> e.getFirst()).orElse(null);
        if (bestSynonym == null) {
            return Collections.emptyList();
        } else {
            return bestSynonym.getSynonyms().stream().collect(Collectors.toList());
        }
    }

    public static void main(String[] args) throws Exception {
        ThesaurusCSVBuilder thesaurus = new ThesaurusCSVBuilder();
        for(String word: Arrays.asList("patent", "invention", "novel", "wifi", "inventions", "patented")) {
            System.out.println("Similar to "+word+": "+String.join("; ", thesaurus.synonymsFor(word)));
        }

    }
}
