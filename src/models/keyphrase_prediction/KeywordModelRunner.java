package models.keyphrase_prediction;

import edu.stanford.nlp.util.*;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.keyphrase_prediction.models.*;
import models.keyphrase_prediction.scorers.KeywordScorer;

import models.keyphrase_prediction.scorers.TechnologyScorer;
import models.keyphrase_prediction.stages.*;
import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.*;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilders;

import org.gephi.graph.api.Node;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;

import user_interface.ui_models.portfolios.items.Item;
import visualization.Visualizer;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class KeywordModelRunner {
    public static final boolean debug = false;

    public static void main(String[] args) {
        runModel(false);
    }

    public static void runModel(boolean thisYearOnly) {
        Model model = new TimeDensityModel();

        final int windowSize = model.getWindowSize();

        boolean runStage1 = model.isRunStage1();
        boolean runStage2 = model.isRunStage2();
        boolean runStage3 = model.isRunStage3();
        boolean runStage4 = model.isRunStage4();
        boolean runStage5 = model.isRunStage5();


        final int endYear = LocalDate.now().getYear();
        final int startYear = endYear - 25;

        // stage 1
        Map<Integer,Stage1> stage1Map = Collections.synchronizedMap(new HashMap<>());
        for(int i = startYear; i <= endYear; i++) {
            final int year = i;
            System.out.println("Starting year: "+year);
            // group results by time windows in years
            Stage1 stage1 = new Stage1(year,model);
            stage1.run(runStage1 || (thisYearOnly&&year==endYear));
            stage1Map.put(year,stage1);
        }

        // time density stage
        System.out.println("Computing time densities...");
        stage1Map.entrySet().parallelStream().forEach(e->{
            Collection<MultiStem> multiStems = new ArrayList<>(e.getValue().get().keySet());
            reindex(multiStems);
            double[][] timeDensityMatrix = new double[multiStems.size()][endYear-startYear+1];
            multiStems.parallelStream().forEach(stem->{
                double[] row = stage1Map.entrySet().stream().sorted(Comparator.comparing(e2->e2.getKey())).mapToDouble(e2->{
                    return e2.getValue().get().getOrDefault(stem,new AtomicLong(0)).doubleValue();
                }).toArray();
                timeDensityMatrix[stem.getIndex()]=row;
            });
            Collection<MultiStem> filteredStems = new HashSet<>(Stage.applyFilters(new TechnologyScorer(), new Array2DRowRealMatrix(timeDensityMatrix,false),multiStems,Math.round(0.6*multiStems.size()),0.4,1.0,0));
            e.getValue().set(e.getValue().get().entrySet().parallelStream().filter(e2->filteredStems.contains(e2.getKey())).collect(Collectors.toMap(e2->e2.getKey(),e2->e2.getValue())));
            System.out.println("Finished time densities for year "+e.getKey());
        });


        // stage 2
        System.out.println("Pre-grouping data for stage 2...");
        Map<Integer,Map<MultiStem,AtomicLong>> stage1TimeWindowStemMap = computeTimeWindowCountMap(startYear, endYear, windowSize, stage1Map);
        Map<Integer,Stage2> stage2Map = Collections.synchronizedMap(new HashMap<>());
        stage1TimeWindowStemMap.forEach((year,countMap)->{
            System.out.println("Starting year: "+year);
            Stage2 stage2 = new Stage2(countMap, model, year);
            stage2.run(runStage2 || (thisYearOnly&&year==endYear));
            stage2Map.put(year,stage2);
        });

        // stage 3
        System.out.println("Pre-grouping data for stage 3...");
        Map<Integer,Collection<MultiStem>> stage2TimeWindowStemMap = computeTimeWindowStemMap(startYear, endYear, windowSize, stage2Map, 2);
        Map<Integer,Stage3> stage3Map = Collections.synchronizedMap(new HashMap<>());
        stage2TimeWindowStemMap.forEach((year,multiStems)->{
            System.out.println("Starting year: "+year);
            Stage3 stage3 = new Stage3(multiStems, model, year);
            stage3.run(runStage3 || (thisYearOnly&&year==endYear));
            stage3Map.put(year,stage3);
        });

        // stage 4
        System.out.println("Pre-grouping data for stage 4...");
        Map<Integer,Collection<MultiStem>> stage3TimeWindowStemMap = computeTimeWindowStemMap(startYear, endYear, windowSize, stage3Map, 3);
        Map<Integer,Stage4> stage4Map = Collections.synchronizedMap(new HashMap<>());
        stage3TimeWindowStemMap.forEach((year,multiStems)->{
            System.out.println("Starting year: "+year);
            Stage4 stage4 = new Stage4(multiStems, model, year);
            stage4.run(runStage4 || (thisYearOnly&&year==endYear));
            stage4Map.put(year,stage4);
        });

        // stage 5
        System.out.println("Starting stage 5...");
        Map<Integer,Collection<MultiStem>> stage4TimeWindowStemMap = computeTimeWindowStemMap(startYear, endYear, windowSize, stage4Map, 4);
        Map<Integer,Stage5> stage5Map = Collections.synchronizedMap(new HashMap<>());
        stage4TimeWindowStemMap.forEach((year,multiStems)->{
            System.out.println("Starting year: "+year);
            Stage1 stage1 = stage1Map.get(year);
            if(stage1!=null) {
                Stage5 stage5 = new Stage5(stage1, multiStems, model, year);
                stage5.run(runStage5 || (thisYearOnly&&year==endYear));
                stage5Map.put(year, stage5);
            }
        });

        Map<String,List<String>> assetToTechnologyMap = Collections.synchronizedMap(new HashMap<>());
        stage5Map.entrySet().stream().parallel().forEach((e)->{
            System.out.println("Starting year: "+e.getKey());
            e.getValue().get().forEach((asset,techList)->{
                assetToTechnologyMap.put(asset,techList);
            });
        });

        saveModelMap(model,assetToTechnologyMap);
        System.out.println("Num assets classified: "+assetToTechnologyMap.size());
    }


    public static void updateLatest() {
        runModel(true);
    }


    public static Map<String,List<String>> loadModelMap(Model model) {
        return (Map<String,List<String>>) Database.tryLoadObject(new File(Constants.DATA_FOLDER+"keyword_asset_to_keyword_final_model_"+model.getModelName()+"_map.jobj"));
    }

    public static void saveModelMap(Model model, Map<String,List<String>> assetToTechnologyMap) {
        Database.trySaveObject(assetToTechnologyMap, new File(Constants.DATA_FOLDER+"keyword_asset_to_keyword_final_model_"+model.getModelName()+"_map.jobj"));
    }

    private static Map<Integer,Collection<MultiStem>> computeTimeWindowStemMap(int startYear, int endYear, int windowSize, Map<Integer,? extends Stage<Collection<MultiStem>>> stageMap, int stageNum) {
        Map<Integer,Collection<MultiStem>> timeWindowStemMap = Collections.synchronizedMap(new HashMap<>());
        for(int i = startYear; i <= endYear; i++) {
            List<Collection<MultiStem>> multiStems = new ArrayList<>();
            for(int j = i-1-(windowSize/2); j <= Math.min(i-1+(windowSize/2), endYear); j++) {
                Stage<Collection<MultiStem>> stage = stageMap.get(j);
                if(stage!=null&&stage.get()!=null) {
                    multiStems.add(stage.get());
                }
            }
            if(multiStems.isEmpty()) continue;
            Collection<MultiStem> mergedStems = multiStems.stream().flatMap(list->list.stream()).distinct().collect(Collectors.toList());
            timeWindowStemMap.put(i,mergedStems);
            System.out.println("Num stems in "+i+": "+mergedStems.size());
        }
        double scoreThreshold = 200f;
        double minEdgeScore = 20f;
        File visualizationFile = new File("data/visualizations-"+stageNum+"-"+LocalDate.now().toString());
        System.out.println("Starting to create visualization: "+visualizationFile.getName());
        createVisualization(timeWindowStemMap,scoreThreshold,minEdgeScore,new File("data/keyword-visualization"));
        System.out.println("Done.");
        return timeWindowStemMap;
    }

    private static Map<Integer,Map<MultiStem,AtomicLong>> computeTimeWindowCountMap(int startYear, int endYear, int windowSize, Map<Integer,Stage1> stageMap) {
        Map<Integer,Map<MultiStem,AtomicLong>> timeWindowStemMap = Collections.synchronizedMap(new HashMap<>());
        for(int i = startYear; i <= endYear; i++) {
            List<Map<MultiStem,AtomicLong>> multiStems = new ArrayList<>();
            for(int j = i-1-(windowSize/2); j <= Math.min(i-1+(windowSize/2), endYear); j++) {
                Stage<Map<MultiStem,AtomicLong>> stage = stageMap.get(j);
                if(stage!=null&&stage.get()!=null) {
                    multiStems.add(stage.get());
                }
            }
            if(multiStems.isEmpty()) continue;

            Map<MultiStem,AtomicLong> mergedCounts = multiStems.parallelStream().reduce((m1,m2)-> {
                Map<MultiStem, AtomicLong> m3 = Collections.synchronizedMap(new HashMap<>());
                m1.entrySet().forEach(e -> m3.put(e.getKey(), e.getValue()));
                m2.entrySet().forEach(e -> {
                    m3.putIfAbsent(e.getKey(), new AtomicLong(0));
                    m3.get(e.getKey()).getAndAdd(e.getValue().get());
                });
                return m3;
            }).get();

            timeWindowStemMap.put(i,mergedCounts);
            System.out.println("Num stems in "+i+": "+mergedCounts.size());
        }
        return timeWindowStemMap;
    }


    public static void reindex(Collection<MultiStem> multiStems) {
        AtomicInteger cnt = new AtomicInteger(0);
        multiStems.parallelStream().forEach(multiStem -> {
            multiStem.setIndex(cnt.getAndIncrement());
        });
    }

    public static void streamElasticSearchData(QueryBuilder query, Function<SearchHit,Item> transformer, int sampling) {
        TransportClient client = DataSearcher.getClient();
        SearchRequestBuilder search = client.prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.TYPE_NAME)
                .setScroll(new TimeValue(60000))
                .setExplain(false)
                .setFrom(0)
                .setSize(10000)
                .setFetchSource(new String[]{Constants.ABSTRACT,Constants.INVENTION_TITLE},new String[]{})
                .setQuery(query);
        if(sampling>0) {
            search = search.addSort(SortBuilders.scoreSort());
        }
        if(debug) {
            System.out.println(search.request().toString());
        }

        SearchResponse response = search.get();
        DataSearcher.iterateOverSearchResults(response, transformer, sampling, false);
    }

    public static void streamElasticSearchData(int year, Function<SearchHit,Item> transformer, int sampling) {
        QueryBuilder query;
        if(year>0) {
            LocalDate dateMin = LocalDate.of(year, 1, 1);
            LocalDate dateMax = dateMin.plusYears(1);
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                    .filter(QueryBuilders.rangeQuery(Constants.FILING_DATE).gte(dateMin.toString()).lt(dateMax.toString()));
            if(sampling>0) {
                boolQuery = boolQuery.must(QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(),ScoreFunctionBuilders.randomFunction(23)));
            }
            query = new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME, boolQuery,false)
                    .innerHit(new InnerHitBuilder()
                            .setSize(1)
                            .setFetchSourceContext(new FetchSourceContext(true, new String[]{Constants.FILING_DATE,Constants.WIPO_TECHNOLOGY}, new String[]{}))
                    );
        } else {
            query = sampling>0 ? QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(), ScoreFunctionBuilders.randomFunction(69))
                    : QueryBuilders.matchAllQuery();
        }
        if(sampling > 0) {
            // remove plant and design patents
            QueryBuilders.boolQuery()
                    .must(query)
                    .filter(
                            QueryBuilders.boolQuery()
                                    .mustNot(QueryBuilders.prefixQuery(Constants.NAME, "PP"))
                                    .mustNot(QueryBuilders.prefixQuery(Constants.NAME, "D"))
                    );
        }

        streamElasticSearchData(query,transformer,sampling);
    }


    public static void writeToCSV(Collection<MultiStem> multiStems, File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Multi-Stem, Key Phrase, Score\n");
            multiStems.forEach(e->{
                try {
                    writer.write(e.toString()+","+e.getBestPhrase()+","+e.getScore()+"\n");
                }catch(Exception e2) {
                    e2.printStackTrace();
                }
            });
            writer.flush();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void createVisualization(Map<Integer,Collection<MultiStem>> yearToMultiStemMap, double scoreThreshold, double minEdgeScore, File file) {
        Map<String,Node> nodeMap = Collections.synchronizedMap(new HashMap<>());
        Visualizer visualizer = new Visualizer(file.getAbsolutePath());
        yearToMultiStemMap.forEach((year,multiStems)->{
            Color color = Color.BLUE;
            // now we have keywords
            reindex(multiStems);
            Map<MultiStem,MultiStem> multiStemToSelfMap = multiStems.parallelStream().collect(Collectors.toMap(e->e,e->e));
            double[][] matrix = Stage3.buildMMatrix(multiStems,multiStemToSelfMap,year,100000).getData();
            double[] sums = Stream.of(matrix).mapToDouble(row-> DoubleStream.of(row).sum()).toArray();
            Node[] nodes = new Node[matrix.length];
            multiStems.forEach(multiStem->{
                float score = (float)sums[multiStem.getIndex()];
                if(score >= scoreThreshold) {
                    Node node = nodeMap.get(multiStem.getBestPhrase());
                    if(node==null) {
                        node = visualizer.addNode(multiStem.getBestPhrase(), score, color);
                        nodeMap.put(multiStem.getBestPhrase(),node);
                    }
                    nodes[multiStem.getIndex()] = node;
                }
            });
            multiStems.forEach(stem->{
                Node node = nodes[stem.getIndex()];
                if(node==null)return;
                multiStems.forEach(stem2->{
                    Node node2 = nodes[stem2.getIndex()];
                    if(node2==null)return;
                    if(!node.getLabel().equals(node2.getLabel())) {
                        float score = (float) matrix[stem.getIndex()][stem2.getIndex()];
                        if(score>=minEdgeScore) {
                            visualizer.addEdge(node, node2, score, Color.BLACK);
                        }
                    }
                });
            });
            System.out.println("Saving visualizer...");
            visualizer.save();

        });
        visualizer.save();
    }
}
