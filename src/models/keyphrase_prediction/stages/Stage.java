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
import models.keyphrase_prediction.scorers.KeywordScorer;
import org.apache.commons.math3.linear.RealMatrix;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.gephi.graph.api.Node;
import seeding.Constants;
import seeding.Database;
import tools.Stemmer;
import user_interface.ui_models.portfolios.items.Item;
import visualization.Visualizer;

import java.awt.*;
import java.io.File;
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
 * Created by ehallmark on 9/12/17.
 */
public abstract class Stage<V> {
    private static Collection<String> validPOS = Arrays.asList("JJ","JJR","JJS","NN","NNS","NNP","NNPS","VBG","VBN");
    private static Collection<String> adjectivesPOS = Arrays.asList("JJ","JJR","JJS");
    static double scoreThreshold = 200f;
    static double minEdgeScore = 50f;
    protected static final String APPEARED = "APPEARED";
    protected static final String APPEARED_WITH_COUNTS = "APPEARED_WITH_COUNTS";
    protected static final String ASSET_ID = "ID";
    protected static final String DATE = "DATE";

    private static final boolean debug = false;
    private static final File baseDir = new File(Constants.DATA_FOLDER+"technologyPredictionStages/");
    protected File mainDir;
    protected V data;
    protected int sampling;
    protected Model model;
    public Stage(Model model) {
        this.model=model;
        this.sampling=model.getSampling();
        if(!baseDir.exists()) baseDir.mkdir();
        if(!baseDir.isDirectory()) throw new RuntimeException(baseDir.getAbsolutePath()+" must be a directory.");
        this.mainDir = new File(baseDir, model.getModelName());
        if(!mainDir.exists()) mainDir.mkdir();
        if(!mainDir.isDirectory()) throw new RuntimeException(mainDir.getAbsolutePath()+" must be a directory.");
    }
    protected void loadData() {
        data = (V) Database.tryLoadObject(getFile());
    }
    public V get() {
        return data;
    }
    public void set(V data) {
        this.data=data;
    }
    public File getFile() {
        return new File(mainDir,this.getClass().getSimpleName());
    }

    public abstract V run(boolean run);

    protected void checkStem(String[] stems, String label, Map<MultiStem,AtomicInteger> appeared) {
        MultiStem stem = new MultiStem(stems,-1);
        appeared.putIfAbsent(stem,new AtomicInteger(0));
        appeared.get(stem).getAndIncrement();
    }

    public void createVisualization() {
        Object data = this.get();
        Collection<MultiStem> multiStems;
        if(data instanceof Collection) {
            multiStems = (Collection<MultiStem>)data;
        } else {
            multiStems = ((Map<MultiStem,?>)data).keySet();
        }
        createVisualization(multiStems);
    }

    private void createVisualization(Collection<MultiStem> multiStems) {
        System.out.println("Starting to make visualization graph...");
        File visualizationFile = new File("data/technologyPredictionStages/"+model.getModelName()+"/visualizations-"+this.getClass().getSimpleName()+"-"+LocalDate.now().toString());
        Map<String,Node> nodeMap = Collections.synchronizedMap(new HashMap<>());
        Visualizer visualizer = new Visualizer(visualizationFile.getAbsolutePath());
        // now we have keywords
        KeywordModelRunner.reindex(multiStems);
        Map<MultiStem,MultiStem> multiStemToSelfMap = multiStems.parallelStream().collect(Collectors.toMap(e->e,e->e));
        double[][] matrix = new Stage3(multiStems,model).buildMMatrix(multiStems,multiStemToSelfMap).getData();
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
    }

    public static Set<MultiStem> applyFilters(KeywordScorer scorer, RealMatrix matrix, Collection<MultiStem> keywords, double lowerBoundPercent, double upperBoundPercent, double minValue) {
        Map<MultiStem,Double> scoreMap = scorer.scoreKeywords(keywords,matrix);
        long count = scoreMap.size();
        double skipFirst = lowerBoundPercent*count;
        double skipLast = (1.0-Math.min(1d,upperBoundPercent))*count;
        return scoreMap.entrySet().stream()
                .filter(e->e.getValue()>minValue)
                .sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                .skip((long)skipLast)
                .limit(count-(long)(skipFirst+skipLast))
                .map(e->{
                    if(debug) {
                        System.out.println("Value for "+e.getKey().toString()+": "+e.getValue());
                    }
                    e.getKey().setScore(e.getValue().floatValue());
                    return e.getKey();
                })
                .collect(Collectors.toSet());
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

    protected void runSamplingIterator(Function<Map<String,Object>,Void> attributesFunction) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        Function<SearchHit,Item> transformer = hit-> {
            String asset = hit.getId();
            String inventionTitle = hit.getSourceAsMap().getOrDefault(Constants.INVENTION_TITLE, "").toString().toLowerCase();
            String abstractText = hit.getSourceAsMap().getOrDefault(Constants.ABSTRACT, "").toString().toLowerCase();
            SearchHits innerHits = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
            Object dateObj = innerHits == null ? null : (innerHits.getHits()[0].getSourceAsMap().get(Constants.FILING_DATE));
            LocalDate date = dateObj == null ? null : (LocalDate.parse(dateObj.toString(), DateTimeFormatter.ISO_DATE));
            String text = String.join(". ", Stream.of(inventionTitle,abstractText).filter(t->t!=null&&t.length()>0).collect(Collectors.toList())).replaceAll("[^a-z .,]"," ");

            Annotation doc = new Annotation(text);
            pipeline.annotate(doc, d -> {
                if(debug) System.out.println("Text: "+text);
                List<CoreMap> sentences = d.get(CoreAnnotations.SentencesAnnotation.class);
                Map<MultiStem,AtomicInteger> appeared = new HashMap<>();
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
                                        checkStem(new String[]{stem}, word, appeared);
                                        if (prevStem != null) {
                                            checkStem(new String[]{prevStem, stem}, String.join(" ", prevWord, word), appeared);
                                            if (prevPrevStem != null) {
                                                checkStem(new String[]{prevPrevStem, prevStem, stem}, String.join(" ", prevPrevWord, prevWord, word), appeared);
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
                Map<String,Object> attributes = new HashMap<>();
                attributes.put(APPEARED,new HashSet<>(appeared.keySet()));
                attributes.put(APPEARED_WITH_COUNTS,appeared);
                attributes.put(DATE,date);
                attributes.put(ASSET_ID,asset);
                attributesFunction.apply(attributes);

            });

            return null;
        };
        KeywordModelRunner.streamElasticSearchData(transformer,sampling);
    }
}
