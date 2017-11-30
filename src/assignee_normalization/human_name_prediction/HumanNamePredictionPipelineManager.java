package assignee_normalization.human_name_prediction;

import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.similarity_models.word2vec_to_cpc_encoding_model.Word2VecToCPCEncodingNN;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AssigneesNestedAttribute;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Created by Evan on 11/30/2017.
 */
public class HumanNamePredictionPipelineManager extends DefaultPipelineManager<DataSetIterator,INDArray> {
    public static final char[] VALID_CHARS = new char[]{'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z',' ','.',',','-','\'','&' };
    private static final File allCompanyNamesFile = new File(Constants.DATA_FOLDER+"human_name_model_all_company_names.jobj");
    private static final File allHumanNamesFile = new File(Constants.DATA_FOLDER+"human_name_model_all_human_names.jobj");
    private static final File INPUT_DATA_FOLDER = new File("human_name_prediction_input_data/");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"human_name_assignee_predictions/predictions_map.jobj");
    private Set<String> humanNames;
    private Set<String> companyNames;
    private String modelName;
    private HumanNamePredictionPipelineManager(File dataFolder, File finalPredictionsFile, String modelName) {
        super(dataFolder, finalPredictionsFile);
        this.modelName=modelName;
    }

    public HumanNamePredictionPipelineManager(String modelName) {
        this(INPUT_DATA_FOLDER,PREDICTION_DATA_FILE,modelName);
    }

    public int inputSize() {
        return VALID_CHARS.length;
    }

    @Override
    public DataSetManager<DataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            setDatasetManager();
        }
        return datasetManager;
    }

    public Set<String> getAllHumanNames() {
        if(humanNames!=null) {
            humanNames = (Set<String>)Database.tryLoadObject(allHumanNamesFile);
        }
        return humanNames;
    }

    public Set<String> getAllCompanyNames() {
        if(companyNames!=null) {
            companyNames = (Set<String>)Database.tryLoadObject(allCompanyNamesFile);
        }
        return companyNames;
    }

    @Override
    public void rebuildPrerequisiteData() {
        final boolean debug = true;

        // need list of companies
        // need list of applicants/inventors
        int limit = -1;
        AtomicInteger cnt = new AtomicInteger(0);
        companyNames= Collections.synchronizedSet(new HashSet<>());
        humanNames = Collections.synchronizedSet(new HashSet<>());

        Function<SearchHit,Item> hitTransformer = hit -> {
            Map<String,Object> source = hit.getSource();
            if(source.containsKey(Constants.ASSIGNEES)) {
                List<Map<String,Object>> assignees = (List<Map<String,Object>>) source.get(Constants.ASSIGNEES);
                assignees.forEach(assignee->{
                    Object name = assignee.get(Constants.ASSIGNEE);

                    if(name==null) {
                        // try first and last name
                        Object firstName = assignee.get(Constants.FIRST_NAME);
                        Object lastName = assignee.get(Constants.LAST_NAME);
                        if(firstName!=null&&lastName!=null) {
                            name = String.join(", ",lastName.toString(),firstName.toString());
                        }
                    }

                    Object role = assignee.get(Constants.ASSIGNEE_ROLE);
                    if(name!=null&&role!=null && name.toString().length()>1 && role.toString().length()>0) {
                        try {
                            int roleNum = Integer.valueOf(role.toString());
                            if(roleNum==1) return; // only partial ownership
                            if(roleNum == 4 || roleNum == 5) {
                                // is human
                                humanNames.add(name.toString());
                                if(debug) System.out.println("HUMAN: "+name.toString());
                            } else {
                                // corporation
                                companyNames.add(name.toString());
                                if(debug) System.out.println("COMPANY: "+name.toString());
                            }

                            // finished
                            if(cnt.getAndIncrement()%10000==9999) {
                                System.out.println("Found "+cnt.get()+" total. "+humanNames.size()+" humans. "+companyNames.size()+" companies.");
                            }
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
            return null;
        };

        SearchRequestBuilder request = DataSearcher.getClient().prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.PARENT_TYPE_NAME)
                .setScroll(new TimeValue(120000))
                .setExplain(false)
                .setFrom(0)
                .setSize(10000)
                .setFetchSource(new String[]{Constants.ASSIGNEES+"."+Constants.ASSIGNEE_ROLE,Constants.ASSIGNEES+"."+Constants.ASSIGNEE,Constants.ASSIGNEES+"."+Constants.FIRST_NAME,Constants.ASSIGNEES+"."+Constants.LAST_NAME},new String[]{})
                .setQuery(QueryBuilders.matchAllQuery());

        SearchResponse response = request.get();
        DataSearcher.iterateOverSearchResults(response, hitTransformer, limit,false);

        System.out.println("Total number of human names: "+humanNames.size());
        System.out.println("Total number of company names: "+companyNames.size());

        // save results
        Database.saveObject(companyNames,allCompanyNamesFile);
        Database.saveObject(humanNames,allHumanNamesFile);
    }

    @Override
    protected void initModel(boolean forceRecreateModel) {
        model = new HumanNamePredictionModel(this, modelName);
        if(!forceRecreateModel) {
            System.out.println("Warning: Loading previous model.");
            try {
                model.loadBestModel();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }

    @Override
    protected void splitData() {

    }

    @Override
    protected void setDatasetManager() {
        getAllHumanNames();
        getAllCompanyNames();

        int numTests = 10000;
    }
}
