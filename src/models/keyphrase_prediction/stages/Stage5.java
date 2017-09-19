package models.keyphrase_prediction.stages;

import elasticsearch.DataIngester;
import models.classification_models.WIPOHelper;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import org.apache.commons.math3.linear.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import seeding.Constants;
import seeding.Database;
import tools.OpenMapBigRealMatrix;
import tools.Stemmer;
import user_interface.ui_models.attributes.WIPOTechnologyAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.portfolios.items.Item;
import util.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
public class Stage5 extends Stage<Map<String,List<String>>> {
    private static final boolean debug = false;
    private Collection<MultiStem> multiStems;
    private int maxCpcLength;
    private Map<String,List<String>> data;
    private SparseRealMatrix cooccurenceTable;
    private Map<MultiStem,Integer> oldMultiStemToIdxMap;
    private Map<Integer,MultiStem> idxToMultiStemMap;
    private Collection<MultiStem> oldMultiStems;
    private Map<Integer,AtomicLong> oldMultiStemsCountMap;
    private AtomicInteger defaultToWIPOCounter = new AtomicInteger(0);
    private AtomicInteger notFoundCounter = new AtomicInteger(0);
    public Stage5(Stage1 stage1, Collection<MultiStem> multiStems, Model model, int year) {
        super(model,year);
        this.multiStems=multiStems;
        this.maxCpcLength=model.getMaxCpcLength();
        this.oldMultiStems=new HashSet<>(stage1.get().keySet());
        AtomicInteger cnt = new AtomicInteger(0);
        oldMultiStemToIdxMap = oldMultiStems.stream().collect(Collectors.toMap(s->s,s->cnt.getAndIncrement()));
        this.oldMultiStemsCountMap = stage1.get().entrySet().stream().collect(Collectors.toMap(e->oldMultiStemToIdxMap.get(e.getKey()),e->e.getValue()));
    }

    @Override
    public Map<String, List<String>> run(boolean run) {
        if(getFile().exists()) {
            try {
                loadData();
                run = false;
            } catch(Exception e) {
                run = true;
            }
        }
        if(run) {
            System.out.println("Starting year: "+year);
            // get cooccurrence map
            KeywordModelRunner.reindex(multiStems);
            idxToMultiStemMap = this.multiStems.stream().collect(Collectors.toMap(s->s.getIndex(),s->s));
            getCooccurrenceMap();
            // run model
            runModel();
            Database.saveObject(data, getFile());
            // print sample
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(getFile().getAbsolutePath()+".csv")))) {
                writer.write("Asset,Technologies\n");
                data.entrySet().stream().limit(10000).forEach(e -> {
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
        }
        return data;
    }

    private void getCooccurrenceMap() {
        cooccurenceTable = new OpenMapBigRealMatrix(oldMultiStems.size(),multiStems.size());

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
                    cooccurenceTable.addToEntry(idx,stem.getIndex(),1);
                }
            }

            return null;
        };

        KeywordModelRunner.streamElasticSearchData(year, transformer, 200000);
    }

    private void runModel() {
        Pair<Map<String,Integer>,RealMatrix> pair = Stage4.buildTMatrix(multiStems,year,maxCpcLength);
        RealMatrix T = pair._2;
        Map<String,Integer> cpcToIndexMap = pair._1;
        Map<String,Set<String>> patentCPCMap = new AssetToCPCMap().getPatentDataMap();
        Map<String,Set<String>> appCPCMap = new AssetToCPCMap().getApplicationDataMap();


        data = Collections.synchronizedMap(new HashMap<>());
        Function<SearchHit,Item> transformer = hit-> {
            String asset = hit.getId();

            // handle design and plant patents
            if(asset.startsWith("P")) {
                data.put(asset, Arrays.asList(WIPOHelper.PLANT_TECHNOLOGY));
                return null;
            } else if(asset.startsWith("D")) {
                data.put(asset, Arrays.asList(WIPOHelper.DESIGN_TECHNOLOGY));
                return null;
            }

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
                return cooccurenceTable.getRowVector(i).mapDivide(idf);
            }).reduce((t1,t2)->{
                return t1.add(t2);
            }).orElse(new ArrayRealVector(new double[]{})).toArray();

            int[] cpcIndices = appCPCMap.getOrDefault(asset,patentCPCMap.getOrDefault(asset,Collections.emptySet())).stream()
                    .map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).distinct()
                    .map(cpc->cpcToIndexMap.get(cpc)).filter(idx->idx!=null).mapToInt(i->i).toArray();

            double[] cpcRow = IntStream.of(cpcIndices).mapToObj(i->{
                    return T.getColumnVector(i);
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

            AtomicBoolean foundTechnology = new AtomicBoolean(false);
            if(row!=null) {
                double max = DoubleStream.of(row).max().getAsDouble();
                if(max>0) {
                    if (debug) System.out.println("Max: " + max);
                    List<String> technologies = IntStream.range(0, row.length).filter(i -> row[i] >= max).mapToObj(i -> idxToMultiStemMap.get(i)).filter(tech -> tech != null).map(stem -> stem.getBestPhrase()).distinct().collect(Collectors.toList());
                    if (technologies.size() > 0) {
                        foundTechnology.set(true);
                        data.put(asset, technologies);
                        if (debug)
                            System.out.println("Technologies for " + asset + ": " + String.join("; ", technologies));
                    } else {
                        throw new RuntimeException("Technologies should never be empty...");
                    }
                }
            }

            if(!foundTechnology.get()) {
                // default to wipo
                SearchHits innerHits = hit.getInnerHits().get(DataIngester.PARENT_TYPE_NAME);
                Object wipoTechnology = innerHits == null ? null : (innerHits.getHits()[0].getSourceAsMap().get(Constants.WIPO_TECHNOLOGY));
                if(wipoTechnology!=null) {
                    data.put(asset, Arrays.asList(wipoTechnology.toString()));
                    defaultToWIPOCounter.getAndIncrement();
                    if(defaultToWIPOCounter.get()%10000==9999) {
                        System.out.println("Defaulted to wipo cnt: "+defaultToWIPOCounter.get());
                    }
                } else {
                    notFoundCounter.getAndIncrement();
                    if(notFoundCounter.get()%10000==9999) {
                        System.out.println("Missing technologies for: "+notFoundCounter.get());
                    }
                }
            }
            return null;
        };

        KeywordModelRunner.streamElasticSearchData(year, transformer, -1);
    }
}
