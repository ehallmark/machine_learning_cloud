package models.keyphrase_prediction;


import cpc_normalization.CPC;
import cpc_normalization.CPCCleaner;
import cpc_normalization.CPCHierarchy;

import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.models.TimeDensityModel;

import models.keyphrase_prediction.stages.*;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SparseRealMatrix;

import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import tools.OpenMapBigRealMatrix;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.portfolios.items.Item;
import util.Pair;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class CPCKeywordModel extends Stage<Map<String,Collection<String>>> {
    public static final boolean debug = false;
    private static final File modelFile = new File(Constants.DATA_FOLDER+"cpc_keyword_model.jobj");

    public CPCKeywordModel(int year) {
        super(new TimeDensityModel(),year);
    }

    public static void main(String[] args) {
        Map<String,Collection<String>> technologyMap = new CPCKeywordModel(2010).run(true);
        System.out.println("Tech map size: "+technologyMap.size());
        Database.trySaveObject(technologyMap,modelFile);
    }

    @Override
    public Map<String,Collection<String>> run(boolean _t) {
        Map<String,String> cpcToRawTitleMap = Database.getClassCodeToClassTitleMap();
        CPCHierarchy cpcHierarchy = new CPCHierarchy();
        cpcHierarchy.loadGraph();

        for(int CPC_DEPTH = 1; CPC_DEPTH <= 4; CPC_DEPTH++) {
            System.out.println("STARTING CPC DEPTH: "+CPC_DEPTH);
            Collection<CPC> mainGroup = CPCCleaner.getCPCsAtDepth(cpcHierarchy.getTopLevel(), CPC_DEPTH);
            mainGroup.forEach(cpc->{
                cpcHierarchy.getLabelToCPCMap().put(cpc.getName(),cpc);
            });

            System.out.println("Num group level cpcs: " + mainGroup.size());

            Stage1 stage1 = new Stage1(new TimeDensityModel(),1,year);

            Function<Pair<CPC, String>, Item> transformer = hit -> {
                String text = hit._2.toLowerCase();
                Item item = new Item(hit._1.getName());
                item.addData(Stage.ASSET_ID, hit._1.getName());
                item.addData(Stage.TEXT, text);
                item.addData("CPC", hit._1);
                return item;
            };

            Function<Function, Void> transformerRunner = v -> {
                mainGroup.parallelStream().map(cpc -> new Pair<>(cpc, cpcToRawTitleMap.get(cpc.getName()))).forEach(e -> {
                    v.apply(e);
                });
                return null;
            };

            Function<Function<Map<String, Object>, Void>, Void> function = attrFunction -> {
                stage1.runSamplingIterator(transformer, transformerRunner, attrFunction);
                return null;
            };

            stage1.buildVocabularyCounts(function, attributes -> {
                CPC cpc = (CPC) attributes.get("CPC");
                cpc.setKeywords(new HashSet<>((Collection<MultiStem>) attributes.get(Stage.APPEARED)));
                return null;
            });

            Map<MultiStem, AtomicLong> wordToDocCounter = stage1.get();
            Map<MultiStem, MultiStem> selfMap = stage1.get().keySet().parallelStream().collect(Collectors.toMap(e -> e, e -> e));
            System.out.println("Vocab size: " + wordToDocCounter.size());

            AtomicInteger cnt = new AtomicInteger(0);
            cpcHierarchy.getLabelToCPCMap().values().parallelStream().filter(cpc -> mainGroup.contains(cpc)).forEach(cpc -> {
                if (cpc.getKeywords() == null || cpc.getKeywords().isEmpty()) {
                    return;
                }
                // pick the one with best tfidf
                cpc.getKeywords().forEach(word -> {
                    double docCount = wordToDocCounter.getOrDefault(word, new AtomicLong(1)).get();
                    double tf = 1d;
                    double idf = Math.log(cpcHierarchy.getLabelToCPCMap().size() / (docCount));
                    double u = word.getStems().length;
                    double l = word.toString().length();
                    double score = tf * idf * u * u * l;
                    if (word.getStems().length > 1) {
                        double denom = Stream.of(word.getStems()).mapToDouble(stem -> wordToDocCounter.getOrDefault(new MultiStem(new String[]{stem}, -1), new AtomicLong(1)).get()).average().getAsDouble();
                        score *= docCount / Math.sqrt(denom);
                    }
                    word.setScore((float) score);
                });
                Set<MultiStem> temp = Collections.synchronizedSet(new HashSet<>());
                temp.addAll(cpc.getKeywords().stream().sorted((s1, s2) -> Float.compare(s2.getScore(), s1.getScore())).map(word -> selfMap.get(word)).limit(5).collect(Collectors.toList()));
                cpc.setKeywords(temp);
                if (cnt.getAndIncrement() % 10000 == 9999) {
                    System.out.println("" + cnt.get() + " / " + cpcHierarchy.getLabelToCPCMap().size());
                }
            });

            System.out.println("Main group: " + mainGroup.size());
            Set<MultiStem> stems = mainGroup.parallelStream().filter(cpc -> cpc.getKeywords() != null).flatMap(cpc -> cpc.getKeywords().stream()).collect(Collectors.toSet());
            System.out.println("num stems: " + stems.size());
            System.out.println("total words: " + stage1.get().size());
        }
        Set<MultiStem> technologyStems = cpcHierarchy.getLabelToCPCMap().values().parallelStream().filter(cpc -> cpc.getKeywords() != null).flatMap(cpc -> cpc.getKeywords().stream()).collect(Collectors.toSet());
        System.out.println("total num stems: " + technologyStems.size());
        //Stage5 stage5 = new Stage5(stage1, stems, new TimeDensityModel());
        //stage5.run(true);

        AssetToCPCMap assetToCPCMap = new AssetToCPCMap();
        Map<String,Set<String>> patentCPCMap = assetToCPCMap.getPatentDataMap();
        Map<String,Set<String>> appCPCMap = assetToCPCMap.getApplicationDataMap();

        Model model = new TimeDensityModel();

        boolean alwaysRerun = false;

        // stage 1

        Stage1 stage1 = new Stage1(model,year);
        stage1.run(alwaysRerun);
        Map<MultiStem,AtomicLong> multiStemToDocumentCount = stage1.get();
        //if(alwaysRerun)stage1.createVisualization();

        // time density stage
        System.out.println("Computing time densities...");

        Set<MultiStem> multiStems;

        // stage 2
        System.out.println("Pre-grouping data for stage 2...");
        Stage2 stage2 = new Stage2(stage1.get(), model, year);
        stage2.run(alwaysRerun);
        //if(alwaysRerun)stage2.createVisualization();
        multiStems = stage2.get();


        System.out.println("Pre-grouping data for time density stage...");
        TimeDensityStage timeDensityStage = new TimeDensityStage(multiStems, model, year);
        timeDensityStage.run(alwaysRerun);
        //if(alwaysRerun) timeDensityStage.createVisualization();
        multiStems = timeDensityStage.get();

        // stage 3
        System.out.println("Pre-grouping data for stage 3...");
        Stage3 stage3 = new Stage3(multiStems, model, year);
        stage3.run(alwaysRerun);
        //if(alwaysRerun) stage3.createVisualization();
        multiStems = stage3.get();

        // stage 4
        System.out.println("Pre-grouping data for cpc density stage...");
        CPCDensityStage CPCDensityStage = new CPCDensityStage(multiStems, model, year);
        CPCDensityStage.run(alwaysRerun);
        // CPCDensityStage.createVisualization();
        multiStems = CPCDensityStage.get();

        SparseRealMatrix matrix = new OpenMapBigRealMatrix(multiStems.size(),technologyStems.size());
        KeywordModelRunner.reindex(multiStems);
        AtomicInteger idx = new AtomicInteger(0);
        Map<MultiStem,Integer> importantToIndex = technologyStems.parallelStream().collect(Collectors.toMap(m->m,m->idx.getAndIncrement()));
        Map<Integer,MultiStem> indexToImportant = importantToIndex.entrySet().parallelStream().collect(Collectors.toMap(e->e.getValue(),e->e.getKey()));
        Map<MultiStem,MultiStem> multiStemToSelfMap = Collections.synchronizedMap(multiStems.parallelStream().collect(Collectors.toMap(e->e,e->e)));

        Function<Map<String,Object>,Void> cooccurrenceFunction = attributes -> {
            Map<MultiStem,AtomicInteger> appeared = (Map<MultiStem,AtomicInteger>)attributes.get(Stage.APPEARED_WITH_COUNTS);
            Map<MultiStem,AtomicInteger> allCooocurrences = appeared.entrySet().stream().filter(e->multiStemToSelfMap.containsKey(e.getKey())).collect(Collectors.toMap(e->multiStemToSelfMap.get(e.getKey()),e->e.getValue()));
            Map<MultiStem,AtomicInteger> importantCoocurrences = allCooocurrences.entrySet().stream().filter(e->technologyStems.contains(e.getKey())).collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));

            if(debug)
                System.out.println("Num coocurrences: "+importantCoocurrences.size());

            // Unavoidable n-squared part
            importantCoocurrences.entrySet().forEach(s2->{
                int idx2 = importantToIndex.get(s2.getKey());
                allCooocurrences.entrySet().forEach(s1->{
                    matrix.addToEntry(s1.getKey().getIndex(),idx2,(double) s1.getValue().get()*s2.getValue().get());
                });
            });
            return null;
        };
        runSamplingIterator(cooccurrenceFunction);

        // turn of sampling
        this.sampling=-1;

        AtomicInteger notFoundCounter = new AtomicInteger(0);
        AtomicInteger cnt = new AtomicInteger(0);
        Map<String,Collection<String>> data = Collections.synchronizedMap(new HashMap<>());
        Function<Map<String,Object>,Void> attributesFunction = map-> {
            String asset = map.get(Stage.ASSET_ID).toString();
            Map<MultiStem,AtomicInteger> documentStems = (Map<MultiStem,AtomicInteger>)map.get(Stage.APPEARED_WITH_COUNTS);

            RealVector result = documentStems.entrySet().parallelStream().map(e->{
                MultiStem stem = multiStemToSelfMap.get(e.getKey());
                if(stem==null)return null;

                double df = multiStemToDocumentCount.getOrDefault(e.getKey(),new AtomicLong(0)).get();
                double tf = e.getValue().get();
                double score = tf / Math.log(Math.E+df);

                RealVector vector = matrix.getRowVector(stem.getIndex()).mapMultiply(score);

                return vector;
            }).filter(s->s!=null).reduce((p1,p2)->{
                return p1.add(p2);
            }).orElse(null);

            List<String> technologies = null;
            if(result!=null) {
                // integrate cpc information
                Collection<String> cpcs = patentCPCMap.getOrDefault(asset,appCPCMap.getOrDefault(asset, new HashSet<>())).stream().map(cpc-> ClassCodeHandler.convertToLabelFormat(cpc)).collect(Collectors.toList());
                if(!cpcs.isEmpty()) {
                    cpcs.forEach(cpcLabel->{
                        CPC cpc = cpcHierarchy.getLabelToCPCMap().get(cpcLabel);
                        if(cpc!=null&&cpc.getKeywords()!=null) {
                            cpc.getKeywords().forEach(word -> {
                                int i = importantToIndex.get(word);
                                result.addToEntry(i, 1d / cpcs.size());
                                result.setEntry(i, result.getEntry(i) * 2);
                            });
                        }
                    });
                }

                int maxIndex = result.getMaxIndex();
                if(maxIndex >= 0) {
                    technologies = Arrays.asList(indexToImportant.get(maxIndex).getBestPhrase());
                }
                data.put(asset,technologies);
                if (debug)
                    System.out.println("Technologies for " + asset + ": " + String.join("; ", technologies));
            }

            if(technologies==null) {
                notFoundCounter.getAndIncrement();
                if(notFoundCounter.get()%10000==9999) {
                    System.out.println("Missing technologies for: "+notFoundCounter.get());
                }
            } else {
                if(technologies!=null&&cnt.getAndIncrement()%10000==9999) {
                    System.out.println("Technologies for " + asset + ": " + String.join("; ", technologies));
                }
            }
            return null;
        };

        runSamplingIterator(attributesFunction);

        return data;
    }

}
