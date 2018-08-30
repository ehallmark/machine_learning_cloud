package user_interface.server;

import ch.qos.logback.classic.Level;
import com.google.gson.Gson;
import data_pipeline.helpers.Function2;
import data_pipeline.helpers.Function3;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import detect_acquisitions.DetermineAcquisitionsServer;
import elasticsearch.DataIngester;
import elasticsearch.DatasetIndex;
import elasticsearch.TestNewFastVectors;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import lombok.Getter;
import lombok.NonNull;
import models.assignee.database.MergeRawAssignees;
import models.dl4j_neural_nets.tools.MyPreprocessor;
import models.keyphrase_prediction.KeyphrasePredictionPipelineManager;
import models.similarity_models.DefaultSimilarityModel;
import models.similarity_models.Vectorizer;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Session;
import user_interface.server.tools.PasswordHandler;
import user_interface.server.tools.SimpleAjaxMessage;
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.attributes.computable_attributes.*;
import user_interface.ui_models.attributes.computable_attributes.asset_graphs.BackwardCitationAttribute;
import user_interface.ui_models.attributes.computable_attributes.asset_graphs.RelatedAssetsAttribute;
import user_interface.ui_models.attributes.hidden_attributes.*;
import user_interface.ui_models.attributes.script_attributes.*;
import user_interface.ui_models.engines.SimilarityEngineController;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractNestedFilter;
import user_interface.ui_models.filters.AcclaimExpertSearchFilter;
import user_interface.ui_models.filters.AssetDedupFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;
import user_interface.ui_models.templates.FormTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;
import static j2html.TagCreator.head;
import static spark.Spark.*;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    private static final boolean TEST = false;
    private static final boolean debug = false;
    private static final Map<String,Lock> fileSynchronizationMap = Collections.synchronizedMap(new HashMap<>());
    static final String GENERATE_REPORTS_FORM_ID = "generate-reports-form";
    private static final String PROTECTED_URL_PREFIX = "/secure";
    public static final String EXCEL_SESSION = "excel_data";
    public static final String PATENTS_TO_SEARCH_FOR_FIELD = "patentsToSearchFor";
    public static final String DATASETS_TO_SEARCH_IN_FIELD = "datasetsToSearchFor";
    public static final String CPCS_TO_SEARCH_FOR_FIELD = "cpcsToSearchFor";
    public static final String LINE_CHART_MAX = "lineChartMax";
    public static final String TEXT_TO_SEARCH_FOR = "textToSearchFor";
    public static final String LINE_CHART_MIN = "lineChartMin";
    public static final String ASSIGNEES_TO_SEARCH_FOR_FIELD = "assigneesToSearchFor";
    public static final String ATTRIBUTES_ARRAY_FIELD = "attributes[]";
    public static final String PRE_FILTER_ARRAY_FIELD = "attributes[]Nested_filter[]";
    public static final String LIMIT_FIELD = "limit";
    public static final String COMPARATOR_FIELD = "comparator";
    public static final String NOT_IMPLEMENTED_STRING = "This functionality is not yet implemented.";
    public static final String SORT_DIRECTION_FIELD = "sortDirection";
    public static final String MAX_SLICES_FIELD = "maxSlices";
    public static final String INCLUDE_REMAINING_FIELD = "includeRemaining";
    public static final String CHARTS_GROUPED_BY_FIELD = "chartsGroupedBy";
    public static final String COLLECT_BY_ATTR_FIELD = "collectByAttr";
    public static final String COLLECT_TYPE_FIELD = "collectType";
    public static final String SWAP_AXES_FIELD = "swapAxes";
    public static final String DRILLDOWN_BOOL_FIELD = "useDrilldown";
    public static final String REPORT_URL = PROTECTED_URL_PREFIX+"/patent_recommendation_engine";
    public static final String HOME_URL = PROTECTED_URL_PREFIX+"/home";
    public static final String UPDATE_DEFAULT_ATTRIBUTES_URL = PROTECTED_URL_PREFIX+"/update_defaults";
    public static final String SAVE_TEMPLATE_URL = PROTECTED_URL_PREFIX+"/save_template";
    public static final String GET_TEMPLATE_URL = PROTECTED_URL_PREFIX+"/get_template";
    public static final String DELETE_TEMPLATE_URL = PROTECTED_URL_PREFIX+"/delete_template";
    public static final String RENAME_TEMPLATE_URL = PROTECTED_URL_PREFIX+"/rename_template";
    public static final String SAVE_DATASET_URL = PROTECTED_URL_PREFIX+"/save_dataset";
    public static final String GET_DATASET_URL = PROTECTED_URL_PREFIX+"/get_dataset";
    public static final String DELETE_DATASET_URL = PROTECTED_URL_PREFIX+"/delete_dataset";
    public static final String RENAME_DATASET_URL = PROTECTED_URL_PREFIX+"/rename_dataset";
    public static final String RESET_DEFAULT_TEMPLATE_URL = PROTECTED_URL_PREFIX+"/reset_default_template";
    public static final String DOWNLOAD_URL = PROTECTED_URL_PREFIX+"/excel_generation";
    public static final String PREVIEW_DOWNLOAD_URL = PROTECTED_URL_PREFIX+"/preview_excel_generation";
    public static final String RANDOM_TOKEN = "<><><>";
    public static final String SUPER_USER = "form_creator";
    public static final String USE_HIGHLIGHTER_FIELD = "useHighlighter";
    public static final String FILTER_NESTED_OBJECTS_FIELD = "filterNestedObjects";
    public static final String LIST_ITEM_SEPARATOR_FIELD = "listItemSeparatorField";
    public static final String ANALYST_USER = "analyst";
    public static final String INTERNAL_USER = "internal";
    protected static final List<String> USER_ROLES = Arrays.asList(ANALYST_USER,INTERNAL_USER);
    public static TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
    public static RecursiveTask<SimilarityEngineController> similarityEngine;
    public static Map<String,AbstractFilter> preFilterModelMap = new HashMap<>();
    public static Map<String,AbstractAttribute> attributesMap = new HashMap<>();
    static Map<String,Function<String,Boolean>> roleToAttributeFunctionMap = new HashMap<>();
    static final Function<String,Boolean> DEFAULT_ROLE_TO_ATTR_FUNCTION = (str) -> false;
    //private static final String PLATFORM_STARTER_IP_ADDRESS = "104.196.199.81";
    static NestedAttribute allAttributes;
    static AbstractNestedFilter allFilters;
    static NestedAttribute allCharts;
    private static RecursiveTask<KeyphrasePredictionPipelineManager> keyphrasePredictionPipelineManagerTask;
    protected static final ForkJoinPool pool = new ForkJoinPool(Math.max(10,Runtime.getRuntime().availableProcessors()/2));

    static {
        roleToAttributeFunctionMap.put(ANALYST_USER, str -> !str.toLowerCase().startsWith("gather")&& !str.toLowerCase().startsWith("compdb"));
        roleToAttributeFunctionMap.put(INTERNAL_USER, str -> true);
        roleToAttributeFunctionMap.put(SUPER_USER, str -> true);
    }

    @Getter
    static Collection<AbstractAttribute> allTopLevelAttributes;

    protected static Map<String,String> humanAttrToJavaAttrMap;
    protected static Map<String,String> javaAttrToHumanAttrMap;
    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
        { // Attributes
            humanAttrToJavaAttrMap = new HashMap<>();
            humanAttrToJavaAttrMap.put("(eg. FIELD:isEmptyANO_F OR FIELD:isNotEmptyTTL)", "FIELD");
            humanAttrToJavaAttrMap.put("Independent Claim", "ICLM");
            humanAttrToJavaAttrMap.put("Dependent Claim", "DCLM");
            humanAttrToJavaAttrMap.put("Title + Abstract + Claims", "TAC");
            humanAttrToJavaAttrMap.put("Maintenance Fee Event Code", Constants.MAINTENANCE_EVENT);
            humanAttrToJavaAttrMap.put("Asset Number", Constants.NAME);
            humanAttrToJavaAttrMap.put("Similarity", Constants.SIMILARITY);
            humanAttrToJavaAttrMap.put("Similarity (Fast)", Constants.SIMILARITY_FAST);
            humanAttrToJavaAttrMap.put("Technology Similarity", Constants.TECHNOLOGY_SIMILARITY);
            humanAttrToJavaAttrMap.put("Assignee Similarity", Constants.ASSIGNEE_SIMILARITY);
            humanAttrToJavaAttrMap.put("Text Similarity", Constants.TEXT_SIMILARITY);
            humanAttrToJavaAttrMap.put("CPC Code Similarity", Constants.CPC_SIMILARITY);
            humanAttrToJavaAttrMap.put("Asset Similarity", Constants.PATENT_SIMILARITY);
            humanAttrToJavaAttrMap.put("Total Asset Count", Constants.TOTAL_ASSET_COUNT);
            humanAttrToJavaAttrMap.put("Assignee Name", Constants.ASSIGNEE);
            humanAttrToJavaAttrMap.put("Organization Name", Constants.ASSIGNEES+"."+Constants.ASSIGNEE);
            humanAttrToJavaAttrMap.put("Invention Title", Constants.INVENTION_TITLE);
            humanAttrToJavaAttrMap.put("AI Value", Constants.AI_VALUE);
            humanAttrToJavaAttrMap.put("Reinstated", Constants.REINSTATED);
            humanAttrToJavaAttrMap.put("Result Type", Constants.DOC_TYPE);
            humanAttrToJavaAttrMap.put("Dataset Name", Constants.DATASET_NAME);
            humanAttrToJavaAttrMap.put("2nd Dataset Name", Constants.DATASET2_NAME);
            humanAttrToJavaAttrMap.put("Expired", Constants.EXPIRED);
            humanAttrToJavaAttrMap.put("Technology", Constants.COMPDB_TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Deal ID", Constants.COMPDB_DEAL_ID);
            humanAttrToJavaAttrMap.put("GTT Group Technology", Constants.TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Original Assignee Entity Type", Constants.ASSIGNEE_ENTITY_TYPE);
            humanAttrToJavaAttrMap.put("Entity Type", Constants.LATEST_ASSIGNEE+"."+Constants.ASSIGNEE_ENTITY_TYPE);
            humanAttrToJavaAttrMap.put("Assignee Divestments (CompDB)", Constants.COMPDB_ASSETS_SOLD);
            humanAttrToJavaAttrMap.put("Assignee Acquisitions (CompDB)", Constants.COMPDB_ASSETS_PURCHASED);
            humanAttrToJavaAttrMap.put("Acquisition Deal", Constants.ACQUISITION_DEAL);
            humanAttrToJavaAttrMap.put("Inactive Deal", Constants.INACTIVE_DEAL);
            humanAttrToJavaAttrMap.put("Portfolio Size", Constants.PORTFOLIO_SIZE);
            humanAttrToJavaAttrMap.put("Patents",PortfolioList.Type.patents.toString());
            humanAttrToJavaAttrMap.put("(Normalized) Assignee",Constants.NORMALIZED_LATEST_ASSIGNEE);
            humanAttrToJavaAttrMap.put("(Normalized) Portfolio Size", Constants.NORMALIZED_PORTFOLIO_SIZE);
            humanAttrToJavaAttrMap.put("Applications",PortfolioList.Type.applications.toString());
            humanAttrToJavaAttrMap.put("Pie Chart", Constants.PIE_CHART);
            humanAttrToJavaAttrMap.put("Column Combination Count Table", Constants.GROUPED_TABLE_CHART);
            humanAttrToJavaAttrMap.put("Column Combination Function Table", Constants.GROUPED_FUNCTION_TABLE_CHART);
            humanAttrToJavaAttrMap.put("Pivot Count Table", Constants.PIVOT_COUNT_TABLE_CHART);
            humanAttrToJavaAttrMap.put("Pivot Function Table", Constants.PIVOT_FUNCTION_TABLE_CHART);
            humanAttrToJavaAttrMap.put("Cited Date", Constants.CITED_DATE);
            humanAttrToJavaAttrMap.put("Forward Citation", Constants.BACKWARD_CITATION);
            humanAttrToJavaAttrMap.put("Remove Duplicate Related Assets",AssetDedupFilter.NAME);
            humanAttrToJavaAttrMap.put("Means Present", Constants.MEANS_PRESENT);
            humanAttrToJavaAttrMap.put("Gather", Constants.GATHER);
            humanAttrToJavaAttrMap.put("Stage Complete", Constants.GATHER_STAGE);
            humanAttrToJavaAttrMap.put("Gather Technology", Constants.GATHER_TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Patent Rating", Constants.GATHER_VALUE);
            humanAttrToJavaAttrMap.put("Relation Type", Constants.RELATION_TYPE);
            humanAttrToJavaAttrMap.put("Filing Number", Constants.FILING_NAME);
            humanAttrToJavaAttrMap.put("CompDB", Constants.COMPDB);
            humanAttrToJavaAttrMap.put("Random Sort", Constants.RANDOM_SORT);
            humanAttrToJavaAttrMap.put("No Sort", Constants.NO_SORT);
            humanAttrToJavaAttrMap.put("CPC Section", Constants.CPC_SECTION);
            humanAttrToJavaAttrMap.put("CPC Class", Constants.CPC_CLASS);
            humanAttrToJavaAttrMap.put("CPC Subclass", Constants.CPC_SUBCLASS);
            humanAttrToJavaAttrMap.put("CPC Main Group", Constants.CPC_MAIN_GROUP);
            humanAttrToJavaAttrMap.put("CPC Subgroup", Constants.CPC_SUBGROUP);
            humanAttrToJavaAttrMap.put("CPC Title", Constants.CPC_TITLE);
            humanAttrToJavaAttrMap.put("Granted", Constants.GRANTED);
            humanAttrToJavaAttrMap.put("Dataset Similarity", DATASETS_TO_SEARCH_IN_FIELD);
            humanAttrToJavaAttrMap.put("Filing Date", Constants.FILING_DATE);
            humanAttrToJavaAttrMap.put("Histogram",Constants.HISTOGRAM);
            humanAttrToJavaAttrMap.put("Assignee Role", Constants.ASSIGNEE_ROLE);
            humanAttrToJavaAttrMap.put("WIPO Technology",Constants.WIPO_TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Est. Remaining Life (Years)",Constants.REMAINING_LIFE);
            humanAttrToJavaAttrMap.put("Filing Country", Constants.FILING_COUNTRY);
            humanAttrToJavaAttrMap.put("Original Expiration Date", Constants.EXPIRATION_DATE);
            humanAttrToJavaAttrMap.put("Term Adjustments (Days)", Constants.PATENT_TERM_ADJUSTMENT);
            humanAttrToJavaAttrMap.put("CPC Code", Constants.CPC_CODES);
            humanAttrToJavaAttrMap.put("CPC Data", Constants.NESTED_CPC_CODES);
            humanAttrToJavaAttrMap.put("Buyer",Constants.BUYER);
            humanAttrToJavaAttrMap.put("Seller",Constants.SELLER);
            humanAttrToJavaAttrMap.put("Original Priority Date", Constants.PRIORITY_DATE);
            humanAttrToJavaAttrMap.put("Recorded Date", Constants.RECORDED_DATE);
            humanAttrToJavaAttrMap.put("Publication Date", Constants.PUBLICATION_DATE);
            humanAttrToJavaAttrMap.put("Timeline Chart", Constants.LINE_CHART);
            humanAttrToJavaAttrMap.put("Reel Frames", Constants.REEL_FRAME);
            humanAttrToJavaAttrMap.put("Include All", AbstractFilter.FilterType.Include.toString());
            humanAttrToJavaAttrMap.put("Include By Prefix", AbstractFilter.FilterType.PrefixInclude.toString());
            humanAttrToJavaAttrMap.put("Exclude By Prefix", AbstractFilter.FilterType.PrefixExclude.toString());
            humanAttrToJavaAttrMap.put("Exclude All", AbstractFilter.FilterType.Exclude.toString());
            humanAttrToJavaAttrMap.put("Expert Query Syntax", AcclaimExpertSearchFilter.NAME);
            humanAttrToJavaAttrMap.put("Advanced Keyword", AbstractFilter.FilterType.AdvancedKeyword.toString());
            humanAttrToJavaAttrMap.put("Advanced Regexp", AbstractFilter.FilterType.Regexp.toString());
            humanAttrToJavaAttrMap.put("Include", AbstractFilter.FilterType.BoolTrue.toString());
            humanAttrToJavaAttrMap.put("Exclude", AbstractFilter.FilterType.BoolFalse.toString());
            humanAttrToJavaAttrMap.put("Range", AbstractFilter.FilterType.Between.toString());
            humanAttrToJavaAttrMap.put("Greater Than", AbstractFilter.FilterType.GreaterThan.toString());
            humanAttrToJavaAttrMap.put("Less Than", AbstractFilter.FilterType.LessThan.toString());
            humanAttrToJavaAttrMap.put("Filters", AbstractFilter.FilterType.Nested.toString());
            humanAttrToJavaAttrMap.put("Exists", AbstractFilter.FilterType.Exists.toString());
            humanAttrToJavaAttrMap.put("Include All With Related Assets", AbstractFilter.FilterType.IncludeWithRelated.toString());
            humanAttrToJavaAttrMap.put("Exclude All With Related Assets", AbstractFilter.FilterType.ExcludeWithRelated.toString());
            humanAttrToJavaAttrMap.put("Does Not Exist", AbstractFilter.FilterType.DoesNotExist.toString());
            humanAttrToJavaAttrMap.put("Latest Execution Date", Constants.EXECUTION_DATE);
            humanAttrToJavaAttrMap.put("Execution Date", Constants.ASSIGNMENTS+"."+Constants.EXECUTION_DATE);
            humanAttrToJavaAttrMap.put("First Name", Constants.FIRST_NAME);
            humanAttrToJavaAttrMap.put("Number of Related Docs", Constants.NUM_RELATED_ASSETS);
            humanAttrToJavaAttrMap.put("Number of Forward Citations", Constants.NUM_BACKWARD_CITATIONS);
            humanAttrToJavaAttrMap.put("Last Name", Constants.LAST_NAME);
            humanAttrToJavaAttrMap.put("Correspondent Address", Constants.CORRESPONDENT_ADDRESS);
            humanAttrToJavaAttrMap.put("Correspondent Name", Constants.CORRESPONDENT);
            humanAttrToJavaAttrMap.put("Number of Reel Frames", Constants.NUM_ASSIGNMENTS);
            humanAttrToJavaAttrMap.put("Country", Constants.COUNTRY);
            humanAttrToJavaAttrMap.put("City", Constants.CITY);
            humanAttrToJavaAttrMap.put("State", Constants.STATE);
            humanAttrToJavaAttrMap.put("Zip Code", Constants.POSTAL_CODE);
            humanAttrToJavaAttrMap.put("Claim Number", Constants.CLAIM_NUM);
            humanAttrToJavaAttrMap.put("Parent Claim Number", Constants.PARENT_CLAIM_NUM);
            humanAttrToJavaAttrMap.put("Document Kind", Constants.DOC_KIND);
            humanAttrToJavaAttrMap.put("Claim Length", Constants.CLAIM_LENGTH);
            humanAttrToJavaAttrMap.put("Length of Smallest Independent Claim", Constants.SMALLEST_INDEPENDENT_CLAIM_LENGTH);
            humanAttrToJavaAttrMap.put("Claim Text", Constants.CLAIM);
            humanAttrToJavaAttrMap.put("Abstract", Constants.ABSTRACT);
            humanAttrToJavaAttrMap.put("Lapsed", Constants.LAPSED);
            humanAttrToJavaAttrMap.put("Priority Date (estimated)", Constants.ESTIMATED_PRIORITY_DATE);
            humanAttrToJavaAttrMap.put("Expiration Date (estimated)", Constants.ESTIMATED_EXPIRATION_DATE);
            humanAttrToJavaAttrMap.put("Assignor Name", Constants.ASSIGNOR);
            humanAttrToJavaAttrMap.put("Conveyance Text", Constants.CONVEYANCE_TEXT);
            humanAttrToJavaAttrMap.put("Is Human", Constants.IS_HUMAN);
            humanAttrToJavaAttrMap.put("Overall Score", Constants.SCORE);

            // custom filter name for excluding granted apps
            humanAttrToJavaAttrMap.put("Exclude Granted Applications", Constants.GRANTED+ AbstractFilter.FilterType.BoolFalse+ Constants.FILTER_SUFFIX);
            humanAttrToJavaAttrMap.put("Related Docs", Constants.ALL_RELATED_ASSETS);
            // nested attrs
            humanAttrToJavaAttrMap.put("Latest Assignee", Constants.LATEST_ASSIGNEE);
            humanAttrToJavaAttrMap.put("Original Assignee", Constants.ASSIGNEES);
            humanAttrToJavaAttrMap.put("Applicants", Constants.APPLICANTS);
            humanAttrToJavaAttrMap.put("Inventors", Constants.INVENTORS);
            humanAttrToJavaAttrMap.put("Agents", Constants.AGENTS);
            humanAttrToJavaAttrMap.put("Backward Citations", Constants.CITATIONS);
            humanAttrToJavaAttrMap.put("Claims", Constants.CLAIMS);
            humanAttrToJavaAttrMap.put("Prior Related Docs", Constants.PATENT_FAMILY);
            humanAttrToJavaAttrMap.put("Assignments", Constants.ASSIGNMENTS);

            buildJavaToHumanAttrMap();

        }
    }

    static void buildJavaToHumanAttrMap() {
        // inverted version to get human readables back
        javaAttrToHumanAttrMap = new HashMap<>();
        humanAttrToJavaAttrMap.forEach((k, v) -> javaAttrToHumanAttrMap.put(v, k));
    }

    static Map<String,NestedAttribute> nestedAttrMap;
    public static Map<String,NestedAttribute> getNestedAttrMap() {
        if(nestedAttrMap==null) {
            nestedAttrMap = new HashMap<>();
            getAllTopLevelAttributes().forEach(attr->{
                if(attr instanceof NestedAttribute) {
                    nestedAttrMap.put(attr.getName(),(NestedAttribute)attr);
                }
            });
        }
        return nestedAttrMap;
    }

    private static Collection<String> allAttrNames;
    public static Collection<String> getAllAttributeNames() {
        if(allAttrNames ==null) {
            allAttrNames = getAllTopLevelAttributes().stream().flatMap(attr->{
                return attributeNameHelper(attr,"").stream();
            }).collect(Collectors.toSet());
        }
        return allAttrNames;
    }

    private static Set<String> numericAttributes;
    static synchronized Set<String> getNumericAttributes() {
        if(numericAttributes==null) {
            List<AbstractAttribute> allAttrs = new ArrayList<>();
            getAttributesHelper(allAttributes,allAttrs);
            numericAttributes = allAttrs.stream().filter(attr->attr.getFieldType().equals(AbstractFilter.FieldType.Integer)||attr.getFieldType().equals(AbstractFilter.FieldType.Double)).map(attr->attr.getFullName()).collect(Collectors.toCollection(HashSet::new));
            numericAttributes.add(Constants.SCORE);
        }
        return numericAttributes;
    }


    private static Collection<String> allStreamingAttrNames;
    public static Collection<String> getAllStreamingAttributeNames() {
        if(allStreamingAttrNames ==null) {
            allStreamingAttrNames = getAllTopLevelAttributes().stream().filter(attr ->!(attr instanceof ComputableAttribute)).flatMap(attr->{
                return attributeNameHelper(attr,"").stream();
            }).collect(Collectors.toSet());
            System.out.println("Streamable Attributes: "+Arrays.toString(allStreamingAttrNames.toArray()));
        }
        return allStreamingAttrNames;
    }

    public static Collection<String> attributeNameHelper(AbstractAttribute attr, String previous) {
        Collection<String> stream;
        if(attr instanceof NestedAttribute) {
            stream = ((NestedAttribute) attr).getAttributes().stream().flatMap(nestedAttr->(attributeNameHelper(nestedAttr,attr.getName()).stream())).collect(Collectors.toList());
        } else {
            stream = Arrays.asList(previous==null||previous.isEmpty() ? attr.getName() : previous+"."+attr.getName());
        }
        return stream;
    }

    public static Collection<ComputableAttribute<?>> getAllComputableAttributes() {
        return getAllTopLevelAttributes().stream().flatMap(attr->getAllComputableAttributesHelper(attr)).collect(Collectors.toList());
    }

    private static Stream<ComputableAttribute<?>> getAllComputableAttributesHelper(AbstractAttribute attribute) {
        if(attribute instanceof NestedAttribute) {
            return ((NestedAttribute)attribute).getAttributes().stream().flatMap(attr->getAllComputableAttributesHelper(attr));
        } else {
            return Arrays.asList(attribute).stream().filter(attr -> attr instanceof ComputableAttribute).map(attr -> (ComputableAttribute<?>) attr);
        }
    }

    public static String fullHumanAttributeFor(String attr) {
        if(attr.contains(".")) {
            return humanAttributeFor(attr) + " ("+fullHumanAttributeFor(attr.substring(0,attr.lastIndexOf(".")))+")";
        } else return humanAttributeFor(attr);
    }

    public static String humanAttributeFor(String attr) {
        String human = attr;
        if(javaAttrToHumanAttrMap.containsKey(human))  {
            human = javaAttrToHumanAttrMap.get(attr);
        } else {
            int commaIdx = attr.indexOf(".");
            if(commaIdx>=0&&commaIdx<attr.length()-1) {
                human = humanAttributeFor(attr.substring(attr.indexOf(".")+1));
            } else {
                if(attr.endsWith(Constants.COUNT_SUFFIX)) {
                    human = "Number of "+humanAttributeFor(attr.substring(0,attr.length()-Constants.COUNT_SUFFIX.length()));
                }
            }
        }
        if(human.endsWith(RANDOM_TOKEN)) {
            human = human.replace(RANDOM_TOKEN,"");
        }
        return human;
    }

    public static void initialize(boolean onlyAttributes, boolean loadHidden) {
        loadAttributes(loadHidden,!onlyAttributes);
        if(!onlyAttributes) {
            loadFilterModels();
            loadChartModels();
            keyphrasePredictionPipelineManagerTask = new RecursiveTask<KeyphrasePredictionPipelineManager>() {
                @Override
                protected KeyphrasePredictionPipelineManager compute() {
                    if(TEST) return null;
                    KeyphrasePredictionPipelineManager keyphrasePredictionPipelineManager = new KeyphrasePredictionPipelineManager(new WordCPC2VecPipelineManager(WordCPC2VecPipelineManager.SMALL_MODEL_NAME, -1, -1, -1));
                    keyphrasePredictionPipelineManager.runPipeline(false, false, false, false, -1, false);
                    //keyphrasePredictionPipelineManager.getWordCPC2VecPipelineManager().getOrLoadCPCVectors();
                    //keyphrasePredictionPipelineManager.getWordCPC2VecPipelineManager().getOrLoadWordVectors();
                    //keyphrasePredictionPipelineManager.loadPredictions();
                    //keyphrasePredictionPipelineManager.getCPCMap();
                    //keyphrasePredictionPipelineManager.buildKeywordToLookupTableMap();
                    try {
                        //AssetKMeans kMeans = new AssetKMeans(Collections.emptyList(), keyphrasePredictionPipelineManager.getWordCPC2VecPipelineManager().getOrLoadCPCVectors(), 2);
                        //kMeans.clusterAssets();
                    } catch (Exception e) {
                        System.out.println("Error while initializing asset K Means");
                        e.printStackTrace();
                    }
                    return keyphrasePredictionPipelineManager;
                }
            };
        }
    }

    static void getAttributesHelper(AbstractAttribute attr, List<AbstractAttribute> all) {
        if(attr instanceof NestedAttribute) {
            ((NestedAttribute) attr).getAttributes().forEach(child->getAttributesHelper(child,all));
        } else {
            all.add(attr);
        }
    }

    static List<AbstractAttribute> groupAttributesToNewParents(List<AbstractAttribute> attributes) {
        List<AbstractAttribute> nonNested = attributes.stream().filter(attr->attr.getParent()==null).map(attr->attr.clone()).collect(Collectors.toList());
        List<AbstractAttribute> nested = attributes.stream().filter(attr->attr.getParent()!=null).collect(Collectors.toList());
        Map<String,Set<AbstractAttribute>> nestedMap = nested.stream().collect(Collectors.groupingBy(attr->attr.getRootName(),Collectors.toSet()));
        nestedMap.entrySet().forEach(e->{
            NestedAttribute nestedAttr = getNestedAttrMap().get(e.getKey());
            nonNested.add(new NestedAttribute(e.getValue().stream().map(attr->attr.clone()).collect(Collectors.toList())) {
                @Override
                public String getName() {
                    return e.getKey();
                }
                @Override
                public boolean isObject() { // important
                    return nestedAttr.isObject();
                }
            });
        });
        Collections.sort(nonNested, Comparator.comparing(attr->attr.getFullName()));
        return nonNested;
    }

    public static List<AbstractAttribute> duplicateAttributes(List<AbstractAttribute> attributes) {
        return attributes.stream().map(attr-> {
                AbstractAttribute clone = attr.clone();
                clone.setParent(attr.getParent());
                return clone;
            }).collect(Collectors.toList());
    }

    public static void loadChartModels() {
    }


    public static void loadFilterModels() {
        if(preFilterModelMap.isEmpty()) {
            try {
                // Do nothing filters
                    // None exist at the moment...
                    // Use doNothingFilterModelMap

                // Pre filters
                attributesMap.forEach((name,attr) -> {
                    attr.createFilters().forEach(filter->{
                        preFilterModelMap.put(filter.getName(),filter);
                        filterNameHelper(filter);
                    });
                });

                preFilterModelMap.put(AcclaimExpertSearchFilter.NAME,new AcclaimExpertSearchFilter());
                preFilterModelMap.put(AssetDedupFilter.NAME, new AssetDedupFilter());

                buildJavaToHumanAttrMap();
                List<AbstractAttribute> nestedAttributes = new ArrayList<>(allAttributes.getAttributes());
                nestedAttributes.addAll(SimilarityEngineController.getAllEngines().stream().map(engine->(AbstractAttribute)engine).collect(Collectors.toList()));
                NestedAttribute attributeWithSimilarity = new NestedAttribute(nestedAttributes,false) {
                    @Override
                    public String getName() {
                        return allAttributes.getName();
                    }
                };
                allFilters = new AbstractNestedFilter(attributeWithSimilarity,false, new AcclaimExpertSearchFilter(), new AssetDedupFilter());
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void filterNameHelper(AbstractFilter filter) {
        String filterHumanName;
        if(!javaAttrToHumanAttrMap.containsKey(filter.getName())) {
            // build name
            filterHumanName = AbstractFilter.isPrefix(filter.getFilterType()) ? humanAttributeFor(filter.getFilterType().toString()) + " " + humanAttributeFor(filter.getFullPrerequisite()) : humanAttributeFor(filter.getFullPrerequisite()) + " " + humanAttributeFor(filter.getFilterType().toString());
            while (humanAttrToJavaAttrMap.containsKey(filterHumanName)) {
                // already exists
                filterHumanName = filterHumanName + RANDOM_TOKEN;
            }
            humanAttrToJavaAttrMap.put(filterHumanName, filter.getName());
        }
        if(filter instanceof AbstractNestedFilter) {
            ((AbstractNestedFilter)filter).getFilters().forEach(_nestedFilter->{
                AbstractFilter nestedFilter = (AbstractFilter)_nestedFilter;
                filterNameHelper(nestedFilter);
            });
        }
    }

    public static void loadAttributes(boolean loadHidden, boolean loadVectors) {
        if(attributesMap.isEmpty()) {
            attributesMap.put(Constants.MAINTENANCE_EVENT, new MaintenanceEventAttribute());
            attributesMap.put(Constants.EXPIRED, new ExpiredAttribute());
            attributesMap.put(Constants.INVENTION_TITLE, new InventionTitleAttribute());
            attributesMap.put(Constants.TECHNOLOGY, TechnologyAttribute.getOrCreate());
            attributesMap.put(Constants.NAME, new AssetNumberAttribute());
            attributesMap.put(Constants.WIPO_TECHNOLOGY, new WIPOTechnologyAttribute());
            attributesMap.put(Constants.REMAINING_LIFE, new RemainingLifeAttribute());
            attributesMap.put(Constants.CPC_CODES, new CPCComputedAttribute());
            attributesMap.put(Constants.EXPIRATION_DATE, new ExpirationDateAttribute());
            attributesMap.put(Constants.ESTIMATED_EXPIRATION_DATE, new CalculatedExpirationDateAttribute());
            attributesMap.put(Constants.ESTIMATED_PRIORITY_DATE, new CalculatedPriorityDateAttribute());
            attributesMap.put(Constants.PATENT_TERM_ADJUSTMENT, new PatentTermAdjustmentAttribute());
            //attributesMap.put(Constants.SIMILARITY, new SimilarityAttribute());
            attributesMap.put(Constants.SIMILARITY_FAST, new FastSimilarityAttribute());
            attributesMap.put(Constants.SMALLEST_INDEPENDENT_CLAIM_LENGTH, new LengthOfSmallestIndependentClaimAttribute());
            attributesMap.put(Constants.MEANS_PRESENT, new MeansPresentAttribute());
            attributesMap.put(Constants.PRIORITY_DATE, new PriorityDateAttribute());
            attributesMap.put(Constants.FILING_DATE, new FilingDateAttribute());
            attributesMap.put(Constants.DOC_TYPE, new ResultTypeAttribute());
            attributesMap.put(Constants.ALL_RELATED_ASSETS, new RelatedAssetsAttribute());
            attributesMap.put(Constants.ABSTRACT, new AbstractTextAttribute());
            attributesMap.put(Constants.BACKWARD_CITATION, new BackwardCitationAttribute());
            attributesMap.put(Constants.PUBLICATION_DATE, new PublicationDateAttribute());
            attributesMap.put(Constants.FILING_NAME, new FilingNameAttribute());
            attributesMap.put(Constants.FILING_COUNTRY, new FilingCountryAttribute());
            attributesMap.put(Constants.REINSTATED, new ReinstatedAttribute());
            attributesMap.put(Constants.LAPSED, new LapsedAttribute());
            attributesMap.put(Constants.DOC_KIND, new DocKindAttribute());
            attributesMap.put(Constants.REEL_FRAME, new ReelFrameAttribute());
            attributesMap.put(Constants.NUM_ASSIGNMENTS, new NumAssignmentsAttribute());
            attributesMap.put(Constants.NUM_RELATED_ASSETS, new NumRelatedAssetsAttribute());
            attributesMap.put(Constants.NUM_BACKWARD_CITATIONS, new NumBackwardCitationsAttribute());
            attributesMap.put(Constants.GRANTED,new IsGrantedApplicationAttribute());
            attributesMap.put(Constants.ASSIGNEE_ENTITY_TYPE, new EntityTypeAttribute());

            // nested attrs
            attributesMap.put(Constants.LATEST_ASSIGNEE, new LatestAssigneeNestedAttribute());
            attributesMap.put(Constants.ASSIGNEES, new AssigneesNestedAttribute());
            attributesMap.put(Constants.ASSIGNMENTS, new AssignmentsNestedAttribute());
            //attributesMap.put(Constants.APPLICANTS, new ApplicantsNestedAttribute());
            //attributesMap.put(Constants.INVENTORS, new InventorsNestedAttribute());
            //attributesMap.put(Constants.AGENTS, new AgentsNestedAttribute());
            attributesMap.put(Constants.CITATIONS, new CitationsNestedAttribute());
            attributesMap.put(Constants.CLAIMS, new ClaimsNestedAttribute());
            //attributesMap.put(Constants.PATENT_FAMILY, new RelatedDocumentsNestedAttribute());
            attributesMap.put(Constants.GATHER, new GatherNestedAttribute());
            attributesMap.put(Constants.COMPDB, new CompDBNestedAttribute());
            //attributesMap.put(Constants.NESTED_CPC_CODES, new CPCNestedAttribute());

            // include count
            Constants.NESTED_ATTRIBUTES.forEach(attr->{
                AbstractAttribute countAttr = new CountAttribute(attr + Constants.COUNT_SUFFIX);
                attributesMap.put(attr + Constants.COUNT_SUFFIX, countAttr);
            });

            if(loadHidden) {
                // hidden attrs
                Arrays.asList(
                        new AssetToAssigneeMap(),
                        new AssetToFilingMap(),
                        new FilingToAssetMap(),
                        new AssetToRelatedAssetsMap(),
                        new AssetToCitedAssetsMap()
                ).forEach(attr -> attributesMap.put(attr.getName(), attr));
            }

            // nested attribute names
            buildJavaToHumanAttrMap();

           // SimilarityEngineController.setAllEngines(Arrays.asList(new DataSetSimilarityEngine(), new PatentSimilarityEngine(), new AssigneeSimilarityEngine(), new TextSimilarityEngine(), new CPCSimilarityEngine()));

            // similarity engine
            similarityEngine = new RecursiveTask<SimilarityEngineController>() {
                @Override
                protected SimilarityEngineController compute() {
                    // current word vectorizer
                    if(!TEST) {
                        new DefaultSimilarityModel(Collections.emptySet());
                    }
                    return new SimilarityEngineController();
                }
            };

            allTopLevelAttributes = new ArrayList<>(attributesMap.values());

            allAttributes = new NestedAttribute(allTopLevelAttributes,false) {
                @Override
                public String getName() {
                    return ATTRIBUTES_ARRAY_FIELD;
                }
            };
        }
    }


    public static void loadAndIngestAllItemsWithAttributes(Collection<ComputableAttribute<?>> attributes, Map<String,Vectorizer> vectorizers, Set<String> onlyAssets) {
        List<String> applications = new AssetToFilingMap().getApplicationDataMap().keySet().stream().filter(asset->onlyAssets==null||onlyAssets.contains(asset)).collect(Collectors.toList());
        System.out.println("Num applications found: "+applications.size());
        handleItemsList(applications, attributes, PortfolioList.Type.applications,vectorizers);
        DataIngester.finishCurrentMongoBatch();
        List<String> patents = new AssetToFilingMap().getPatentDataMap().keySet().stream().filter(asset->onlyAssets==null||onlyAssets.contains(asset)).collect(Collectors.toList());
        System.out.println("Num patents found: "+patents.size());
        handleItemsList(patents, attributes, PortfolioList.Type.patents,vectorizers);
    }

    public static Map<String,Float> vectorToElasticSearchObject(INDArray vector) {
        float[] data = vector.data().asFloat();
        Map<String, Float> obj = new HashMap<>();
        for (int i = 0; i < data.length; i++) {
            obj.put(String.valueOf(i), data[i]);
        }
        return obj;
    }

    public static String vectorToFastElasticSearchObject(INDArray vector) {
        double[] data = vector.data().asDouble();
        return TestNewFastVectors.vectorToHex(data);
    }

    public static void handleItemsList(List<String> inputs, Collection<ComputableAttribute<?>> attributes, PortfolioList.Type type, Map<String,Vectorizer> vectorizers) {
        Map<String,String> assetToFiling = type.equals(PortfolioList.Type.patents) ? new AssetToFilingMap().getPatentDataMap() : new AssetToFilingMap().getApplicationDataMap();
        AtomicInteger cnt = new AtomicInteger(0);
        inputs.forEach(label->{
            String filing = assetToFiling.get(label);
            // vec
            if(filing!=null) {
                Item item = new Item(label);
                Set<String> attributesToRemove = new HashSet<>();
                attributes.forEach(model -> {
                    Object obj = ((ComputableAttribute)model).attributesFor(Arrays.asList(item.getName()), 1,type.equals(PortfolioList.Type.applications));
                    AbstractAttribute parent = model.getParent();
                    boolean isAttrOfObject = parent!=null && parent.isObject();
                    if(isAttrOfObject&&!item.getDataMap().containsKey(parent.getName())) {
                        item.addData(parent.getName(), new HashMap<String,Object>());
                    }
                    if(obj!=null) {
                        if(obj instanceof LocalDate) {
                            obj = ((LocalDate)obj).format(DateTimeFormatter.ISO_DATE);
                        }
                        if(isAttrOfObject) {
                            // group results to override other values
                            ((Map<String,Object>)item.getDataMap().get(parent.getName())).put(model.getName(),obj);

                        } else {
                            item.addData(model.getMongoDBName(), obj);
                        }
                    } else if(!isAttrOfObject) {
                        attributesToRemove.add(model.getMongoDBName());
                    }
                });
                // handle assignee data
                if(item.getDataMap().containsKey(Constants.LATEST_ASSIGNEE)) {
                    Map<String,Object> assigneeMap = ((Map<String,Object>)item.getDataMap().get(Constants.LATEST_ASSIGNEE));
                    Object assignee = assigneeMap.get(Constants.ASSIGNEE);
                    if(assignee!=null) {
                        Map<String, Object> preComputed = MergeRawAssignees.get().get(assignee.toString());
                        if(preComputed!=null) {
                            preComputed.forEach((k, v) -> {
                                assigneeMap.put(k, v);
                            });
                        }
                    }
                } else {
                    attributesToRemove.add(Constants.LATEST_ASSIGNEE);
                }
                vectorizers.forEach((name,vectorizer)->{
                    INDArray vec = vectorizer.vectorFor(filing);
                    if(vec==null) {
                        vec = vectorizer.vectorFor(label); // default to regular asset name
                    }
                    if(vec!=null) {
                        item.addData(name, vectorToFastElasticSearchObject(vec));

                    } else {
                        attributesToRemove.add(name);
                    }
                });

                if(item.getDataMap().size()>0 || attributesToRemove.size()>0) {
                    DataIngester.ingestItem(item, attributesToRemove);
                    if (debug) System.out.println("Item: " + item.getName());
                }
            }

            if(cnt.getAndIncrement()%100000==99999) {
                System.out.println("Seen "+cnt.get());
            }
            if(cnt.get()%1000000==999999) System.gc();
        });
    }

    private static boolean canCreateUser(String creatorRole, String childRole) {
        if(creatorRole==null||childRole==null) return false;
        if(creatorRole.equals(ANALYST_USER)) return false;
        if(creatorRole.equals(INTERNAL_USER) && (childRole.equals(ANALYST_USER) || childRole.equals(INTERNAL_USER))) return true;
        if(creatorRole.equals(SUPER_USER)) return true;
        return false;
    }

    private static boolean canPotentiallyCreateUser(String creatorRole) {
        if(creatorRole==null) return false;
        if(creatorRole.equals(ANALYST_USER)) return false;
        if(creatorRole.equals(INTERNAL_USER)) return true;
        if(creatorRole.equals(SUPER_USER)) return true;
        return false;
    }

    public static String getUserGroupFor(Session session) {
        String role = session.attribute("role");
        String dynamicUserGroup = session.attribute("dynamicUserGroup");
        if(dynamicUserGroup!=null && dynamicUserGroup.length()>0 && (role.equals(SUPER_USER)||role.equals(INTERNAL_USER))) {
            // find user group
            return dynamicUserGroup;
        } else {
            return session.attribute("userGroup");
        }
    }

    private static void authorize(Request req, Response res) {
        try {
            if (req.session().attribute("authorized") == null || ! (Boolean) req.session().attribute("authorized")) {
                res.redirect("/");
                halt("Access expired. Please sign in.");
            }
        } catch(Exception e) {
            e.printStackTrace();
            res.redirect("/");
            halt("Error during authentication.");
        }
    }

    private static boolean softAuthorize(Request req, Response res) {
        try {
            if (req.session().attribute("authorized") == null || ! (Boolean) req.session().attribute("authorized")) {
                res.redirect("/");
                return false;
            }
        } catch(Exception e) {
            e.printStackTrace();
            res.redirect("/");
            return false;
        }
        return true;
    }

    public static void server() {
        port(8080);

        // HOST ASSETS
        staticFiles.externalLocation(new File("public").getAbsolutePath());

        final PasswordHandler passwordHandler = new PasswordHandler();

        post("/login", (req,res)->{
            Session session = req.session(true);
            String username = extractString(req, "username", "");
            String password = extractString(req, "password", "");
            String role = passwordHandler.authorizeUser(username,password);
            session.attribute("authorized",role!=null);
            if(role==null) {
                halt("User not found.");
            }
            session.attribute("username",username);
            session.attribute("role", role);
            String userGroup = passwordHandler.getUserGroup(username);
            session.attribute("userGroup", userGroup);
            res.status(200);
            res.redirect(HOME_URL);
            return null;
        });

        get("/logout", (req,res)->{
            req.session(true).attribute("authorized",false);
            req.session().removeAttribute("role");
            req.session().removeAttribute("username");
            req.session().removeAttribute("userGroup");
            res.redirect("/");
            res.status(200);
            return null;
        });

        post("/change_dynamic_user_group", (req,res) -> {
            authorize(req,res);
            String role = req.session().attribute("role");
            if(!(role.equals(INTERNAL_USER)||role.equals(SUPER_USER))) {
                return null;
            }
            String newUserGroup = req.queryParams("userGroup");
            if(newUserGroup!=null&&newUserGroup.length()>0) {
                req.session().attribute("dynamicUserGroup",newUserGroup);
            } else {
                if(req.session().attribute("dynamicUserGroup")!=null) {
                    req.session().removeAttribute("dynamicUserGroup");
                }
            }
            res.redirect(HOME_URL);
            return null;
        });

        post("/new_user", (req,res)->{
            authorize(req,res);
            String username = extractString(req, "username", null);
            String password = extractString(req, "password", null);
            String role = extractString(req, "role",null);
            String redirect;
            String message = null;
            if(password == null || username == null || role == null) {
                message = "Please enter a username and password.";
            }
            if(!canCreateUser(req.session().attribute("role"),role)) {
                message = "Unable to create user with specified role.";
            }
            if(message == null) {
                try {
                    passwordHandler.createUser(username, password, role);
                    redirect = HOME_URL;
                    message = "Successfully created user "+username+".";
                } catch (Exception e) {
                    System.out.println("Error while creating user...");
                    e.printStackTrace();
                    redirect = "/create_user";
                    message = e.getMessage();
                }
            } else {
                redirect = "/create_user";
            }
            res.redirect(redirect);
            req.session().attribute("message", message);
            return null;
        });

        post("/update_user", (req,res)->{
            authorize(req,res);
            String username = req.session(false).attribute("username");
            String oldPassword = extractString(req, "old_password", null);
            String newPassword = extractString(req, "new_password", null);
            String redirect;
            String message = null;
            if(newPassword == null || username == null || oldPassword == null) {
                message = "Please enter current and new password.";
            }
            if(message == null) {
                try {
                    passwordHandler.changePassword(username, oldPassword, newPassword);
                    redirect = HOME_URL;
                    message = "Successfully updated user.";
                } catch (Exception e) {
                    System.out.println("Error while updating user...");
                    e.printStackTrace();
                    redirect = "/edit_user";
                    message = e.getMessage();
                }
            } else {
                redirect = "/edit_user";
            }
            res.redirect(redirect);
            req.session().attribute("message", message);
            return null;
        });

        post("/update_user_group", (req,res)->{
            authorize(req,res);
            String username = req.session(false).attribute("username");
            String role = req.session(false).attribute("role");
            String usernameToChange = extractString(req,"username",null);
            String message = null;
            if(role==null||role.equals(ANALYST_USER)) {
                message = "Analysts are not allowed to change their own user groups. Please contact an administrator.";
            }
            String newUserGroup = extractString(req, "user_group", null);
            String redirect;
            if(username == null || usernameToChange == null) {
                message = "Please enter a user.";
            }
            if(message == null) {
                if(role.equals(INTERNAL_USER)&&!username.equals(usernameToChange)) {
                    message = "Internal users can only change their own user groups.";
                }
                if(message == null && (role.equals(INTERNAL_USER)||role.equals(SUPER_USER))) {
                    try {
                        passwordHandler.changeUserGroup(usernameToChange, newUserGroup);
                        if(usernameToChange.equals(username)) {
                            // update session
                            req.session(false).removeAttribute("userGroup");
                            req.session(false).attribute("userGroup", newUserGroup);
                        }
                        redirect = "/edit_user_group";
                        message = "Successfully updated user.";
                    } catch (Exception e) {
                        System.out.println("Error while updating user...");
                        e.printStackTrace();
                        redirect = "/edit_user_group";
                        message = e.getMessage();
                    }
                } else {
                    redirect = "/edit_user_group";
                }
            } else {
                redirect = "/edit_user_group";
            }
            res.redirect(redirect);
            req.session().attribute("message", message);
            return null;
        });

        get("/edit_user_group", (req, res)->{
            authorize(req,res);
            String role = req.session().attribute("role");
            String message = req.session().attribute("message");
            req.session().removeAttribute("message");
            String username = req.session().attribute("username");
            Tag form;
            if(role==null||username==null) {
                form = div();
            } else {
                final boolean isSuperUser = role != null && role.equals(SUPER_USER);
                String selectedOption = isSuperUser ? null : passwordHandler.getUserGroup(username);
                form = form().withId("create-user-form").withAction("/update_user_group").withMethod("POST").attr("style", "margin-top: 100px;").with(
                        (message == null ? span() : div().withClass("not-implemented").withText(
                                message
                        )), br(),
                        (isSuperUser) ?
                                label("User to Update").with(
                                        select().withClass("form-control single-select2").withName("username").with(
                                                passwordHandler.getAllUsers().stream().sorted().map(user -> {
                                                    try {
                                                        String group = passwordHandler.getUserGroup(user);
                                                        if (group == null) group = "(Revoke Access)";
                                                        return option(user + " - " + group).withValue(user);
                                                    } catch (Exception e) {
                                                        return option(user).withValue(user);
                                                    }
                                                }).collect(Collectors.toList())
                                        )
                                ) : div().with(
                                        input().withType("hidden").withName("username").withValue(username)
                                )
                        , br(), br(), label("User Group").with(
                                select().withClass("form-control single-select2").withName("user_group").with(
                                        selectedOption==null ? option("(No user group)").withValue("").attr("selected","selected") : option("(No user group)").withValue("")
                                ).with(
                                        passwordHandler.getUserGroups().stream().sorted().map(userGroup -> {
                                            if(selectedOption!=null && selectedOption.equals(userGroup)) {
                                                return option(userGroup).withValue(userGroup).attr("selected","selected");
                                            }
                                            return option(userGroup).withValue(userGroup);
                                        }).collect(Collectors.toList())
                                ),
                                a("Or create a new user group.").withHref("/user_groups")
                        ), br(), br(), button("Update User Group").withClass("btn btn-outline-secondary")
                );
            }
            return templateWrapper(passwordHandler,true, req, res, form, false);
        });

        post("/new_user_group", (req,res)->{
            authorize(req,res);
            String username = req.session(false).attribute("username");
            String role = req.session(false).attribute("role");
            String userGroup = extractString(req, "user_group", null);
            String redirect;
            String message = null;
            if(role == null || username == null || userGroup == null || (!role.equals(SUPER_USER) && !role.equals(INTERNAL_USER))) {
                message = "Not authorized.";
            }
            if(message == null) {
                try {
                    passwordHandler.createUserGroup(userGroup);
                    redirect = "/edit_user_group";
                    message = "Successfully created user group.";
                } catch (Exception e) {
                    System.out.println("Error while creating user group...");
                    e.printStackTrace();
                    redirect = "/user_groups";
                    message = e.getMessage();
                }
            } else {
                redirect = "/user_groups";
            }
            res.redirect(redirect);
            req.session().attribute("message", message);
            return null;
        });

        get("/user_groups", (req, res)->{
            authorize(req,res);
            String ownerRole = req.session().attribute("role");
            if(ownerRole == null || ownerRole.equals(ANALYST_USER)) return div();
            String message = req.session().attribute("message");
            req.session().removeAttribute("message");
            Tag form = div().with(
                    form().withId("create-user-form").withAction("/new_user_group").withMethod("POST").attr("style","margin-top: 100px;").with(
                            (message == null ? span() : div().withClass("not-implemented").withText(
                                    message
                            )),br(),
                            label("New User Group").with(
                                    input().withType("text").withClass("form-control").withName("user_group")
                            ), br(), br(),  button("Create").withClass("btn btn-outline-secondary")
                    ), div().with(
                            p("Current user groups: "),
                            ul().with(
                                    passwordHandler.getUserGroups().stream().sorted()
                                    .map(userGroup->li(userGroup))
                                    .collect(Collectors.toList())
                            )
                    )
            );
            return templateWrapper(passwordHandler,true, req, res, form, false);
        });

        get("/edit_user", (req, res)->{
            authorize(req,res);
            String ownerRole = req.session().attribute("role");
            String message = req.session().attribute("message");
            req.session().removeAttribute("message");
            Tag form = form().withId("create-user-form").withAction("/update_user").withMethod("POST").attr("style","margin-top: 100px;").with(
                    (message == null ? span() : div().withClass("not-implemented").withText(
                            message
                    )),br(),
                    label("Current Password").with(
                            input().withType("password").withClass("form-control").withName("old_password")
                    ), br(), br(), label("New Password").with(
                            input().withType("password").withClass("form-control").withName("new_password")
                    ), br(), br(), button("Change Password").withClass("btn btn-outline-secondary")
            );
            return templateWrapper(passwordHandler,true, req, res, form, false);
        });

        get("/create_user", (req, res)->{
            authorize(req,res);
            String ownerRole = req.session().attribute("role");
            String message = req.session().attribute("message");
            req.session().removeAttribute("message");
            Tag form = form().withId("create-user-form").withAction("/new_user").withMethod("POST").attr("style","margin-top: 100px;").with(
                    (message == null ? span() : div().withClass("not-implemented").withText(
                            message
                    )),br(),
                    label("Username").with(
                            input().withType("text").withClass("form-control").withName("username")
                    ), br(), br(), label("Password").with(
                            input().withType("password").withClass("form-control").withName("password")
                    ), br(), br(), label("Role").with(
                            select().withClass("form-control single-select2").withName("role").with(
                                    USER_ROLES.stream().filter(role->canCreateUser(ownerRole,role)).map(role->{
                                        return option(role).withValue(role);
                                    }).collect(Collectors.toList())
                            )
                    ), br(), br(), button("Create User").withClass("btn btn-outline-secondary")
            );
            return templateWrapper(passwordHandler,true, req, res, form, false);
        });

        post("/remove_user", (req,res)->{
            authorize(req,res);
            String role = req.session(false).attribute("role");
            String userToDelete = extractString(req, "user_to_delete", null);
            String redirect;
            String message = null;
            if(role==null||!role.equals(SUPER_USER)) {
                message = "Not properly authenticated.";
            } else if(userToDelete == null) {
                message = "Please enter a user to remove.";
            }
            if(message == null) {
                try {
                    passwordHandler.deleteUser(userToDelete);
                    redirect = "/delete_user";
                    message = "Successfully deleted user.";
                } catch (Exception e) {
                    System.out.println("Error while updating user...");
                    e.printStackTrace();
                    redirect = "/delete_user";
                    message = e.getMessage();
                }
            } else {
                redirect = "/delete_user";
            }
            res.redirect(redirect);
            req.session().attribute("message", message);
            return null;
        });

        get("/delete_user", (req, res)->{
            authorize(req,res);
            String ownerRole = req.session().attribute("role");
            Tag form;
            String message = req.session().attribute("message");
            req.session().removeAttribute("message");
            if(ownerRole!=null&&ownerRole.equals(SUPER_USER)) {
                form = form().withId("create-user-form").withAction("/remove_user").withMethod("POST").attr("style", "margin-top: 100px;").with(
                        (message == null ? span() : div().withClass("not-implemented").withText(
                                message
                        )), br(),
                        label("User to Remove").with(
                                select().withClass("form-control single-select2").withName("user_to_delete").with(
                                        passwordHandler.getAllUsers().stream().sorted().map(user -> {
                                            return option(user).withValue(user);
                                        }).collect(Collectors.toList())
                                )
                        ), br(), br(), button("Remove User").withClass("btn btn-outline-secondary")
                );
            } else {
                form = div().with(
                        p("Unable to access this page. Only administrators can delete user accounts.")
                );
            }
            return templateWrapper(passwordHandler,true, req, res, form,false);
        });

        get("/", (req, res)->{
            try {
                return templateWrapper(passwordHandler, false, req, res, form().withClass("form-group").withMethod("POST").withAction("/login").attr("style", "margin-top: 100px;").with(
                        p("Log in"),
                        label("Username").with(
                                input().withType("text").withClass("form-control").withName("username")
                        ), br(), br(), label("Password").with(
                                input().withType("password").withClass("form-control").withName("password")
                        ), br(), br(), button("Login").withType("submit").withClass("btn btn-outline-secondary")
                ), false);
            } catch(Exception e) {
                e.printStackTrace();
                System.out.println("During /");
                return null;
            }
        });


        get(RESET_DEFAULT_TEMPLATE_URL, (req,res)->{
            authorize(req,res);
            String actualUser = req.session().attribute("username");
            if(actualUser==null) return null;
            String userGroup = getUserGroupFor(req.session());
            String fileName = actualUser;
            String message = deleteForm(fileName,Constants.USER_DEFAULT_ATTRIBUTES_FOLDER,actualUser,userGroup,false,false);
            System.out.println("Delete form message: "+message);
            res.redirect(HOME_URL);
            return null;
        });

        post(REPORT_URL, (req, res) -> {
            authorize(req,res);
            return handleReport(req,res);
        });

        post(SAVE_TEMPLATE_URL, (req, res) -> {
            authorize(req,res);
            boolean defaultFile = Boolean.valueOf(req.queryParamOrDefault("defaultFile","false"));
            if(defaultFile) {
                return handleUpdateDefaultAttributes(req,res);
            } else {
                return handleSaveForm(req, res, Constants.USER_TEMPLATE_FOLDER, templateFormMapFunction(), saveTemplatesFunction(), saveTemplateUpdatesFunction());
            }
        });

        post(GET_TEMPLATE_URL, (req, res) -> {
            authorize(req,res);
            return handleGetForm(req,res,Constants.USER_TEMPLATE_FOLDER,false);
        });

        post(DELETE_TEMPLATE_URL, (req, res) -> {
            authorize(req,res);
            return handleDeleteForm(req,res,Constants.USER_TEMPLATE_FOLDER,false);
        });

        post(RENAME_TEMPLATE_URL, (req, res) -> {
            authorize(req,res);
            return handleRenameForm(req,res,Constants.USER_TEMPLATE_FOLDER,true,false);
        });

        post(SAVE_DATASET_URL, (req, res) -> {
            authorize(req,res);
            String user = req.session().attribute("username");

            String userGroup = getUserGroupFor(req.session());
            return handleSaveForm(req,res,Constants.USER_DATASET_FOLDER,datasetFormMapFunction(),saveDatasetsFunction(user,userGroup),saveDatasetUpdatesFunction());
        });

        post(GET_DATASET_URL, (req, res) -> {
            authorize(req,res);
            return handleGetForm(req,res,Constants.USER_DATASET_FOLDER,true);
        });

        post(DELETE_DATASET_URL, (req, res) -> {
            authorize(req,res);
            return handleDeleteForm(req,res,Constants.USER_DATASET_FOLDER,true);
        });

        post(RENAME_DATASET_URL, (req, res) -> {
            authorize(req,res);
            return handleRenameForm(req,res,Constants.USER_DATASET_FOLDER,false,true);
        });

        // Host my own image asset!
        get("/images/brand.png", (request, response) -> {
            response.type("image/png");
            String pathToImage = "public/images/brand.png";
            File f = new File(pathToImage);
            BufferedImage bi = ImageIO.read(f);
            OutputStream out = response.raw().getOutputStream();
            ImageIO.write(bi, "png", out);
            out.close();
            response.status(200);
            return response.body();
        });

        // setup select2 ajax remote data sources
        get(Constants.DATASET_NAME_AJAX_URL, (req,res)->{
            String username = req.session(false).attribute("username");
            String userGroup = getUserGroupFor(req.session());
            if(username==null||userGroup==null) {
                throw halt("No username and/or usergroup.");
            }
            Long lastSearchTime = req.session(false).attribute("dataset-lastSearchTime");
            if(lastSearchTime==null) lastSearchTime= System.currentTimeMillis();
            Long currentSearchTime = System.currentTimeMillis();

            SortedMap<String,String> sortedNameToIdMap = req.session(false).attribute("dataset-trie");
            Map<String,String> idToNameMap;
            if(sortedNameToIdMap==null||currentSearchTime>lastSearchTime+(2000)) {
                sortedNameToIdMap = new TreeMap<>();
                idToNameMap = new HashMap<>();
                idToNameMap.putAll(getDatasetIdToNameMaps(username,"My Datasets"));
                idToNameMap.putAll(getDatasetIdToNameMaps(userGroup, "Shared Datasets"));
                for(String id : idToNameMap.keySet()) {
                    sortedNameToIdMap.put(idToNameMap.get(id).toLowerCase(),id);
                }
                req.session(false).attribute("dataset-trie",sortedNameToIdMap);
                req.session(false).attribute("dataset-map",idToNameMap);
            } else {
                idToNameMap = req.session(false).attribute("dataset-map");
            }
            final SortedMap<String,String> finalTrie = sortedNameToIdMap;
            Function<String,List<String>> resultsSearchFunction = search -> {
                if(search==null) return new ArrayList<>(idToNameMap.keySet());
                List<String> list = new ArrayList<>();
                final String lowerSearch = search.toLowerCase();
                finalTrie.forEach((name,id)->{
                    if(name.contains(lowerSearch)) {
                        list.add(id);
                    }
                });
                return list;
            };
            Function<String,String> displayFunction = result ->  idToNameMap.get(result);

            req.session(false).attribute("dataset-lastSearchTime",currentSearchTime);
            return handleAjaxRequest(req, resultsSearchFunction, displayFunction);
        });


        // setup select2 ajax remote data sources
        get(Constants.ASSIGNEE_NAME_AJAX_URL, (req,res)->{
            Function<String,List<String>> resultsSearchFunction = search -> Database.sortedPossibleAssignees(search);
            Function<String,String> displayFunction = result ->  result + " (" + Database.getAssetCountFor(result)+")";
            return handleAjaxRequest(req, resultsSearchFunction, displayFunction);
        });

        // setup select2 ajax remote data sources
        get(Constants.CPC_CODE_AJAX_URL, (req,res)->{
            Function<String,List<String>> resultsSearchFunction = search -> Database.sortedPossibleClassCodes(search);
            Function<String,String> displayFunction = result -> result;
            return handleAjaxRequest(req, resultsSearchFunction, displayFunction);
        });

        // TODO get technology filtering for AJAX
        // setup select2 ajax remote data sources
        get(Constants.GTT_TECHNOLOGY_AJAX_URL, (req,res)->{
            Function<String,List<String>> resultsSearchFunction = search -> new ArrayList<>();
            Function<String,String> displayFunction = result -> result;
            return handleAjaxRequest(req, resultsSearchFunction, displayFunction);
        });

        // setup select2 ajax remote data sources
        get(Constants.NORMALIZED_ASSIGNEE_NAME_AJAX_URL, (req,res)->{
            Function<String,List<String>> resultsSearchFunction = search -> Database.sortedPossibleNormalizedAssignees(search);
            Function<String,String> displayFunction = result ->  result + " (" + Database.getNormalizedAssetCountFor(result)+")";
            return handleAjaxRequest(req, resultsSearchFunction, displayFunction);
        });
    }

    private static Object handleAjaxRequest(Request req, Function<String,List<String>> resultsSearchFunction, Function<String,String> displayFunction) {
        List<String> neededLabels = extractArray(req,"get_label_for[]");
        if(neededLabels.size()>0) {
            Map<String,Object> response = new HashMap<>();
            List<String> labels = neededLabels.stream().map(label->displayFunction.apply(label)).collect(Collectors.toList());
            response.put("labels", labels);
            response.put("values", neededLabels);
            return new Gson().toJson(response);
        } else {
            int PER_PAGE = 30;
            String search = req.queryParams("search");
            int page = Integer.valueOf(req.queryParamOrDefault("page", "1"));

            System.out.println("Search: " + search);
            System.out.println("Page: " + page);

            List<String> allResults = resultsSearchFunction.apply(search);

            int start = (page - 1) * PER_PAGE;
            int end = start + PER_PAGE;

            List<Map<String, Object>> results;
            if (start >= allResults.size()) {
                results = Collections.emptyList();
            } else {
                results = allResults.subList(start, Math.min(allResults.size(), end)).stream().map(result -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", result);
                    map.put("text", displayFunction.apply(result));
                    return map;
                }).collect(Collectors.toList());
            }

            Map<String, Boolean> pagination = new HashMap<>();
            pagination.put("more", end < allResults.size());

            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("pagination", pagination);
            return new Gson().toJson(response);
        }
    }

    private static Function3<Map<String,Object>,File,Boolean,Void> saveTemplatesFunction() {
        return (formMap,file,shared) -> {
            Database.trySaveObject(formMap, file);
            return null;
        };
    }

    private static Function2<Map<String,Object>,File,Void> saveTemplateUpdatesFunction() {
        return (updateMap,updatesFile)->{
            if (updatesFile.exists()) updatesFile.delete();
            if (updateMap.size() > 0) {
                Database.trySaveObject(updateMap, updatesFile);
            }
            return null;
        };
    }

    private static Function3<Map<String,Object>,File,Boolean,Void> saveDatasetsFunction(String user, String userGroup) {
        return (formMap, file, shared) -> {
            String[] assets = (String[])formMap.get("assets");
            if(assets!=null&&file!=null) {
                String username = shared ? userGroup : user;
                DatasetIndex.index(username,file.getName(),Arrays.asList(assets));
            }
            formMap.remove("assets");
            Database.trySaveObject(formMap,file);
            return null;
        };
    }

    private static Function2<Map<String,Object>,File,Void> saveDatasetUpdatesFunction() {
        return (updateMap,updatesFile) -> {
            return null;
        };
    }



    private static Object handleRenameForm(Request req, Response res, String baseFolder, boolean useUpdatesFile, boolean isDataset) {
        String filename = req.queryParams("file");
        String name = req.queryParams("name");
        String[] parentDirs = req.queryParamsValues("parentDirs[]");
        String userGroup = getUserGroupFor(req.session());
        String message;
        Map<String,Object> responseMap = new HashMap<>();
        if(filename!=null&&name!=null&&name.length()>0) {
            name = decodeURLString(name);
            if(parentDirs!=null) {
                for(int i = 0; i < parentDirs.length; i++) {
                    parentDirs[i]=decodeURLString(parentDirs[i]);
                }
            }

            boolean isShared = false;
            if(parentDirs!=null&&parentDirs.length>0&&parentDirs[0].startsWith("Shared")) {
                isShared = true;
            }
            String username = isShared ? userGroup : req.session().attribute("username");
            if(username!=null&&username.length()>0) {
                String templateFolderStr = baseFolder+username+"/";
                File formFile = new File(templateFolderStr+filename);
                File updatesFile = new File(formFile.getAbsolutePath()+"_updates");
                if(formFile.exists()) {
                    Map<String,Object> updates = new HashMap<>();
                    updates.put("name", name);
                    if (parentDirs != null && parentDirs.length > 0) updates.put("parentDirs", Arrays.copyOfRange(parentDirs,1,parentDirs.length));

                    Lock sync;
                    synchronized (fileSynchronizationMap) {
                        fileSynchronizationMap.putIfAbsent(formFile.getAbsolutePath(),new ReentrantLock());
                        sync = fileSynchronizationMap.get(formFile.getAbsolutePath());
                    }

                    sync.lock();
                    try {
                        if(useUpdatesFile) {
                            Database.trySaveObject(updates, updatesFile);
                        } else {
                            Database.trySaveObject(updates,formFile);
                        }
                    } finally {
                        sync.unlock();
                    }

                    message = "Saved sucessfully.";
                } else {
                    message = "Unable to find form.";
                }
            } else {
                message = "Unable to find user.";
            }
        } else {
            if(name==null||name.isEmpty()) {
                message = "Please enter a valid name.";
            } else {
                message = "Unable to create form. Data missing.";
            }
        }
        responseMap.put("message", message);
        return new Gson().toJson(responseMap);
    }

    public static String datasetNameFrom(String user, String file) {
        try {
            Integer.valueOf(file);
        } catch(Exception e) {
            return "";
        }

        Map<String,Object> data = getMapFromFile(new File(Constants.USER_DATASET_FOLDER+user+"/"+file),true);
        return data.getOrDefault("name","").toString();
    }

    private static Object handleGetForm(Request req, Response res, String baseFolder, boolean dataset) {
        String file = req.queryParams("file");
        boolean defaultFile = Boolean.valueOf(req.queryParamOrDefault("defaultFile","false"));
        boolean shared = Boolean.valueOf(req.queryParamOrDefault("shared","false"));
        String userGroup = getUserGroupFor(req.session());
        String user = req.session().attribute("username");
        if(user==null||user.isEmpty()) {
            return null;
        }

        String filename;
        if(defaultFile) {
            filename = Constants.USER_DEFAULT_ATTRIBUTES_FOLDER+user+"/"+user;
            // may not exist so default to super user default attributes form
            if(! new File(filename).exists()) {
                filename = Constants.USER_DEFAULT_ATTRIBUTES_FOLDER+SUPER_USER+"/"+SUPER_USER;
            }
        } else {
            filename = baseFolder + (shared ? userGroup : user) + "/" + file;
        }

        Map<String,Object> data = getMapFromFile(new File(filename),true);

        if(dataset) {
            String username = shared ? userGroup : user;
            data.put("assets", DatasetIndex.get(username,file));
        }

        return new Gson().toJson(data);
    }


    private static Function<Request,Map<String,Object>> templateFormMapFunction() {
        return req -> {
            String attributesMap = req.queryParams("attributesMap");
            String searchOptionsMap = req.queryParams("searchOptionsMap");
            String filtersMap = req.queryParams("filtersMap");
            String highlightMap = req.queryParams("highlightMap");
            String chartsMap = req.queryParams("chartsMap");
            String name = req.queryParams("name");
            if(attributesMap!=null&&searchOptionsMap!=null&&chartsMap!=null&&highlightMap!=null&&filtersMap!=null&&name!=null&&name.length()>0) {
                Map<String, Object> formMap = new HashMap<>();

                name = decodeURLString(name);

                formMap.put("name", name);
                formMap.put("attributesMap", attributesMap);
                formMap.put("searchOptionsMap", searchOptionsMap);
                formMap.put("filtersMap", filtersMap);
                formMap.put("chartsMap", chartsMap);
                formMap.put("highlightMap", highlightMap);
                // check file
                String file = req.queryParams("file");
                if(file!=null) {
                    formMap.put("file",file);
                }
                return formMap;
            } else return null;
        };
    }

    public static String decodeURLString(String in) {
        try {
            in = in.replace("&amp;","&");
            in = in.replace("&lt;","<");
            in = in.replace("&gt;",">");
            in = in.replace("&quot;","\"");
        } catch(Exception e) {
            e.printStackTrace();
        }
        return in;
    }

    private static Function<Request,Map<String,Object>> datasetFormMapFunction() {
        return req -> {
            String userGroup = getUserGroupFor(req.session());
            String[] assets = req.queryParamsValues("assets[]");
            if(assets==null) assets = req.session(false).attribute("assets"); // default to last seen report
            String name = req.queryParams("name");
            if(assets!=null&&name!=null&&name.length()>0) {
                name = decodeURLString(name);
                String file = req.queryParams("file");
                Map<String, Object> formMap = new HashMap<>();
                formMap.put("name", name);
                formMap.put("assets", assets);
                boolean addToAssets = Boolean.valueOf(req.queryParamOrDefault("addToAssets","false"));
                if(addToAssets&&file!=null) {
                    synchronized (DatasetIndex.class) {
                        String[] parentDirs = req.queryParamsValues("parentDirs[]");
                        boolean isShared = false;
                        if (parentDirs != null && parentDirs.length > 0 && parentDirs[0].startsWith("Shared")) {
                            isShared = true;
                        }
                        String username = isShared ? userGroup : req.session(false).attribute("username");
                        List<String> prevAssets = DatasetIndex.get(username, file);
                        if (prevAssets == null) prevAssets = Collections.emptyList();
                        List<String> allAssets = Stream.of(prevAssets, Arrays.asList(assets)).flatMap(s -> s.stream()).distinct().collect(Collectors.toList());
                        formMap.put("assets", allAssets.toArray(new String[allAssets.size()]));
                    }
                }

                // check file
                if(file!=null) {
                    formMap.put("file",file);
                }
                return formMap;
            } else return null;
        };
    }

    private static Object handleUpdateDefaultAttributes(Request req, Response res) {
        String username = req.session().attribute("username");
        if(username == null) return null;
        Function<Map<String,Object>,Map<String,Object>> afterFunction = map -> {
            map.put("file", username);
            return map;
        };
        return handleSaveForm(req,res,Constants.USER_DEFAULT_ATTRIBUTES_FOLDER,templateFormMapFunction().andThen(afterFunction),saveTemplatesFunction(),saveTemplateUpdatesFunction());
    }


    private static Pair<String,Map<String,Object>> saveFormToFile(String userGroup, Map<String,Object> formMap, String name, String[] parentDirs, String actualUsername, String baseFolder, Function3<Map<String,Object>,File,Boolean,Void> saveFunction, Function2<Map<String,Object>,File,Void> saveUpdatesFunction) {
        String message;
        Random random = new Random(System.currentTimeMillis());
        Map<String,Object> responseMap = new HashMap<>();

        Object prevFilename = formMap.get("file");
        if(formMap!=null) {
            if(debug) System.out.println("Form "+name+" attributes: "+new Gson().toJson(formMap));

            if (parentDirs != null && parentDirs.length > 0) {
                for(int i = 1; i < parentDirs.length; i++) {
                    parentDirs[i]=decodeURLString(parentDirs[i]);
                }
                formMap.put("parentDirs", Arrays.copyOfRange(parentDirs,1,parentDirs.length));
            }

            boolean isShared = false;
            if(parentDirs!=null&&parentDirs.length>0&&parentDirs[0].startsWith("Shared")) {
                isShared = true;
            }
            String username = isShared ? userGroup : actualUsername;
            if(username!=null&&username.length()>0) {
                String templateFolderStr = baseFolder+username+"/";
                File templateFolder = new File(templateFolderStr);
                if(!templateFolder.exists()) templateFolder.mkdirs();
                File file = null;
                if(prevFilename==null) {
                    while (file == null || file.exists()) {
                        file = new File(templateFolderStr + Math.abs(random.nextInt()));
                    }
                } else {
                    System.out.println("Saving to previous file: "+prevFilename.toString());
                    file = new File(templateFolderStr+prevFilename);

                }

                Map<String, Object> updateMap = new HashMap<>();
                if (formMap.containsKey("name")) {
                    updateMap.put("name", formMap.get("name"));
                }
                if (formMap.containsKey("parentDirs")) {
                    updateMap.put("parentDirs", formMap.get("parentDirs"));
                }

                File updatesFile = new File(file.getAbsolutePath() + "_updates");

                Lock sync;
                synchronized (fileSynchronizationMap) {
                    fileSynchronizationMap.putIfAbsent(file.getAbsolutePath(),new ReentrantLock());
                    sync = fileSynchronizationMap.get(file.getAbsolutePath());
                }

                sync.lock();
                try {
                    saveFunction.apply(formMap,file,isShared);
                    saveUpdatesFunction.apply(updateMap,updatesFile);

                } finally {
                    sync.unlock();
                }
                message = "Saved sucessfully.";
                responseMap.put("file",file.getName());
                responseMap.put("user",username);

            } else {
                message = "Unable to find user.";
            }
        } else {
            if(name==null||name.isEmpty()) {
                message = "Please enter a name for the new template.";
            } else {
                message = "Unable to create form. Data missing.";
            }
        }
        return new Pair<>(message, responseMap);
    }

    private static Object handleSaveForm(Request req, Response res, String baseFolder, Function<Request,Map<String,Object>> formMapFunction, Function3<Map<String,Object>,File,Boolean,Void> saveFunction, Function2<Map<String,Object>,File,Void> saveUpdatesFunction) {
        String name = req.queryParams("name");
        String userGroup = getUserGroupFor(req.session());
        String[] parentDirs = req.queryParamsValues("parentDirs[]");
        if(parentDirs==null) {
            System.out.println("Parent dirs is null...");
        } else {
            for(int i = 0; i < parentDirs.length; i++) {
                parentDirs[i] = decodeURLString(parentDirs[i]);
            }
            System.out.println("Parent dirs: "+Arrays.toString(parentDirs));
        }

        if(name!=null) {
            name = decodeURLString(name);
        }

        String actualUsername = req.session().attribute("username");
        Map<String,Object> formMap = formMapFunction.apply(req);

        Pair<String,Map<String,Object>> pairResponse = saveFormToFile(userGroup,formMap,name,parentDirs,actualUsername,baseFolder,saveFunction,saveUpdatesFunction);

        String message = pairResponse.getFirst();
        Map<String,Object> responseMap = pairResponse.getSecond();
        responseMap.put("message",message);
        return new Gson().toJson(responseMap);
    }

    private static String deleteForm(String fileName, String baseFolder, String actualUser, String userGroup, boolean shared, boolean deleteFromES) {
        String message;
        if(fileName!=null && fileName.replaceAll(PasswordHandler.USER_NAME_REGEX,"").length() > 0) {
            fileName = fileName.replaceAll(PasswordHandler.USER_NAME_REGEX,"");
            try {
                String username = shared ? userGroup : actualUser;
                if(username==null||username.isEmpty()) {
                    message = "Unable to locate user.";
                } else {
                    File toDelete = new File(baseFolder+username+"/"+fileName);
                    File updatesFile = new File(toDelete.getAbsolutePath()+"_updates");

                    Lock sync;
                    synchronized (fileSynchronizationMap) {
                        fileSynchronizationMap.putIfAbsent(toDelete.getAbsolutePath(),new ReentrantLock());
                        sync = fileSynchronizationMap.get(toDelete.getAbsolutePath());
                    }

                    sync.lock();
                    try {
                        if(deleteFromES) {
                            DatasetIndex.delete(username,toDelete.getName());
                        }

                        if (toDelete.exists() && toDelete.isFile()) {
                            boolean success = toDelete.delete();
                            // check updates file
                            if (updatesFile.exists()) {
                                updatesFile.delete();
                            }
                            message = "Success: " + success;
                        } else {
                            message = "Unable to locate file.";
                        }
                    } finally {
                        sync.unlock();
                    }

                    synchronized (fileSynchronizationMap) {
                        fileSynchronizationMap.remove(toDelete.getAbsolutePath());
                    }
                }
            } catch(Exception e) {
                message = e.getMessage();
            }
        } else {
            message = "Did not provide path.";
        }
        return message;
    }

    private static Object handleDeleteForm(Request req, Response res, String baseFolder, boolean deleteFromES) {
        String fileName = req.queryParams("path_to_remove");
        boolean shared = Boolean.valueOf(req.queryParamOrDefault("shared","false"));
        String actualUser = req.session().attribute("username");
        String userGroup = getUserGroupFor(req.session());
        String message = deleteForm(fileName,baseFolder,actualUser,userGroup,shared,deleteFromES);

        return new Gson().toJson(new SimpleAjaxMessage(message));
    };

    private static Object handleReport(Request req, Response res) {
        /*try {
            ProcessBuilder ps = new ProcessBuilder("/bin/bash", "-c", "curl http://"+PLATFORM_STARTER_IP_ADDRESS+":8080/ping");
            ps.start();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("While pinging platform starter...");
        }*/

        List<RecursiveTask> otherTasks = Collections.synchronizedList(new ArrayList<>());
        // start timer
        RecursiveTask<String> handleReportTask = new RecursiveTask<String>() {
            @Override
            protected String compute() {
                return null;
            }
        };

        pool.execute(handleReportTask);

        long maxTimeMillis =  180 * 1000;
        try {
            String html = handleReportTask.get(maxTimeMillis, TimeUnit.MILLISECONDS);
            return html;
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Timeout exception!");
            return new Gson().toJson(new SimpleAjaxMessage("Timeout occurred after "+(maxTimeMillis/(60*1000))+" minutes."));
        } finally {
            try {
                if(!handleReportTask.isDone()){
                    // clean up other tasks
                    otherTasks.forEach(task->{
                        try {
                            task.cancel(true);
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    });
                    handleReportTask.cancel(true);
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }


    static Tag tableFromPatentList(List<String> attributes, boolean preview, long total) {
        return span().withClass("collapse show").withId(preview? "data-table-preview":"data-table").with(
                form().withMethod("post").attr("style", "text-align: center;").withTarget("_blank").withAction(preview?PREVIEW_DOWNLOAD_URL:DOWNLOAD_URL).with(
                        button("Download to CSV").withType("submit").withClass("btn btn-outline-secondary div-button").attr("style","width: 40%; margin-bottom: 20px;")
                ),
                div().withClass("col-12").with(
                        div().withClass("row").with(
                            div().withClass("col-6").with(
                                    span().withText("Number of assets selected: ").attr("style", "float: left;").with(
                                        span().withClass("table-selection-counter")
                                    )
                            ), div().withClass("col-6").with(
                                    span().attr("style", "float: right;").with(
                                            div().withId(preview?"preview-data-table-pagination-clone-holder":"data-table-pagination-clone-holder")
                                    )
                            )
                        )
                ),
                dataTableFromHeadersAndData(attributes, preview, total)
        );
    }

    static Tag dataTableFromHeadersAndData(List<String> attributes, boolean preview, long totalHits) {
        return table().withClass("table table-striped").attr("data-total", String.valueOf(totalHits)).withId(preview?"main-preview-data-table":"main-data-table").attr("style","margin-left: 3%; margin-right: 3%; width: 94%;").with(
                thead().with(
                        tr().with(
                                th().attr("data-dynatable-no-sort", "true").attr("data-dynatable-column", "selection").with(
                                        label("Select All").with( br(),
                                                input().withType("checkbox").withId(preview?"preview-data-table-select-all":"data-table-select-all").attr("onclick", "$('#"+(preview?"data-table-preview":"data-table")+" table tbody tr input.tableSelection').prop('checked', $(this).prop('checked')).trigger('change');")
                                        )
                                ),
                                th().attr("data-dynatable-column", "idx").withText(
                                        "Index"
                                )
                        ).with(
                                attributes.stream().map(attr -> th(fullHumanAttributeFor(attr)).attr("data-dynatable-column", attr)).collect(Collectors.toList())
                        )
                ), tbody()
        );
    }

    static String selectionTag(String id) {
        return span().with(input().withType("checkbox").withClass(BigQueryServer.TABLE_SELECTION_FIELD).withValue(id)).render();
    }

    static List<Map<String,String>> getTableRowData(List<Item> items, List<String> attributes, boolean useHighlighter, String itemSeparator) {
        AtomicLong cnt = new AtomicLong(1L);
        return items.stream().map(item -> {
            Map<String,String> map = item.getDataAsMap(attributes,useHighlighter,itemSeparator);
            map.put("selection", selectionTag(item.getName()));
            map.put("idx", String.valueOf(cnt.getAndIncrement()));
            return map;
        }).collect(Collectors.toList());
    }

    public static Tag addAttributesToRow(ContainerTag tag, List<String> data, List<String> headers) {
        AtomicReference<ContainerTag> ref = new AtomicReference<>(tag);
        for(int i = 0; i < data.size(); i++) {
            ref.set(ref.get().attr("data-"+headers.get(i),data.get(i).toString()));
        }
        return ref.get();
    }

    public static List<String> preProcess(String toSplit, String delim, String toReplace) {
        if(toSplit==null||toSplit.trim().length()==0) return new ArrayList<>();
        toSplit = toSplit.replace("\r","");
        return Arrays.asList(toSplit.split(delim)).stream().filter(str->str!=null).map(str->toReplace!=null&&toReplace.length()>0?str.replaceAll(toReplace,"").trim():str).filter(str->str!=null&&!str.isEmpty()).collect(Collectors.toList());
    }

    private static Map<String,Object> getMapFromFile(File file, boolean loadData) {
        File updatesFile = new File(file.getAbsolutePath() + "_updates");
        loadData = loadData || !updatesFile.exists();

        Lock sync;
        Map<String,Object> updates = null;
        Map<String, Object> templateMap = null;
        synchronized (fileSynchronizationMap) {
            fileSynchronizationMap.putIfAbsent(file.getAbsolutePath(),new ReentrantLock());
            sync = fileSynchronizationMap.get(file.getAbsolutePath());
        }

        sync.lock();
        try {
            if (loadData) {
                templateMap = (Map<String, Object>) Database.tryLoadObject(file,false);
            } else {
                if(updates!=null) {
                    templateMap = updates;
                } else {
                    templateMap = (Map<String, Object>) Database.tryLoadObject(updatesFile,false);
                }
            }

            if(templateMap!=null && loadData) {
                // check updates file
                if (updatesFile.exists() || updates!=null) {
                    if(updates==null) {
                        updates = (Map<String, Object>) Database.tryLoadObject(updatesFile,false);
                    }
                    if (updates != null) {
                        templateMap.put("name", updates.get("name"));
                        templateMap.put("parentDirs", (String[]) updates.get("parentDirs"));
                    }
                }
            }

        } finally {
            sync.unlock();
        }
        return templateMap;
    }

    public static Tag getTemplatesForUser(String username, boolean deletable, String rootName, boolean loadData) {
        if(username==null) return div();
        Function2<Map<String,Object>,File,FormTemplate> formTemplateFunction = (templateMap,file) -> {
            FormTemplate template;

            Object name = templateMap.get("name");
            if (loadData) {
                Object searchObjectsMap = templateMap.get("searchOptionsMap");
                Object attributesMap = templateMap.get("attributesMap");
                Object chartsMap = templateMap.get("chartsMap");
                Object filtersMap = templateMap.get("filtersMap");
                Object highlightMap = templateMap.getOrDefault("highlightMap", templateMap.getOrDefault("highlight", ""));

                if (name != null && searchObjectsMap != null && attributesMap != null && chartsMap != null && filtersMap != null) {

                    // create template
                    template = new FormTemplate(file, username, name.toString(), searchObjectsMap.toString(), attributesMap.toString(), filtersMap.toString(), chartsMap.toString(), highlightMap.toString());


                } else template = null;
            } else {
                if (name != null) {
                    template = new FormTemplate(file, username, name.toString(), -1);
                } else template = null;
            }
            return template;
        };

        return getDataForUser(username,deletable,rootName,Constants.USER_TEMPLATE_FOLDER,loadData,formTemplateFunction);
    }

    public static Tag getDatasetsForUser(String username, boolean deletable, String rootName) {
        if(username==null) return div();
        Function2<Map<String,Object>,File,FormTemplate> formTemplateFunction = (templateMap,file) -> {
            FormTemplate template;
            Object name = templateMap.get("name");
            if (name != null) {
                template = new FormTemplate(file, username, name.toString(), -1);
            } else template = null;
            return template;
        };
        return getDataForUser(username,deletable,rootName,Constants.USER_DATASET_FOLDER,false,formTemplateFunction);
    }

    private static Map<String,String> getDatasetIdToNameMaps(String username,@NonNull String topLevelFolderName) {
        if(username!=null) {
            File folder = new File(Constants.USER_DATASET_FOLDER+username+"/");
            if(!folder.exists()) folder.mkdirs();
            Map<String,String> results = new HashMap<>();
            Arrays.stream(folder.listFiles(file->!file.getName().endsWith("_updates"))).forEach(file->{
                Map<String,Object> templateMap = getMapFromFile(file, false);
                String[] parentDirs = (String[])templateMap.get("parentDirs");
                String name = (String)templateMap.get("name");
                if(parentDirs!=null&&name!=null) {
                    results.put(file.getName()+"_"+username,topLevelFolderName+"/"+String.join("/",parentDirs)+"/"+name);
                }
            });
            return results;
        } else return Collections.emptyMap();
    }

    public static Tag getDataForUser(String username, boolean deletable, String rootName, String baseFolder, boolean loadData, Function2<Map<String,Object>,File,FormTemplate> formTemplateFunction) {
        if(username!=null && username.length()>0) {
            File folder = new File(baseFolder+username+"/");
            if(!folder.exists()) folder.mkdirs();
            Pair<Map<String,Object>,List<FormTemplate>> directoryStructure = new Pair<>(new HashMap<>(),new ArrayList<>());
            Arrays.stream(folder.listFiles(file->!file.getName().endsWith("_updates"))).forEach(file->{
                Map<String,Object> templateMap = getMapFromFile(file, loadData);

                String[] parentDirs = (String[])templateMap.get("parentDirs");
                Pair<Map<String, Object>, List<FormTemplate>> currentDirectory = directoryStructure;
                if (parentDirs != null) { // build directory as necessary
                    //System.out.println("Parent Dirs for "+file.getName()+": "+Arrays.toString(parentDirs));
                    for (String dir : parentDirs) {
                        currentDirectory.getFirst().putIfAbsent(dir, new Pair<>(new HashMap<>(), new ArrayList<>()));
                        currentDirectory = (Pair<Map<String, Object>, List<FormTemplate>>) currentDirectory.getFirst().get(dir);
                    }
                }

                FormTemplate template = formTemplateFunction.apply(templateMap,file);

                // add to current directory
                if (template!=null) currentDirectory.getSecond().add(template);
            });

            // recursively build directory
            return templateHelper(directoryStructure,rootName,deletable, new ArrayList<>(), loadData, true);
        } else {
            return div();
        }
    }


    public static Tag templateHelper(Pair<Map<String,Object>,List<FormTemplate>> directoryStructure, String folderName, boolean deletable, List<String> parentDirs, boolean loadData, boolean topLevel) {
        // find nested
        if(!topLevel&&directoryStructure.getFirst().isEmpty()&&directoryStructure.getSecond().isEmpty()) return span();

        return li(folderName).attr("data-deletable", String.valueOf(deletable)).attr("data-jstree","{\"type\":\"folder\"}").with(
                ul().with(
                        directoryStructure.getFirst().entrySet().stream()
                                //.sorted(Comparator.comparing(e->e.getKey()))
                                .map(e->{
                                    List<String> parentDirsCopy = new ArrayList<>(parentDirs);
                                    parentDirsCopy.add(e.getKey());
                                    return templateHelper((Pair<Map<String,Object>,List<FormTemplate>>)e.getValue(),e.getKey(),deletable,parentDirsCopy, loadData, false);
                                })
                        .collect(Collectors.toList())
                ).with(
                        directoryStructure.getSecond().stream()
                                //.sorted(Comparator.comparing(e->e.getName()))
                                .map(template->{
                                    ContainerTag tag = li(template.getName()).withClass("template-show-button")
                                            .attr("data-deletable", String.valueOf(deletable))
                                            .attr("data-jstree","{\"type\":\"file\"}")
                                            .attr("data-name",template.getName())
                                            .attr("data-user",template.getUser())
                                            .attr("data-file", template.getFile().getName());
                                    if(loadData) {
                                        tag = tag
                                                .attr("data-chartsMap", template.getChartsMap())
                                                .attr("data-highlightMap", template.getHighlightMap())
                                                .attr("data-attributesMap", template.getAttributesMap())
                                                .attr("data-filtersMap", template.getFiltersMap())
                                                .attr("data-searchOptionsMap", template.getSearchOptionsMap());
                                    }
                                    return tag;
                                }).collect(Collectors.toList())
                )
        );
    }


    static Tag templateWrapper(PasswordHandler handler, boolean authorized, Request req, Response res, Tag form, boolean showTemplates) {
        res.type("text/html");
        String message = req.session().attribute("message");
        req.session().removeAttribute("message");
        List<Pair<String,String>> acclaimAttrs;
        if(authorized) {
            acclaimAttrs = Constants.acclaimAttrs;
        } else {
            acclaimAttrs = Collections.emptyList();
        }
        String role = authorized ? req.session().attribute("role") : null;
        String userGroup = authorized ? getUserGroupFor(req.session()) : null;
        boolean showDynamicUserGroups = authorized&&((role.equals(INTERNAL_USER)||role.equals(SUPER_USER)));
        String dynamicUserGroup = showDynamicUserGroups ? req.session().attribute("dynamicUserGroup") : null;
        return html().with(
                head().with(
                        title("AI Search Platform"),
                        script().withSrc("/js/jquery-3.3.1.min.js"),
                        script().withSrc("/js/jquery-ui-1.12.1.min.js"),
                        script().withSrc("/js/highstock.js"),
                        script().withSrc("/js/exporting.js"),
                        script().withSrc("/js/offline-exporting.js"),
                        script().withSrc("/js/customEvents.js"),
                        script().withSrc("/js/jquery.dynatable.js"),
                        script().withSrc("/js/defaults.js"),
                        script().withSrc("/js/jquery.miniTip.js"),
                        script().withSrc("/js/jstree.min.js"),
                        script().withSrc("/js/select2.min.js"),
                        script().withSrc("/js/bootstrap.min.js"),
                        script().withSrc("/js/tether.min.js"),
                        link().withRel("stylesheet").withHref("/css/bootstrap.min.css"),
                        link().withRel("stylesheet").withHref("/css/select2.min.css"),
                        link().withRel("stylesheet").withHref("/css/defaults.css"),
                        link().withRel("stylesheet").withHref("/css/jquery.dynatable.css"),
                        link().withRel("stylesheet").withHref("/css/miniTip.css"),
                        link().withRel("stylesheet").withHref("/css/jstree.min.css"),
                        link().withRel("stylesheet").withHref("/css/jquery-ui.min.css"),
                        script().withText("function disableEnterKey(e){var key;if(window.event)key = window.event.keyCode;else key = e.which;return (key != 13);}")
                ),
                body().with(
                        div().withId("acclaim-supported-fields").attr("style","display: none;").with(
                                ul().attr("style","max-height: 300px; overflow-y: auto;").with(
                                        acclaimAttrs.stream().sorted(Comparator.comparing(e->e.getFirst())).map(e->{
                                            return li(e.getFirst()+" -> "+fullHumanAttributeFor(e.getSecond()));
                                        }).collect(Collectors.toList())
                                )
                        ),div().withId("new-dataset-from-asset-list-overlay").with(
                                div().withId("new-dataset-from-asset-list-inside").attr("style","background-color: lightgray; padding: 5px;").with(
                                        textarea().withPlaceholder("Enter asset list separated by spaces or newlines (eg. 8234523 8323233 2013335323 RE032353).").withId("new-dataset-from-asset-list"),br(),
                                        button("Create").attr("style","cursor: pointer;").withClass("btn btn-sm btn-default").withId("new-dataset-from-asset-list-submit"),
                                        button("Cancel").attr("style","cursor: pointer;").withClass("btn btn-sm btn-default").withId("new-dataset-from-asset-list-cancel")
                                )
                        ),div().withId("k-for-clustering-overlay").with(
                                div().withId("k-for-clustering-inside").attr("style","background-color: lightgray; padding: 5px;").with(
                                        label("Number of Clusters").with(div().withText("(or leave blank to automatically find optimal number)"),input().withType("number").attr("min","2").withId("k-for-clustering")),br(),
                                        button("Cluster").attr("style","cursor: pointer;").withClass("btn btn-sm btn-default").withId("k-for-clustering-submit"),
                                        button("Cancel").attr("style","cursor: pointer;").withClass("btn btn-sm btn-default").withId("k-for-clustering-cancel")
                                )
                        ),
                        div().withClass("container-fluid text-center").attr("style","height: 100%; z-index: 1;").with(
                                div().withClass("row").attr("style","height: 100%;").with(
                                        nav().withClass("sidebar col-3").attr("style","z-index: 2; overflow-y: auto; height: 100%; position: fixed; padding-top: 75px;").with(
                                                div().withClass("row").with(
                                                        div().withClass("col-12").with(authorized ? div().withText("Signed in as "+req.session().attribute("username")+" ("+req.session().attribute("role")+").") : div().withText("Not signed in.")),
                                                        div().withClass("col-12").with(authorized && showDynamicUserGroups ? span().with(
                                                                form().withAction("/change_dynamic_user_group").withMethod("POST").with(
                                                                        label("Change User Group").with(
                                                                                select().withClass("single-select2 form-control").attr("onchange","this.form.submit()").withName("userGroup").with(
                                                                                        option(dynamicUserGroup==null?userGroup:dynamicUserGroup).attr("selected","selected").withValue(dynamicUserGroup==null?userGroup:dynamicUserGroup)
                                                                                ).with(
                                                                                        handler.getUserGroups().stream().filter(group->{
                                                                                            return dynamicUserGroup==null?!userGroup.equals(group):!dynamicUserGroup.equals(group);
                                                                                        }).map(group->{
                                                                                            return option(group).withValue(group);
                                                                                        }).collect(Collectors.toList())
                                                                                )

                                                                        )
                                                                )
                                                        ) : span()),
                                                        div().withClass("col-12").with(authorized ? a("Sign Out").withHref("/logout") : a("Log In").withHref("/")),
                                                        div().withClass("col-12").with(authorized && canPotentiallyCreateUser(role) ? a("Create User").withHref("/create_user") : a("Contact Us").withHref("http://www.gttgrp.com")),
                                                        div().withClass("col-12").with(authorized && (role.equals(SUPER_USER)) ? a("Change User Group").withHref("/edit_user_group") : span()),
                                                        div().withClass("col-12").with(authorized ? a("Change Password").withHref("/edit_user") : span()),
                                                        div().withClass("col-12").with(authorized && (role.equals(SUPER_USER)) ? a("Remove Users").withHref("/delete_user") : span()),
                                                        div().withClass("col-12").with(authorized ? a("Update Defaults").withHref(UPDATE_DEFAULT_ATTRIBUTES_URL) : span()),
                                                        div().withClass("col-12").with(authorized ? a("Help").withHref("/help") : span())

                                                ), hr(),
                                                (!authorized) ? div() : div().with(
                                                        ul().withClass("nav nav-tabs nav-fill").attr("role","tablist").with(
                                                                li().withClass("nav-item").with(
                                                                        a("Templates").withClass("nav-link active").attr("data-toggle", "tab")
                                                                                .attr("role","tab")
                                                                                .withHref("#templates-tree")
                                                                ),
                                                                li().withClass("nav-item").with(
                                                                        a("Datasets").withClass("nav-link").attr("data-toggle", "tab")
                                                                                .attr("role","tab")
                                                                                .withHref("#datasets-tree")
                                                                )
                                                        ), br(),
                                                        (showTemplates ? div().withClass("tab-content").withId("sidebar-jstree-wrapper").attr("style","max-height: 50%; overflow-y: auto; text-align: left; display: none;").with(
                                                                div().withClass("tab-pane active").attr("role","tabpanel").withId("templates-tree").with(
                                                                        ul().with(
                                                                                getTemplatesForUser(SUPER_USER,false,"Preset Templates",false),
                                                                                getTemplatesForUser(req.session().attribute("username"),true,"My Templates",false),
                                                                                getTemplatesForUser(userGroup,true, "Shared Templates",false)
                                                                        )

                                                                ),div().withClass("tab-pane").attr("role","tabpanel").withId("datasets-tree").with(
                                                                        ul().with(
                                                                                getDatasetsForUser(SUPER_USER,false,"Preset Datasets"),
                                                                                getDatasetsForUser(req.session().attribute("username"),true,"My Datasets"),
                                                                                getDatasetsForUser(userGroup,true, "Shared Datasets")
                                                                        )
                                                                )
                                                        ) : div())
                                                )
                                        ),div().withClass("col-9 offset-3").attr("style","padding-top: 58px; padding-left:0px; padding-right:0px;").with(
                                                customFormHeader(),
                                                (message==null ? span() : div().withText(message)),
                                                form,
                                                br(),
                                                br(),
                                                br()
                                        )
                                )
                        )
                )
        );
    }



    public static Tag technologySelectWithCustomClass(String name, String id, String clazz, List<Pair<String,String>> orderedClassifications) {
        return select().attr("style","width:100%;").withName(name).withId(id).withClass(clazz).attr("multiple","multiple").with(
                orderedClassifications.stream().map(pair->{
                    String humanName = humanAttributeFor(pair.getFirst());
                    return div().with(option(humanName).withValue(pair.getFirst()).attr("data-id", pair.getSecond()));
                }).collect(Collectors.toList())
        );
    }

    public static Tag technologySelectWithCustomClass(String name, String id, String clazz, Map<String,List<Pair<String,String>>> orderedClassifications, String defaultOption) {
        ContainerTag select = select().attr("style","width:100%;").withName(name).withId(id).withClass(clazz);
        if(defaultOption==null) select = select.attr("multiple","multiple");
        else {
            select = select.with(option(defaultOption).withValue(""));
        }
        return select
                .with(
                        orderedClassifications.entrySet().stream().map(e-> {
                            String optGroup = e.getKey();
                            return optgroup().attr("label",humanAttributeFor(optGroup)).attr("name",optGroup).with(
                                    e.getValue().stream().map(pair->{
                                        return div().with(option(humanAttributeFor(pair.getFirst())).withValue(pair.getFirst()).attr("data-id", pair.getSecond()));
                                    }).collect(Collectors.toList())
                            );
                        }).collect(Collectors.toList())
                );
    }


    private static Tag loaderTag() {
        return div().withClass("loader col-12").attr("style","display: none;").with(
                img().attr("height","48").attr("width","48").withSrc("/images/loader48.gif")
        );
    }

    private static Tag customFormHeader() {
        return div().withClass("row header-main header-top").with(
                div().withClass("col-3").attr("style","padding-top: 30px; border-right: 1px lightgray solid;").with(
                        a().attr("href", HOME_URL).with(
                                img().attr("style","display: block; margin-left: auto; margin-right: auto; margin-top: -20px")
                                        .attr("src", "/images/brand.png")
                        )
                ),div().withClass("col-9").attr("style","padding-top: 30px; background-color: #c4c4c4;").with(
                        div().withClass("row").with(
                                 div().withClass("col-12").with(
                                        h3("AI Search Platform").withClass("collapsible-header")
                                                .attr("data-target","#main-content-id")
                                                .attr("style","margin-top: -10px;")
                                )
                        )
                )

        );
    }

    private static Tag toggleButton(String id, String text) {
        return div().withClass("row").with(
                div().withId(id+"-panel-toggle").withClass("col-12").with(
                        h5(text).withClass("collapsible-header").attr("style","margin-bottom: 15px;").attr("data-target","#"+id)
                )
        );
    }

    public static Tag createAttributeElement(String humanName, String modelName, String optGroup, String collapseId, Tag optionTag, String selectId, String attributeId, Collection<String> inputIds, boolean notImplemented, String description) {
        //if(optGroup!=null)System.out.println("Tag for "+modelName+": "+attributeId);
        //if(optGroup!=null)System.out.println("Inputs ids for "+modelName+": "+inputIds);
        return div().attr("data-model",modelName).attr("data-attribute",attributeId).attr("data-inputs",  inputIds == null ? null : new Gson().toJson(inputIds)).withClass("attributeElement draggable " + (notImplemented ? " not-implemented" : "")).with(
                div().attr("style","width: 100%;").attr("title", notImplemented ? NOT_IMPLEMENTED_STRING : description).withClass("collapsible-header").attr("data-target","#"+collapseId).with(
                        label(humanName).attr("opt-group",optGroup),
                        span().withClass("remove-button").attr("data-model",modelName).attr("data-select","#"+selectId).withText("x")
                ), span().withClass("collapse show").withId(collapseId).with(optionTag)
        );
    }

    public static List<String> allSortableAttributes() {
        return Stream.of(Stream.of(Constants.SCORE, Constants.SIMILARITY_FAST, Constants.AI_VALUE, Constants.RANDOM_SORT, Constants.NO_SORT, Constants.LATEST_ASSIGNEE+"."+Constants.PORTFOLIO_SIZE, Constants.REMAINING_LIFE, Constants.LATEST_ASSIGNEE+"."+Constants.COMPDB_ASSETS_PURCHASED, Constants.LATEST_ASSIGNEE+"."+Constants.COMPDB_ASSETS_SOLD),
                getAllTopLevelAttributes().stream()
                        .flatMap(attr->{
                            if(attr instanceof NestedAttribute) {
                                return ((NestedAttribute) attr).getAttributes().stream().filter(child->child.getName().endsWith(Constants.COUNT_SUFFIX));
                            } else return Stream.of(attr);
                        })
                        .filter(attr->attr.getName().endsWith(Constants.COUNT_SUFFIX)||attr.getFieldType().equals(AbstractFilter.FieldType.Date))
                        .map(AbstractAttribute::getFullName).sorted()).flatMap(stream->stream).collect(Collectors.toList());
    }

    public static Map<String,String> allSortableAttributesToRootName() {
        return allSortableAttributes().stream().collect(Collectors.toMap(e->e,e->{
            if(e.contains(".")) e = e.substring(0,e.indexOf("."));
            return e;
        }));
    }

    private static Tag mainOptionsRow() {
        return div().withClass("row").with(
                div().withClass("col-12").with(
                        h5("Sort and Limit")
                ), div().withClass("col-12").with(
                        div().withClass("row collapsible-form").with(
                                div().withClass("col-12 attributeElement").with(
                                        label("Sort By").attr("style","width: 100%;").with(
                                                br(),select().withId("main-options-"+COMPARATOR_FIELD).withClass("form-control single-select2").withName(COMPARATOR_FIELD).with(
                                                        allSortableAttributes().stream()
                                                                .map(key->option(humanAttributeFor(key)).withValue(key)).collect(Collectors.toList())
                                                )
                                        )
                                ),
                                div().withClass("col-6 attributeElement").with(
                                        label("Sort Direction").attr("style","width: 100%;").with(
                                                br(),
                                                select().withId("main-options-"+SORT_DIRECTION_FIELD).withClass("form-control single-select2").withName(SORT_DIRECTION_FIELD).with(
                                                        option("Ascending").withValue("asc"),
                                                        option("Descending").withValue("desc").attr("selected","selected")
                                                )
                                        )
                                ),
                                div().withClass("col-6 attributeElement").with(
                                        label("Result Limit").attr("style","width: 100%;").with(
                                                br(),input().withId("main-options-"+LIMIT_FIELD).withClass("form-control").withType("number").withValue("10").withName(LIMIT_FIELD)
                                        )
                                )
                        )
                )
        );
    }

    public static List<String> extractArray(Request req, String param) {
        try {
            String[] array = req.queryParamsValues(param);
            if (array != null) {
                return Arrays.stream(array).map(str->str.replace("\r","").trim()).collect(Collectors.toList());
            }
            else return Collections.emptyList();
        } catch(Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
    public static String extractString(Request req, String param, String defaultVal) {
        return extractString(req.queryMap(),param, defaultVal);
    }
    public static String extractString(QueryParamsMap paramsMap, String param, String defaultVal) {
        if(paramsMap.value(param)!=null&&paramsMap.value(param).trim().length()>0) {
            return paramsMap.value(param).replaceAll("\\r","");
        } else {
            return defaultVal;
        }
    }
    public static Integer extractInt(Request req, String param, Integer defaultVal) {
        return extractInt(req.queryMap(),param, defaultVal);
    }
    static Integer extractInt(QueryParamsMap req, String param, Integer defaultVal) {
        try {
            return Integer.valueOf(req.value(param));
        } catch(Exception e) {
            System.out.println("No "+param+" parameter specified... using default");
            return defaultVal;
        }
    }

    public static Double extractDouble(Request req, String param, Double defaultVal) {
        return extractDouble(req.queryMap(),param, defaultVal);
    }
    static Double extractDouble(QueryParamsMap req, String param, Double defaultVal) {
        try {
            return Double.valueOf(req.value(param));
        } catch(Exception e) {
            System.out.println("No "+param+" parameter specified... using default");
            return defaultVal;
        }
    }

    public static Double extractDoubleFromArrayField(Request req, String param, Double defaultVal) {
        try {
            return Double.valueOf(req.queryParamsValues(param)[0]);
        } catch(Exception e) {
            System.out.println("No "+param+" parameter specified... using default");
            return defaultVal;
        }
    }

    public static boolean extractBool(Request req, String param) {
        return extractBool(req.queryMap(),param);
    }
    static boolean extractBool(QueryParamsMap req, String param) {
        try {
            return (req.value(param)==null||(!(req.value(param).startsWith("on")||req.value(param).startsWith("true")))) ? false : true;
        } catch(Exception e) {
            System.out.println("No "+param+" parameter specified... using default");
            return false;
        }
    }

    public static void loadStuff() throws Exception {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        DefaultPipelineManager.setLoggingLevel(Level.INFO);

        long t1 = System.currentTimeMillis();
        //Database.setupSeedConn();
        boolean preLoad =  true;

        System.out.println("Starting to load base finder...");
        initialize(false,false);
        System.out.println("Finished loading base finder.");
        System.out.println("Starting server...");

        getAllTopLevelAttributes().forEach(attr->{
            if(attr instanceof NestedAttribute) {
                ((NestedAttribute) attr).getAttributes().forEach(child->{
                    try {
                        child.getAllValues();
                    } catch(Exception e) {

                    }
                });
            } else {
                try {
                    attr.getAllValues();
                } catch(Exception e) {

                }
            }
        });


        if(!TEST) {
            pool.execute(keyphrasePredictionPipelineManagerTask);
            pool.execute(similarityEngine);
            pool.execute(new RecursiveAction() {
                @Override
                protected void compute() {
                //    new RelatedAssetsAttribute().getPatentDataMap();
                //    new RelatedAssetsAttribute().getApplicationDataMap();
                //    new AssetToFilingMap().getPatentDataMap();
                //    new AssetToFilingMap().getApplicationDataMap();
                //    new FilingToAssetMap().getApplicationDataMap();
                //    new FilingToAssetMap().getPatentDataMap();
                //    new AssetToCPCMap().getApplicationDataMap();
                //    new AssetToCPCMap().getPatentDataMap();
                    if (preLoad) {
                       // Database.preLoad();
                    }
                }
            });

            final int numMinutes = 5;
            pool.awaitQuiescence(60L * numMinutes, TimeUnit.SECONDS);
        }

        server();
        System.out.println("Finished starting server.");

        GatherClassificationServer.StartServer();
        HumanNamePredictionServer.startServer(-1);
        DetermineAcquisitionsServer.startServer(-1);

        long t2 = System.currentTimeMillis();
        System.out.println("Time to start server: "+ ((t2-t1)/(1000*60)) + " minutes");
    }

    public static void awaitTermination() throws Exception {
        System.out.println("Awaiting termination...");
        pool.shutdown();
        pool.awaitTermination(Long.MAX_VALUE,TimeUnit.MICROSECONDS);
    }

    public static void main(String[] args) throws Exception {


        loadStuff();


        HelpPage.helpPage(false);
    }
}
