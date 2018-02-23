package models.similarity_models.combined_similarity_model;

import com.google.common.util.concurrent.AtomicDouble;
import data_pipeline.helpers.Function2;
import elasticsearch.DataSearcher;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;
import user_interface.acclaim_compatibility.Parser;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.FilingNameAttribute;
import user_interface.ui_models.attributes.computable_attributes.IsGrantedApplicationAttribute;
import user_interface.ui_models.engines.TextSimilarityEngine;
import user_interface.ui_models.filters.AbstractBooleanExcludeFilter;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AcclaimExpertSearchFilter;
import user_interface.ui_models.portfolios.items.Item;
import wiki.ScrapeWikipedia;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestTextModels extends TestModelHelper {



    public static double testModel(Map<String,Pair<String[],Set<String>>> keywordToWikiAndAssetsMap, Function2<String[],Integer,Set<String>> model, int n) {
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicDouble sum = new AtomicDouble(0d);
        keywordToWikiAndAssetsMap.forEach((keyword,pair)->{
            String[] text = pair.getFirst();
            Set<String> predictions = model.apply(text,n);
            if(predictions!=null&&predictions.size()>0) {
                Set<String> actual = pair.getSecond();
                if(actual!=null&&actual.size()>0) {
                    sum.getAndAdd(((double)intersect(predictions,actual))/((double)(union(predictions,actual))));
                }
            }
            cnt.getAndIncrement();
        });
        return sum.get()/cnt.get();
    }

    private static Map<String,Set<String>> keywordToFilingMap;
    private static final File keywordToFilingMapFile = new File(Constants.DATA_FOLDER+"keyword_to_filing_map_for_test_text_models.jobj");
    private static Map<String,Set<String>> loadKeywordToFilingsMap(List<String> keywords) {
        if(keywordToFilingMap==null) {
            keywordToFilingMap = (Map<String,Set<String>>) Database.tryLoadObject(keywordToFilingMapFile);
            if(keywordToFilingMap==null) {
                System.out.println("Rebuilding keyword to filing map...");
                keywordToFilingMap = Collections.synchronizedMap(new HashMap<>(keywords.size()));
                keywords.forEach(keyword->{
                    Set<String> results = searchForFilings(keyword);
                    if(results!=null&&results.size()>0) {
                        System.out.println("Num results for "+keyword+": "+results.size());
                        keywordToFilingMap.put(keyword,results);
                    }
                });
                System.out.println("Saving...");
                Database.trySaveObject(keywordToFilingMap,keywordToFilingMapFile);
            }
        }
        return keywordToFilingMap;
    }

    private static Set<String> searchForFilings(String keyword) {
        String comparator = Constants.SCORE;
        Collection<AbstractAttribute> attributes = Collections.singleton(new FilingNameAttribute());
        AcclaimExpertSearchFilter filter = new AcclaimExpertSearchFilter();
        QueryBuilder builder = new Parser(SimilarPatentServer.SUPER_USER).parseAcclaimQuery("TAC:("+keyword+")");
        filter.setQuery(builder);
        Collection<AbstractFilter> filters = Arrays.asList(filter, new AbstractBooleanExcludeFilter(new IsGrantedApplicationAttribute(), AbstractFilter.FilterType.BoolFalse));
        List<Item> items = DataSearcher.searchForAssets(attributes,filters,comparator, SortOrder.DESC, 10000, new HashMap<>(),false,false);
        if(items==null) return null;
        return items.stream().map(item->item.getData(Constants.FILING_NAME)).filter(obj->obj!=null).map(obj->obj.toString()).collect(Collectors.toSet());
    };

    public static void main(String[] args) {
        // load input data
        Map<String,List<String>> wikipediaData = ScrapeWikipedia.loadWikipediaMap();
        Map<String,Set<String>> filingData = loadKeywordToFilingsMap(new ArrayList<>(wikipediaData.keySet()));

        final int maxSentences = 20;
        // need to run searches on the keys

        Set<String> allFilings = Collections.synchronizedSet(new HashSet<>());

        final Map<String,Pair<String[],Set<String>>> keywordToWikiAndAssetsMap = wikipediaData.entrySet().stream()
                .map(e->{
                    String[] text = e.getValue().stream().limit(maxSentences)
                            .flatMap(sentence-> Stream.of(sentence.split("\\s+")))
                            .toArray(size->new String[size]);
                    if(text.length==0) return null;
                    Set<String> relevantFilings = filingData.get(e.getKey());
                    if(relevantFilings!=null&&relevantFilings.size()>0) {
                        allFilings.addAll(relevantFilings);
                        return new Pair<>(e.getKey(), new Pair<>(text, relevantFilings));
                    } else return null;
                }).filter(p->p!=null)
                .collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond()));

        // new model
        CombinedCPC2Vec2VAEEncodingPipelineManager encodingPipelineManager1 = CombinedCPC2Vec2VAEEncodingPipelineManager.getOrLoadManager(true);
        encodingPipelineManager1.runPipeline(false,false,false,false,-1,false);
        CombinedCPC2Vec2VAEEncodingModel encodingModel1 = (CombinedCPC2Vec2VAEEncodingModel)encodingPipelineManager1.getModel();
        Map<String,INDArray> allPredictions1 = CombinedDeepCPC2VecEncodingPipelineManager.getOrLoadManager(false).loadPredictions();
        Map<String,INDArray> predictions1 = allFilings.stream().filter(allPredictions1::containsKey).collect(Collectors.toMap(e->e,e->allPredictions1.get(e)));

        final Pair<List<String>,INDArray> filingsWithMatrix1 = createFilingMatrix(predictions1);
        final List<String> filings1 = filingsWithMatrix1.getFirst();
        final INDArray filingsMatrix1 = filingsWithMatrix1.getSecond();
        Function2<String[],Integer,Set<String>> model1 = (text,n) -> {
            INDArray encodingVec = encodingModel1.encodeText(Arrays.asList(text),20);
            if(encodingVec==null)return null;
            return topNByCosineSim(filings1,filingsMatrix1,encodingVec,n);
        };


        // older model
        TextSimilarityEngine encodingModel2 = new TextSimilarityEngine();
        Map<String,INDArray> allPredictions2 = CombinedSimilarityVAEPipelineManager.getOrLoadManager().loadPredictions();
        Map<String,INDArray> predictions2 = allFilings.stream().filter(allPredictions2::containsKey).collect(Collectors.toMap(e->e,e->allPredictions2.get(e)));

        final Pair<List<String>,INDArray> filingsWithMatrix2 = createFilingMatrix(predictions2);
        final List<String> filings2 = filingsWithMatrix2.getFirst();
        final INDArray filingsMatrix2 = filingsWithMatrix2.getSecond();
        Function2<String[],Integer,Set<String>> model2 = (text,n) -> {
            INDArray encodingVec = encodingModel2.encodeText(text);
            if(encodingVec==null)return null;
            return topNByCosineSim(filings2,filingsMatrix2,encodingVec,n);
        };


        System.out.println("All relevant filings size: "+allFilings.size());
        System.out.println("Size of filings (Model 1): "+filings1.size());
        System.out.println("Size of filings (Model 2): "+filings2.size());

        for(int n = 10; n <= 1000; n*=10) {
            double score1 = testModel(keywordToWikiAndAssetsMap, model1, n);
            double score2 = testModel(keywordToWikiAndAssetsMap, model2, n);

            System.out.println("Score for model [n=" + n + "] 1: " + score1);
            System.out.println("Score for model [n=" + n + "] 2: " + score2);
        }
    }
}
