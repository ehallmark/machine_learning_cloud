package models.keyphrase_prediction.stages;

import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import org.apache.commons.math3.linear.*;
import org.elasticsearch.search.SearchHit;
import seeding.Constants;
import seeding.Database;
import tools.Stemmer;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.portfolios.items.Item;
import util.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/13/17.
 */
public class Stage5 implements Stage<Map<String,List<String>>> {
    private static final boolean debug = false;
    private static final File stage5File = new File("data/keyword_model_keywords_set_stage5.jobj");
    private Collection<MultiStem> multiStems;
    private int year;
    private int maxCpcLength;
    private Map<String,List<String>> assetToKeywordMap;
    private SparseRealMatrix cooccurenceTable;
    private Map<MultiStem,Integer> oldMultiStemToIdxMap;
    private Map<Integer,MultiStem> idxToMultiStemMap;
    private Collection<MultiStem> oldMultiStems;
    private Map<Integer,AtomicLong> oldMultiStemsCountMap;
    public Stage5(Stage1 stage1, Collection<MultiStem> multiStems, int year, int maxCpcLength) {
        this.multiStems=multiStems;
        this.maxCpcLength=maxCpcLength;
        this.oldMultiStems=new HashSet<>(stage1.get().keySet());
        AtomicInteger cnt = new AtomicInteger(0);
        oldMultiStemToIdxMap = oldMultiStems.stream().collect(Collectors.toMap(s->s,s->cnt.getAndIncrement()));
        this.oldMultiStemsCountMap = stage1.get().entrySet().stream().collect(Collectors.toMap(e->oldMultiStemToIdxMap.get(e.getKey()),e->e.getValue()));
        this.year=year;
    }

    @Override
    public void loadData() {
        assetToKeywordMap = (Map<String,List<String>>) Database.loadObject(getFile(year));
    }

    @Override
    public File getFile(int year) {
        return new File(stage5File.getAbsolutePath()+year);
    }


    @Override
    public Map<String, List<String>> run(boolean run) {
        if(run) {
            System.out.println("Starting year: "+year);
            // get cooccurrence map
            KeywordModelRunner.reindex(multiStems);
            idxToMultiStemMap = this.multiStems.stream().collect(Collectors.toMap(s->s.getIndex(),s->s));
            getCooccurrenceMap();
            // run model
            runModel();
            Database.saveObject(assetToKeywordMap, getFile(year));
            // print sample
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File("data/keyword_asset_to_keyword_map"+year+".csv")))) {
                writer.write("Asset,Technologies\n");
                assetToKeywordMap.entrySet().stream().limit(10000).forEach(e -> {
                    try {
                        writer.write(e.getKey()+","+String.join("; ",e.getValue())+"\n");
                    }catch(Exception _e) {
                        _e.printStackTrace();
                    }
                });
                writer.flush();
            } catch(Exception e) {
                e.printStackTrace();
            }
        } else {
            loadData();
        }
        return assetToKeywordMap;
    }

    @Override
    public Map<String, List<String>> get() {
        return assetToKeywordMap;
    }

    private void getCooccurrenceMap() {
        cooccurenceTable = new OpenMapRealMatrix(oldMultiStems.size(),multiStems.size());

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

            documentStems.removeIf(stem->!oldMultiStems.contains(stem));

            Collection<MultiStem> cooccurringStems = Collections.synchronizedCollection(new ArrayList<>());
            multiStems.parallelStream().forEach(stem->{
                if(documentStems.contains(stem)) {
                    cooccurringStems.add(stem);
                }
            });

            int[] documentStemIndices = documentStems.stream().map(stem->oldMultiStemToIdxMap.get(stem)).filter(i->i!=null).mapToInt(i->i).toArray();

            if(debug)
                System.out.println("Num coocurrences: "+cooccurringStems.size());

            for (int idx : documentStemIndices) {
                for(MultiStem stem : cooccurringStems) {
                    synchronized (cooccurenceTable) {
                        cooccurenceTable.addToEntry(idx,stem.getIndex(),1);
                    }
                }
            }

            return null;
        };

        KeywordModelRunner.streamElasticSearchData(year, transformer, 100000);
    }

    private void runModel() {
        Pair<Map<String,Integer>,RealMatrix> pair = Stage4.buildTMatrix(multiStems,year,maxCpcLength);
        RealMatrix T = pair._2;
        Map<String,Integer> cpcToIndexMap = pair._1;
        AssetToCPCMap assetToCPCMap = new AssetToCPCMap();

        assetToKeywordMap = Collections.synchronizedMap(new HashMap<>());
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

            int[] documentStemIndices = documentStems.stream().map(stem->oldMultiStemToIdxMap.get(stem)).filter(i->i!=null).mapToInt(i->i).toArray();

            double[] stemRow = IntStream.of(documentStemIndices).mapToObj(i->{
                double idf = (1+Math.log(Math.E+oldMultiStemsCountMap.get(i).get())); // inverse document frequency
                synchronized (cooccurenceTable) {
                    return cooccurenceTable.getRowVector(i).mapDivide(idf);
                }
            }).reduce((t1,t2)->{
                return t1.add(t2);
            }).orElse(new ArrayRealVector(new double[]{})).toArray();

            int[] cpcIndices = assetToCPCMap.getApplicationDataMap().getOrDefault(asset,assetToCPCMap.getPatentDataMap().getOrDefault(asset,Collections.emptySet())).stream()
                    .map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).distinct()
                    .map(cpc->cpcToIndexMap.get(cpc)).filter(idx->idx!=null).mapToInt(i->i).toArray();

            double[] cpcRow = IntStream.of(cpcIndices).mapToObj(i->{
                synchronized (T) {
                    return T.getColumnVector(i).copy();
                }

            }).reduce((t1,t2)->{
                return t1.add(t2);
            }).orElse(new ArrayRealVector(new double[]{})).toArray();

            double[] row;
            if(stemRow.length>0||cpcRow.length>0) {
                row = new double[cooccurenceTable.getColumnDimension()];
                Arrays.fill(row,1d);
                for (int i = 0; i < row.length; i++) {
                    if (stemRow.length>0) {
                        row[i]*=(1d+Math.log(1d+stemRow[i]));
                    }
                    if (cpcRow.length>0) {
                        row[i]*=(1d+cpcRow[i]);
                    }
                }
            } else row = null;

            if(row!=null) {
                double max = DoubleStream.of(row).max().getAsDouble();
                if(max>0) {
                    if (debug) System.out.println("Max: " + max);
                    List<String> technologies = IntStream.range(0, row.length).filter(i -> row[i] >= max).mapToObj(i -> idxToMultiStemMap.get(i)).filter(tech -> tech != null).map(stem -> stem.getBestPhrase()).distinct().collect(Collectors.toList());
                    if (technologies.size() > 0) {
                        assetToKeywordMap.put(asset, technologies);
                        if (debug)
                            System.out.println("Technologies for " + asset + ": " + String.join("; ", technologies));
                    } else {
                        throw new RuntimeException("Technologies should never be empty...");
                    }
                }
            }
            return null;
        };

        KeywordModelRunner.streamElasticSearchData(year, transformer, -1);
    }
}
