package models.keyphrase_prediction.stages;

import elasticsearch.DataIngester;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.scorers.TechnologyScorer;
import models.keyphrase_prediction.scorers.TermhoodScorer;
import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.Stemmer;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
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
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/12/17.
 */
public class Stage4 implements Stage<Collection<MultiStem>> {
    private static final boolean debug = false;
    private static final File stage4File = new File("data/keyword_model_keywords_set_stage4.jobj");
    private Collection<MultiStem> keywords;
    private long targetCardinality;
    private int year;
    private final int maxCpcLength;
    public Stage4(Collection<MultiStem> keywords, long targetCardinality, int year, int maxCpcLength) {
        this.keywords = keywords;
        this.year=year;
        this.maxCpcLength=maxCpcLength;
        this.targetCardinality=targetCardinality;
    }

    @Override
    public Collection<MultiStem> get() {
        return keywords;
    }

    @Override
    public void loadData() {
        keywords = (Collection<MultiStem>)Database.loadObject(getFile(year));
    }

    @Override
    public File getFile(int year) {
        return new File(stage4File.getAbsolutePath()+year);
    }

    @Override
    public Collection<MultiStem> run(boolean run) {
        if(run) {
            // apply filter 3
            System.out.println("Starting year: "+year);
            KeywordModelRunner.reindex(keywords);
            RealMatrix T = buildTMatrix(keywords, year, maxCpcLength)._2;

            keywords = KeywordModelRunner.applyFilters(new TechnologyScorer(), T, keywords, targetCardinality, 0.2, 0.95);
            Database.saveObject(keywords, getFile(year));
            // write to csv for records
            KeywordModelRunner.writeToCSV(keywords,new File("data/keyword_model_stage4-"+year+".csv"));
        } else {
            loadData();
        }
        return keywords;
    }


    public static Pair<Map<String,Integer>,RealMatrix> buildTMatrix(Collection<MultiStem> multiStems, int year, int maxCpcLength) {
        // create cpc code co-occurrrence statistics
        List<String> allCpcCodes = Database.getClassCodes().stream().map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).distinct().collect(Collectors.toList());
        System.out.println("Num cpc codes found: "+allCpcCodes.size());
        Map<String,Integer> cpcCodeIndexMap = new HashMap<>();
        AtomicInteger idx = new AtomicInteger(0);
        allCpcCodes.forEach(cpc->cpcCodeIndexMap.put(cpc,idx.getAndIncrement()));

        RealMatrix matrix = new OpenMapRealMatrix(multiStems.size(),allCpcCodes.size());

        final AssetToCPCMap assetToCPCMap = new AssetToCPCMap();
        Function<SearchHit,Item> transformer = hit-> {
            String asset = hit.getId();

            Collection<String> currentCpcs = assetToCPCMap.getApplicationDataMap().getOrDefault(asset,assetToCPCMap.getPatentDataMap().get(asset));
            if(currentCpcs==null||currentCpcs.isEmpty()) return null;

            int[] cpcIndices = currentCpcs.stream().map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).map(cpc->cpcCodeIndexMap.get(cpc)).filter(cpc->cpc!=null).mapToInt(i->i).toArray();
            if(cpcIndices==null||cpcIndices.length==0) return null;

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

            for(MultiStem stem : cooccurringStems) {
                for (int cpcIdx : cpcIndices) {
                    synchronized (matrix) {
                        matrix.addToEntry(stem.getIndex(), cpcIdx, 1);
                    }
                }
            }

            return null;
        };

        KeywordModelRunner.streamElasticSearchData(year, transformer, 100000);
        return new Pair<>(cpcCodeIndexMap,matrix);
    }

}
