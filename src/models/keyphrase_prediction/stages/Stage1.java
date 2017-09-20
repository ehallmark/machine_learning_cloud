package models.keyphrase_prediction.stages;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import elasticsearch.DataIngester;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import seeding.Constants;
import seeding.Database;
import tools.Stemmer;
import user_interface.ui_models.portfolios.items.Item;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/12/17.
 */
public class Stage1 extends Stage<Map<MultiStem,AtomicLong>> {
    private static Collection<String> validPOS = Arrays.asList("JJ","JJR","JJS","NN","NNS","NNP","NNPS","VBG","VBN");
    private static Collection<String> adjectivesPOS = Arrays.asList("JJ","JJR","JJS");

    private static final boolean debug = false;
    private int minTokenFrequency;
    private int maxTokenFrequency;
    public Stage1(int year, Model model) {
        super(model,year);
        this.minTokenFrequency=model.getMinTokenFrequency();
        this.maxTokenFrequency=model.getMaxTokenFrequency();
    }

    @Override
    public Map<MultiStem,AtomicLong> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            data = buildVocabularyCounts();
            data = truncateBetweenLengths(data, minTokenFrequency, maxTokenFrequency);
            Database.trySaveObject(data, getFile());
        } else {
            try {
                loadData();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return data;
    }

    private static Map<MultiStem,AtomicLong> truncateBetweenLengths(Map<MultiStem,AtomicLong> stemMap, int min, int max) {
        return stemMap.entrySet().parallelStream().filter(e->e.getValue().get()>=min&&e.getValue().get()<=max).collect(Collectors.toMap(e->e.getKey(), e->e.getValue()));
    }

    private Map<MultiStem,AtomicLong> buildVocabularyCounts() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        Map<MultiStem,AtomicLong> multiStemMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Map<String,AtomicInteger>> stemToPhraseCountMap = Collections.synchronizedMap(new HashMap<>());

        AtomicLong cnt = new AtomicLong(0);
        Function<SearchHit,Item> transformer = hit-> {
            String asset = hit.getId();
            String inventionTitle = hit.getSourceAsMap().getOrDefault(Constants.INVENTION_TITLE, "").toString().toLowerCase();
            String abstractText = hit.getSourceAsMap().getOrDefault(Constants.ABSTRACT, "").toString().toLowerCase();
           // SearchHits innerHits = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
           // Object dateObj = innerHits == null ? null : (innerHits.getHits()[0].getSourceAsMap().get(Constants.FILING_DATE));
           // LocalDate date = dateObj == null ? null : (LocalDate.parse(dateObj.toString(), DateTimeFormatter.ISO_DATE));
            String text = String.join(". ", Stream.of(inventionTitle,abstractText).filter(t->t!=null&&t.length()>0).collect(Collectors.toList())).replaceAll("[^a-z .,]"," ");

            Annotation doc = new Annotation(text);
            if(cnt.getAndIncrement() % 10000 == 9999) {
                System.out.println("Num distinct multistems: "+multiStemMap.size());
            }
            pipeline.annotate(doc, d -> {
                if(debug) System.out.println("Text: "+text);
                List<CoreMap> sentences = d.get(CoreAnnotations.SentencesAnnotation.class);
                for(CoreMap sentence: sentences) {
                    // traversing the words in the current sentence
                    // a CoreLabel is a CoreMap with additional token-specific methods
                    String prevStem = null;
                    String prevPrevStem = null;
                    String prevWord = null;
                    String prevPrevWord = null;
                    for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                        // this is the text of the token
                        String word = token.get(CoreAnnotations.TextAnnotation.class);
                        // could be the stem
                        String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);

                        if(Constants.STOP_WORD_SET.contains(lemma)||Constants.STOP_WORD_SET.contains(word)) {
                            prevPrevStem=null;
                            prevStem=null;
                            prevWord=null;
                            prevPrevWord=null;
                            continue;
                        }

                        try {
                            String stem = new Stemmer().stem(lemma);
                            if (stem.length() > 3 && !Constants.STOP_WORD_SET.contains(stem)) {
                                // this is the POS tag of the token
                                String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                                if (validPOS.contains(pos)) {
                                    // don't want to end in adjectives (nor past tense verb)
                                    if (!adjectivesPOS.contains(pos) && !pos.equals("VBD")) {
                                        multiStemChecker(new String[]{stem}, multiStemMap, word, stemToPhraseCountMap);
                                        if (prevStem != null) {
                                            multiStemChecker(new String[]{prevStem, stem}, multiStemMap, String.join(" ", prevWord, word), stemToPhraseCountMap);
                                            if (prevPrevStem != null) {
                                                multiStemChecker(new String[]{prevPrevStem, prevStem, stem}, multiStemMap, String.join(" ", prevPrevWord, prevWord, word), stemToPhraseCountMap);
                                            }
                                        }
                                    }
                                } else {
                                    stem = null;
                                }
                            } else {
                                stem = null;
                            }
                            prevPrevStem = prevStem;
                            prevStem = stem;
                            prevPrevWord = prevWord;
                            prevWord = word;

                        } catch(Exception e) {
                            System.out.println("Error while stemming: "+lemma);
                            prevStem = null;
                            prevPrevStem = null;
                            prevWord=null;
                            prevPrevWord=null;
                        }
                    }
                }
            });

            return null;
        };
        KeywordModelRunner.streamElasticSearchData(year, transformer,200000);
        System.out.println("Starting to find best phrases for each stemmed phrase.");
        new ArrayList<>(multiStemMap.keySet()).parallelStream().forEach(stem->{
            String stemStr = stem.toString();
            if(stemToPhraseCountMap.containsKey(stemStr)) {
                // extract most common representation of the stem
                String bestPhrase = stemToPhraseCountMap.get(stemStr).entrySet().stream().sorted((e1,e2)->Integer.compare(e2.getValue().get(),e1.getValue().get())).map(e->e.getKey()).findFirst().orElse(null);
                if(bestPhrase!=null) {
                    stem.setBestPhrase(bestPhrase);
                } else {
                    multiStemMap.remove(stem);
                }
                if(debug) System.out.println("Best phrase for "+stemStr+": "+bestPhrase);
            }
        });
        return multiStemMap;
    }

    private static void multiStemChecker(String[] stems, Map<MultiStem,AtomicLong> multiStems, String label, Map<String,Map<String,AtomicInteger>> phraseCountMap) {
        MultiStem multiStem = new MultiStem(stems, multiStems.size());
        String stemPhrase = multiStem.toString();
        phraseCountMap.putIfAbsent(stemPhrase,Collections.synchronizedMap(new HashMap<>()));
        Map<String,AtomicInteger> innerMap = phraseCountMap.get(stemPhrase);
        innerMap.putIfAbsent(label,new AtomicInteger(0));
        innerMap.get(label).getAndIncrement();
        synchronized (multiStems) {
            AtomicLong currentCount = multiStems.get(multiStem);
            if(currentCount == null) {
                if (debug) System.out.println("Adding word " + multiStem.getIndex() + ": " + multiStem);
                multiStems.put(multiStem, new AtomicLong(1L));
            } else {
                currentCount.getAndIncrement();
            }
        }
    }


}
