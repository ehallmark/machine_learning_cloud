package models.wipo_technology_prediction;

import ch.qos.logback.classic.Level;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.classification_models.WIPOHelper;
import models.iterators.AbstractAssetDataSetIterator;
import models.similarity_models.cpc2vec_model.CPC2VecPipelineManager;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;
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
    public static final String MODEL_NAME = "wipo_prediction_model";
    private static final File INPUT_DATA_FOLDER = new File("wipo_prediction_input_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"wipo_predictions/predictions_map.jobj");
    private static Map<String,String> wipoTechnologyMap;
    private static final File wipoTechnologyMapFile = new File(Constants.DATA_FOLDER+"wipo_prediction_model_asset_to_technology_map.jobj");
    private static List<String> wipoTechnologyList;
    private static final int BATCH_SIZE = 128;
    private static final int TEST_BATCH_SIZE = 512;

    // default label function
    private static Function<String,INDArray> labelsFunc = asset -> {
        if(getWipoTechnologyMap().containsKey(asset)) {
            INDArray label = Nd4j.zeros(wipoTechnologyList.size());
            String wipo = getWipoTechnologyMap().get(asset);
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
    DefaultPipelineManager<?,INDArray> cpcModel;
    public WIPOPredictionPipelineManager(String modelName, DefaultPipelineManager<?,INDArray> cpcModel) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.modelName=modelName;
        this.cpcModel=cpcModel;
    }

    @Override
    public DataSetManager<DataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            //datasetManager = new PreSaveDataSetManager(dataFolder);
            setDatasetManager();
        }
        return datasetManager;
    }

    public static synchronized Map<String,String> getWipoTechnologyMap() {
        if(wipoTechnologyMap==null) {
            wipoTechnologyMap = (Map<String,String>) Database.tryLoadObject(wipoTechnologyMapFile);
        }
        return wipoTechnologyMap;
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
        wipoTechnologyMap = Collections.synchronizedMap(new HashMap<>());

        Consumer<Pair<String,String>> assetWipoConsumer = pair -> {
            wipoTechnologyMap.put(pair.getFirst(), pair.getSecond());
        };

        iterateOverDocuments(assetWipoConsumer,null);

        Database.trySaveObject(wipoTechnologyMap,wipoTechnologyMapFile);
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
            if(trainAssets==null) splitData();
            Map<String,INDArray> assetCPCVectors = cpcModel.loadPredictions();
            this.nInputs = assetCPCVectors.entrySet().stream().findAny().get().getValue().length();
            this.featuresFunc = assetCPCVectors::get;
            datasetManager = new NoSaveDataSetManager<>(
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

    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        boolean rebuildPrerequisites = false;

        int nEpochs = 5;
        String modelName = MODEL_NAME;

        DefaultPipelineManager<?,INDArray> cpcModel = new CPC2VecPipelineManager(CPC2VecPipelineManager.MODEL_NAME,-1);

        setLoggingLevel(Level.INFO);
        WIPOPredictionPipelineManager pipelineManager = new WIPOPredictionPipelineManager(modelName,cpcModel);

        rebuildPrerequisites = rebuildPrerequisites || !wipoTechnologyMapFile.exists() ; // Check if wipoCPCTechnologyMapFile exists

        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);

    }
}
