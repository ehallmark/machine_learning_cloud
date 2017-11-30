package assignee_normalization.human_name_prediction;

import ch.qos.logback.classic.Level;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.portfolios.items.Item;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Created by Evan on 11/30/2017.
 */
public class HumanNamePredictionPipelineManager extends DefaultPipelineManager<DataSetIterator,INDArray> {
    public static final String MODEL_NAME = "human_name_prediction_model";
    public static final char[] VALID_CHARS = new char[]{'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z',' ','.',',','-','&' };
    static {
        Arrays.sort(VALID_CHARS);
    }
    private static final File allCompanyNamesFile = new File(Constants.DATA_FOLDER+"human_name_model_all_company_names.jobj");
    private static final File allHumanNamesFile = new File(Constants.DATA_FOLDER+"human_name_model_all_human_names.jobj");
    private static final File INPUT_DATA_FOLDER = new File("human_name_prediction_input_data/");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"human_name_assignee_predictions/predictions_map.jobj");
    public static final int MAX_NAME_LENGTH = 64;
    public static final int BATCH_SIZE = 32;
    public static final int TEST_BATCH_SIZE = 512;

    private Set<String> humanNames;
    private Set<String> companyNames;
    private String modelName;
    private List<String> testHumans;
    private List<String> trainHumans;
    private List<String> valHumans;
    private List<String> testCompanies;
    private List<String> trainCompanies;
    private List<String> valCompanies;
    private HumanNamePredictionPipelineManager(File dataFolder, File finalPredictionsFile, String modelName) {
        super(dataFolder, finalPredictionsFile);
        this.modelName=modelName;
    }

    public HumanNamePredictionPipelineManager(String modelName) {
        this(INPUT_DATA_FOLDER,PREDICTION_DATA_FILE,modelName);
    }

    public int getNumTimeSteps() {
        return MAX_NAME_LENGTH;
    }

    public int inputSize() {
        return VALID_CHARS.length+1;
    }

    @Override
    public DataSetManager<DataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            setDatasetManager();
        }
        return datasetManager;
    }

    public Set<String> getAllHumanNames() {
        if(humanNames==null) {
            humanNames = (Set<String>)Database.tryLoadObject(allHumanNamesFile);
        }
        return humanNames;
    }

    public Set<String> getAllCompanyNames() {
        if(companyNames==null) {
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
            if(source.containsKey(Constants.INVENTORS)) {
                List<Map<String,Object>> inventors = (List<Map<String,Object>>) source.get(Constants.INVENTORS);
                inventors.forEach(assignee-> {
                    // try first and last name
                    Object firstName = assignee.get(Constants.FIRST_NAME);
                    Object lastName = assignee.get(Constants.LAST_NAME);
                    if (firstName != null && lastName != null) {
                        String name = String.join(", ", lastName.toString().trim(), firstName.toString().trim()).toLowerCase();
                        humanNames.add(name);
                    }
                });
            }
            if(source.containsKey(Constants.APPLICANTS)) {
                List<Map<String,Object>> applicants = (List<Map<String,Object>>) source.get(Constants.APPLICANTS);
                applicants.forEach(assignee-> {
                    // try first and last name
                    Object firstName = assignee.get(Constants.FIRST_NAME);
                    Object lastName = assignee.get(Constants.LAST_NAME);
                    if (firstName != null && lastName != null) {
                        String name = String.join(", ", lastName.toString().trim(), firstName.toString().trim()).toLowerCase();
                        humanNames.add(name);
                    }
                });
            }
            if(source.containsKey(Constants.ASSIGNEES)) {
                List<Map<String,Object>> assignees = (List<Map<String,Object>>) source.get(Constants.ASSIGNEES);
                assignees.forEach(assignee->{
                    Object name = assignee.get(Constants.ASSIGNEE);

                    if(name==null) {
                        // try first and last name
                        Object firstName = assignee.get(Constants.FIRST_NAME);
                        Object lastName = assignee.get(Constants.LAST_NAME);
                        if(firstName!=null&&lastName!=null) {
                            name = String.join(", ", lastName.toString().trim(), firstName.toString().trim()).toLowerCase();
                        }
                    }

                    Object role = assignee.get(Constants.ASSIGNEE_ROLE);
                    if(name!=null&&role!=null && name.toString().length()>1 && role.toString().length()>0) {
                        name = name.toString().trim().toLowerCase();
                        try {
                            int roleNum = Integer.valueOf(role.toString());
                            if(roleNum==1) return; // only partial ownership
                            if(roleNum == 4 || roleNum == 5) {
                                // is human
                                humanNames.add(name.toString());
                                //if(debug) System.out.println("HUMAN: "+name.toString());
                            } else {
                                // corporation
                                companyNames.add(name.toString());
                                //if(debug) System.out.println("COMPANY: "+name.toString());
                            }
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
            // finished
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Found "+cnt.get()+" total. "+humanNames.size()+" humans. "+companyNames.size()+" companies.");
            }
            return null;
        };

        SearchRequestBuilder request = DataSearcher.getClient().prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.PARENT_TYPE_NAME)
                .setScroll(new TimeValue(120000))
                .setExplain(false)
                .setFrom(0)
                .setSize(10000)
                .setFetchSource(new String[]{
                        Constants.ASSIGNEES+"."+Constants.ASSIGNEE_ROLE,
                        Constants.ASSIGNEES+"."+Constants.ASSIGNEE,
                        Constants.ASSIGNEES+"."+Constants.FIRST_NAME,
                        Constants.ASSIGNEES+"."+Constants.LAST_NAME,
                        Constants.INVENTORS+"."+Constants.FIRST_NAME,
                        Constants.INVENTORS+"."+Constants.LAST_NAME,
                        Constants.APPLICANTS+"."+Constants.FIRST_NAME,
                        Constants.APPLICANTS+"."+Constants.LAST_NAME
                },new String[]{})
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
        getAllHumanNames();
        getAllCompanyNames();

        int numTests = 10000;
        int seed = 569;

        List<String> allCompanies = new ArrayList<>(companyNames);
        List<String> allHumans = new ArrayList<>(humanNames);

        Collections.shuffle(allCompanies,new Random(seed));
        Collections.shuffle(allHumans, new Random(seed));

        trainCompanies = new ArrayList<>();
        testCompanies = new ArrayList<>();
        valCompanies = new ArrayList<>();

        trainHumans = new ArrayList<>();
        testHumans = new ArrayList<>();
        valHumans = new ArrayList<>();

        testCompanies.addAll(allCompanies.subList(0,numTests/2));
        testHumans.addAll(allHumans.subList(0,numTests/2));

        valCompanies.addAll(allCompanies.subList(numTests/2,numTests));
        valHumans.addAll(allHumans.subList(numTests/2,numTests));

        trainCompanies.addAll(allCompanies.subList(numTests,allCompanies.size()));
        trainHumans.addAll(allHumans.subList(numTests,allHumans.size()));

        Collections.shuffle(trainCompanies);
        Collections.shuffle(testCompanies);
        Collections.shuffle(valCompanies);
        Collections.shuffle(trainHumans);
        Collections.shuffle(testHumans);
        Collections.shuffle(valHumans);

    }

    @Override
    protected void setDatasetManager() {
        if(datasetManager==null) {
            if(trainHumans==null) splitData();
            datasetManager = new NoSaveDataSetManager<>(
                    getRawIterator(trainHumans,trainCompanies,1000000,BATCH_SIZE),
                    getRawIterator(testHumans,testCompanies,10000,TEST_BATCH_SIZE),
                    getRawIterator(valHumans,valCompanies,10000,TEST_BATCH_SIZE)
            );
        }
    }

    private DataSetIterator getRawIterator(List<String> humans, List<String> companies, int numSamples, int batchSize) {
        System.out.println("Iterator with "+humans.size()+" humans and "+companies.size()+" companies...");
        return new DataSetIterator() {
            Random rand = new Random(69);
            AtomicInteger cnt = new AtomicInteger(0);
            AtomicBoolean flip = new AtomicBoolean(false);
            @Override
            public DataSet next(int batch) {
                INDArray features = Nd4j.create(batch,this.inputColumns(),MAX_NAME_LENGTH);
                INDArray labels = Nd4j.zeros(batch,this.totalOutcomes(),MAX_NAME_LENGTH);
                INDArray featureMask = Nd4j.create(batch,MAX_NAME_LENGTH);

                int idx = 0;
                while(idx < batch && hasNext()) {
                    float[] mask = new float[MAX_NAME_LENGTH];
                    boolean isHuman = flip.getAndSet(!flip.get());
                    String name = (isHuman ? humans.get(rand.nextInt(humans.size())) : companies.get(rand.nextInt(companies.size())))
                            .toLowerCase().trim()+" ";

                    int labelIdx = isHuman ? 1 : 0;
                    name = name.substring(1);
                    float[][] x = new float[MAX_NAME_LENGTH][this.inputColumns()];
                    for(int i = 0; i < MAX_NAME_LENGTH; i++) { x[i] = new float[this.inputColumns()];}
                    for(int i = 0; i < name.length() && i < MAX_NAME_LENGTH; i++) {
                        mask[i]=1;
                        char c = name.charAt(i);
                        float[] xi = x[i];
                        int pos = Arrays.binarySearch(VALID_CHARS,c);
                        if(pos>=0) {
                            // exists
                            xi[pos]=1;
                        } else {
                            xi[xi.length-1]=1;
                        }
                    }
                    features.put(new INDArrayIndex[]{NDArrayIndex.point(idx),NDArrayIndex.all(),NDArrayIndex.all()},Nd4j.create(x).transposei());
                    featureMask.putRow(idx,Nd4j.create(mask));
                    labels.put(new int[]{idx,labelIdx,MAX_NAME_LENGTH-1}, Nd4j.scalar(1d));
                    idx++;
                    cnt.getAndIncrement();
                    //System.gc();
                }

                if(idx>0) {
                    if (idx < batch) {
                        features = features.get(NDArrayIndex.interval(0, idx), NDArrayIndex.all(), NDArrayIndex.all());
                        featureMask = featureMask.get(NDArrayIndex.interval(0, idx), NDArrayIndex.all());
                        labels = labels.get(NDArrayIndex.interval(0, idx), NDArrayIndex.all(), NDArrayIndex.all());
                    }

                    INDArray labelMask = Nd4j.zeros(idx, MAX_NAME_LENGTH);
                    labelMask.putColumn(MAX_NAME_LENGTH - 1, Nd4j.ones(idx));

                    //System.out.println("Label shape: "+labels.shapeInfoToString());
                    //System.out.println("Label Mask shape: "+labelMask.shapeInfoToString());
                    //System.out.println("Feature shape: "+features.shapeInfoToString());
                    //System.out.println("Feature Mask shape: "+featureMask.shapeInfoToString());

                    return new DataSet(features, labels, featureMask, labelMask);
                } else {
                    return null;
                }
            }

            @Override
            public int totalExamples() {
                return numSamples;
            }

            @Override
            public int inputColumns() {
                return HumanNamePredictionPipelineManager.this.inputSize();
            }

            @Override
            public int totalOutcomes() {
                return 2;
            }

            @Override
            public boolean resetSupported() {
                return true;
            }

            @Override
            public boolean asyncSupported() {
                return false;
            }

            @Override
            public void reset() {
                cnt.set(0);
            }

            @Override
            public int batch() {
                return BATCH_SIZE;
            }

            @Override
            public int cursor() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int numExamples() {
                return numSamples;
            }

            @Override
            public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {

            }

            @Override
            public DataSetPreProcessor getPreProcessor() {
                return null;
            }

            @Override
            public List<String> getLabels() {
                throw new UnsupportedOperationException();
            }

            @Override
            public synchronized boolean hasNext() {
                return cnt.get()<numSamples;
            }

            @Override
            public DataSet next() {
                return next(batch());
            }
        };
    }

    public static void main(String[] args) throws Exception {
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false; // NO PREDICTIONS FOR THIS MODEL
        boolean rebuildPrerequisites = false;

        int nEpochs = 5;
        String modelName = MODEL_NAME;

        setLoggingLevel(Level.INFO);
        HumanNamePredictionPipelineManager pipelineManager = new HumanNamePredictionPipelineManager(modelName);

        rebuildPrerequisites = rebuildPrerequisites || !allCompanyNamesFile.exists() || !allHumanNamesFile.exists(); // Check if vocab map exists

        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }
}
