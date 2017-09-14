package models.keyphrase_prediction.stages;

import elasticsearch.DataIngester;
import model.edges.Edge;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.scorers.TermhoodScorer;
import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.linear.SparseRealMatrix;
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
    private Map<MultiStem,MultiStem> multiStemToSelfMap;
    private long targetCardinality;
    private int year;
    private boolean rebuildMMatrix;
    public Stage3(Collection<MultiStem> multiStems, long targetCardinality, boolean rebuildMMatrix, int year) {
        this.multiStems = new HashSet<>(multiStems);
        this.multiStemToSelfMap = multiStems.parallelStream().collect(Collectors.toMap(e->e,e->e));
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
            System.out.println("Starting year: "+year);
            System.out.println("Num keywords before stage 3: "+multiStems.size());
            SparseRealMatrix M;
            if(rebuildMMatrix) {
                M = buildMMatrix();
                System.out.println("saving matrix");
                Database.trySaveObject(M, new File("data/keyword_m_matrix.jobj"+year));
            } else {
                M = (SparseRealMatrix) Database.tryLoadObject(new File("data/keyword_m_matrix.jobj"+year));
            }
            multiStems = KeywordModelRunner.applyFilters(new TermhoodScorer(), M, multiStems, targetCardinality, 0.3, 0.8);
            M=null;
            System.out.println("Num keywords after stage 3: "+multiStems.size());

            Database.saveObject(multiStems, getFile(year));
            // write to csv for records
            KeywordModelRunner.writeToCSV(multiStems,new File("data/keyword_model_stage3"+year+".csv"));
        } else {
            loadData();
        }
        return multiStems;
    }

    private SparseRealMatrix buildMMatrix() {
        SparseRealMatrix matrix = new OpenMapRealMatrix(multiStems.size(),multiStems.size());
        Function<SearchHit,Item> transformer = hit-> {
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
            documentStems.forEach(docStem->{
                if(multiStems.contains(docStem)) {
                    cooccurringStems.add(multiStemToSelfMap.get(docStem));
                }
            });

            if(debug)
                System.out.println("Num coocurrences: "+cooccurringStems.size());

            // Unavoidable n-squared part
            for(MultiStem stem1 : cooccurringStems) {
                for (MultiStem stem2 : cooccurringStems) {
                    synchronized (matrix) {
                        matrix.addToEntry(stem1.getIndex(), stem2.getIndex(), 1);
                    }
                }
            }
            return null;
        };

        KeywordModelRunner.streamElasticSearchData(year, transformer, 100000);
        return matrix;
    }


}
