package models.wipo_technology_prediction;

import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.classification_models.WIPOHelper;
import models.iterators.AbstractAssetDataSetIterator;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;
import user_interface.ui_models.portfolios.items.Item;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by ehallmark on 12/13/17.
 */
public class WIPOPredictionPipelineManager extends DefaultPipelineManager<DataSetIterator,String> {
    private static final File INPUT_DATA_FOLDER = new File("wipo_prediction_input_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"wipo_predictions/predictions_map.jobj");
    private static Map<String,Pair<String,Set<String>>> wipoCPCTechnologyMap;
    private static final File wipoCPCTechnologyMapFile = new File("wipo_prediction_model_asset_to_technology_map.jobj");
    private static List<String> wipoTechnologyList;
    private static final int BATCH_SIZE = 128;
    private static final int TEST_BATCH_SIZE = 512;

    // default label function
    private static Function<String,INDArray> labelsFunc = asset -> {
        if(getWipoCPCTechnologyMap().containsKey(asset)) {
            INDArray label = Nd4j.zeros(wipoTechnologyList.size());
            String wipo = getWipoCPCTechnologyMap().get(asset).getFirst();
            int wipoIdx = getWipoTechnologyList().indexOf(wipo);
            if(wipoIdx>=0) {
                label.putScalar(wipoIdx,1);
                return label;
            }
        }
        return null;
    };

    private String modelName;
    private Function<String,INDArray> featuresFunc;
    private int nInputs;
    public WIPOPredictionPipelineManager(String modelName, DefaultPipelineManager<?,INDArray> cpcModel) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.modelName=modelName;
        Map<String,INDArray> assetCPCVectors = cpcModel.loadPredictions();
        this.nInputs = assetCPCVectors.entrySet().stream().findAny().get().getValue().length();
        this.featuresFunc = assetCPCVectors::get;
    }

    @Override
    public DataSetManager<DataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            datasetManager = new PreSaveDataSetManager(dataFolder);
        }
        return datasetManager;
    }

    public static synchronized Map<String,Pair<String,Set<String>>> getWipoCPCTechnologyMap() {
        if(wipoCPCTechnologyMap==null) {
            wipoCPCTechnologyMap = (Map<String,Pair<String,Set<String>>>) Database.tryLoadObject(wipoCPCTechnologyMapFile);
        }
        return wipoCPCTechnologyMap;
    }

    public static synchronized List<String> getWipoTechnologyList() {
        if(wipoTechnologyList==null) {
            wipoTechnologyList = WIPOHelper.getOrderedClassifications();
        }
        return wipoTechnologyList;
    }
    @Override
    public void rebuildPrerequisiteData() {
        System.out.println("Rebuilding prerequisite data... this may take awhile");
        wipoCPCTechnologyMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Set<String>> assetToCPCMap = new AssetToCPCMap().getPatentDataMap();

        Consumer<Pair<String,String>> assetWipoConsumer = pair -> {
            String asset = pair.getFirst();
            Set<String> cpcs = assetToCPCMap.get(asset);
            if(cpcs!=null&&cpcs.size()>0) {
                wipoCPCTechnologyMap.put(asset, new Pair<>(pair.getSecond(),cpcs));
            }
        };

        iterateOverDocuments(assetWipoConsumer,null);

        Database.trySaveObject(wipoCPCTechnologyMap,wipoCPCTechnologyMapFile);
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new WIPOPredictionModel(this, modelName);
        if(!forceRecreateModels) {
            System.out.println("Warning: Loading previous model.");
            try {
                model.loadMostRecentModel();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }

    @Override
    protected void splitData() {
        final int numTest = 20000;

        List<String> allAssets = new ArrayList<>(Database.getCopyOfAllPatents());
        Collections.sort(allAssets);
        Collections.shuffle(allAssets, new Random(69));

        trainAssets = new ArrayList<>(allAssets.subList(numTest*2,allAssets.size()));
        testAssets = new ArrayList<>(allAssets.subList(0,numTest));
        validationAssets = new ArrayList<>(allAssets.subList(numTest,2*numTest));
    }

    @Override
    protected void setDatasetManager() {
        if(datasetManager==null) {
            datasetManager = new PreSaveDataSetManager(
                    dataFolder,
                    getIterator(trainAssets,BATCH_SIZE,true),
                    getIterator(testAssets,TEST_BATCH_SIZE,false),
                    getIterator(validationAssets,TEST_BATCH_SIZE,false)
            );
        }
    }

    private DataSetIterator getIterator(List<String> assets, int batchSize, boolean shuffle) {
        return new AbstractAssetDataSetIterator(assets,featuresFunc,labelsFunc,nInputs,getWipoTechnologyList().size(),batchSize,shuffle);
    }

    public static void iterateOverDocuments(Consumer<Pair<String,String>> assetWipoConsumer, Function<Void,Void> finallyDo) {
        BoolQueryBuilder query = QueryBuilders.boolQuery()
                .must(QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(), ScoreFunctionBuilders.randomFunction(2039852)));

        Map<String,Collection<String>> filingToAssetMap = new FilingToAssetMap().getPatentDataMap();

        SearchRequestBuilder request = DataSearcher.getClient().prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.PARENT_TYPE_NAME)
                .setScroll(new TimeValue(120000))
                .setExplain(false)
                .setFrom(0)
                .setSize(10000)
                .setFetchSource(new String[]{Constants.WIPO_TECHNOLOGY},new String[]{})
                .setQuery(query)
                .addSort(SortBuilders.scoreSort());


        Function<SearchHit,Item> transformer = hit -> {
            String filing = hit.getId();
            Collection<String> assets = filingToAssetMap.get(filing);
            if(assets!=null&&assets.size()>0) {
                Object wipo = hit.getSource().get(Constants.WIPO_TECHNOLOGY);
                if(wipo!=null) {
                    assetWipoConsumer.accept(new Pair<>(assets.stream().findAny().get(), (String)wipo));
                }
            }
            return null;
        };
        SearchResponse response = request.get();
        DataSearcher.iterateOverSearchResults(response, transformer, -1, false);
        System.out.println("Finished iterating ES.");
        if(finallyDo!=null)finallyDo.apply(null);
    }
}
