package models.keyphrase_prediction.stages;

import elasticsearch.DataIngester;
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
import tools.Stemmer;
import user_interface.ui_models.portfolios.items.Item;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/12/17.
 */
public class Stage3 implements Stage<Collection<MultiStem>> {
    private static final boolean debug = false;
    private static final File stage3File = new File("data/keyword_model_keywords_set_stage3.jobj");
    private Collection<MultiStem> keywords;
    private long targetCardinality;
    private int year;
    private boolean rebuildMMatrix;
    public Stage3(Stage2 stage2, long targetCardinality, boolean rebuildMMatrix, int year) {
        this.keywords = stage2.get();
        this.rebuildMMatrix=rebuildMMatrix;
        this.year=year;
        this.targetCardinality=targetCardinality;
    }

    @Override
    public Collection<MultiStem> get() {
        return keywords;
    }

    @Override
    public void loadData() {
        keywords = (Collection<MultiStem>)Database.loadObject(stage3File);
    }

    @Override
    public Collection<MultiStem> run(boolean run) {
        if(run) {
            // apply filter 2
            KeywordModelRunner.reindex(keywords);
            System.out.println("Num keywords before stage 3: "+keywords.size());
            INDArray M;
            if(rebuildMMatrix) {
                M = buildMMatrix(keywords, year);
                Database.trySaveObject(M, new File("data/keyword_m_matrix.jobj"));
            } else {
                M = (INDArray) Database.tryLoadObject(new File("data/keyword_m_matrix.jobj"));
            }
            keywords = KeywordModelRunner.applyFilters(new TermhoodScorer(), M, keywords, targetCardinality, 0, Double.MAX_VALUE);
            System.out.println("Num keywords after stage 3: "+keywords.size());

            Database.saveObject(keywords, stage3File);
            // write to csv for records
            KeywordModelRunner.writeToCSV(keywords,new File("data/keyword_model_stage3.csv"));
        } else {
            loadData();
        }
        return keywords;
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
                double[] row = matrix[stem1.getIndex()];
                Object[] lockRow = locks[stem1.getIndex()];
                for (MultiStem stem2 : cooccurringStems) {
                    synchronized(lockRow[stem2.getIndex()]) {
                        row[stem2.getIndex()]++;
                    }
                }
            }

            return null;
        };

        KeywordModelRunner.streamElasticSearchData(year, transformer);
        return Nd4j.create(matrix);
    }


}
