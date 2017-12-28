package models.keyphrase_prediction.stages;

import data_pipeline.helpers.Function3;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import lombok.Getter;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.scorers.KeywordScorer;
import models.text_streaming.FileTextDataSetIterator;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.math3.linear.RealMatrix;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.gephi.graph.api.Node;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;
import tools.Stemmer;
import visualization.Visualizer;

import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/12/17.
 */
public abstract class Stage<V> {
    @Getter
    private static File transformedDataFolder = new File("filing_text_data_with_counts/");
    public static final Collection<String> validPOS = Arrays.asList("JJ", "JJR", "JJS", "NN", "NNS", "NNP", "NNPS", "VBG", "VBN");
    public static final Collection<String> adjectivesPOS = Arrays.asList("JJ", "JJR", "JJS");
    static double scoreThreshold = 200f;
    static double minEdgeScore = 50f;
    public static final String APPEARED = "APPEARED";
    public static final String APPEARED_WITH_COUNTS = "APPEARED_WITH_COUNTS";
    public static final String ASSET_ID = "ID";
    public static final String DATE = "DATE";
    public static final String TEXT = "TEXT";

    private static final boolean debug = false;
    @Getter
    private static final File baseDir = new File(Constants.DATA_FOLDER + "technologyPredictionStages/");
    protected File mainDir;
    protected V data;
    protected Model model;
    protected double defaultUpper;
    protected double defaultLower;

    protected Stage(Model model) {
        this.model = model;
        this.defaultUpper = model.getDefaultUpperBound();
        this.defaultLower = model.getDefaultLowerBound();
        if (!baseDir.exists()) baseDir.mkdir();
        if (!baseDir.isDirectory()) throw new RuntimeException(baseDir.getAbsolutePath() + " must be a directory.");
        this.mainDir = new File(baseDir, model.getModelName());
        if (!mainDir.exists()) mainDir.mkdir();
        if (!mainDir.isDirectory()) throw new RuntimeException(mainDir.getAbsolutePath() + " must be a directory.");
    }

    protected void loadData() {
        data = (V) Database.tryLoadObject(getFile());
    }

    public V get() {
        return data;
    }

    public void set(V data) {
        this.data = data;
    }

    public File getFile() {
        return new File(mainDir, this.getClass().getSimpleName());
    }

    public abstract V run(boolean run);

    protected static void checkStem(String[] stems, String label, Map<MultiStem, AtomicInteger> appeared) {
        MultiStem stem = new MultiStem(stems, -1);
        appeared.putIfAbsent(stem, new AtomicInteger(0));
        appeared.get(stem).getAndIncrement();
    }

    public void createVisualization() {
        Object data = this.get();
        Collection<MultiStem> multiStems;
        if (data instanceof Collection) {
            multiStems = (Collection<MultiStem>) data;
        } else {
            multiStems = ((Map<MultiStem, ?>) data).keySet();
        }
        createVisualization(multiStems);
    }

    private void createVisualization(Collection<MultiStem> multiStems) {
        System.out.println("Starting to make visualization graph...");
        File visualizationFile = new File("data/technologyPredictionStages/" + model.getModelName() + "/visualizations-" + this.getClass().getSimpleName() + "-" + LocalDate.now().toString());
        Map<String, Node> nodeMap = Collections.synchronizedMap(new HashMap<>());
        Visualizer visualizer = new Visualizer(visualizationFile.getAbsolutePath());
        // now we have keywords
        KeywordModelRunner.reindex(multiStems);
        Map<MultiStem, MultiStem> multiStemToSelfMap = multiStems.parallelStream().collect(Collectors.toMap(e -> e, e -> e));
        double[][] matrix = new Stage3(multiStems, model).buildMMatrix(multiStems, multiStemToSelfMap).getData();
        double[] sums = Stream.of(matrix).mapToDouble(row -> DoubleStream.of(row).sum()).toArray();

        multiStems.forEach(stem -> {
            float score = (float) sums[stem.getIndex()];
            if (score >= scoreThreshold) {
                Node node = getNode(nodeMap, stem, visualizer, score);
                for (MultiStem stem2 : multiStems) {
                    if (stem.equals(stem2)) continue;
                    float score2 = (float) matrix[stem.getIndex()][stem2.getIndex()];
                    if (score2 > minEdgeScore) {
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

    public static Set<MultiStem> applyFilters(KeywordScorer scorer, RealMatrix matrix, Set<MultiStem> keywords, double lowerBoundPercent, double upperBoundPercent, double minValue) {
        Map<MultiStem, Double> scoreMap = scorer.scoreKeywords(keywords, matrix);
        long count = scoreMap.size();
        double skipFirst = lowerBoundPercent * count;
        double skipLast = (1.0 - Math.min(1d, upperBoundPercent)) * count;
        return scoreMap.entrySet().stream()
                .filter(e -> e.getValue() > minValue)
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .skip((long) skipLast)
                .limit(count - (long) (skipFirst + skipLast))
                .map(e -> {
                    if (debug) {
                        System.out.println("Value for " + e.getKey().toString() + ": " + e.getValue());
                    }
                    e.getKey().setScore(e.getValue().floatValue());
                    return e.getKey();
                })
                .collect(Collectors.toSet());
    }

    private static Node getNode(Map<String, Node> nodeMap, MultiStem stem, Visualizer visualizer, float score) {
        Color color = Color.BLUE;

        Node node = nodeMap.get(stem.getBestPhrase());
        if (node == null) {
            node = visualizer.addNode(stem.getBestPhrase(), score, color);
            nodeMap.put(stem.getBestPhrase(), node);
        }

        return node;
    }

    public static Function<Map<String, Object>, Consumer<Annotation>> getDefaultAnnotator() {
        return getDefaultAnnotator(3, (stem, label, map) -> {
            checkStem(stem, label, map);
            return null;
        });
    }

    public static Function<Map<String, Object>, Consumer<Annotation>> getDefaultAnnotator(Function3<String[], String, Map<MultiStem, AtomicInteger>, Void> checkStemFunction) {
        return getDefaultAnnotator(3, checkStemFunction);
    }

    public static Function<Map<String, Object>, Consumer<Annotation>> getDefaultAnnotator(int maxPhraseLength, Function3<String[], String, Map<MultiStem, AtomicInteger>, Void> checkStemFunction) {
        return dataMap -> d -> {
            List<CoreMap> sentences = d.get(CoreAnnotations.SentencesAnnotation.class);
            Map<MultiStem, AtomicInteger> appeared = new HashMap<>();
            for (CoreMap sentence : sentences) {
                // traversing the words in the current sentence
                // a CoreLabel is a CoreMap with additional token-specific methods
                String prevStem = null;
                String prevPrevStem = null;
                String prevWord = null;
                String prevPrevWord = null;
                String prevPos = null;
                String prevPrevPos = null;
                for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                    // this is the text of the token
                    String word = token.get(CoreAnnotations.TextAnnotation.class);
                    boolean valid = true;
                    for (int i = 0; i < word.length(); i++) {
                        if (!Character.isAlphabetic(word.charAt(i))) {
                            valid = false;
                        }
                    }
                    if (!valid) continue;

                    // could be the stem
                    String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);

                    if (Constants.STOP_WORD_SET.contains(lemma) || Constants.STOP_WORD_SET.contains(word)) {
                        prevPrevStem = null;
                        prevStem = null;
                        prevWord = null;
                        prevPrevWord = null;
                        prevPos = null;
                        prevPrevPos = null;
                        continue;
                    }

                    try {
                        String stem = new Stemmer().stem(lemma);
                        String pos;
                        if (stem.length() > 3 && !Constants.STOP_WORD_SET.contains(stem)) {
                            // this is the POS tag of the token
                            pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                            if (validPOS.contains(pos)) {
                                // don't want to end in adjectives (nor past tense verb)
                                if (!adjectivesPOS.contains(pos) && !((!pos.startsWith("N")) && (word.endsWith("ing") || word.endsWith("ed")))) {
                                    checkStemFunction.apply(new String[]{stem}, word, appeared);
                                    if (maxPhraseLength > 1) {
                                        if (prevStem != null && !prevStem.equals(stem)) {
                                            long numVerbs = Stream.of(pos, prevPos).filter(p -> p != null && p.startsWith("V")).count();
                                            if (numVerbs <= 1) {
                                                checkStemFunction.apply(new String[]{prevStem, stem}, String.join(" ", prevWord, word), appeared);
                                                if (maxPhraseLength > 2) {
                                                    if (prevPrevStem != null && !prevStem.equals(prevPrevStem) && !prevPrevStem.equals(stem)) {
                                                        numVerbs = Stream.of(pos, prevPos, prevPrevPos).filter(p -> p != null && p.startsWith("V")).count();
                                                        if (numVerbs <= 1) {
                                                            checkStemFunction.apply(new String[]{prevPrevStem, prevStem, stem}, String.join(" ", prevPrevWord, prevWord, word), appeared);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                stem = null;
                                pos = null;
                            }
                        } else {
                            pos = null;
                            stem = null;
                        }
                        prevPrevStem = prevStem;
                        prevStem = stem;
                        prevPrevWord = prevWord;
                        prevWord = word;
                        prevPrevPos = prevPos;
                        prevPos = pos;

                    } catch (Exception e) {
                        System.out.println("Error while stemming: " + lemma);
                        prevStem = null;
                        prevPrevStem = null;
                        prevWord = null;
                        prevPrevWord = null;
                        prevPos = null;
                        prevPrevPos = null;
                    }
                }
            }
            dataMap.put(APPEARED, new HashSet<>(appeared.keySet()));
            dataMap.put(APPEARED_WITH_COUNTS, appeared);
        };
    }


    protected static void collectVocabAndTransformData(Function<Map<String, Object>, Void> attributesFunction, Function<Map<String, Object>, Consumer<Annotation>> annotator) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        Function<LabelledDocument, String> documentTransformer = doc -> {
            String asset = doc.getLabels().get(0);
            String date = doc.getLabels().get(1);
            String text = doc.getContent();
            Annotation annotation = new Annotation(text);
            Map<String, Object> data = new HashMap<>();
            data.put(TEXT, text);
            data.put(ASSET_ID, asset);
            data.put(DATE, date);
            pipeline.annotate(annotation, annotator.apply(data));
            attributesFunction.apply(data);
            Map<MultiStem, AtomicInteger> docCounts = (Map<MultiStem, AtomicInteger>) data.get(APPEARED_WITH_COUNTS);
            if (docCounts == null) return null;
            String ret = asset + "," + date + "," + String.join(",", docCounts.entrySet().stream().map(e -> e.getKey().toString() + ":" + e.getValue().get()).collect(Collectors.toList()));
            if (ret == null || ret.isEmpty()) return null;
            return ret;
        };

        FileTextDataSetIterator.transformData(transformedDataFolder, documentTransformer);

    }

    public static void runSamplingIterator(Function<Map<MultiStem, Integer>, Void> attributesFunction) {
        Function<String, Void> lineTransformer = line -> {
            String[] cells = line.split(",", 2);
            if (cells.length == 1) return null;
            String text = cells[1];
            Map<MultiStem, Integer> data = Stream.of(text.split(",")).map(str -> {
                String[] pair = str.split(":");
                if (pair.length == 1) return null;
                return new Pair<>(new MultiStem(pair[0].split("_"), -1), Integer.valueOf(pair[1]));
            }).filter(p -> p != null).collect(Collectors.toMap(p -> p.getFirst(), p -> p.getSecond()));
            attributesFunction.apply(data);
            return null;
        };
        samplingIteratorHelper(lineTransformer);
    }

    public static void runSamplingIteratorWithLabels(Function<Pair<String, Map<MultiStem, Integer>>, Void> attributesFunction) {
        Function<String, Void> lineTransformer = line -> {
            String[] cells = line.split(",", 3);
            String asset = cells[0];
            if (cells.length < 3) return null;
            String date = cells[1];
            String text = cells[2];
            Map<MultiStem, Integer> data = Stream.of(text.split(",")).map(str -> {
                String[] pair = str.split(":");
                if (pair.length == 1) return null;
                return new Pair<>(new MultiStem(pair[0].split("_"), -1), Integer.valueOf(pair[1]));
            }).filter(p -> p != null).collect(Collectors.toMap(p -> p.getFirst(), p -> p.getSecond()));
            attributesFunction.apply(new Pair<>(asset, data));
            return null;
        };
        samplingIteratorHelper(lineTransformer);
    }

    private static void samplingIteratorHelper(Function<String, Void> lineTransformer) {
        int taskLimit = Math.max(Runtime.getRuntime().availableProcessors(), 1);
        File file = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.trainFile.getName());
        LineIterator iterator;
        try {
            iterator = FileUtils.lineIterator(file);
        } catch (Exception e) {
            throw new RuntimeException("Unable to find file: " + file.getAbsolutePath());
        }
        AtomicInteger cnt = new AtomicInteger(0);
        List<RecursiveAction> tasks = new ArrayList<>();
        while (iterator.hasNext()) {
            if (cnt.getAndIncrement() % 100000 == 99999) {
                taskLimit = Math.max(Runtime.getRuntime().availableProcessors(), 1);
                System.out.println("Iterated through: " + cnt.get());
            }
            if (tasks.size() >= taskLimit) {
                tasks.remove(0).join();
            }
            try {
                String line = iterator.nextLine();
                RecursiveAction task = new RecursiveAction() {
                    @Override
                    protected void compute() {
                        lineTransformer.apply(line);
                    }
                };
                task.fork();
                tasks.add(task);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        tasks.forEach(task -> task.join());
    }

}
