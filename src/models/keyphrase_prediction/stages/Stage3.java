package models.keyphrase_prediction.stages;

import elasticsearch.DataIngester;
import model.edges.Edge;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.scorers.TermhoodScorer;
import models.keyphrase_prediction.scorers.UnithoodScorer;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import spire.math.algebraic.Mul;
import tools.Stemmer;
import user_interface.ui_models.portfolios.items.Item;
import util.Pair;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/12/17.
 */
public class Stage3 implements Stage<Collection<MultiStem>> {
    private static final boolean debug = false;
    private static final File stage3File = new File("data/keyword_model_keywords_set_stage3.jobj");
    private Collection<MultiStem> multiStems;
    private long targetCardinality;
    private int year;
    private boolean rebuildMMatrix;
    public Stage3(Stage2 stage2, long targetCardinality, boolean rebuildMMatrix, int year) {
        this.multiStems = new ArrayList<>(stage2.get());
        this.rebuildMMatrix=rebuildMMatrix;
        this.year=year;
        this.targetCardinality=targetCardinality;
    }

    @Override
    public Collection<MultiStem> get() {
        return multiStems;
    }

    @Override
    public void loadData() {
        multiStems = (Collection<MultiStem>)Database.loadObject(getFile(year));
    }

    @Override
    public File getFile(int year) {
        return new File(stage3File.getAbsolutePath()+year);
    }

    @Override
    public Collection<MultiStem> run(boolean run) {
        if(run) {
            // apply filter 2
            KeywordModelRunner.reindex(multiStems);
            System.out.println("Num keywords before stage 3: "+multiStems.size());
            float[][] M;
            if(rebuildMMatrix) {
                M = buildMMatrix();
                Database.trySaveObject(M, new File("data/keyword_m_matrix.jobj"+year));
            } else {
                M = (float[][]) Database.tryLoadObject(new File("data/keyword_m_matrix.jobj"+year));
            }
            multiStems = KeywordModelRunner.applyFilters(new TermhoodScorer(), M, multiStems, targetCardinality, 0, Double.MAX_VALUE);
            System.out.println("Num keywords after stage 3: "+multiStems.size());

            Database.saveObject(multiStems, getFile(year));
            // write to csv for records
            KeywordModelRunner.writeToCSV(multiStems,new File("data/keyword_model_stage3"+year+".csv"));
        } else {
            loadData();
        }
        return multiStems;
    }

    private float[][] buildMMatrix() {
        Map<Long,AtomicInteger> cooccurrenceMap = Collections.synchronizedMap(new HashMap<>(multiStems.size()));
        Function<SearchHit,Item> transformer = hit-> {
            String asset = hit.getId();
            String inventionTitle = hit.getSourceAsMap().getOrDefault(Constants.INVENTION_TITLE, "").toString().toLowerCase();
            String abstractText = hit.getSourceAsMap().getOrDefault(Constants.ABSTRACT, "").toString().toLowerCase();
           // SearchHits innerHits = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
           // Object dateObj = innerHits == null ? null : (innerHits.getHits()[0].getSourceAsMap().get(Constants.FILING_DATE));
           // LocalDate date = dateObj == null ? null : (LocalDate.parse(dateObj.toString(), DateTimeFormatter.ISO_DATE));
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
                for (MultiStem stem2 : cooccurringStems) {
                    long value = ((long)stem1.getIndex())*multiStems.size() + stem2.getIndex();
                    cooccurrenceMap.putIfAbsent(value,new AtomicInteger(0));
                    cooccurrenceMap.get(value).getAndIncrement();
                }
            }
            return null;
        };

        KeywordModelRunner.streamElasticSearchData(year, transformer, 100000);

        int oldMultiStemSize = multiStems.size();
        System.out.println("building cooccurrence map...");
        // create co-occurrrence statistics
        Collection<Integer> indicesOfMultiStems = cooccurrenceMap.keySet().parallelStream().flatMap(index->{
            long idx1 = index / multiStems.size();
            long idx2 = index % multiStems.size();
            return Stream.of((int)idx1,(int)idx2);
        }).distinct().collect(Collectors.toSet());

        System.out.println("Filtered multistems size: "+indicesOfMultiStems.size());
        float[][] matrix = new float[indicesOfMultiStems.size()][indicesOfMultiStems.size()];
        IntStream.range(0,matrix.length).parallel().forEach(i->{
            matrix[i] = new float[indicesOfMultiStems.size()];
            Arrays.fill(matrix[i],0f);
        });

        System.out.println("Built float[][]...");

        Map<Integer,MultiStem> oldIdxToMultiStemMap = multiStems.parallelStream().filter(stem->{
            return indicesOfMultiStems.contains(stem.getIndex());
        }).collect(Collectors.toMap(stem->stem.getIndex(),stem->stem));

        List<MultiStem> multiStemList = new ArrayList<>(oldIdxToMultiStemMap.values());
        multiStems=multiStemList;

        System.out.println("Reindexing...");
        KeywordModelRunner.reindex(multiStemList);

        System.out.println("Looping thru cooccurrence map...");
        cooccurrenceMap.entrySet().parallelStream().forEach(e->{
            long index = e.getKey();
            long idx1 = index / oldMultiStemSize;
            long idx2 = index % oldMultiStemSize;
            int i = oldIdxToMultiStemMap.get((int)idx1).getIndex();
            int j = oldIdxToMultiStemMap.get((int)idx2).getIndex();
            matrix[i][j]=e.getValue().get();
        });

        return matrix;
    }


}
