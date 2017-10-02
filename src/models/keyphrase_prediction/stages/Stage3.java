package models.keyphrase_prediction.stages;

import elasticsearch.DataIngester;
import model.edges.Edge;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
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
import tools.OpenMapBigRealMatrix;
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
public class Stage3 extends Stage<Collection<MultiStem>> {
    private static final boolean debug = false;
    private Map<MultiStem,MultiStem> multiStemToSelfMap;
    private long targetCardinality;
    private double lowerBound;
    private double upperBound;
    private double minValue;
    public Stage3(Collection<MultiStem> multiStems, Model model, int year) {
        super(model,year);
        this.data = new HashSet<>(multiStems);
        this.multiStemToSelfMap = multiStems.parallelStream().collect(Collectors.toMap(e->e,e->e));
        this.targetCardinality=model.getK2()*model.getKw();
        this.lowerBound=model.getStage3Lower();
        this.upperBound=model.getStage3Upper();
        this.minValue = model.getStage3Min();
    }



    @Override
    public Collection<MultiStem> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists()) {
            // apply filter 2
            KeywordModelRunner.reindex(data);
            System.out.println("Starting year: " + year);
            System.out.println("Num keywords before stage 3: " + data.size());
            SparseRealMatrix M = buildMMatrix(data,multiStemToSelfMap,year,sampling);
            data = applyFilters(new TermhoodScorer(), M, data, targetCardinality, lowerBound, upperBound, minValue);
            System.out.println("Num keywords after stage 3: " + data.size());

            Database.saveObject(data, getFile());
            // write to csv for records
            KeywordModelRunner.writeToCSV(data, new File(getFile().getAbsoluteFile() + ".csv"));
        } else {
            try {
                loadData();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return data;
    }

    public static SparseRealMatrix buildMMatrix(Collection<MultiStem> data, Map<MultiStem,MultiStem> multiStemToSelfMap, int year, int sampling) {
        SparseRealMatrix matrix = new OpenMapBigRealMatrix(data.size(),data.size());
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

            Collection<MultiStem> cooccurringStems = documentStems.stream().filter(docStem->data.contains(docStem)).map(docStem->multiStemToSelfMap.get(docStem)).collect(Collectors.toList());

            if(debug)
                System.out.println("Num coocurrences: "+cooccurringStems.size());

            // Unavoidable n-squared part
            for(MultiStem stem1 : cooccurringStems) {
                for (MultiStem stem2 : cooccurringStems) {
                    matrix.addToEntry(stem1.getIndex(), stem2.getIndex(), 1);
                }
            }
            return null;
        };

        KeywordModelRunner.streamElasticSearchData(year, transformer, sampling);
        return matrix;
    }


}
