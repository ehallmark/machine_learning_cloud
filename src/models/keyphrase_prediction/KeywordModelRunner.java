package models.keyphrase_prediction;

import com.google.gson.Gson;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.keyphrase_prediction.scorers.KeywordScorer;
import models.keyphrase_prediction.scorers.TechnologyScorer;
import models.keyphrase_prediction.scorers.TermhoodScorer;
import models.keyphrase_prediction.scorers.UnithoodScorer;
import org.apache.lucene.analysis.util.StemmerUtil;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.join.query.HasChildQueryBuilder;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortOrder;
import org.mapdb.Atomic;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.Stemmer;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.portfolios.items.Item;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class KeywordModelRunner {
    public static final boolean debug = false;
    private static Collection<String> validPOS = Arrays.asList("JJ","JJR","JJS","NN","NNS","NNP","NNPS","VBG","VBN");
    public static final File keywordCountsFile = new File("data/keyword_model_counts_2010.jobj");
    private static final File stage2File = new File("data/keyword_model_keywords_set_stage2.jobj");
    private static final File stage3File = new File("data/keyword_model_keywords_set_stage3.jobj");
    private static final File stage4File = new File("data/keyword_model_keywords_set_stage4.jobj");
    public static void main(String[] args) {
        final long Kw = 5000;
        final int k1 = 20;
        final int k2 = 5;
        final int k3 = 1;
        final int minTokenFrequency = 50;
        final int maxTokenFrequency = 100000;

        int year = 2010;

        boolean stage1 = false;
        boolean stage2 = false;
        boolean stage3 = false;
        boolean rebuildMMatrix = false;
        boolean stage4 = true;
        boolean rebuildTMatrix = true;


        Map<MultiStem, AtomicLong> keywordsCounts;
        if(stage1) {
            keywordsCounts = buildVocabularyCounts(year);
            Database.trySaveObject(keywordsCounts, keywordCountsFile);
        } else {
            keywordsCounts = (Map<MultiStem,AtomicLong>)Database.loadObject(keywordCountsFile);
        }

        Collection<MultiStem> keywords;
        if(stage2) {
            // filter outliers
            keywordsCounts = truncateBetweenLengths(keywordsCounts, minTokenFrequency, maxTokenFrequency);
            keywords = new HashSet<>(keywordsCounts.keySet());

            reindex(keywords);

            // apply filter 1
            INDArray F = buildFMatrix(keywordsCounts);
            keywords = applyFilters(new UnithoodScorer(), F, keywords, Kw * k1, 0, Double.MAX_VALUE);
            Database.saveObject(keywords, stage2File);
            // write to csv for records
            writeToCSV(keywords,new File("data/keyword_model_stage2.csv"));
        } else {
            keywords = (Collection<MultiStem>)Database.loadObject(stage2File);
        }

        if(stage3) {
            // apply filter 2
            reindex(keywords);
            System.out.println("Num keywords before stage 3: "+keywords.size());
            INDArray M;
            if(rebuildMMatrix) {
                M = buildMMatrix(keywords, year);
                Database.trySaveObject(M, new File("data/keyword_m_matrix.jobj"));
            } else {
                M = (INDArray) Database.tryLoadObject(new File("data/keyword_m_matrix.jobj"));
            }
            keywords = applyFilters(new TermhoodScorer(), M, keywords, Kw * k2, 0, Double.MAX_VALUE);
            System.out.println("Num keywords after stage 3: "+keywords.size());

            Database.saveObject(keywords, stage3File);
            // write to csv for records
            writeToCSV(keywords,new File("data/keyword_model_stage3.csv"));
        } else {
            keywords = (Collection<MultiStem>)Database.loadObject(stage3File);
        }


        if(stage4) {
            // apply filter 3
            reindex(keywords);
            INDArray T;
            if(rebuildTMatrix) {
                T = buildTMatrix(keywords, year);
                Database.trySaveObject(T, new File("data/keyword_t_matrix.jobj"));
            } else {
                T = (INDArray) Database.loadObject(new File("data/keyword_t_matrix.jobj"));
            }
            keywords = applyFilters(new TechnologyScorer(), T, keywords, Kw * k3, 0, Double.MAX_VALUE);
            Database.saveObject(keywords, stage4File);
            // write to csv for records
            writeToCSV(keywords,new File("data/keyword_model_stage4.csv"));
        } else {
            keywords = (Collection<MultiStem>)Database.loadObject(stage4File);
        }
    }

    private static void reindex(Collection<MultiStem> multiStems) {
        AtomicInteger cnt = new AtomicInteger(0);
        multiStems.parallelStream().forEach(multiStem -> {
            multiStem.setIndex(cnt.getAndIncrement());
        });
    }

    private static Collection<MultiStem> applyFilters(KeywordScorer scorer, INDArray matrix, Collection<MultiStem> keywords, long targetNumToKeep, double minThreshold, double maxThreshold) {
        return scorer.scoreKeywords(keywords,matrix).entrySet().stream().filter(e->e.getValue()>=minThreshold&&e.getValue()<=maxThreshold).sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                .limit(targetNumToKeep)
                .map(e->{
                    if(debug) {
                        System.out.println("Value for "+e.getKey().toString()+": "+e.getValue());
                    }
                    return e.getKey();
                })
                .collect(Collectors.toList());
    }

    private static INDArray buildFMatrix(Map<MultiStem,AtomicLong> multiStemMap) {
        INDArray array = Nd4j.create(multiStemMap.size());
        multiStemMap.entrySet().parallelStream().forEach(e->{
            array.putScalar(e.getKey().getIndex(),e.getValue().get());
        });
        return array;
    }

    private static Map<MultiStem,AtomicLong> truncateBetweenLengths(Map<MultiStem,AtomicLong> stemMap, int min, int max) {
        return stemMap.entrySet().parallelStream().filter(e->e.getValue().get()>=min&&e.getValue().get()<=max).collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));
    }

    private static INDArray buildTMatrix(Collection<MultiStem> multiStems, int year) {
        // create cpc code co-occurrrence statistics
        final int maxCpcLength = 8;
        List<String> allCpcCodes = Database.getClassCodes().stream().map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).distinct().collect(Collectors.toList());
        System.out.println("Num cpc codes found: "+allCpcCodes.size());
        Map<String,Integer> cpcCodeIndexMap = new HashMap<>();
        AtomicInteger idx = new AtomicInteger(0);
        allCpcCodes.forEach(cpc->cpcCodeIndexMap.put(cpc,idx.getAndIncrement()));

        double[][] matrix = new double[multiStems.size()][allCpcCodes.size()];
        Object[][] locks = new Object[multiStems.size()][allCpcCodes.size()];
        for(int i = 0; i < matrix.length; i++) {
            matrix[i] = new double[multiStems.size()];
            locks[i] = new Object[multiStems.size()];
            for(int j = 0; j < allCpcCodes.size(); j++) {
                matrix[i][j] = 0d;
                locks[i][j] = new Object();
            }
        }

        final AssetToCPCMap assetToCPCMap = new AssetToCPCMap();
        Function<SearchHit,Item> transformer = hit-> {
            String asset = hit.getId();

            Collection<String> currentCpcs = assetToCPCMap.getApplicationDataMap().getOrDefault(asset,assetToCPCMap.getPatentDataMap().get(asset));
            if(currentCpcs==null||currentCpcs.isEmpty()) return null;

            int[] cpcIndices = currentCpcs.stream().map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).map(cpc->cpcCodeIndexMap.get(cpc)).filter(cpc->cpc!=null).mapToInt(i->i).toArray();
            if(cpcIndices==null||cpcIndices.length==0) return null;

            String inventionTitle = hit.getSourceAsMap().getOrDefault(Constants.INVENTION_TITLE, "").toString().toLowerCase();
            String abstractText = hit.getSourceAsMap().getOrDefault(Constants.ABSTRACT, "").toString().toLowerCase();
            SearchHits innerHits = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
            Object dateObj = innerHits == null ? null : (innerHits.getHits()[0].getSourceAsMap().get(Constants.FILING_DATE));
            LocalDate date = dateObj == null ? null : (LocalDate.parse(dateObj.toString(), DateTimeFormatter.ISO_DATE));
            String text = String.join(". ", Stream.of(inventionTitle, abstractText).filter(t -> t != null && t.length() > 0).collect(Collectors.toList())).replaceAll("[^a-z .,]", " ");

            Collection<MultiStem> documentStems = new HashSet<>();

            if(debug) System.out.println("Text: "+text);
            String prevWord = null;
            String prevPrevWord = null;
            for (String word: text.split("\\s+")) {
                word = word.replace(".","").replace(",","").trim();
                // this is the text of the token

                String lemma = word; // no lemmatizer
                if(Constants.STOP_WORD_SET.contains(lemma)) {
                    continue;
                }

                try {
                    String stem = new Stemmer().stem(lemma);
                    if (stem.length() > 3 && !Constants.STOP_WORD_SET.contains(stem)) {
                        // this is the POS tag of the token
                        documentStems.add(new MultiStem(new String[]{stem},-1));
                        if(prevWord != null) {
                            documentStems.add(new MultiStem(new String[]{prevWord,stem},-1));
                            if (prevPrevWord != null) {
                                documentStems.add(new MultiStem(new String[]{prevPrevWord,prevWord,stem},-1));
                            }
                        }
                    } else {
                        stem = null;
                    }
                    prevPrevWord = prevWord;
                    prevWord = stem;

                } catch(Exception e) {
                    System.out.println("Error while stemming: "+lemma);
                    prevWord = null;
                    prevPrevWord = null;
                }
            }

            Collection<MultiStem> cooccurringStems = Collections.synchronizedCollection(new ArrayList<>());
            multiStems.parallelStream().forEach(stem->{
                if(documentStems.contains(stem)) {
                    cooccurringStems.add(stem);
                }
            });

            if(debug)
                System.out.println("Num coocurrences: "+cooccurringStems.size());

            for(MultiStem stem : cooccurringStems) {
                double[] row = matrix[stem.index];
                Object[] lockRow = locks[stem.index];
                for (int cpcIdx : cpcIndices) {
                    synchronized(lockRow[cpcIdx]) {
                        row[cpcIdx]++;
                    }
                }
            }

            return null;
        };

        streamElasticSearchData(year, transformer);
        return Nd4j.create(matrix);
    }

    private static INDArray buildMMatrix(Collection<MultiStem> multiStems, int year) {
        // create co-occurrrence statistics
        double[][] matrix = new double[multiStems.size()][multiStems.size()];
        Object[][] locks = new Object[multiStems.size()][multiStems.size()];
        for(int i = 0; i < matrix.length; i++) {
            matrix[i] = new double[multiStems.size()];
            locks[i] = new Object[multiStems.size()];
            for(int j = 0; j < multiStems.size(); j++) {
                matrix[i][j] = 0d;
                locks[i][j] = new Object();
            }
        }


        AtomicLong cnt = new AtomicLong(0);
        Function<SearchHit,Item> transformer = hit-> {
            String asset = hit.getId();
            String inventionTitle = hit.getSourceAsMap().getOrDefault(Constants.INVENTION_TITLE, "").toString().toLowerCase();
            String abstractText = hit.getSourceAsMap().getOrDefault(Constants.ABSTRACT, "").toString().toLowerCase();
            SearchHits innerHits = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
            Object dateObj = innerHits == null ? null : (innerHits.getHits()[0].getSourceAsMap().get(Constants.FILING_DATE));
            LocalDate date = dateObj == null ? null : (LocalDate.parse(dateObj.toString(), DateTimeFormatter.ISO_DATE));
            String text = String.join(". ", Stream.of(inventionTitle, abstractText).filter(t -> t != null && t.length() > 0).collect(Collectors.toList())).replaceAll("[^a-z .,]", " ");

            Collection<MultiStem> documentStems = new HashSet<>();

            if(debug) System.out.println("Text: "+text);
            String prevWord = null;
            String prevPrevWord = null;
            for (String word: text.split("\\s+")) {
                word = word.replace(".","").replace(",","").trim();
                // this is the text of the token

                String lemma = word; // no lemmatizer
                if(Constants.STOP_WORD_SET.contains(lemma)) {
                    continue;
                }

                try {
                    String stem = new Stemmer().stem(lemma);
                    if (stem.length() > 3 && !Constants.STOP_WORD_SET.contains(stem)) {
                        // this is the POS tag of the token
                        documentStems.add(new MultiStem(new String[]{stem},-1));
                        if(prevWord != null) {
                            documentStems.add(new MultiStem(new String[]{prevWord,stem},-1));
                            if (prevPrevWord != null) {
                                documentStems.add(new MultiStem(new String[]{prevPrevWord,prevWord,stem},-1));
                            }
                        }
                    } else {
                        stem = null;
                    }
                    prevPrevWord = prevWord;
                    prevWord = stem;

                } catch(Exception e) {
                    System.out.println("Error while stemming: "+lemma);
                    prevWord = null;
                    prevPrevWord = null;
                }
            }

            Collection<MultiStem> cooccurringStems = Collections.synchronizedCollection(new ArrayList<>());
            multiStems.parallelStream().forEach(stem->{
                if(documentStems.contains(stem)) {
                    cooccurringStems.add(stem);
                }
            });

            if(debug)
                System.out.println("Num coocurrences: "+cooccurringStems.size());

            // Unavoidable n-squared part
            for(MultiStem stem1 : cooccurringStems) {
                double[] row = matrix[stem1.index];
                Object[] lockRow = locks[stem1.index];
                for (MultiStem stem2 : cooccurringStems) {
                    synchronized(lockRow[stem2.index]) {
                        row[stem2.index]++;
                    }
                }
            }

            return null;
        };

        streamElasticSearchData(year, transformer);
        return Nd4j.create(matrix);
    }

    private static Map<MultiStem,AtomicLong> buildVocabularyCounts(int year) {
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
            SearchHits innerHits = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
            Object dateObj = innerHits == null ? null : (innerHits.getHits()[0].getSourceAsMap().get(Constants.FILING_DATE));
            LocalDate date = dateObj == null ? null : (LocalDate.parse(dateObj.toString(), DateTimeFormatter.ISO_DATE));
            String text = String.join(". ",Stream.of(inventionTitle,abstractText).filter(t->t!=null&&t.length()>0).collect(Collectors.toList())).replaceAll("[^a-z .,]"," ");

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
                                    multiStemChecker(new String[]{stem},multiStemMap, word, stemToPhraseCountMap);
                                    if(prevStem != null) {
                                        multiStemChecker(new String[]{prevStem,stem},multiStemMap, String.join(" ", prevWord, word), stemToPhraseCountMap);
                                        if(prevPrevStem != null) {
                                            multiStemChecker(new String[]{prevPrevStem,prevStem,stem},multiStemMap, String.join(" ", prevPrevWord, prevWord, word), stemToPhraseCountMap);
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
        streamElasticSearchData(year, transformer);
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


    private static void streamElasticSearchData(int year, Function<SearchHit,Item> transformer) {
        LocalDate dateMin = LocalDate.of(year,1,1);
        LocalDate dateMax = dateMin.plusYears(1);
        TransportClient client = DataSearcher.getClient();
        SearchRequestBuilder search = client.prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.TYPE_NAME)
                //.addSort(Constants.FILING_DATE, SortOrder.ASC)
                .setScroll(new TimeValue(60000))
                .setExplain(false)
                .setFrom(0)
                .setSize(10000)
                .setFetchSource(new String[]{Constants.ABSTRACT,Constants.INVENTION_TITLE},new String[]{})
                .setQuery(new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME, QueryBuilders.boolQuery().filter(QueryBuilders.rangeQuery(Constants.FILING_DATE).gte(dateMin.toString()).lt(dateMax.toString())),true).innerHit(
                        new InnerHitBuilder().setSize(1).setFetchSourceContext(new FetchSourceContext(true, new String[]{Constants.FILING_DATE}, new String[]{}))
                ));
        if(debug) {
            System.out.println(search.request().toString());
        }

        SearchResponse response = search.get();
        DataSearcher.iterateOverSearchResults(response, transformer, -1, false);
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
                if (debug) System.out.println("Adding word " + multiStem.index + ": " + multiStem);
                multiStems.put(multiStem, new AtomicLong(1L));
            } else {
                currentCount.getAndIncrement();
            }
        }
    }

    private static void writeToCSV(Collection<MultiStem> multiStems, File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Key Phrase\n");
            multiStems.forEach(e->{
                try {
                    writer.write(e.toString()+"\n");
                }catch(Exception e2) {
                    e2.printStackTrace();
                }
            });
            writer.flush();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
