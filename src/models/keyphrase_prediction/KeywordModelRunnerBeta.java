package models.keyphrase_prediction;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.models.TimeDensityModel;
import models.keyphrase_prediction.scorers.TechnologyScorer;
import models.keyphrase_prediction.stages.*;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilders;
import org.gephi.graph.api.Node;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.Stemmer;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.portfolios.items.Item;
import util.Pair;
import visualization.Visualizer;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class KeywordModelRunnerBeta {
    private static Collection<String> validPOS = Arrays.asList("JJ","JJR","JJS","NN","NNS","NNP","NNPS","VBG","VBN");
    private static Collection<String> adjectivesPOS = Arrays.asList("JJ","JJR","JJS");

    public static final boolean debug = false;

    public static void main(String[] args) {
        runModel(false);
    }
    enum DataType {
        DocumentsAppearedIn,
        FilingDate,
        ID
    }

    public static void runModel(boolean thisYearOnly) {
        int NUM_SAMPLES_STEP1 = 1000000;
        int NUM_WORDS_STEP1 = 250000;


        Map<MultiStem,AtomicLong> documentsAppearedInCounter = Collections.synchronizedMap(new HashMap<>());
        Function<Map<DataType,Object>,Void> collectDocumentCountsFunction = data -> {
            Set<MultiStem> appeared = (Set<MultiStem>)data.get(DataType.DocumentsAppearedIn);
            appeared.forEach(stem->{
                documentsAppearedInCounter.putIfAbsent(stem, new AtomicLong(0));
                documentsAppearedInCounter.get(stem).getAndIncrement();
            });
            return null;
        };

        Map<String,Map<String,AtomicInteger>> stemToPhraseCountMap = Collections.synchronizedMap(new HashMap<>());
        Map<MultiStem,AtomicLong> multiStemMap = Collections.synchronizedMap(new HashMap<>());
        Function<SearchHit,Item> transformer =  buildItemTransformerFunction(collectDocumentCountsFunction, multiStemMap, stemToPhraseCountMap);
        streamElasticSearchData(transformer, NUM_SAMPLES_STEP1);

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
            } else {
                multiStemMap.remove(stem);
            }
        });


        // compute scores
        Map<MultiStem,Double> scores = documentsAppearedInCounter.entrySet().parallelStream().map(e->{
            MultiStem multiStem = e.getKey();
            double score = e.getValue().doubleValue();
            if(multiStem.getStems().length > 1) {
                double denom = Math.pow(Stream.of(multiStem.getStems()).map(s -> documentsAppearedInCounter.getOrDefault(new MultiStem(new String[]{s}, -1), new AtomicLong(1))).mapToDouble(d -> d.doubleValue()).reduce((d1, d2) -> d1 * d2).getAsDouble(), 1d / multiStem.getStems().length);
                score = (score * e.getValue().doubleValue() * multiStem.getStems().length) / denom;
            }
            return new Pair<>(multiStem,score);
        }).sorted((e1,e2)->e2._2.compareTo(e1._2)).limit(NUM_WORDS_STEP1).collect(Collectors.toMap(p->p._1,p->p._2));


        AssetToCPCMap assetToCPCMap = new AssetToCPCMap();
        Map<String,Set<String>> patentCPCMap = assetToCPCMap.getPatentDataMap();
        Map<String,Set<String>> applicationCPCMap = assetToCPCMap.getApplicationDataMap();

        Function<Map<DataType,Object>,Void> buildStatisticsFunction = data-> {
            LocalDate date = (LocalDate)data.get(DataType.FilingDate);
            Set<MultiStem> appearedIn = (Set<MultiStem>)data.get(DataType.DocumentsAppearedIn);
            String id = data.get(DataType.ID).toString();
            Collection<String> cpcs = patentCPCMap.getOrDefault(id,applicationCPCMap.get(id));

            if(cpcs!=null) {

            }
            return null;
        };
        Function<SearchHit,Item> transformer2 =  buildItemTransformerFunction(buildStatisticsFunction, null, null);
        streamElasticSearchData(transformer, NUM_SAMPLES_STEP1);


    }

    private static Function<SearchHit,Item> buildItemTransformerFunction(Function<Map<DataType,Object>,Void> appearedFunction, Map<MultiStem,AtomicLong> multiStemMap, Map<String,Map<String,AtomicInteger>> stemToPhraseCountMap) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        AtomicLong cnt = new AtomicLong(0);
        return hit-> {
            String id = hit.getId();
            String inventionTitle = hit.getSourceAsMap().getOrDefault(Constants.INVENTION_TITLE, "").toString().toLowerCase();
            String abstractText = hit.getSourceAsMap().getOrDefault(Constants.ABSTRACT, "").toString().toLowerCase();
            SearchHits innerHits = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
            Object dateObj = innerHits == null ? null : (innerHits.getHits()[0].getSourceAsMap().get(Constants.FILING_DATE));
            LocalDate date = dateObj == null ? null : (LocalDate.parse(dateObj.toString(), DateTimeFormatter.ISO_DATE));
            String text = String.join(". ", Stream.of(inventionTitle,abstractText).filter(t->t!=null&&t.length()>0).collect(Collectors.toList())).replaceAll("[^a-z .,]"," ");

            Annotation doc = new Annotation(text);
            if(cnt.getAndIncrement() % 10000 == 9999) {
                if(multiStemMap!=null)System.out.println("Num distinct multistems: "+multiStemMap.size());
            }
            pipeline.annotate(doc, d -> {
                if(debug) System.out.println("Text: "+text);
                List<CoreMap> sentences = d.get(CoreAnnotations.SentencesAnnotation.class);
                Set<MultiStem> appeared = new HashSet<>();
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
                                    if (!adjectivesPOS.contains(pos) && !pos.equals("VBD") && !((!pos.startsWith("N"))&&(word.endsWith("ing")||word.endsWith("ed")))) {
                                        multiStemChecker(new String[]{stem}, multiStemMap, word, stemToPhraseCountMap, appeared);
                                        if (prevStem != null) {
                                            multiStemChecker(new String[]{prevStem, stem}, multiStemMap, String.join(" ", prevWord, word), stemToPhraseCountMap, appeared);
                                            if (prevPrevStem != null) {
                                                multiStemChecker(new String[]{prevPrevStem, prevStem, stem}, multiStemMap, String.join(" ", prevPrevWord, prevWord, word), stemToPhraseCountMap, appeared);
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
                Map<DataType,Object> data = new HashMap<>();
                data.put(DataType.DocumentsAppearedIn, appeared);
                data.put(DataType.FilingDate, date);
                data.put(DataType.ID,id);
                appearedFunction.apply(data);
            });

            return null;
        };
    }


    public static void updateLatest() {
        runModel(true);
    }


    public static Map<String,List<String>> loadModelMap(Model model) {
        return (Map<String,List<String>>) Database.tryLoadObject(new File(Constants.DATA_FOLDER+"keyword_asset_to_keyword_final_model_beta_"+model.getModelName()+"_map.jobj"));
    }

    public static void saveModelMap(Model model, Map<String,List<String>> assetToTechnologyMap) {
        Database.trySaveObject(assetToTechnologyMap, new File(Constants.DATA_FOLDER+"keyword_asset_to_keyword_final_model_beta_"+model.getModelName()+"_map.jobj"));
    }

    public static void reindex(Collection<MultiStem> multiStems) {
        AtomicInteger cnt = new AtomicInteger(0);
        multiStems.parallelStream().forEach(multiStem -> {
            multiStem.setIndex(cnt.getAndIncrement());
        });
    }

    public static void streamElasticSearchData(QueryBuilder query, Function<SearchHit,Item> transformer, int sampling) {
        TransportClient client = DataSearcher.getClient();
        SearchRequestBuilder search = client.prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.TYPE_NAME)
                .setScroll(new TimeValue(60000))
                .setExplain(false)
                .setFrom(0)
                .setSize(10000)
                .setFetchSource(new String[]{Constants.ABSTRACT,Constants.INVENTION_TITLE},new String[]{})
                .setQuery(query);
        if(sampling>0) {
            search = search.addSort(SortBuilders.scoreSort());
        }
        if(debug) {
            System.out.println(search.request().toString());
        }

        SearchResponse response = search.get();
        DataSearcher.iterateOverSearchResults(response, transformer, sampling, false);
    }

    public static void streamElasticSearchData(Function<SearchHit,Item> transformer, int sampling) {
        QueryBuilder query;
        if(sampling>0) {
            query = QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(),ScoreFunctionBuilders.randomFunction(23));
        } else {
            query = QueryBuilders.matchAllQuery();
        }
        query = new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME, query,false)
                .innerHit(new InnerHitBuilder()
                        .setSize(1)
                        .setFetchSourceContext(new FetchSourceContext(true, new String[]{Constants.FILING_DATE,Constants.WIPO_TECHNOLOGY}, new String[]{}))
                );

        if(sampling > 0) {
            // remove plant and design patents
            QueryBuilders.boolQuery()
                    .must(query)
                    .filter(
                            QueryBuilders.boolQuery()
                                    .mustNot(QueryBuilders.prefixQuery(Constants.NAME, "PP"))
                                    .mustNot(QueryBuilders.prefixQuery(Constants.NAME, "D"))
                    );
        }

        streamElasticSearchData(query,transformer,sampling);
    }

    private static void multiStemChecker(String[] stems, Map<MultiStem,AtomicLong> multiStems, String label, Map<String,Map<String,AtomicInteger>> phraseCountMap, Set<MultiStem> appeared) {
        MultiStem multiStem = new MultiStem(stems, multiStems.size());
        String stemPhrase = multiStem.toString();
        if(phraseCountMap!=null) {
            phraseCountMap.putIfAbsent(stemPhrase, Collections.synchronizedMap(new HashMap<>()));
            Map<String, AtomicInteger> innerMap = phraseCountMap.get(stemPhrase);
            innerMap.putIfAbsent(label, new AtomicInteger(0));
            innerMap.get(label).getAndIncrement();
        }
        appeared.add(multiStem);
        if(multiStems!=null) {
            synchronized (KeywordModelRunnerBeta.class) {
                AtomicLong currentCount = multiStems.get(multiStem);
                if (currentCount == null) {
                    if (debug) System.out.println("Adding word " + multiStem.getIndex() + ": " + multiStem);
                    multiStems.put(multiStem, new AtomicLong(1L));
                } else {
                    currentCount.getAndIncrement();
                }
            }
        }
    }

    public static void writeToCSV(Collection<MultiStem> multiStems, File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Multi-Stem, Key Phrase, Score\n");
            multiStems.forEach(e->{
                try {
                    writer.write(e.toString()+","+e.getBestPhrase()+","+e.getScore()+"\n");
                }catch(Exception e2) {
                    e2.printStackTrace();
                }
            });
            writer.flush();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static Node getNode(Map<String,Node> nodeMap, MultiStem stem, Visualizer visualizer, float score) {
        Color color = Color.BLUE;

        Node node = nodeMap.get(stem.getBestPhrase());
        if (node == null) {
            node = visualizer.addNode(stem.getBestPhrase(), score, color);
            nodeMap.put(stem.getBestPhrase(), node);
        }

        return node;
    }

    public static void createVisualization(Map<Integer,Collection<MultiStem>> yearToMultiStemMap, double scoreThreshold, double minEdgeScore, File file) {
        Map<String,Node> nodeMap = Collections.synchronizedMap(new HashMap<>());
        Visualizer visualizer = new Visualizer(file.getAbsolutePath());
        yearToMultiStemMap.forEach((year,multiStems)->{
            // now we have keywords
            reindex(multiStems);
            Map<MultiStem,MultiStem> multiStemToSelfMap = multiStems.parallelStream().collect(Collectors.toMap(e->e,e->e));
            double[][] matrix = Stage3.buildMMatrix(multiStems,multiStemToSelfMap,year,100000).getData();
            double[] sums = Stream.of(matrix).mapToDouble(row-> DoubleStream.of(row).sum()).toArray();

            multiStems.forEach(stem->{
                float score = (float)sums[stem.getIndex()];
                if(score >= scoreThreshold) {
                    Node node = getNode(nodeMap,stem,visualizer,score);
                    for(MultiStem stem2 : multiStems) {
                        if(stem.equals(stem2)) continue;
                        float score2 = (float) matrix[stem.getIndex()][stem2.getIndex()];
                        if(score2 > minEdgeScore) {
                            Node node2 = getNode(nodeMap, stem2, visualizer, score2);
                            if (score2 >= minEdgeScore) {
                                visualizer.addEdge(node, node2, score2, Color.BLACK);
                            }
                        }
                    }
                }
            });
            System.out.println("Saving visualizer...");
            visualizer.save();

        });
        visualizer.save();
    }
}
