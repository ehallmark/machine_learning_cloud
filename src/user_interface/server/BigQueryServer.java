package user_interface.server;

import ch.qos.logback.classic.Level;
import com.google.gson.Gson;
import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import data_pipeline.helpers.Function2;
import data_pipeline.helpers.Function3;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import detect_acquisitions.DetermineAcquisitionsServer;
import elasticsearch.DataSearcher;
import elasticsearch.DatasetIndex;
import elasticsearch.MyClient;
import elasticsearch.TestNewFastVectors;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import lombok.NonNull;
import models.dl4j_neural_nets.tools.MyPreprocessor;
import models.kmeans.AssetKMeans;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;
import seeding.google.elasticsearch.Attributes;
import seeding.google.elasticsearch.attributes.DateRangeAttribute;
import seeding.google.mongo.ingest.IngestPatents;
import seeding.google.word2vec.Word2VecManager;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Session;
import user_interface.server.tools.AjaxChartMessage;
import user_interface.server.tools.ChartTask;
import user_interface.server.tools.PasswordHandler;
import user_interface.server.tools.SimpleAjaxMessage;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute2;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregate_charts.*;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.charts.tables.TableResponse;
import user_interface.ui_models.engines.*;
import user_interface.ui_models.excel.ExcelHandler;
import user_interface.ui_models.filters.*;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;
import user_interface.ui_models.templates.FormTemplate;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
public class BigQueryServer extends SimilarPatentServer {
    private static final boolean TEST = false;
    private static final boolean debug = false;
    public static final String TABLE_SELECTION_FIELD = "tableSelection";
    private static final Map<String,Lock> fileSynchronizationMap = Collections.synchronizedMap(new HashMap<>());
    private static final String GLOBAL_PREFIX = "";// "/_global";
    private static final String PROTECTED_URL_PREFIX = GLOBAL_PREFIX+"/secure";
    public static final String CHART_MODELS_ARRAY_FIELD = "chartModels[]";
    public static final String REPORT_URL = PROTECTED_URL_PREFIX+"/patent_recommendation_engine";
    public static final String HOME_URL = PROTECTED_URL_PREFIX+"/home";
    public static final String LOGIN_URL = GLOBAL_PREFIX+"/login";
    public static final String NEW_USER_URL = GLOBAL_PREFIX+"/new_user";
    public static final String DELETE_USER_URL = GLOBAL_PREFIX+"/delete_user";
    public static final String FORM_ELEMENT_BY_ID_URL = GLOBAL_PREFIX+"/form_elem_by_id";
    public static final String EDIT_USER_GROUP_URL = GLOBAL_PREFIX+"/edit_user_group";
    public static final String EDIT_USER_URL = GLOBAL_PREFIX+"/edit_user";
    public static final String NEW_USER_GROUP_URL = GLOBAL_PREFIX+"/new_user_group";
    public static final String USER_GROUPS_URL = GLOBAL_PREFIX+"/user_groups";
    public static final String UPDATE_USER_URL = GLOBAL_PREFIX+"/update_user";
    public static final String OVERRIDE_PASSWORD_URL = GLOBAL_PREFIX+"/override_password";
    public static final String UPDATE_USER_GROUP_URL = GLOBAL_PREFIX+"/update_user_group";
    public static final String REMOVE_USER_URL = GLOBAL_PREFIX+"/remove_user";
    public static final String CREATE_USER_URL = GLOBAL_PREFIX+"/create_user";
    public static final String CANCEL_REPORT_URL = GLOBAL_PREFIX+"/cancel_report";
    public static final String UPDATE_DEFAULT_ATTRIBUTES_URL = PROTECTED_URL_PREFIX+"/update_defaults";
    public static final String SAVE_TEMPLATE_URL = PROTECTED_URL_PREFIX+"/save_template";
    public static final String GET_TEMPLATE_URL = PROTECTED_URL_PREFIX+"/get_template";
    public static final String DELETE_TEMPLATE_URL = PROTECTED_URL_PREFIX+"/delete_template";
    public static final String RENAME_TEMPLATE_URL = PROTECTED_URL_PREFIX+"/rename_template";
    public static final String SAVE_DATASET_URL = PROTECTED_URL_PREFIX+"/save_dataset";
    public static final String GET_DATASET_URL = PROTECTED_URL_PREFIX+"/get_dataset";
    public static final String CLUSTER_DATASET_URL = PROTECTED_URL_PREFIX+"/cluster_dataset";
    public static final String ASSIGNEE_LIST_DATASETS_URL = PROTECTED_URL_PREFIX + "/assignee_datasets";
    public static final String DELETE_DATASET_URL = PROTECTED_URL_PREFIX+"/delete_dataset";
    public static final String RENAME_DATASET_URL = PROTECTED_URL_PREFIX+"/rename_dataset";
    public static final String RESET_DEFAULT_TEMPLATE_URL = PROTECTED_URL_PREFIX+"/reset_default_template";
    public static final String DOWNLOAD_URL = PROTECTED_URL_PREFIX+"/excel_generation";
    public static final String PREVIEW_URL = PROTECTED_URL_PREFIX+"/preview";
    public static final String SHOW_DATATABLE_URL = PROTECTED_URL_PREFIX+"/dataTable.json";
    public static final String SHOW_CHART_URL = PROTECTED_URL_PREFIX+"/charts";
    public static final String PRESET_USER_GROUP = "presets";
    public static final Map<String,Map<String,String>> ROLE_TO_FORM_ELEMENTS_BY_ID = Collections.synchronizedMap(new HashMap<>());
    public static final Map<String,Map<String,String>> ROLE_TO_TOP_LEVEL_FORM_ELEMENTS_BY_ID = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,AggregationChart<?>> chartModelMap = new HashMap<>();

    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
        { // Attributes
            humanAttrToJavaAttrMap = new HashMap<>();
            humanAttrToJavaAttrMap.put("Length of Smallest Independent Claim", Attributes.LENGTH_OF_SMALLEST_IND_CLAIM);
            humanAttrToJavaAttrMap.put("Application Kind", Attributes.APPLICATION_KIND);
            humanAttrToJavaAttrMap.put("Latest Assignees (by Family)", Attributes.LATEST_FAM_ASSIGNEES);
            humanAttrToJavaAttrMap.put("Latest Assignees", Attributes.LATEST_ASSIGNEES);
            humanAttrToJavaAttrMap.put("Latest Assignee Name (by Family)", Attributes.LATEST_FAM_ASSIGNEE);
            humanAttrToJavaAttrMap.put("Latest Assignee Name", Attributes.LATEST_ASSIGNEE);
            humanAttrToJavaAttrMap.put("Portfolio Size (by Family)", Attributes.LATEST_FAM_PORTFOLIO_SIZE);
            humanAttrToJavaAttrMap.put("Portfolio Size", Attributes.LATEST_PORTFOLIO_SIZE);
            humanAttrToJavaAttrMap.put("Latest Assignee Name (1st listed)", Attributes.LATEST_FIRST_ASSIGNEE);
            humanAttrToJavaAttrMap.put("Latest Assignee Name (by Family, 1st listed)", Attributes.LATEST_FAM_FIRST_ASSIGNEE);
            humanAttrToJavaAttrMap.put("Original Assignee Name", Attributes.ASSIGNEE_HARMONIZED);
            humanAttrToJavaAttrMap.put("Assignee Country Code", Attributes.ASSIGNEE_HARMONIZED_CC);
            humanAttrToJavaAttrMap.put("Latest Assignee Entity Type (1st listed)", Attributes.LATEST_ENTITY_TYPE);
            humanAttrToJavaAttrMap.put("Latest Assignee Entity Type (by Family, 1st listed)", Attributes.LATEST_FAM_ENTITY_TYPE);
            humanAttrToJavaAttrMap.put("Latest Assignee First Filing Date (1st listed)", Attributes.LATEST_FIRST_FILING_DATE);
            humanAttrToJavaAttrMap.put("Latest Assignee First Filing Date (by Family, 1st listed)", Attributes.LATEST_FAM_FIRST_FILING_DATE);
            humanAttrToJavaAttrMap.put("Latest Assignee Last Filing Date (1st listed)", Attributes.LATEST_LAST_FILING_DATE);
            humanAttrToJavaAttrMap.put("Latest Assignee Last Filing Date (by Family, 1st listed)", Attributes.LATEST_FAM_LAST_FILING_DATE);
            humanAttrToJavaAttrMap.put("Latest Assignee Date (1st listed)", Attributes.LATEST_ASSIGNEE_DATE);
            humanAttrToJavaAttrMap.put("Latest Assignee Date (by Family, 1st listed)", Attributes.LATEST_FAM_ASSIGNEE_DATE);
            humanAttrToJavaAttrMap.put("Security Interest", Attributes.SECURITY_INTEREST);
            humanAttrToJavaAttrMap.put("Security Interest (by Family)", Attributes.SECURITY_INTEREST_FAM);
            humanAttrToJavaAttrMap.put("Security Interest Exec. Date", Attributes.SECURITY_INTEREST_DATE);
            humanAttrToJavaAttrMap.put("Security Interest Exec. Date (by Family)", Attributes.SECURITY_INTEREST_FAM_DATE);
            humanAttrToJavaAttrMap.put("Security Interest Holder", Attributes.SECURITY_INTEREST_HOLDER);
            humanAttrToJavaAttrMap.put("Security Interest Holder (by Family)", Attributes.SECURITY_INTEREST_FAM_HOLDER);
            humanAttrToJavaAttrMap.put("Standards", Attributes.STANDARDS);
            humanAttrToJavaAttrMap.put("Standard", Attributes.STANDARD);
            humanAttrToJavaAttrMap.put("SSO",Attributes.SSO);
            humanAttrToJavaAttrMap.put("Application Number (w/ Country)", Attributes.APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY);
            humanAttrToJavaAttrMap.put("Application Number", Attributes.APPLICATION_NUMBER_FORMATTED);
            humanAttrToJavaAttrMap.put("Publication Number Full", Attributes.PUBLICATION_NUMBER_FULL);
            humanAttrToJavaAttrMap.put("Publication Number (w/ Country)", Attributes.PUBLICATION_NUMBER_WITH_COUNTRY);
            humanAttrToJavaAttrMap.put("Publication Number", Attributes.PUBLICATION_NUMBER);
            humanAttrToJavaAttrMap.put("(eg. FIELD:isEmptyANO_F OR FIELD:isNotEmptyTTL)", "FIELD");
            humanAttrToJavaAttrMap.put("Independent Claim", "ICLM");
            humanAttrToJavaAttrMap.put("Kind Code", Attributes.KIND_CODE);
            humanAttrToJavaAttrMap.put("Dependent Claim", "DCLM");
            humanAttrToJavaAttrMap.put("Counterparts", Attributes.COUNTERPARTS);
            humanAttrToJavaAttrMap.put("Counterpart Country", Attributes.COUNTERPART_COUNTRY_CODE);
            humanAttrToJavaAttrMap.put("Counterpart Application Number", Attributes.COUNTERPART_APPLICATION_NUMBER_FORMATTED);
            humanAttrToJavaAttrMap.put("Counterpart Application Number w/ Coutnry", Attributes.COUNTERPART_APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY);
            humanAttrToJavaAttrMap.put("Counterpart Publication Number", Attributes.COUNTERPART_PUBLICATION_NUMBER);
            humanAttrToJavaAttrMap.put("Counterpart Full Publication Number", Attributes.COUNTERPART_PUBLICATION_NUMBER_FULL);
            humanAttrToJavaAttrMap.put("Counterpart Publication Number w/ Country", Attributes.COUNTERPART_PUBLICATION_NUMBER_WITH_COUNTRY);
            humanAttrToJavaAttrMap.put("Counterpart Kind Code", Attributes.COUNTERPART_KIND_CODE);
            humanAttrToJavaAttrMap.put("Title + Abstract + Claims", "TAC");
            humanAttrToJavaAttrMap.put("Assignee (Estimated)", Attributes.GUESSED_ASSIGNEE);
            humanAttrToJavaAttrMap.put("Maintenance Fee Event Code", Attributes.MAINTENANCE_EVENT);
            humanAttrToJavaAttrMap.put("Similarity", Attributes.SIMILARITY);
            humanAttrToJavaAttrMap.put("Technology Similarity", Constants.TECHNOLOGY_SIMILARITY);
            humanAttrToJavaAttrMap.put("Assignee Similarity", Constants.ASSIGNEE_SIMILARITY);
            humanAttrToJavaAttrMap.put("Text Similarity", Constants.TEXT_SIMILARITY);
            humanAttrToJavaAttrMap.put("CPC Code Similarity", Constants.CPC_SIMILARITY);
            humanAttrToJavaAttrMap.put("Asset Similarity", Constants.PATENT_SIMILARITY);
            humanAttrToJavaAttrMap.put("Total Asset Count", Constants.TOTAL_ASSET_COUNT);
            humanAttrToJavaAttrMap.put("Invention Title", Attributes.INVENTION_TITLE);
            humanAttrToJavaAttrMap.put("Reinstated", Constants.REINSTATED);
            humanAttrToJavaAttrMap.put("Result Type", Constants.DOC_TYPE);
            humanAttrToJavaAttrMap.put("Dataset Name", Constants.DATASET_NAME);
            humanAttrToJavaAttrMap.put("2nd Dataset Name", Constants.DATASET2_NAME);
            humanAttrToJavaAttrMap.put("Expired", Attributes.EXPIRED);
            humanAttrToJavaAttrMap.put("Technology", Attributes.COMPDB_TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Deal ID", Attributes.COMPDB_DEAL_ID);
            humanAttrToJavaAttrMap.put("Primary Technology", Attributes.TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Secondary Technology", Attributes.TECHNOLOGY2);
            humanAttrToJavaAttrMap.put("Significant Keywords", Attributes.KEYWORDS);
            humanAttrToJavaAttrMap.put("Assignee Entity Type", Attributes.ORIGINAL_ENTITY_TYPE);
            humanAttrToJavaAttrMap.put("Acquisition Deal", Attributes.COMPDB_ACQUISITION);
            humanAttrToJavaAttrMap.put("Inactive Deal", Attributes.COMPDB_INACTIVE);
            humanAttrToJavaAttrMap.put("Portfolio Size (pub. match)", Attributes.LATEST_PORTFOLIO_SIZE);
            humanAttrToJavaAttrMap.put("Portfolio Size (family match)", Attributes.LATEST_FAM_PORTFOLIO_SIZE);
            humanAttrToJavaAttrMap.put("Patents",PortfolioList.Type.patents.toString());
            humanAttrToJavaAttrMap.put("Applications",PortfolioList.Type.applications.toString());
            humanAttrToJavaAttrMap.put("Pie Chart", Constants.PIE_CHART);
            humanAttrToJavaAttrMap.put("Cited Date", Constants.CITED_DATE);
            humanAttrToJavaAttrMap.put("Heat Map", Constants.HEAT_MAP);
            humanAttrToJavaAttrMap.put("Word Cloud", Constants.WORD_CLOUD);
            humanAttrToJavaAttrMap.put("Forward Citation", Constants.BACKWARD_CITATION);
            humanAttrToJavaAttrMap.put("Remove Duplicate Related Assets",AssetDedupFilter.NAME);
            humanAttrToJavaAttrMap.put("Gather", Attributes.GATHER);
            humanAttrToJavaAttrMap.put("Stage Complete", Attributes.GATHER_STAGE);
            humanAttrToJavaAttrMap.put("Gather Technology", Attributes.GATHER_TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Patent Rating", Attributes.GATHER_VALUE);
            humanAttrToJavaAttrMap.put("CompDB", Attributes.COMPDB);
            humanAttrToJavaAttrMap.put("Random Sort", Constants.RANDOM_SORT);
            humanAttrToJavaAttrMap.put("No Sort", Constants.NO_SORT);
            humanAttrToJavaAttrMap.put("CPC Code With Children", Attributes.TREE);
            humanAttrToJavaAttrMap.put("Dataset Similarity", DATASETS_TO_SEARCH_IN_FIELD);
            humanAttrToJavaAttrMap.put("Filing Date", Attributes.FILING_DATE);
            humanAttrToJavaAttrMap.put("Histogram",Constants.HISTOGRAM);
            humanAttrToJavaAttrMap.put("WIPO Technology",Attributes.WIPO_TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Est. Remaining Life (Years)",Attributes.REMAINING_LIFE);
            humanAttrToJavaAttrMap.put("Filing Country", Attributes.COUNTRY_CODE);
            humanAttrToJavaAttrMap.put("Term Adjustments (Days)", Attributes.TERM_ADJUSTMENTS);
            humanAttrToJavaAttrMap.put("CPC Code", Attributes.CODE);
            humanAttrToJavaAttrMap.put("Original Priority Date", Attributes.PRIORITY_DATE);
            humanAttrToJavaAttrMap.put("Recorded Date", Attributes.RECORDED_DATE);
            humanAttrToJavaAttrMap.put("Publication Date", Attributes.PUBLICATION_DATE);
            humanAttrToJavaAttrMap.put("Timeline Chart", Constants.LINE_CHART);
            humanAttrToJavaAttrMap.put("Reel Frames", Attributes.REEL_FRAME);
            humanAttrToJavaAttrMap.put("Inventor Name", Attributes.INVENTOR_HARMONIZED);
            humanAttrToJavaAttrMap.put("Inventor Country", Attributes.INVENTOR_HARMONIZED_CC);
            humanAttrToJavaAttrMap.put("Include All", AbstractFilter.FilterType.Include.toString());
            humanAttrToJavaAttrMap.put("Include By Prefix", AbstractFilter.FilterType.PrefixInclude.toString());
            humanAttrToJavaAttrMap.put("Exclude By Prefix", AbstractFilter.FilterType.PrefixExclude.toString());
            humanAttrToJavaAttrMap.put("Include Assets By", AbstractFilter.FilterType.AssetInclude.toString());
            humanAttrToJavaAttrMap.put("Exclude Assets By", AbstractFilter.FilterType.AssetExclude.toString());
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
            humanAttrToJavaAttrMap.put("First Name", Constants.FIRST_NAME);
            humanAttrToJavaAttrMap.put("Last Name", Constants.LAST_NAME);
            humanAttrToJavaAttrMap.put("Country", Constants.COUNTRY);
            humanAttrToJavaAttrMap.put("City", Constants.CITY);
            humanAttrToJavaAttrMap.put("State", Constants.STATE);
            humanAttrToJavaAttrMap.put("Zip Code", Constants.POSTAL_CODE);
            humanAttrToJavaAttrMap.put("Claim Number", Constants.CLAIM_NUM);
            humanAttrToJavaAttrMap.put("Parent Claim Number", Constants.PARENT_CLAIM_NUM);
            humanAttrToJavaAttrMap.put("Document Kind", Constants.DOC_KIND);
            humanAttrToJavaAttrMap.put("Claim Length", Constants.CLAIM_LENGTH);
            humanAttrToJavaAttrMap.put("Abstract Text", Attributes.ABSTRACT);
            humanAttrToJavaAttrMap.put("Lapsed", Attributes.LAPSED);
            humanAttrToJavaAttrMap.put("Priority Date (estimated)", Attributes.PRIORITY_DATE_ESTIMATED);
            humanAttrToJavaAttrMap.put("Expiration Date (estimated)", Attributes.EXPIRATION_DATE_ESTIMATED);
            humanAttrToJavaAttrMap.put("Conveyance Text", Attributes.CONVEYANCE_TEXT);
            humanAttrToJavaAttrMap.put("Overall Score", Constants.SCORE);
            humanAttrToJavaAttrMap.put("PTAB", Attributes.PTAB);
            humanAttrToJavaAttrMap.put("Case Name", Attributes.PTAB_CASE_NAME);
            humanAttrToJavaAttrMap.put("Case Type", Attributes.PTAB_CASE_TYPE);
            humanAttrToJavaAttrMap.put("Case Status", Attributes.PTAB_CASE_STATUS);
            humanAttrToJavaAttrMap.put("Appeal No.", Attributes.PTAB_APPEAL_NO);
            humanAttrToJavaAttrMap.put("Mailed Date", Attributes.PTAB_MAILED_DATE);
            humanAttrToJavaAttrMap.put("Case Text (PDF)", Attributes.PTAB_CASE_TEXT);
            humanAttrToJavaAttrMap.put("Interference No.", Attributes.PTAB_INTERFERENCE_NO);
            humanAttrToJavaAttrMap.put("Inventor First Name", Attributes.PTAB_INVENTOR_FIRST_NAME);
            humanAttrToJavaAttrMap.put("Inventor Last Name", Attributes.PTAB_INVENTOR_LAST_NAME);
            humanAttrToJavaAttrMap.put("Family Size", Attributes.FAMILY_SIZE);
            humanAttrToJavaAttrMap.put("Family ID",Attributes.FAMILY_ID);
            humanAttrToJavaAttrMap.put("Means in All Claims", Attributes.MEANS_PRESENT);
            humanAttrToJavaAttrMap.put("AI Value", Attributes.AI_VALUE);
            humanAttrToJavaAttrMap.put("CompDB Recorded Date", Attributes.COMPDB_RECORDED_DATE);
            humanAttrToJavaAttrMap.put("Related Docs", Constants.ALL_RELATED_ASSETS);
            // nested attrs
            humanAttrToJavaAttrMap.put("Granted", Attributes.GRANTED);
            humanAttrToJavaAttrMap.put("Recorded Assignee", Attributes.RECORDED_ASSIGNEE);
            humanAttrToJavaAttrMap.put("Recorded Assignor", Attributes.RECORDED_ASSIGNOR);
            humanAttrToJavaAttrMap.put("Execution Date", Attributes.EXECUTION_DATE);
            humanAttrToJavaAttrMap.put("Original Assignee", Attributes.ASSIGNEES);
            humanAttrToJavaAttrMap.put("Inventors", Attributes.INVENTORS);
            humanAttrToJavaAttrMap.put("Forward Citations", Constants.CITATIONS);
            humanAttrToJavaAttrMap.put("Claim Text", Attributes.CLAIMS);
            humanAttrToJavaAttrMap.put("Description Text", Attributes.DESCRIPTION);
            humanAttrToJavaAttrMap.put("Priority Claims", Attributes.PRIORITY_CLAIMS);
            humanAttrToJavaAttrMap.put("Assignments", Attributes.ASSIGNMENTS);
            humanAttrToJavaAttrMap.put("Reverse Citations", Attributes.RCITATIONS);
            humanAttrToJavaAttrMap.put("Reverse Cited Filing Date", Attributes.RCITE_FILING_DATE);
            humanAttrToJavaAttrMap.put("Reverse Cited Type", Attributes.RCITE_TYPE);
            humanAttrToJavaAttrMap.put("Reverse Cited Category", Attributes.RCITE_CATEGORY);
            humanAttrToJavaAttrMap.put("Reverse Cited Family ID", Attributes.RCITE_FAMILY_ID);
            humanAttrToJavaAttrMap.put("Reverse Cited Publication Number", Attributes.RCITE_PUBLICATION_NUMBER);
            humanAttrToJavaAttrMap.put("Reverse Cited Publication Number w/ Country", Attributes.RCITE_PUBLICATION_NUMBER_WITH_COUNTRY);
            humanAttrToJavaAttrMap.put("Reverse Cited Publication Number Full", Attributes.RCITE_PUBLICATION_NUMBER_FULL);
            humanAttrToJavaAttrMap.put("Reverse Cited Application Number", Attributes.RCITE_APPLICATION_NUMBER_FORMATTED);
            humanAttrToJavaAttrMap.put("Reverse Cited Application Number w/ Country", Attributes.RCITE_APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY);
            humanAttrToJavaAttrMap.put("Forward Cited Filing Date", Attributes.CITED_FILING_DATE);
            humanAttrToJavaAttrMap.put("Forward Cited Type", Attributes.CITED_TYPE);
            humanAttrToJavaAttrMap.put("Forward Cited Category", Attributes.CITED_CATEGORY);
            humanAttrToJavaAttrMap.put("Forward Cited NPL Text", Attributes.CITED_NPL_TEXT);
            humanAttrToJavaAttrMap.put("Forward Cited Family ID", Attributes.CITED_FAMILY_ID);
            humanAttrToJavaAttrMap.put("Forward Cited Publication Number", Attributes.CITED_PUBLICATION_NUMBER);
            humanAttrToJavaAttrMap.put("Forward Cited Publication Number w/ Country", Attributes.CITED_PUBLICATION_NUMBER_WITH_COUNTRY);
            humanAttrToJavaAttrMap.put("Forward Cited Publication Number Full", Attributes.CITED_PUBLICATION_NUMBER_FULL);
            humanAttrToJavaAttrMap.put("Forward Cited Application Number", Attributes.CITED_APPLICATION_NUMBER_FORMATTED);
            humanAttrToJavaAttrMap.put("Forward Cited Application Number w/ Country", Attributes.CITED_APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY);
            humanAttrToJavaAttrMap.put("Priority Claim Publication Number", Attributes.PC_PUBLICATION_NUMBER);
            humanAttrToJavaAttrMap.put("Priority Claim Publication Number w/ Country", Attributes.PC_PUBLICATION_NUMBER_WITH_COUNTRY);
            humanAttrToJavaAttrMap.put("Priority Claim Publication Number Full", Attributes.PC_PUBLICATION_NUMBER_FULL);
            humanAttrToJavaAttrMap.put("Priority Claim Application Number", Attributes.PC_APPLICATION_NUMBER_FORMATTED);
            humanAttrToJavaAttrMap.put("Priority Claim Application Number w/ Country", Attributes.PC_APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY);
            humanAttrToJavaAttrMap.put("Priority Claim Family ID", Attributes.PC_FAMILY_ID);
            humanAttrToJavaAttrMap.put("Priority Filing Date", Attributes.PC_FILING_DATE);
            humanAttrToJavaAttrMap.put("Number of Reverse Citations", Attributes.RCITATIONS_COUNT);
            humanAttrToJavaAttrMap.put("Number of Forward Citations", Attributes.CITATIONS_COUNT);
            humanAttrToJavaAttrMap.put("Number of CPC Codes", Attributes.CODE_COUNT);
            humanAttrToJavaAttrMap.put("Number of Priority Claims", Attributes.PRIORITY_CLAIMS_COUNT);
            humanAttrToJavaAttrMap.put("Number of Inventors", Attributes.INVENTORS_COUNT);
            humanAttrToJavaAttrMap.put("Number of Assignees", Attributes.ASSIGNEES_COUNT);
            humanAttrToJavaAttrMap.put("Number of Claims", Attributes.CLAIMS_COUNT);
            humanAttrToJavaAttrMap.put("Number of Keywords", Attributes.KEYWORD_COUNT);
            humanAttrToJavaAttrMap.put("Number of Standards", Attributes.STANDARD_COUNT);
            humanAttrToJavaAttrMap.put("Number of PTAB Cases", Attributes.PTAB_COUNT);
            humanAttrToJavaAttrMap.put("Number of CompDB Deals", Attributes.COMPDB_COUNT);
            humanAttrToJavaAttrMap.put("Number of Assignments", Attributes.ASSIGNMENTS_COUNT);
            humanAttrToJavaAttrMap.put("Number of Maintenance Events", Attributes.MAINTENANCE_EVENT_COUNT);
            humanAttrToJavaAttrMap.put("Number of Latest Assignees", Attributes.LATEST_ASSIGNEE_COUNT);
            humanAttrToJavaAttrMap.put("Number of Latest Assignees (by Family)", Attributes.LATEST_FAM_ASSIGNEE_COUNT);
            humanAttrToJavaAttrMap.put("Pivot Table", Constants.PIVOT_FUNCTION_TABLE_CHART);
            // ACCLAIM RELATED
            humanAttrToJavaAttrMap.put("Dataset Path Name", "RFID");
            humanAttrToJavaAttrMap.put("Forward Examiner Citations", "FEXCITE");
            humanAttrToJavaAttrMap.put("2nd Dataset Path Name", "RFID2");
            humanAttrToJavaAttrMap.put("Document Status", "DOC_STATUS");
            humanAttrToJavaAttrMap.put("Document Type", "DT");
            humanAttrToJavaAttrMap.put("Patent Type", "PT");
            buildJavaToHumanAttrMap();

        }
    }

    public static void initialize() {
        loadAttributes();
        loadFilterModels();
        loadChartModels();
        roleToAttributeFunctionMap.keySet().forEach(role->{
            ROLE_TO_FORM_ELEMENTS_BY_ID.put(role, Collections.synchronizedMap(new HashMap<>()));
            ROLE_TO_TOP_LEVEL_FORM_ELEMENTS_BY_ID.put(role, Collections.synchronizedMap(new HashMap<>()));
        });
        addToFormElements(allAttributes,false);
        addToFormElements(allFilters,false);
        addToFormElements(allCharts,true);
    }

    private static void addToFormElements(AbstractAttribute attr, boolean allCharts) {
        if(allCharts) {
            roleToAttributeFunctionMap.forEach((role,f)-> {
                ((NestedAttribute) attr).getAttributes().forEach(child->{
                    Map<String, String> childTags = new HashMap<>();
                    child.getOptionsTag(f, true, childTags);
                    System.out.println("Found child tags: "+String.join("\nTag: ", childTags.keySet()));
                    childTags.forEach((id, tag)->{
                        ROLE_TO_FORM_ELEMENTS_BY_ID.get(role).put(id, tag);
                    });
                    ROLE_TO_TOP_LEVEL_FORM_ELEMENTS_BY_ID.get(role).put(((NestedAttribute) child).getId(), child.getOptionsTag(f,false, null).render());
                });
            });
        } else if (attr instanceof NestedAttribute) {
            ((NestedAttribute) attr).getAttributes().forEach(child->{
                roleToAttributeFunctionMap.forEach((role,f)->{
                    if(child instanceof NestedAttribute) {
                        ROLE_TO_TOP_LEVEL_FORM_ELEMENTS_BY_ID.get(role).put(((NestedAttribute) child).getId(), child.getOptionsTag(f,true, null).render());
                    } else {
                        ROLE_TO_TOP_LEVEL_FORM_ELEMENTS_BY_ID.get(role).put(child.getAttributeId(), child.getOptionsTag(f,null,null,(d1,d2)->div().with(d1,d2), false, true, null).render());
                    }
                });
            });
        } else if(attr instanceof AbstractNestedFilter) {
            ((AbstractNestedFilter) attr).getFilters().forEach(child->{
                roleToAttributeFunctionMap.forEach((role,f)->{
                    if(child instanceof AbstractNestedFilter) {
                        ROLE_TO_TOP_LEVEL_FORM_ELEMENTS_BY_ID.get(role).put(child.getId(), child.getOptionsTag(f, true, null).render());
                    } else {
                        ROLE_TO_TOP_LEVEL_FORM_ELEMENTS_BY_ID.get(role).put(child.getId(), child.getOptionsTag(f,null,null,true, null).render());
                    }
                });
            });
        }
        ROLE_TO_TOP_LEVEL_FORM_ELEMENTS_BY_ID.forEach((role,map)->{
            ROLE_TO_FORM_ELEMENTS_BY_ID.get(role).putAll(map);
        });
    }

    public static void loadChartModels() {
        List<AbstractAttribute> attributes = new ArrayList<>();
        getAttributesHelper(allAttributes,attributes);

        List<AbstractAttribute> discreteAttrs = attributes.stream().filter(attr->attr.getType().equals("keyword")||attr.getType().equals("date")||(attr.getType().equals("text")&&attr.getNestedFields()!=null)).collect(Collectors.toList());
        List<AbstractAttribute> dateAttrs = attributes.stream().filter(attr->attr.getType().equals("date")).collect(Collectors.toList());
        List<AbstractAttribute> rangeAttrs = attributes.stream().filter(attr->attr instanceof RangeAttribute).collect(Collectors.toList());
        List<AbstractAttribute> numericAttrs = attributes.stream().filter(attr->attr.getFieldType().equals(AbstractFilter.FieldType.Double)||attr.getFieldType().equals(AbstractFilter.FieldType.Integer)).collect(Collectors.toList());
        List<AbstractAttribute> discreteAndNumeric = new ArrayList<>();
        discreteAndNumeric.addAll(duplicateAttributes(numericAttrs));
        discreteAndNumeric.addAll(duplicateAttributes(discreteAttrs));

        chartModelMap.put(Constants.PIE_CHART, new AggregatePieChart(groupAttributesToNewParents(discreteAttrs),duplicateAttributes(discreteAttrs), duplicateAttributes(discreteAndNumeric)));
        chartModelMap.put(Constants.HISTOGRAM, new AggregateHistogramChart(groupAttributesToNewParents(rangeAttrs),duplicateAttributes(discreteAttrs),duplicateAttributes(discreteAndNumeric)));
        chartModelMap.put(Constants.LINE_CHART, new AggregateLineChart(groupAttributesToNewParents(dateAttrs),duplicateAttributes(discreteAttrs), duplicateAttributes(discreteAndNumeric)));
        chartModelMap.put(Constants.HEAT_MAP, new AggregateHeatMapChart(groupAttributesToNewParents(discreteAttrs),duplicateAttributes(discreteAttrs),duplicateAttributes(discreteAndNumeric)));
        chartModelMap.put(Constants.WORD_CLOUD, new AggregateWordCloudChart(groupAttributesToNewParents(discreteAttrs)));
        chartModelMap.put(Constants.PIVOT_FUNCTION_TABLE_CHART, new AggregatePivotChart(groupAttributesToNewParents(discreteAttrs),duplicateAttributes(discreteAttrs),duplicateAttributes(discreteAndNumeric)));

        allCharts = new NestedAttribute(chartModelMap.values().stream().map(chart->(AbstractAttribute)chart).collect(Collectors.toList()),false) {
            @Override
            public String getName() {
                return CHART_MODELS_ARRAY_FIELD;
            }
        };
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

    public static void loadAttributes() {
        if(attributesMap.isEmpty()) {
            Attributes.buildAttributes().forEach(attr->{
                attributesMap.put(attr.getName(),attr);
            });
            attributesMap.put(Constants.DATASET_NAME, DatasetAttribute.getDatasetAttribute());
            attributesMap.put(Constants.DATASET2_NAME, DatasetAttribute2.getDatasetAttribute());

            // nested attribute names
            buildJavaToHumanAttrMap();

            SimilarityEngineController.setAllEngines(Arrays.asList(new DataSetSimilarityEngine("big_query_embedding_by_pub"), new PatentSimilarityEngine("big_query_embedding_by_pub"), new AssigneeSimilarityEngine("big_query_embedding_assignee"), new TextSimilarityEngine(), new CPCSimilarityEngine("big_query_embedding_cpc")));

            // similarity engine
            similarityEngine = new RecursiveTask<SimilarityEngineController>() {
                @Override
                protected SimilarityEngineController compute() {
                    // current word vectorizer
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

    public static String vectorToFastElasticSearchObject(Number[] vector) {
        float[] _vector = new float[vector.length];
        for(int i = 0; i < vector.length; i++) {
            _vector[i]=vector[i].floatValue();
        }
        return TestNewFastVectors.vectorToHex(_vector);
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
        if(GLOBAL_PREFIX.equals("/global")) {
            System.out.println("Using port 8081");
            port(8081);
        } else {
            System.out.println("Using port 8080");
            port(8080);
        }

        // HOST ASSETS
        staticFiles.externalLocation(new File("public").getAbsolutePath());

        final PasswordHandler passwordHandler = new PasswordHandler();

        post(LOGIN_URL, (req,res)->{
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
            Database.saveUserSignedIn(username);
            res.status(200);
            res.redirect(HOME_URL);
            return null;
        });

        get(GLOBAL_PREFIX+"/logout", (req,res)->{
            req.session(true).attribute("authorized",false);
            req.session().removeAttribute("role");
            req.session().removeAttribute("username");
            req.session().removeAttribute("userGroup");
            res.redirect("/");
            res.status(200);
            return null;
        });

        post(GLOBAL_PREFIX+"/change_dynamic_user_group", (req,res) -> {
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

        post(NEW_USER_URL, (req,res)->{
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

        post(UPDATE_USER_URL, (req,res)->{
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
                    redirect = EDIT_USER_URL;
                    message = e.getMessage();
                }
            } else {
                redirect = EDIT_USER_URL;
            }
            res.redirect(redirect);
            req.session().attribute("message", message);
            return null;
        });

        post(UPDATE_USER_GROUP_URL, (req,res)->{
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
                        redirect = EDIT_USER_GROUP_URL;
                        message = "Successfully updated user.";
                    } catch (Exception e) {
                        System.out.println("Error while updating user...");
                        e.printStackTrace();
                        redirect = EDIT_USER_GROUP_URL;
                        message = e.getMessage();
                    }
                } else {
                    redirect = EDIT_USER_GROUP_URL;
                }
            } else {
                redirect = EDIT_USER_GROUP_URL;
            }
            res.redirect(redirect);
            req.session().attribute("message", message);
            return null;
        });

        post(OVERRIDE_PASSWORD_URL, (req, res)->{
            authorize(req,res);
            String role = req.session().attribute("role");
            String message;
            final boolean isSuperUser = role != null && role.equals(SUPER_USER);
            if (isSuperUser) {
                String username = extractString(req, "username", null);
                String password = extractString(req, "password", null);
                String confPassword = extractString(req, "confirm_password", null);
                if (username != null && password != null && confPassword != null) {
                    if (password.equals(confPassword)) {
                        passwordHandler.forceChangePassword(username, confPassword);
                        message = "Changed password.";
                    } else {
                        message = "Passwords do not match.";
                    }
                } else {
                    message = "Username, password or password confirmation was not found.";
                }
            } else {
                message = "Unable to change password.";
            }
            req.session().attribute("message", message);
            res.redirect(OVERRIDE_PASSWORD_URL);
            return null;
        });

        get(EDIT_USER_GROUP_URL, (req, res)->{
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
                form = form().withId("create-user-form").withAction(UPDATE_USER_GROUP_URL).withMethod("POST").attr("style", "margin-top: 100px;").with(
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
                                a("Or create a new user group.").withHref(GLOBAL_PREFIX+"/user_groups")
                        ), br(), br(), button("Update User Group").withClass("btn btn-outline-secondary")
                );
            }
            return templateWrapper(passwordHandler,true, req, res, form, false);
        });

        post(NEW_USER_GROUP_URL, (req,res)->{
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
                    redirect = EDIT_USER_GROUP_URL;
                    message = "Successfully created user group.";
                } catch (Exception e) {
                    System.out.println("Error while creating user group...");
                    e.printStackTrace();
                    redirect = USER_GROUPS_URL;
                    message = e.getMessage();
                }
            } else {
                redirect = USER_GROUPS_URL;
            }
            res.redirect(redirect);
            req.session().attribute("message", message);
            return null;
        });

        get(USER_GROUPS_URL, (req, res)->{
            authorize(req,res);
            String ownerRole = req.session().attribute("role");
            if(ownerRole == null || ownerRole.equals(ANALYST_USER)) return div();
            String message = req.session().attribute("message");
            req.session().removeAttribute("message");
            Tag form = div().with(
                    form().withId("create-user-form").withAction(NEW_USER_GROUP_URL).withMethod("POST").attr("style","margin-top: 100px;").with(
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

        get(EDIT_USER_URL, (req, res)->{
            authorize(req,res);
            String ownerRole = req.session().attribute("role");
            String message = req.session().attribute("message");
            req.session().removeAttribute("message");
            Tag form = form().withId("create-user-form").withAction(UPDATE_USER_URL).withMethod("POST").attr("style","margin-top: 100px;").with(
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

        get(OVERRIDE_PASSWORD_URL, (req, res)->{
            authorize(req,res);
            String ownerRole = req.session().attribute("role");
            if (!ownerRole.equals(SUPER_USER)) {
                return null;
            }
            String message = req.session().attribute("message");
            req.session().removeAttribute("message");
            Tag form = form().withId("override-user-form").withAction(OVERRIDE_PASSWORD_URL).withMethod("POST").attr("style","margin-top: 100px;").with(
                    (message == null ? span() : div().withClass("not-implemented").withText(
                            message
                    )),br(),
                    label("User").with(
                            input().withType("text").withClass("form-control").withName("username")
                    ), br(), br(),
                    label("Password").with(
                            input().withType("password").withClass("form-control").withName("password")
                    ), br(), br(), label("New Password").with(
                            input().withType("password").withClass("form-control").withName("confirm_password")
                    ), br(), br(), button("Confirm Password").withClass("btn btn-outline-secondary")
            );
            return templateWrapper(passwordHandler,true, req, res, form, false);
        });

        get(CREATE_USER_URL, (req, res)->{
            authorize(req,res);
            String ownerRole = req.session().attribute("role");
            String message = req.session().attribute("message");
            req.session().removeAttribute("message");
            Tag form = form().withId("create-user-form").withAction(NEW_USER_URL).withMethod("POST").attr("style","margin-top: 100px;").with(
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

        post(REMOVE_USER_URL, (req,res)->{
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
                    redirect = DELETE_USER_URL;
                    message = "Successfully deleted user.";
                } catch (Exception e) {
                    System.out.println("Error while updating user...");
                    e.printStackTrace();
                    redirect = DELETE_USER_URL;
                    message = e.getMessage();
                }
            } else {
                redirect = DELETE_USER_URL;
            }
            res.redirect(redirect);
            req.session().attribute("message", message);
            return null;
        });

        get(DELETE_USER_URL, (req, res)->{
            authorize(req,res);
            String ownerRole = req.session().attribute("role");
            Tag form;
            String message = req.session().attribute("message");
            req.session().removeAttribute("message");
            if(ownerRole!=null&&ownerRole.equals(SUPER_USER)) {
                form = form().withId("create-user-form").withAction(REMOVE_USER_URL).withMethod("POST").attr("style", "margin-top: 100px;").with(
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

        get(GLOBAL_PREFIX, (req, res)->{
            try {
                return templateWrapper(passwordHandler, false, req, res, form().withClass("form-group").withMethod("POST").withAction(LOGIN_URL).attr("style", "margin-top: 100px;").with(
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

        get(HOME_URL, (req, res) -> {
            if(softAuthorize(req,res)) {
                return templateWrapper(passwordHandler,true, req, res, candidateSetModelsForm(req.session().attribute("role")),true);
            } else {
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
            final String username = req.session().attribute("username");
            if(username!=null) {
                Database.saveUserSearchedRequest(username);
            }
            return handleReport(req,res);
        });

        post(FORM_ELEMENT_BY_ID_URL, (req, res) -> {
            authorize(req,res);
            String formID = extractString(req, "id", null);
            List<String> formIds = extractArray(req, "ids[]");
            System.out.println("Found form ids: "+String.join("; ", formIds));
            if(formID==null&&formIds.isEmpty()) {
                return null;
            }
            String role = req.session().attribute("role");
            if(role == null) return null;
            String element;
            Map<String,Object> results;
            if(formID==null) {
                List<String> ids = formIds.stream().filter(f->f!=null).filter(id->ROLE_TO_TOP_LEVEL_FORM_ELEMENTS_BY_ID.get(role).containsKey(id)).collect(Collectors.toList());
                element = String.join("", ids.stream().map(id->ROLE_TO_TOP_LEVEL_FORM_ELEMENTS_BY_ID.get(role).get(id)).filter(f->f!=null).collect(Collectors.toList()));
                results = new HashMap<>();
                results.put("results", element);
                results.put("ids", ids);
            } else {
                element = ROLE_TO_FORM_ELEMENTS_BY_ID.get(role).get(formID);
                results = Collections.singletonMap("results", element);
            }
            return new Gson().toJson(results);
        });


        post(PREVIEW_URL, (req,res) -> {
            authorize(req, res);
            return handlePreviewAssets(req, res);
        });

        post(DOWNLOAD_URL, (req, res) -> {
            authorize(req,res);
            //return handleExcel(req,res);
            return handleCSV(req,res, false);
        });

        post(PREVIEW_DOWNLOAD_URL, (req, res) -> {
            authorize(req, res);
            return handleCSV(req,res, true);
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

        post(CLUSTER_DATASET_URL, (req, res) -> {
            authorize(req,res);
            String userGroup = getUserGroupFor(req.session());
            return handleClusterForm(req,res,userGroup,Constants.USER_DATASET_FOLDER);
        });

        post(ASSIGNEE_LIST_DATASETS_URL, (req, res)-> {
            authorize(req, res);
            String userGroup = getUserGroupFor(req.session());
            return handleAssigneeListForm(req,res,userGroup,Constants.USER_DATASET_FOLDER);
        });

        post(DELETE_DATASET_URL, (req, res) -> {
            authorize(req,res);
            return handleDeleteForm(req,res,Constants.USER_DATASET_FOLDER,true);
        });

        post(RENAME_DATASET_URL, (req, res) -> {
            authorize(req,res);
            return handleRenameForm(req,res,Constants.USER_DATASET_FOLDER,false,true);
        });

        get(SHOW_DATATABLE_URL, (req, res) -> {
            authorize(req,res);
            return handleDataTable(req,res);
        });

        post(SHOW_CHART_URL, (req, res) -> {
            authorize(req,res);
            return handleCharts(req,res);
        });

        get(GLOBAL_PREFIX+"/", (req, res)->{
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

        get("/images/favicon-16x16.png", (request, response) -> {
            response.type("image/png");
            String pathToImage = "public/images/favicon-16x16.png";
            File f = new File(pathToImage);
            BufferedImage bi = ImageIO.read(f);
            OutputStream out = response.raw().getOutputStream();
            ImageIO.write(bi, "png", out);
            out.close();
            response.status(200);
            return response.body();
        });

        get("/images/favicon-32x32.png", (request, response) -> {
            response.type("image/png");
            String pathToImage = "public/images/favicon-32x32.png";
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
            return handleAjaxRequest(req, resultsSearchFunction, displayFunction, null);
        });


        // setup select2 ajax remote data sources
        get(Constants.ASSIGNEE_NAME_AJAX_URL, (req,res)->{
            Map<String,Integer> portfolioSizeMap = new HashMap<>();
            Function<String,List<String>> resultsSearchFunction = search -> Database.searchBigQueryAssignees("big_query_assignee",search, 10, portfolioSizeMap);
            Function<String,String> displayFunction = result ->  result + " ("+portfolioSizeMap.getOrDefault(result,0)+")";
            return handleAjaxRequest(req, resultsSearchFunction, displayFunction, null);
        });

        // setup select2 ajax remote data sources
        get(Constants.CPC_CODE_AJAX_URL, (req,res)->{
            Map<String,String> titlePartMap = new HashMap<>();
            Function<String,List<String>> resultsSearchFunction = search -> Database.searchBigQueryCPCs("big_query_cpc_definition",search, 10,titlePartMap);
            Function<String,String> displayFunction = result ->  result;
            Function<String,String> htmlFunction = result -> "<span>"+ (result+" ("+titlePartMap.getOrDefault(result,"")+")").replace(" ()","") + "</span>";
            return handleAjaxRequest(req, resultsSearchFunction, displayFunction, htmlFunction);
        });

        // setup select2 ajax remote data sources
        get(Constants.GTT_TECHNOLOGY_AJAX_URL, (req,res)->{
            Function<String,List<String>> resultsSearchFunction = search -> new ArrayList<>();
            Function<String,String> displayFunction = result -> result;
            return handleAjaxRequest(req, resultsSearchFunction, displayFunction, null);
        });


        post(CANCEL_REPORT_URL, (req,res)->{
            RecursiveTask<String> reportTask = req.session().attribute("ongoingReportTask");
            List<RecursiveTask> otherTasks = req.session().attribute("ongoingTasks");
            if(reportTask!=null) {
                reportTask.cancel(true);
                req.session().removeAttribute("ongoingReportTask");
            }
            if(otherTasks!=null) {
                for(RecursiveTask task : otherTasks) {
                    task.cancel(true);
                    req.session().removeAttribute("ongoingTasks");
                }
            }
            return new Gson().toJson(Collections.singletonMap("success", true));
        });

    }

    private static Tag createMenuIcon() {
        return span().withClass("custom-menu").attr("title", "Click to toggle main menu.").with(
                div(),
                div(),
                div()
        );
    }

    private static Object handleAjaxRequest(Request req, Function<String,List<String>> resultsSearchFunction, Function<String,String> labelFunction, Function<String,String> htmlResultFunction) {
        List<String> neededLabels = extractArray(req,"get_label_for[]");
        if(neededLabels.size()>0) {
            Map<String,Object> response = new HashMap<>();
            List<String> labels = neededLabels.stream().map(label->labelFunction.apply(label)).collect(Collectors.toList());
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
                    String html = htmlResultFunction == null ? null : htmlResultFunction.apply(result);
                    if(result!=null) {
                        map.put("html_result", html);
                    }
                    map.put("text", labelFunction.apply(result));
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
            if(assets == null && formMap.containsKey("emptyDataset")) {
                assets = new String[]{};
            }
            if(assets!=null&&file!=null) {
                String username = shared ? userGroup : user;
                DatasetIndex.index(username,file.getName(),Arrays.asList(assets));
                formMap.put("asset_count", assets.length);
            } else {
                if(assets==null) formMap.put("asset_count", 0);
                else formMap.put("asset_count", assets.length);
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

    private static Object handleCSV(Request req, Response res, boolean preview) {
        return handleSpreadsheet(req,res,false, preview);
    }

    private static Object handleSpreadsheet(Request req, Response res, boolean excel, boolean preview) {
        try {
            System.out.println("Received excel request");
            long t0 = System.currentTimeMillis();
            final String paramIdx = preview ? "preview" : req.queryParamOrDefault("tableId","");
            // try to get custom data
            List<String> headers;
            List<Map<String,String>> data;
            List<String> nonHumanAttrs;
            String title;
            if(paramIdx.equals("preview")) {
                title = "Preview";
                System.out.println("Creating preview table!");
                Map<String, Object> map = req.session(false).attribute("table-" + paramIdx);
                if (map == null) return null;

                headers = (List<String>) map.getOrDefault("headers", Collections.emptyList());
                if(headers!=null && headers.contains("selection")) {
                    headers = new ArrayList<>(headers);
                    headers.remove("selection");
                    if(headers.contains("idx")) {
                        headers.remove("idx");
                    }
                }
                nonHumanAttrs = null;
                data = (List<Map<String, String>>) map.getOrDefault("rows-highlighted", Collections.emptyList());
            } else if(paramIdx.length()>0) {
                TableResponse tableResponse = req.session(false).attribute("table-"+paramIdx);
                if(tableResponse!=null) {
                    System.out.println("Found tableResponse...");
                    Pair<List<Map<String, String>>, List<Double>> pair = tableResponse.computeAttributesTask.join();
                    data = pair.getFirst();
                    headers = tableResponse.headers;
                    title = tableResponse.title;
                    nonHumanAttrs = tableResponse.nonHumanAttrs;
                    System.out.println("Data size: "+data.size());
                } else {
                    System.out.println("WARNING:: Could not find tableResponse...");
                    headers = Collections.emptyList();
                    data = Collections.emptyList();
                    nonHumanAttrs = null;
                    title = "Data";
                }
            } else {

                System.out.println("Received datatable request");
                Map<String,Object> map = req.session(false).attribute(EXCEL_SESSION);
                if(map==null) return null;
                nonHumanAttrs = null;
                headers = (List<String>)map.getOrDefault("headers",Collections.emptyList());
                if(headers!=null && headers.contains("selection")) {
                    headers = new ArrayList<>(headers);
                    headers.remove("selection");
                    if(headers.contains("idx")) {
                        headers.remove("idx");
                    }
                }
                data = (List<Map<String,String>>)map.getOrDefault("rows",Collections.emptyList());
                title = "Data";
            }

            System.out.println("Number of excel headers: "+headers.size());
            List<String> humanHeaders = headers.stream().map(header->{
                if(nonHumanAttrs==null || !nonHumanAttrs.contains(header)) {
                    return fullHumanAttributeFor(header);
                } else {
                    return header;
                }
            }).collect(Collectors.toList());
            if(excel) {
                HttpServletResponse raw = res.raw();
                res.type("application/force-download");
                res.header("Content-Disposition", "attachment; filename=download.xls");
                ExcelHandler.writeDefaultSpreadSheetToRaw(raw, "Data", title, data, headers, humanHeaders);
                long t1 = System.currentTimeMillis();
                System.out.println("Time to create excel sheet: " + (t1 - t0) / 1000 + " seconds");
                return raw;
            } else {
                res.type("text/csv");
                res.header("Content-Disposition", "attachment; filename=download.csv");
                StringJoiner csvFile = new StringJoiner("\n");
                StringJoiner csvLine = new StringJoiner("\",\"","\"","\"");
                for(String header : humanHeaders) {
                    csvLine.add(header);
                }
                csvFile.add(csvLine.toString());
                for(Map<String,String> row : data) {
                    csvLine = new StringJoiner("\",\"","\"","\"");
                    for(int i = 0; i < headers.size(); i++) {
                        csvLine.add(row.getOrDefault(headers.get(i),""));
                    }
                    csvFile.add(csvLine.toString());
                }
                long t1 = System.currentTimeMillis();
                System.out.println("Time to create csv sheet: " + (t1 - t0) / 1000 + " seconds");
                return csvFile.toString();
            }
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            return new Gson().toJson(new SimpleAjaxMessage("ERROR "+e.getClass().getName()+": " + e.getMessage()));
        }
    }

    private static Object handleDataTable(Request req, Response res) {
        System.out.println("Received data table request.....");
        Map<String,Object> response = new HashMap<>();
        final String paramIdx = req.queryParamOrDefault("tableId","");
        long timeLimit = 180 * 1000;
        Lock lock;
        List<Double> totals = null;
        List<Double> filteredTotals = null;
        try {

            // try to get custom data
            List<String> headers;
            List<Map<String,String>> data;
            Set<String> numericAttrNames;
            if(paramIdx.equals("preview")) {
                System.out.println("Creating preview table!");
                Map<String,Object> map = req.session(false).attribute("table-"+paramIdx);
                if(map==null) return null;

                headers = (List<String>)map.getOrDefault("headers",Collections.emptyList());
                if(headers!=null&&!headers.contains("selection")) {
                    headers.add("selection");
                    headers.add("idx");
                    headers = new ArrayList<>(headers);
                }
                data = (List<Map<String,String>>)map.getOrDefault("rows-highlighted",Collections.emptyList());
                numericAttrNames = (Set<String>)map.getOrDefault("numericAttrNames",new HashSet<>());
                numericAttrNames.add("idx");
                lock = (Lock)map.getOrDefault("lock",new ReentrantLock());

            } else if(paramIdx.length()>0) {
                TableResponse tableResponse = req.session().attribute("table-"+paramIdx);
                if(tableResponse!=null) {
                    System.out.println("Found tableResponse...");
                    lock=tableResponse.lock;
                    try {
                        Pair<List<Map<String, String>>, List<Double>> pair = tableResponse.computeAttributesTask.join();
                        data = pair.getFirst();
                        totals = pair.getSecond();
                        headers = tableResponse.headers;
                        lock = tableResponse.lock;

                    } catch(Exception e) {
                        Map<String,String> noDataMap = new HashMap<>();
                        noDataMap.put("Could not compute data in time", "Max time limit: "+(timeLimit/(1000*60)+" minutes."));
                        data = Arrays.asList(noDataMap);
                        headers = Collections.singletonList("Count not compute data in time");
                    }

                    numericAttrNames = tableResponse.numericAttrNames;
                    System.out.println("Data size: "+data.size());
                } else {
                    System.out.println("WARNING:: Could not find tableResponse...");
                    headers = Collections.emptyList();
                    data = Collections.emptyList();
                    lock = new ReentrantLock();
                    numericAttrNames = Collections.emptySet();
                }
            } else {
                System.out.println("Received datatable request");
                Map<String,Object> map = req.session(false).attribute(EXCEL_SESSION);
                if(map==null) return null;

                headers = (List<String>)map.getOrDefault("headers",Collections.emptyList());
                if(headers!=null&&!headers.contains("selection")) {
                    headers.add("selection");
                    headers.add("idx");
                    headers = new ArrayList<>(headers);
                }
                data = (List<Map<String,String>>)map.getOrDefault("rows-highlighted",Collections.emptyList());
                numericAttrNames = (Set<String>)map.getOrDefault("numericAttrNames",new HashSet<>());
                numericAttrNames.add("idx");
                lock = (Lock)map.getOrDefault("lock",new ReentrantLock());
            }
            System.out.println("Number of headers: "+headers.size());

            lock.lock();
            try {
                int perPage = extractInt(req, "perPage", 10);
                int page = extractInt(req, "page", 1);
                int offset = extractInt(req, "offset", 0);

                long totalCount = data.size();
                // check for search
                List<Map<String, String>> queriedData;
                String searchStr;
                if (req.queryMap("queries") != null && req.queryMap("queries").hasKey("search")) {
                    searchStr = req.queryMap("queries").value("search").toLowerCase();
                    if (searchStr == null || searchStr.trim().isEmpty()) {
                        queriedData = data;
                    } else {
                        queriedData = new ArrayList<>(data.stream().filter(m -> m.values().stream().anyMatch(val -> val.toLowerCase().contains(searchStr))).collect(Collectors.toList()));
                    }
                } else {
                    searchStr = "";
                    queriedData = data;
                }
                long queriedCount = queriedData.size();
                // check for sorting
                if (req.queryMap("sorts") != null) {
                    req.queryMap("sorts").toMap().forEach((k, v) -> {
                        System.out.println("Sorting " + k + ": " + v);
                        if (v == null || k == null) return;
                        boolean isNumericField = numericAttrNames.contains(k);
                        boolean reversed = (v.length > 0 && v[0].equals("-1"));

                        String directionStr = reversed ? "-1" : "1";

                        String sortStr = k + directionStr + searchStr;
                        System.out.println("New sort string: " + sortStr);

                        Comparator<Map<String, String>> comp = (d1, d2) -> {
                            if (isNumericField) {
                                Double v1 = null;
                                Double v2 = null;
                                try {
                                    v1 = Double.valueOf(d1.get(k));
                                } catch (Exception nfe) {
                                }
                                try {
                                    v2 = Double.valueOf(d2.get(k));
                                } catch (Exception e) {
                                }
                                if (v1 == null && v2 == null) return 0;
                                if (v1 == null) return 1;
                                if (v2 == null) return -1;
                                return v1.compareTo(v2) * (reversed ? -1 : 1);
                            } else {
                                return d1.get(k).compareTo(d2.get(k)) * (reversed ? -1 : 1);
                            }
                        };
                        queriedData.sort(comp);

                    });
                }
                List<Map<String, String>> dataPage;
                AtomicInteger cnt = new AtomicInteger(1);
                if(queriedData!=null) {
                    for (Map<String, String> row : queriedData) {
                        row.put("idx", String.valueOf(cnt.getAndIncrement()));
                    }
                }
                if (offset < totalCount) {
                    dataPage = queriedData.subList(offset, Math.min(queriedData.size(), offset + perPage));
                } else {
                    dataPage = Collections.emptyList();
                }
                if(totals!=null) {
                    // try to get filtered totals
                    filteredTotals = new ArrayList<>(totals.size());
                    for(int i = 0; i < totals.size(); i++) {
                        filteredTotals.add(0d);
                    }
                    for(int i = 1; i < headers.size(); i++) {
                        String header = headers.get(i);
                        for(Map<String,String> row : dataPage) {
                            try {
                                filteredTotals.set(i-1, filteredTotals.get(i-1) + Double.valueOf(row.get(header)));
                            } catch(Exception e) {

                            }
                        }
                    }
                }
                response.put("totalRecordCount",totalCount);
                response.put("queryRecordCount",queriedCount);
                response.put("records", dataPage);
                if(totals!=null) {
                    response.put("totals", totals);
                }
                if(filteredTotals!=null) {
                    response.put("filteredTotals", filteredTotals);
                }

            } catch(Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }

            return new Gson().toJson(response);
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            response.put("totalRecordCount",0);
            response.put("queryRecordCount",0);
            response.put("records",Collections.emptyList());
        }
        return new Gson().toJson(response);
    }

    private static Object handleCharts(Request req, Response res) {
        try {
            System.out.println("Received chart request");
            Integer chartNum = extractInt(req,"chartNum", null);
            if(chartNum != null) {
                RecursiveTask<List<? extends AbstractChart>> task = req.session(false).attribute("chart-"+chartNum);
                List<? extends AbstractChart> charts = task.get();
                Map<String,Object> ret = new HashMap<>();
                ret.put("charts", charts.stream().map(chart->chart.getOptions()).collect(Collectors.toList()));
                ret.put("isStockCharts", charts.stream().map(chart->chart.isStockChart()).collect(Collectors.toList()));
                ret.put("chartId", "chart-"+chartNum);
                return new JsonRenderer().toJson(ret);
            } else {
                return new Gson().toJson(new SimpleAjaxMessage("Unable to create chart"));
            }
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            return new Gson().toJson(new SimpleAjaxMessage("ERROR "+e.getClass().getName()+": " + e.getMessage()));
        }
    }
    private static Object handleRenameForm(Request req, Response res, String baseFolder, boolean useUpdatesFile, boolean isDataset) {
        String filename = req.queryParams("file");
        String name = req.queryParams("name");
        String[] parentDirs = req.queryParamsValues("parentDirs[]");
        String userGroup = getUserGroupFor(req.session());
        return handleRenameFormHelper(filename, name, parentDirs, userGroup, req.session(false), baseFolder, useUpdatesFile, isDataset);
    }

    private static Object handleRenameFormHelper(String filename, String name, String[] parentDirs, String userGroup, Session session, String baseFolder, boolean useUpdatesFile, boolean isDataset) {
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
            String username = isShared ? userGroup : session.attribute("username");
            if(username!=null&&username.length()>0) {
                String templateFolderStr = baseFolder+username+"/";
                File formFile = new File(templateFolderStr+filename);
                File updatesFile = new File(formFile.getAbsolutePath()+"_updates");
                if(formFile.exists()) {
                    Map<String,Object> updates = new HashMap<>();
                    updates.put("name", name);
                    if (parentDirs != null && parentDirs.length > 0) updates.put("parentDirs", Arrays.copyOfRange(parentDirs,1,parentDirs.length));

                    if(isDataset) {
                        Map<String,Object> prevUpdates = (Map<String,Object>) Database.tryLoadObject(formFile);
                        if(prevUpdates.containsKey("asset_count")) {
                            System.out.println("Previous asset count: "+prevUpdates.get("asset_count"));
                            updates.put("asset_count", prevUpdates.get("asset_count"));
                        } else {
                            // add asset count
                            updates.put("asset_count", DatasetIndex.get(username, filename).size());
                            System.out.println("New asset count: "+updates.get("asset_count"));
                        }
                        responseMap.put("asset_count", updates.get("asset_count"));
                    }

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
        boolean preset = Boolean.valueOf(req.queryParamOrDefault("preset","false"));
        String userGroup = getUserGroupFor(req.session());
        String user = req.session().attribute("username");
        if(user==null||user.isEmpty()) {
            return null;
        }

        String filename;
        if(defaultFile) {
            filename = Constants.USER_DEFAULT_ATTRIBUTES_FOLDER+user+"/"+user;
            // may not exist so default to super user group default and then global default attributes form
            if(! new File(filename).exists()) {
                // try to get the user group default (if exists)
                filename = Constants.USER_DEFAULT_ATTRIBUTES_FOLDER+userGroup+"/"+userGroup;
                if(! new File(filename).exists()) {
                    filename = Constants.USER_DEFAULT_ATTRIBUTES_FOLDER + PRESET_USER_GROUP + "/" + PRESET_USER_GROUP;
                }
            }
        } else {
            String group = (shared? userGroup : (preset? PRESET_USER_GROUP : user));
            filename = baseFolder + group + "/" + file;
        }

        Map<String,Object> data = getMapFromFile(new File(filename),true);

        if(dataset) {
            String username = shared ? userGroup : user;
            List<String> assets = DatasetIndex.get(username, file);
            data.put("assets", assets);
            data.put("asset_count", assets.size());
        }

        return new Gson().toJson(data);
    }


    private static Object handleAssigneeListForm(Request req, Response res, String userGroup, String baseFolder) {
        Map<String, Object> response = new HashMap<>();

        boolean shared = Boolean.valueOf(req.queryParamOrDefault("shared","false"));
        List<String> assignees = preProcess(extractString(req, "assignees", ""), "\\n", null);

        System.out.println("Number of assignees: "+assignees.size());

        StringJoiner message = new StringJoiner("; ", "Messages: ", ".");

        String user = req.session().attribute("username");
        if(user==null||user.isEmpty()) {
            message.add("no user found");
        } else {

            String username = shared ? userGroup : user;
            String[] parentDirs = req.queryParamsValues("parentDirs[]");
            if(parentDirs==null) {
                message.add("no parent dirs found.");
            } else {
                Map<String, List<String>> assigneeClusters = assignees.stream().distinct().map(assignee -> {
                    SearchHit[] hits = MyClient.get().prepareSearch(IngestPatents.INDEX_NAME).setTypes(IngestPatents.TYPE_NAME)
                            .setFrom(0)
                            .setSize(1000)
                            .setQuery(
                                    QueryBuilders.boolQuery()
                                            .must(QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.randomFunction(1)))
                                            .must(QueryBuilders.matchPhraseQuery(Attributes.LATEST_ASSIGNEES + "." + Attributes.LATEST_FIRST_ASSIGNEE, assignee))
                            ).setFetchSource(false)
                            .get().getHits().getHits();
                    List<String> assets = Stream.of(hits).map(hit -> hit.getId()).collect(Collectors.toList());
                    System.out.println("Found " + assets.size() + " assets for assignee: " + assignee);
                    return new Pair<>(assignee, assets);
                }).collect(Collectors.toMap(e -> e.getFirst(), e -> e.getSecond()));
                // search for assignee assets

                if (assigneeClusters == null) {
                    message.add("clusters are null");
                } else {

                    List<Map<String, Object>> assigneeData = new ArrayList<>(assigneeClusters.size());
                    response.put("assignees", assigneeData);

                    assigneeClusters.forEach((name, cluster) -> {
                        //handleSaveForm()
                        Map<String, Object> formMap = new HashMap<>();

                        formMap.put("name", name);
                        formMap.put("assets", cluster.toArray(new String[cluster.size()]));
                        formMap.put("asset_count", cluster.size());

                        System.out.println("Parent dirs of cluster: " + Arrays.toString(parentDirs));

                        Pair<String, Map<String, Object>> pair = saveFormToFile(req, userGroup, formMap, name, parentDirs, user, baseFolder, saveDatasetsFunction(user, userGroup), saveDatasetUpdatesFunction());

                        Map<String, Object> clusterData = pair.getSecond();
                        clusterData.put("name", name);
                        assigneeData.add(clusterData);
                    });
                }
            }
        }

        response.put("message", message.toString());

        return new Gson().toJson(response);
    }

    private static Object handleClusterForm(Request req, Response res, String userGroup, String baseFolder) {
        Map<String, Object> response = new HashMap<>();

        String file = req.queryParams("file");
        boolean shared = Boolean.valueOf(req.queryParamOrDefault("shared","false"));

        Integer k = null;
        try {
            k = Integer.valueOf(req.queryParams("k"));
            if(k!=null && (k<2||k>500)) throw new RuntimeException("Number of clusters must be between 2 and 500");
        } catch(Exception e) {

        }
        System.out.println("Number of clusters: "+k);

        StringJoiner message = new StringJoiner("; ", "Messages: ", ".");

        String user = req.session().attribute("username");
        if(user==null||user.isEmpty()) {
            message.add("no user found");
        } else {

            String filename = baseFolder + (shared ? userGroup : user) + "/" + file;

            Map<String, Object> data = getMapFromFile(new File(filename), false);

            System.out.println("Parent data: "+new Gson().toJson(data));

            String parentName = (String) data.get("name");
            if (parentName == null) {
                message.add("no parent name");
            } else {
                String username = shared ? userGroup : user;
                List<String> assets = DatasetIndex.get(username, file);

                String[] parentParentDirs = Stream.of(new String[]{shared ? "Shared Datasets":"My Datasets"},(String[]) data.getOrDefault("parentDirs",new String[]{}))
                        .flatMap(array->Stream.of(array)).toArray(size->new String[size]);

                if (assets == null) {
                    message.add("assets are null");
                } else {
                    Map<String,INDArray> vectorMap = Database.loadPatentVectorsFor(assets);
                    Map<String,List<String>> technologyMap = Database.loadTechnologiesFor(assets);
                    System.out.println("Total technologies found: "+technologyMap.size());
                    Function<String,List<String>> techPredictionFunction = asset -> technologyMap.getOrDefault(asset,Collections.emptyList());
                    AssetKMeans kMeans = new AssetKMeans(techPredictionFunction, vectorMap, k);

                    Map<String, List<String>> clusters = kMeans.clusterAssets();

                    if (clusters == null) {
                        message.add("clusters are null");
                    } else {
                        System.out.println("Num clusters: "+clusters.size());
                        Set<String> valid = clusters.values().stream().flatMap(l->l.stream()).collect(Collectors.toSet());
                        List<String> unknown = assets.stream().filter(asset->!valid.contains(asset)).collect(Collectors.toList());
                        if(unknown.size()>0) {
                            clusters.putIfAbsent("unknown", new ArrayList<>());
                            clusters.get("unknown").addAll(unknown);
                        }
                        System.out.println("Num others: "+unknown.size());

                        List<Map<String, Object>> clustersData = new ArrayList<>(clusters.size());
                        response.put("clusters", clustersData);

                        clusters.forEach((name, cluster) -> {
                            //handleSaveForm()
                            String[] parentDirs = Stream.of(Stream.of(parentParentDirs), Stream.of(parentName)).flatMap(stream -> stream).toArray(size -> new String[size]);

                            Map<String, Object> formMap = new HashMap<>();

                            formMap.put("name",name);
                            formMap.put("assets", cluster.toArray(new String[cluster.size()]));
                            formMap.put("asset_count", cluster.size());

                            System.out.println("Parent dirs of cluster: "+Arrays.toString(parentDirs));

                            Pair<String, Map<String, Object>> pair = saveFormToFile(req, userGroup, formMap, name, parentDirs, user, baseFolder, saveDatasetsFunction(user,userGroup), saveDatasetUpdatesFunction());

                            Map<String, Object> clusterData = pair.getSecond();
                            clusterData.put("name",name);
                            clustersData.add(clusterData);
                        });
                    }
                }
            }
        }

        response.put("message", message.toString());

        return new Gson().toJson(response);
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
            if(assets == null && extractBool(req, "emptyDataset")) {
                assets = new String[]{};
            }
            if(assets==null) {
                assets = req.session(false).attribute("assets"); // default to last seen report
            } else {
                // asset list or empty dataset... check for other names
                if (assets.length > 0) {
                    System.out.println("Initial assets: "+String.join(", ", assets));
                    assets = PatentSimilarityEngine.convertToFullPatentNumbers(Arrays.asList(assets)).toArray(new String[]{});
                    System.out.println("Reconciled assets: "+String.join(", ", assets));
                }
            }
            String name = req.queryParams("name");
            if(assets!=null&&name!=null&&name.length()>0) {
                name = decodeURLString(name);
                String file = req.queryParams("file");
                Map<String, Object> formMap = new HashMap<>();
                formMap.put("name", name);
                formMap.put("assets", assets);
                formMap.put("asset_count", assets.length);
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
                        formMap.put("asset_count", allAssets.size());
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
        String userGroup = getUserGroupFor(req.session());
        if(username == null || userGroup==null) return null;
        final boolean saveToUserGroup = extractBool(req,"extract_to_usergroup");
        Function<Map<String, Object>, Map<String, Object>> afterFunction = map -> {
            if(saveToUserGroup) {
                map.put("file", userGroup);
            } else {
                map.put("file", username);
            }
            return map;
        };
        return handleSaveForm(req,res,Constants.USER_DEFAULT_ATTRIBUTES_FOLDER,templateFormMapFunction().andThen(afterFunction),saveTemplatesFunction(),saveTemplateUpdatesFunction());
    }


    private static Pair<String,Map<String,Object>> saveFormToFile(Request req, String userGroup, Map<String,Object> formMap, String name, String[] parentDirs, String actualUsername, String baseFolder, Function3<Map<String,Object>,File,Boolean,Void> saveFunction, Function2<Map<String,Object>,File,Void> saveUpdatesFunction) {
        String message;
        Random random = new Random(System.currentTimeMillis());
        Map<String,Object> responseMap = new HashMap<>();

        if(formMap!=null) {
            Object prevFilename = formMap.get("file");

            if(debug) System.out.println("Form "+name+" attributes: "+new Gson().toJson(formMap));

            if (parentDirs != null && parentDirs.length > 0) {
                for(int i = 1; i < parentDirs.length; i++) {
                    parentDirs[i]=decodeURLString(parentDirs[i]);
                }
                formMap.put("parentDirs", Arrays.copyOfRange(parentDirs,1,parentDirs.length));
            }

            boolean isShared = extractBool(req, "extract_to_usergroup");
            // override is shared by parent dirs
            if(!isShared && parentDirs!=null&&parentDirs.length>0&&parentDirs[0].startsWith("Shared")) {
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
                if(formMap.containsKey("asset_count")) {
                    responseMap.put("asset_count", formMap.get("asset_count"));
                }

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

        Pair<String,Map<String,Object>> pairResponse = saveFormToFile(req, userGroup,formMap,name,parentDirs,actualUsername,baseFolder,saveFunction,saveUpdatesFunction);

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

    private static AbstractAttribute findAttribute(Collection<AbstractAttribute> couldBeNested, String attrName, int attrStartIndex) {
        return couldBeNested.stream()
                .flatMap(a->a instanceof NestedAttribute ? ((NestedAttribute)a).getAttributes().stream() : Stream.of(a))
                .filter(c -> c.getFullName().substring(attrStartIndex).equals(attrName))
                .limit(1).findFirst().orElse(null);
    }

    private static QueryBuilder createPreviewQuery(AbstractAttribute attribute, String value) {
        QueryBuilder query;
        if(attribute!=null && value != null) {
            AbstractFilter filter;
            if(attribute instanceof DateRangeAttribute) {
                String[] dateRange = new String[2];
                String[] years = value.split("-");
                if(years.length==1) {
                    years[0] = years[0].replace("+", "");
                }
                for(int i = 0; i < years.length; i++) {
                    years[i] = years[i].trim();
                    dateRange[i] = years[i]+"-01-01";
                }
                filter = new AbstractBetweenFilter(attribute, AbstractFilter.FilterType.Between);
                ((AbstractBetweenFilter)filter).setMin(dateRange[0]);
                ((AbstractBetweenFilter)filter).setMax(dateRange[1]);
            } else if(attribute instanceof RangeAttribute) {
                Number[] range = new Number[2];
                String[] values = value.split(" - ");
                if(values.length==1) {
                    values[0] = values[0].replace("+","");
                }
                for(int i = 0; i < values.length; i++) {
                    values[i] = values[i] == null ? null : values[i].trim();
                    values[i] = values[i] == null ? null : (values[i].substring(0, values[i].length()-((RangeAttribute) attribute).valueSuffix().length()));
                    range[i] = values[i] == null ? null : (attribute.getType().startsWith("int") ? Integer.valueOf(values[i]) : Double.valueOf(values[i]));
                }
                filter = new AbstractBetweenFilter(attribute, AbstractFilter.FilterType.Between);
                ((AbstractBetweenFilter)filter).setMin(range[0]);
                ((AbstractBetweenFilter)filter).setMax(range[1]);

            } else {
                if(attribute instanceof DatasetAttribute) {
                    String prev = value;
                    value = ((DatasetAttribute) attribute).getDatasetIDFromName(value);
                    if(value==null) {
                        System.out.println("Unable to find dataset attribute: "+prev);
                        return null;
                    }
                }
                filter = new AbstractIncludeFilter(attribute, AbstractFilter.FilterType.Include, attribute.getFieldType(), Collections.singleton(value), true);
            }
            // construct possible nested filter
            if(attribute instanceof AbstractScriptAttribute) {
                query = filter.getScriptFilter();
            } else if(attribute.getParent()!=null&&!(attribute.getParent() instanceof AbstractChartAttribute)) {
                String rootName = attribute.getParent().getName();
                AbstractNestedFilter nestedFilter = new AbstractNestedFilter(new NestedAttribute(Collections.emptyList(), false) {
                    @Override
                    public String getName() {
                        return rootName;
                    }
                    @Override
                    public boolean isObject() {
                        return attribute.getParent().isObject();
                    }
                }, true, filter);
                nestedFilter.setFilterSubset(Collections.singleton(filter));
                query = nestedFilter.getFilterQuery();
            } else {
                query = filter.getFilterQuery();
            }
        } else {
            query = null;
        }
        return query;
    }


    private static Object handlePreviewAssets(Request req, Response res) {
        String value1 = extractString(req, "group1", null);
        String value2 = extractString(req, "group2", null);
        String chartId = req.queryParams("chart_id");

        AggregationChart<?> chart;

        AbstractAttribute attribute;
        AbstractAttribute groupByAttribute;
        if(chartId.contains("table")) {
            System.out.println("Looking for tableresponse with id: "+chartId);
            TableResponse task = req.session(false).attribute(chartId);
            attribute = task.attribute;
            groupByAttribute = task.groupByAttribute;

        } else {
            chartId = chartId.substring(0, chartId.length() - 2);
            ChartTask task = req.session(false).attribute(chartId);
            attribute = task.getAttribute();
            groupByAttribute = task.getGroupByAttribute();
        }

        if(attribute!=null) {
            System.out.println("Found attribute: "+attribute.getFullName());
        }
        if(groupByAttribute!=null) {
            System.out.println("Found group by attribute: "+groupByAttribute.getFullName());
        }

        QueryBuilder query1 = createPreviewQuery(attribute, value1);
        QueryBuilder query2 = createPreviewQuery(groupByAttribute, value2);

        SearchRequestBuilder requestBuilder = req.session(false).attribute("searchRequest");
        QueryBuilder query = req.session(false).attribute("searchQuery");
        Map<String,Object> results = new HashMap<>();
        if(requestBuilder != null) {
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
            if(query!=null) {
                queryBuilder = queryBuilder.must(query);
            }
            if(query1 != null) {
                queryBuilder = queryBuilder.must(query1);
            }
            if(query2 != null) {
                queryBuilder = queryBuilder.must(query2);
            }
            System.out.println("New query: "+queryBuilder.toString());
            int limit = 100;
            Boolean filterNestedObjects = req.session(false).attribute("filterNestedObjects");
            Boolean isOverallScore = req.session(false).attribute("isOverallScore");
            if(isOverallScore==null) {
                isOverallScore = false;
            }
            if(filterNestedObjects == null) {
                filterNestedObjects = false;
            }
            List<String> tableHeaders = req.session(false).attribute("tableHeaders");
            if(tableHeaders!=null) {
                System.out.println("Table headers: " + String.join("; ", tableHeaders));
            }
            Boolean useHighlighter = req.session(false).attribute("useHighlighter");
            if(useHighlighter==null) {
                useHighlighter = true;
            }
            String itemSeparator = req.session(false).attribute("itemSeparator");
            if(itemSeparator==null) {
                itemSeparator = "; ";
            }
            SearchResponse response = requestBuilder.setSize(limit).setFrom(0).setQuery(queryBuilder).get();
            long totalHits = response.getHits().totalHits;
            List<Item> items = DataSearcher.getItemsFromSearchResponse(response, Attributes.getNestedAttrMap(), isOverallScore, filterNestedObjects, limit, true, false);
            List<Map<String, String>> tableData = new ArrayList<>(getTableRowData(items, tableHeaders, false, itemSeparator));
            List<Map<String, String>> tableDataHighlighted;
            if (useHighlighter) {
                tableDataHighlighted = new ArrayList<>(getTableRowData(items, tableHeaders, true, itemSeparator));
            } else {
                tableDataHighlighted = tableData;
            }

            Map<String, Object> excelRequestMap = new HashMap<>();
            excelRequestMap.put("headers", tableHeaders);
            excelRequestMap.put("rows", tableData);
            excelRequestMap.put("rows-highlighted", tableDataHighlighted);
            excelRequestMap.put("numericAttrNames", getNumericAttributes());
            excelRequestMap.put("totalHits", totalHits);
            excelRequestMap.put("lock", new ReentrantLock());
            req.session(false).attribute("table-preview", excelRequestMap);
            req.session(false).attribute("preview_assets", items.stream().map(Item::getName).collect(Collectors.toList()));

            if(tableHeaders.contains("selection")) {
                tableHeaders.remove("selection");
                tableHeaders.remove("idx");
            }
            Tag dataTable = div().withClass("row").attr("style", "margin-top: 10px;").with(
                    tableFromPatentList(tableHeaders, true, totalHits)
            );
            results.put("html", dataTable.render());
            System.out.println("Table: "+dataTable.render());
            System.out.println("Num assets found: "+tableData.size());
        }
        return new Gson().toJson(results);
    }

    private static Object handleReport(Request req, Response res) {
        List<RecursiveTask> otherTasks = Collections.synchronizedList(new ArrayList<>());
        // start timer
        RecursiveTask<String> handleReportTask = new RecursiveTask<String>() {
            @Override
            protected String compute() {
                try {
                    if (req.session(false).attribute(EXCEL_SESSION) != null)
                        req.session(false).removeAttribute(EXCEL_SESSION);
                    //System.out.println("Getting parameters...");
                    System.out.println("Getting models...");
                    long timeStart = System.currentTimeMillis();
                    // Sorted by
                    // Get Models to use
                    List<String> attributes = extractArray(req, ATTRIBUTES_ARRAY_FIELD);
                    List<String> nestedAttributeParents = getNestedAttrMap().keySet().stream().filter(attr -> attributes.contains(attr)).collect(Collectors.toList());
                    Map<String, List<String>> nestedAttributeParentMap = nestedAttributeParents.stream().collect(Collectors.toMap(attr -> attr, attr -> {
                        return extractArray(req, attr + "[]");
                    }));
                    List<String> nestedAttributes = nestedAttributeParentMap.entrySet().stream().flatMap(e -> e.getValue().stream()).collect(Collectors.toList());
                    List<String> itemAttributes = Stream.of(attributes.stream().filter(attr -> !getNestedAttrMap().containsKey(attr)), nestedAttributes.stream()).flatMap(stream -> stream).collect(Collectors.toList());

                    //System.out.println("FOUND ATTRIBUTES: " + String.join("; ", itemAttributes));
                    //System.out.println("FOUND NESTED ATTRIBUTES: " + String.join("; ", nestedAttributes));
                    List<String> chartModels = extractArray(req, CHART_MODELS_ARRAY_FIELD);

                    SimilarityEngineController engine = similarityEngine.join().dup();

                    List<AggregationChart<?>> abstractCharts = chartModels.stream().map(chart -> chartModelMap.get(chart).dup()).collect(Collectors.toList());
                    abstractCharts.forEach(chart->chart.extractRelevantInformationFromParams(req));
                    Set<String> chartPreReqs = abstractCharts.stream().flatMap(chart->chart.getAttrNames()==null?Stream.empty():chart.getAttrNames().stream()).collect(Collectors.toCollection(HashSet::new));
                    System.out.println("Chart prereqs: "+String.join("; ", chartPreReqs));
                    chartPreReqs.addAll(abstractCharts.stream().flatMap(chart->chart.getAttrNameToGroupByAttrNameMap().values().stream()).collect(Collectors.toList()));
                    chartPreReqs.addAll(abstractCharts.stream().flatMap(chart->chart.getAttrToCollectByAttrMap().values().stream()).collect(Collectors.toList()));
                    engine.setChartPrerequisites(chartPreReqs);

                    engine.buildAttributes(req);

                    if(Thread.currentThread().isInterrupted()) throw new CancellationException();

                    List<AggregationBuilder> aggregationBuilders = abstractCharts.stream().flatMap(chart->{
                        List<AggregationBuilder> builders = new ArrayList<>();
                        for (int i = 0; i < chart.getAttrNames().size(); i++) {
                            String attrName = chart.getAttrNames().get(i);
                            int attrStartIdx = chart.getName().replace("[]","").length()+1;
                            AbstractAttribute attribute = findAttribute(chart.getAttributes(),attrName,attrStartIdx);
                            if (attribute == null || attribute instanceof NestedAttribute) {
                                continue;
                            }
                            if(attribute instanceof DependentAttribute) {
                                ((DependentAttribute)attribute).extractRelevantInformationFromParams(req);
                            }
                            List<AggregationBuilder> aggregations = chart.getAggregations(req, attribute,attrName).stream().map(a->a.getAggregation()).collect(Collectors.toList());
                            aggregations.forEach(agg->{
                                System.out.println("Agg: "+agg.toString());
                            });
                            builders.addAll(aggregations);
                        }
                        return builders.stream();
                    }).collect(Collectors.toList());

                    if(Thread.currentThread().isInterrupted()) throw new CancellationException();

                    engine.setAggregationBuilders(aggregationBuilders);
                    engine.extractRelevantInformationFromParams(req);
                    final PortfolioList portfolioList = engine.getPortfolioList();
                    final Aggregations aggregations = engine.getAggregations();
                    final long totalCount = engine.getTotalCount();

                    if(Thread.currentThread().isInterrupted()) throw new CancellationException();

                    res.type("application/json");

                    // build ordering

                    Map<String, Double> baseOrderMap = new HashMap<>();
                    attributes.forEach(attr -> {
                        baseOrderMap.put(attr, (double) extractInt(req, "order_" + attr, 0));
                    });
                    nestedAttributeParentMap.entrySet().forEach(e -> {
                        double baseOrder = baseOrderMap.get(e.getKey());
                        Map<String,Double> valueMap = e.getValue().stream().collect(Collectors.toMap(nested -> nested, nested -> {
                            return ((double)extractInt(req, "order_" + nested, 0));
                        }));
                        if(valueMap.size()>0) {
                            int size = valueMap.size();
                            AtomicInteger cnt = new AtomicInteger(0);
                            valueMap = valueMap.entrySet().stream().sorted(Comparator.comparing(e2->e2.getValue()))
                                    .collect(Collectors.toMap(e2->e2.getKey(),e2->baseOrder+(((double)cnt.getAndIncrement())/size)));
                            baseOrderMap.putAll(valueMap);
                        }
                    });

                    System.out.println("Base order map: "+new Gson().toJson(baseOrderMap));

                    List<String> tableHeaders = new ArrayList<>(itemAttributes);
                    tableHeaders.sort(Comparator.comparing(h -> baseOrderMap.get(h)));

                    String comparator = extractString(req,COMPARATOR_FIELD,Constants.SCORE);
                    if(comparator.equals(Constants.SCORE)) {
                        tableHeaders.add(Math.min(1,tableHeaders.size()),Constants.SCORE);
                    }

                    if(Thread.currentThread().isInterrupted()) throw new CancellationException();

                    System.out.println("Rendering table...");
                    boolean useHighlighter = extractBool(req, USE_HIGHLIGHTER_FIELD);
                    String itemSeparator = extractString(req, LIST_ITEM_SEPARATOR_FIELD, "; ");
                    req.session(false).attribute("useHighlighter", useHighlighter);
                    req.session(false).attribute("itemSeparator", itemSeparator);
                    req.session(false).attribute("tableHeaders", new ArrayList<>(tableHeaders));
                    List<Map<String, String>> tableData = new ArrayList<>(getTableRowData(portfolioList.getItemList(), tableHeaders, false, itemSeparator));
                    List<Map<String, String>> tableDataHighlighted;
                    if (useHighlighter) {
                        tableDataHighlighted = new ArrayList<>(getTableRowData(portfolioList.getItemList(), tableHeaders, true, itemSeparator));
                    } else {
                        tableDataHighlighted = tableData;
                    }

                    boolean onlyExcel = extractBool(req, "onlyExcel");
                    String html;

                    Map<String, Object> excelRequestMap = new HashMap<>();
                    excelRequestMap.put("headers", tableHeaders);
                    excelRequestMap.put("rows", tableData);
                    excelRequestMap.put("rows-highlighted", tableDataHighlighted);
                    excelRequestMap.put("numericAttrNames", getNumericAttributes());
                    excelRequestMap.put("lock", new ReentrantLock());
                    req.session().attribute(EXCEL_SESSION, excelRequestMap);
                    req.session().attribute("assets", portfolioList.getIds());

                    if(Thread.currentThread().isInterrupted()) throw new CancellationException();

                    if (onlyExcel) {
                        System.out.println("ONLY EXCEL:: Skipping chart building and html building...");
                        Map<String, String> results = new HashMap<>();
                        results.put("message", "success");
                        html = new Gson().toJson(results);
                    } else {
                        try {
                            // try to erase previous charts and tables from session memory
                            //  stopping any computations if present
                            List<String> previousSessionIds = req.session().attribute("previousSessionIds");
                            if(previousSessionIds!=null) {
                                previousSessionIds.forEach(id->{
                                    Object attr = req.session().attribute(id);
                                    if(attr!=null) {
                                        ForkJoinTask task = null;
                                        if(attr instanceof TableResponse) {
                                            TableResponse tableResponse = (TableResponse)attr;
                                            task=tableResponse.computeAttributesTask;
                                        } else if (attr instanceof ForkJoinTask) {
                                            task = (ForkJoinTask)attr;
                                        }
                                        if(task!=null) {
                                            try {
                                                task.cancel(true);
                                                task.quietlyComplete();

                                            }catch(Exception e) {

                                            }
                                        }
                                        req.session().removeAttribute(id);
                                    }
                                });
                            }
                        } catch(Exception e) {
                            e.printStackTrace();
                        }

                        if(Thread.currentThread().isInterrupted()) throw new CancellationException();


                        List<String> sessionIds = new ArrayList<>();
                        // add chart futures
                        AtomicInteger totalChartCnt = new AtomicInteger(0);
                        AtomicInteger totalTableCnt = new AtomicInteger(0);
                        List<String> chartTypes = new ArrayList<>();
                        List<TableResponse> tableResponses = new ArrayList<>();
                        if(abstractCharts.size()>0 && aggregations==null) {
                            System.out.println("WARNING:: Building charts but no aggregations were found!");
                        }
                        abstractCharts.forEach(chart -> {
                            boolean isTable = chart.isTable();
                            for (int i = 0; i < chart.getAttrNames().size(); i++) {
                                String attrName = chart.getAttrNames().get(i);
                                int attrStartIdx = chart.getName().replace("[]","").length()+1;
                                AbstractAttribute attribute = findAttribute(chart.getAttributes(),attrName,attrStartIdx);
                                if (attribute == null || attribute instanceof NestedAttribute) {
                                    continue;
                                }
                                AbstractAttribute groupByAttr = null;
                                String groupByAttrName = chart.getAttrNameToGroupByAttrNameMap().get(attrName);
                                if(groupByAttrName!=null) {
                                    groupByAttr = AggregationChart.findAttribute(chart.getGroupByAttributes(), groupByAttrName);
                                }
                                AbstractAttribute collectByAttr = null;
                                String collectByAttrName = chart.getAttrToCollectByAttrMap().get(attrName);
                                if(collectByAttrName!=null) {
                                    collectByAttr = AggregationChart.findAttribute(chart.getCollectByAttributes(), collectByAttrName);
                                }
                                String id;
                                RecursiveTask task;
                                if(isTable) {
                                    Collection<TableResponse> tableResponse = (Collection<TableResponse>)chart.create(req, attrName, attribute, groupByAttr, collectByAttr, aggregations);
                                    for(TableResponse tr : tableResponse) {
                                        id = "table-" + totalTableCnt.getAndIncrement();
                                        task = tr.computeAttributesTask;
                                        req.session(false).attribute(id, tr);
                                        tableResponses.add(tr);
                                        otherTasks.add(task);
                                        sessionIds.add(id);
                                    }
                                } else {
                                    task = new ChartTask(req, chart, attrName, attribute, groupByAttr, collectByAttr, aggregations);
                                    chartTypes.add(chart.getType());
                                    pool.execute(task);
                                    id = "chart-" + totalChartCnt.getAndIncrement();
                                    req.session(false).attribute(id, task);
                                    otherTasks.add(task);
                                    sessionIds.add(id);
                                }
                            }
                        });

                        req.session().attribute("previousSessionIds", sessionIds);

                        AtomicInteger tableCnt = new AtomicInteger(0);
                        AtomicInteger chartCnt = new AtomicInteger(0);
                        Tag chartTag = div().withClass("row").attr("style", "margin-bottom: 10px;").with(
                                span().withId("data-charts").withClass("collapse show").with(
                                        totalChartCnt.get() == 0 ? Collections.emptyList() : chartTypes.stream().map(type -> div().attr("style", "width: 80%; margin-left: 10%; margin-bottom: 30px;").withClass(type).withId("chart-" + chartCnt.getAndIncrement())).collect(Collectors.toList())
                                ).with(
                                        totalTableCnt.get() == 0 ? Collections.emptyList() : tableResponses.stream().map(table -> AggregatePivotChart.getTable(table,table.type,tableCnt.getAndIncrement())).collect(Collectors.toList())
                                )
                        );
                        Tag dataTable = div().withClass("row").attr("style", "margin-top: 10px;").with(
                                tableFromPatentList(tableHeaders, false, 0l)
                        );
                        long timeEnd = System.currentTimeMillis();
                        double timeSeconds = new Double(timeEnd - timeStart) / 1000;

                        ContainerTag results = div();
                        results = results.with(
                                div().withClass("col-12").with(
                                        br(),
                                        p("Showing "+tableData.size()+" results out of "+totalCount+" total results matched. Took " + timeSeconds + " seconds."),
                                        p("(Double click table to select a row)"),
                                        dataTable
                                ), div().withClass("col-12").with(
                                        br(),
                                        (abstractCharts.size()==0?br():
                                        p("Charts computed from "+totalCount+" matched results. Took " + timeSeconds + " seconds.")),
                                        p("(Right click on chart data for more options)"),
                                        chartTag
                                )
                        );
                        html = new Gson().toJson(new AjaxChartMessage(results.render(), totalChartCnt.get(), totalTableCnt.get()));
                    }

                    return html;
                } catch (Exception e) {
                    System.out.println(e.getClass().getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    return new Gson().toJson(new SimpleAjaxMessage("ERROR " + e.getClass().getName() + ": " + e.getMessage()));
                }
            }
        };

        pool.execute(handleReportTask);
        req.session().attribute("ongoingReportTask", handleReportTask);
        req.session().attribute("ongoingTasks", otherTasks);

        long maxTimeMillis =  180 * 1000;
        try {
            String html = handleReportTask.get(maxTimeMillis, TimeUnit.MILLISECONDS);
            return html;
        } catch(CancellationException|InterruptedException e) {
            System.out.println("Interupted!");
            return new Gson().toJson(new SimpleAjaxMessage("Report Canceled."));
        } catch (Exception e) {
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
                Integer assetCount =  (Integer) templateMap.get("asset_count");
                if(assetCount == null) { // TODO asset_count should never be null on new datasets... this is just to keep backwards compatibility
                    assetCount = DatasetIndex.get(username, file.getName()).size();
                    System.out.println("No asset count...");
                    Map<String,Object> prevMap = (Map<String,Object>) Database.tryLoadObject(file);
                    prevMap.put("asset_count", assetCount);
                    Database.trySaveObject(prevMap, file);
                    System.out.println("Saved new map: "+new Gson().toJson(prevMap));
                }
                template = new FormTemplate(file, username, name.toString(), assetCount);
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

    public static List<String> searchForIds(String username, String[] query, boolean shared) {
        List<String> ret = new ArrayList<>();
        if(query==null||query.length==0) return ret;
        File folder = new File(Constants.USER_DATASET_FOLDER+username+"/");
        if(!folder.exists()) folder.mkdirs();
        Pair<Map<String,Object>,List<FormTemplate>> directoryStructure = new Pair<>(new HashMap<>(),new ArrayList<>());
        Arrays.stream(folder.listFiles(file->!file.getName().endsWith("_updates"))).forEach(file->{
            Map<String,Object> templateMap = getMapFromFile(file, false);

            String[] parentDirs = (String[])templateMap.get("parentDirs");
            Pair<Map<String, Object>, List<FormTemplate>> currentDirectory = directoryStructure;
            StringJoiner toMatch = new StringJoiner("<<>>");
            if(shared) {
                toMatch.add("Shared Datasets");
            } else {
                toMatch.add("My Datasets");
            }
            if (parentDirs != null) { // build directory as necessary
                //System.out.println("Parent Dirs for "+file.getName()+": "+Arrays.toString(parentDirs));
                for (String dir : parentDirs) {
                    toMatch.add(dir);
                    currentDirectory.getFirst().putIfAbsent(dir, new Pair<>(new HashMap<>(), new ArrayList<>()));
                    currentDirectory = (Pair<Map<String, Object>, List<FormTemplate>>) currentDirectory.getFirst().get(dir);
                }
            }
            String name = (String)templateMap.get("name");
            if(name!=null) {
                toMatch.add(name);
            }
            String toMatchStr = toMatch.toString().toLowerCase();
            String queryStr = String.join("<<>>",query).toLowerCase();
            System.out.println("To Match: "+toMatchStr);
            System.out.println("Query: "+queryStr);
            if(toMatchStr.endsWith(queryStr)) {
                ret.add(file.getName());
                System.out.println("Found match! "+file.getName());
            }
        });

        return ret;
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
                                    if(template.getAssetCount() >= 0) {
                                        tag = tag.attr("data-assetcount", String.valueOf(template.getAssetCount()));
                                    }
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
            acclaimAttrs = Attributes.acclaimAttrs;
        } else {
            acclaimAttrs = Collections.emptyList();
        }
        String role = authorized ? req.session().attribute("role") : null;
        String userGroup = authorized ? getUserGroupFor(req.session()) : null;
        boolean showDynamicUserGroups = authorized&&((role.equals(INTERNAL_USER)||role.equals(SUPER_USER)));
        String dynamicUserGroup = showDynamicUserGroups ? req.session().attribute("dynamicUserGroup") : null;
        final boolean canUpdateUserGroup = role == null ? false : (role.equals(SUPER_USER)||role.equals(INTERNAL_USER));
        List<Tag> menuItems = new ArrayList<>();
        if(authorized && showDynamicUserGroups) {
            menuItems.add(
                    form().withAction(GLOBAL_PREFIX+"/change_dynamic_user_group").attr("onclick","$(this).find('select.single-select2').select2('open');").withClass("btn btn-outline-secondary").attr("style", "margin-bottom: 0px").withMethod("POST").with(
                            label("Change User Group").withId("change_user_group_label").with(br(),
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
            );
        }
        if(authorized) {
            menuItems.add(a("Sign Out").withClass("btn btn-outline-secondary").withHref(GLOBAL_PREFIX+"/logout"));
            if(canPotentiallyCreateUser(role)) {
                menuItems.add(a("Create User").withClass("btn btn-outline-secondary").withHref(CREATE_USER_URL));
            }
            if(role.equals(SUPER_USER)) {
                menuItems.add(a("Change User Group").withClass("btn btn-outline-secondary").withHref(EDIT_USER_GROUP_URL));
                menuItems.add(a("Remove Users").withClass("btn btn-outline-secondary").withHref(DELETE_USER_URL));
                menuItems.add(a("Update Password").withClass("btn btn-outline-secondary").withHref(OVERRIDE_PASSWORD_URL));
            }
            menuItems.add(a("Change Password").withClass("btn btn-outline-secondary").withHref(EDIT_USER_URL));

            menuItems.add(a("Help").withClass("btn btn-outline-secondary").withTarget("_blank").withHref("/help"));
            menuItems.add(a("Update Default Template").withClass("btn btn-outline-secondary").attr("title", "The current form will become the new default template.").withId("update-default-attributes-form").withHref("#"));
            menuItems.add(a("Reset Default Template").withClass("btn btn-outline-secondary").attr("title", "Resets the default template.").withHref(RESET_DEFAULT_TEMPLATE_URL));
            if(canUpdateUserGroup) {
                menuItems.add(label("For User Group?").withClass("btn btn-outline-secondary").attr("title", "Warning: Selecting this option will affect the default template for all users in this user group.").with(
                        input().withType("checkbox").attr("style", "margin-left: 10px").withValue("on").withId("extract_to_usergroup")
                ));
            }

        } else {
            menuItems.add(a("Log In").withClass("btn btn-outline-secondary").withHref(GLOBAL_PREFIX+"/"));
            menuItems.add(a("Contact Us").withClass("btn btn-outline-secondary").withTarget("_blank").withHref("http://www.gttgrp.com"));
        }

        return html().with(
                head().with(
                        title("PSP - GTT Group"),
                        link().withRel("icon").withType("image/png").attr("sizes", "32x32").withHref("/images/favicon-32x32.png"),
                        link().withRel("icon").withType("image/png").attr("sizes", "16x16").withHref("/images/favicon-16x16.png"),
                        script().withSrc("/js/jquery-3.3.1.min.js"),
                        script().withSrc("/js/jquery-ui-1.12.1.min.js"),
                        script().withText(
                                "/*** Handle jQuery plugin naming conflict between jQuery UI and Bootstrap ***/\n " +
                                "$.widget.bridge('uibutton', $.ui.button); " +
                                "$.widget.bridge('uitooltip', $.ui.tooltip); "),
                        script().withSrc("/js/popper.min.js"),
                        script().withSrc("/js/highcharts.js"),
                        script().withSrc("/js/exporting.js"),
                        script().withSrc("/js/drilldown.js"),
                        script().withSrc("/js/word_cloud.js"),
                        script().withSrc("/js/heatmap.js"),
                        script().withSrc("/js/offline-exporting.js"),
                        script().withSrc("/js/customEvents.js"),
                        script().withSrc("/js/jquery.dynatable.js"),
                        script().withSrc("/js/defaults.js"),
                        script().withSrc("/js/jquery.miniTip.js"),
                        script().withSrc("/js/jstree.min.js"),
                        script().withSrc("/js/jstree.misc.js"),
                        script().withSrc("/js/select2.min.js"),
                        script().withSrc("/js/bootstrap.min.js"),
                        script().withSrc("/js/tether.min.js"),
                        script().withSrc("/js/notify.min.js"),
                        link().withRel("stylesheet").withHref("/css/bootstrap.min.css"),
                        link().withRel("stylesheet").withHref("/css/select2.min.css"),
                        link().withRel("stylesheet").withHref("/css/defaults.css"),
                        link().withRel("stylesheet").withHref("/css/jquery.dynatable.css"),
                        link().withRel("stylesheet").withHref("/css/miniTip.css"),
                        link().withRel("stylesheet").withHref("/css/jstree.min.css"),
                        link().withRel("stylesheet").withHref("/css/jquery-ui.min.css"),
                        script().withSrc("/js/codemirror.js"),
                        link().withRel("stylesheet").withHref("/css/codemirror.css"),
                        script().withSrc("/js/javascript_mode.js"),
                        script().withSrc("/js/close_brackets.js"),
                        script().withSrc("/js/match_brackets.js"),
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
                                        textarea().withPlaceholder("Enter asset list separated by spaces or newlines (eg. US8234523B1 CA8323233A US2013335323A9 USRE032353B9 ).").withId("new-dataset-from-asset-list"),br(),
                                        button("Create").attr("style","cursor: pointer;").withClass("btn btn-sm btn-default").withId("new-dataset-from-asset-list-submit"),
                                        button("Cancel").attr("style","cursor: pointer;").withClass("btn btn-sm btn-default").withId("new-dataset-from-asset-list-cancel")
                                )
                        ), div().withId("create-assignee-datasets-overlay").with(
                                div().withId("create-assignee-datasets-inside").attr("style","background-color: lightgray; padding: 5px;").with(
                                        label("Assignee List").with(textarea().withClass("form-control").withPlaceholder("1 assignee per line.").withId("create-assignee-datasets")),br(),
                                        button("Create").attr("style","cursor: pointer;").withClass("btn btn-sm btn-default").withId("create-assignee-datasets-submit"),
                                        button("Cancel").attr("style","cursor: pointer;").withClass("btn btn-sm btn-default").withId("create-assignee-datasets-cancel")
                                )
                        ),
                        div().withId("k-for-clustering-overlay").with(
                                div().withId("k-for-clustering-inside").attr("style","background-color: lightgray; padding: 5px;").with(
                                        label("Number of Clusters").with(div().withText("(or leave blank to automatically find optimal number)"),input().withType("number").attr("min","2").withId("k-for-clustering")),br(),
                                        button("Cluster").attr("style","cursor: pointer;").withClass("btn btn-sm btn-default").withId("k-for-clustering-submit"),
                                        button("Cancel").attr("style","cursor: pointer;").withClass("btn btn-sm btn-default").withId("k-for-clustering-cancel")
                                )
                        ),div().withId("url-prefix").attr("style","display: none;").attr("prefix", PROTECTED_URL_PREFIX),
                        div().withClass("container-fluid text-center").attr("style","height: 100%; z-index: 1;").with(
                                div().withClass("row").attr("style","height: 100%;").with(
                                        nav().withClass("sidebar col-3").attr("style","z-index: 2; overflow-y: auto; height: 100%; position: fixed; padding-top: 75px;").with(
                                                div().withClass("row").with(
                                                        div().withClass("col-12").with(authorized ? div().withText("Signed in as "+req.session().attribute("username")+" ("+req.session().attribute("role")+").") : div().withText("Not signed in.")),
                                                        div().withClass("col-12").with(
                                                                createMenuIcon()
                                                        ),
                                                        div().withClass("col-12").withId("main-menu").attr("style","display: none;").with(
                                                                div().withClass("row").with(
                                                                        div().withClass("col-12 btn-group-vertical").with(
                                                                                menuItems
                                                                        )
                                                                )
                                                        )

                                                ), hr(),
                                                (!authorized) ? div() : div().with(
                                                        div().attr("style","display: none;").withId("numeric-attributes-list")
                                                        .attr("value", new Gson().toJson(getNumericAttributes().stream().collect(Collectors.toList()))),
                                                        ul().withClass("nav nav-tabs nav-fill").attr("role","tablist").with(
                                                                li().withClass("nav-item").attr("title", "Double click to apply template, or right click for more options.").with(
                                                                        a("Templates").withClass("nav-link active").attr("data-toggle", "tab")
                                                                                .attr("role","tab")
                                                                                .withHref("#templates-tree")
                                                                ),
                                                                li().withClass("nav-item").attr("title","Double click to apply dataset, or right click for more options.").with(
                                                                        a("Datasets").withClass("nav-link").attr("data-toggle", "tab")
                                                                                .attr("role","tab")
                                                                                .withHref("#datasets-tree")
                                                                )
                                                        ), br(),
                                                        (showTemplates ? div().withClass("tab-content").withId("sidebar-jstree-wrapper").attr("style","max-height: 50%; overflow-y: auto; text-align: left; display: none;").with(
                                                                div().withClass("tab-pane active").attr("role","tabpanel").withId("templates-tree").with(
                                                                        ul().with(
                                                                                getTemplatesForUser(PRESET_USER_GROUP,false,"Preset Templates",false),
                                                                                getTemplatesForUser(req.session().attribute("username"),true,"My Templates",false),
                                                                                getTemplatesForUser(userGroup,true, "Shared Templates",false)
                                                                        )

                                                                ),div().withClass("tab-pane").attr("role","tabpanel").withId("datasets-tree").with(
                                                                        ul().with(
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

    private static Tag innerFiltersAndSettings(Function<String,Boolean> userRoleFunction, Tag buttons) {
        return div().withClass("col-12").with(
                div().withClass("row").withId("all_tabs_row").with(
                        buttons,br(),
                        div().withClass("col-12 form-top").with(
                                ul().withClass("nav nav-tabs").attr("role","tablist").attr("style","border-bottom: none !important;").with(
                                        li().withClass("nav-item").with(
                                                a("Search Options").withId("data-tab-link").withClass("nav-link active").attr("data-toggle","tab").withHref("#data-tab").attr("role","tab")
                                        ), li().withClass("nav-item").with(
                                                a("Visualizations").withId("chart-tab-link").withClass("nav-link").attr("data-toggle","tab").withHref("#chart-tab").attr("role","tab")
                                        )
                                        , li().withClass("nav-item").with(
                                                a("Results").withId("results-link").withClass("nav-link").attr("data-toggle","tab").withHref("#results").attr("role","tab")
                                        )
                                )
                        ),
                        div().withClass("col-12").withId("main-container").attr("style", "position: fixed; overflow-y: auto; width: 75%; bottom: 0px; top: 0px; margin-top: 172px; padding-bottom: 5px;").with(
                                div().withClass("row tab-content").with(
                                        div().withClass("col-12 tab-pane fade show active").attr("role","tabpanel").withId("data-tab").with(
                                                div().withClass("row").attr("style", "padding-bottom: 250px;").with(
                                                        div().withClass("col-12 form-top").with(
                                                                ul().withClass("nav nav-tabs").attr("role","tablist").attr("style","border-bottom: none !important;").with(
                                                                        li().withClass("nav-item").with(
                                                                                a("Filters").withId("filters-link").withClass("nav-link active").attr("data-toggle","tab").withHref("#filtersForm").attr("role","tab")
                                                                        ), li().withClass("nav-item").with(
                                                                                a("Sort and Limit").withId("sort-and-limit-link").withClass("nav-link").attr("data-toggle","tab").withHref("#searchOptionsForm").attr("role","tab")
                                                                        ), li().withClass("nav-item").with(
                                                                                a("Attributes").withId("attributes-link").withClass("nav-link").attr("data-toggle","tab").withHref("#attributesForm").attr("role","tab")
                                                                        ), li().withClass("nav-item").with(
                                                                                a("Settings").withId("settings-link").withClass("nav-link").attr("data-toggle", "tab").withHref("#highlightForm").attr("role", "tab")
                                                                        )
                                                                )
                                                        ), div().withClass("col-12").with(
                                                                div().withClass("row tab-content").with(
                                                                        loaderTag(),
                                                                        div().withClass("col-12 tab-pane fade").withId("searchOptionsForm").attr("role","tabpanel").with(
                                                                                mainOptionsRow()
                                                                        ), div().withClass("col-12 tab-pane fade show active").withId("filtersForm").attr("role","tabpanel").with(
                                                                                customFormRow("filters", allFilters, userRoleFunction)
                                                                        ), div().withClass("col-12 tab-pane fade").withId("attributesForm").attr("role","tabpanel").with(
                                                                                customFormRow("attributes", allAttributes, userRoleFunction)
                                                                        ),
                                                                        div().withClass("col-12 tab-pane fade").withId("highlightForm").attr("role","tabpanel").with(
                                                                                div().withClass("collapsible-form row").with(
                                                                                        div().withClass("col-12").with(
                                                                                                h5("Settings")
                                                                                        ),
                                                                                        div().withClass("col-12 attributeElement").with(
                                                                                                label("Highlighting").attr("style","width: 100%;").with(
                                                                                                        input().withId("main-options-"+USE_HIGHLIGHTER_FIELD).withClass("form-control").withType("checkbox").attr("style","margin-top: 5px; margin-left: auto; width: 20px; margin-right: auto;").withValue("on").attr("checked","checked").withName(USE_HIGHLIGHTER_FIELD)
                                                                                                )
                                                                                        ), div().withClass("col-12 attributeElement").with(
                                                                                                label("Filter Nested Attributes").attr("style","width: 100%;").with(
                                                                                                        input().withId("main-options-"+FILTER_NESTED_OBJECTS_FIELD).withClass("form-control").withType("checkbox").attr("style","margin-top: 5px; margin-left: auto; width: 20px; margin-right: auto;").withValue("on").attr("checked","checked").withName(FILTER_NESTED_OBJECTS_FIELD)
                                                                                                )
                                                                                        ), div().withClass("col-12 attributeElement").with(
                                                                                                label("List Item Separator").attr("style","width: 100%;").with(
                                                                                                        input().withId("main-options-"+LIST_ITEM_SEPARATOR_FIELD).withClass("form-control").withType("text").attr("style","margin-top: 5px; margin-left: auto; width: 100px; margin-right: auto;").withPlaceholder("; ").withName(LIST_ITEM_SEPARATOR_FIELD)
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        ),buttons, br(), br(), br(), br()
                                                )
                                        ), div().withClass("col-12 tab-pane fade").withId("chart-tab").attr("role","tabpanel").with(
                                                div().withClass("row").attr("style", "padding-bottom: 250px;").with(
                                                        loaderTag(),
                                                        div().withClass("col-12").withId("chartsForm").with(
                                                                customFormRow("charts",allCharts, userRoleFunction)
                                                        ),buttons, br(), br(), br(), br()
                                                )
                                        ), div().withClass("col-12 tab-pane fade").attr("role","tabpanel").withId("results").with(
                                                div().withClass("row").attr("style", "padding-bottom: 250px;").with(
                                                        div().withClass("col-12 content").with(

                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static Tag loaderTag() {
        return div().withClass("loader col-12").attr("style","display: none;").with(
                img().attr("height","48").attr("width","48").withSrc("/images/loader48.gif")
        );
    }

    private static Tag candidateSetModelsForm(String role) {
        if(role==null) return null;
        Function<String,Boolean> userRoleFunction = roleToAttributeFunctionMap.getOrDefault(role,DEFAULT_ROLE_TO_ATTR_FUNCTION);
        Tag buttons =  div().withClass("col-10 offset-1").with(
                div().withClass("btn-group row").with(
                        div().withText("Generate Report").withClass("btn btn-outline-secondary div-button "+GENERATE_REPORTS_FORM_ID+"-button"),
                        div().withText("Download to CSV").withClass("btn btn-outline-secondary div-button download-to-excel-button")
                )
        );
        return div().withClass("row").attr("style","margin-left: 0px; margin-right: 0px; position: fixed; width: 75%; top: 0px; padding-top: 75px; height: 100%;").with(
                span().withId("main-content-id").withClass("collapse").with(
                        form().withAction(REPORT_URL).withMethod("post").attr("style","margin-bottom: 0px;").withId(GENERATE_REPORTS_FORM_ID).with(
                                input().withType("hidden").withName("onlyExcel").withId("only-excel-hidden-input"),
                                innerFiltersAndSettings(userRoleFunction,buttons)
                        )
                )
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
                                        h3("Patent Search Platform").withClass("collapsible-header")
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

    private static Tag customFormRow(String type, AbstractAttribute attribute, Function<String,Boolean> userRoleFunction) {
        String shortTitle = type.substring(0, 1).toUpperCase() + type.substring(1);
        String groupID = type + "-row";
        return span().with(
                toggleButton(groupID, shortTitle),
                span().withId(groupID).withClass("collapse show").with(
                        div().withClass("collapsible-form row").with(
                                div().withClass("col-12").with(
                                        div().withClass("attributeElement").with(
                                                attribute.getOptionsTag(userRoleFunction, false, null)
                                        )
                                )
                        )
                )
        );
    }

    public static List<String> allSortableAttributes() {
        return Stream.of(Stream.of(Constants.SCORE, Attributes.SIMILARITY, Attributes.AI_VALUE, Constants.RANDOM_SORT, Constants.NO_SORT, Attributes.LATEST_PORTFOLIO_SIZE, Attributes.LATEST_FAM_PORTFOLIO_SIZE, Attributes.REMAINING_LIFE),
                getAllTopLevelAttributes().stream()
                        .flatMap(attr->{
                            if(attr instanceof NestedAttribute) {
                                return ((NestedAttribute) attr).getAttributes().stream().filter(child->child.getName().endsWith(Constants.COUNT_SUFFIX));
                            } else return Stream.of(attr);
                        })
                        .filter(attr->attr.getName().endsWith(Constants.COUNT_SUFFIX)||attr.getFieldType().equals(AbstractFilter.FieldType.Date))
                        .map(AbstractAttribute::getFullName).sorted()).flatMap(stream->stream).collect(Collectors.toList());
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
                                        label("Export Limit").attr("style","width: 100%;").attr("title", "The maximum number of documents to include when creating Datasets or exporting to CSV. The maximum limit is 10,000. <br/> If the number of search results is greater than the Export Limit, the 'Sort By' and 'Sort Direction' fields determine which documents will be retrieved. <br/> Please note: The Export Limit does not limit the number of documents used in chart aggregation calculations.").with(
                                                br(),input().withId("main-options-"+LIMIT_FIELD).attr("style", "height: 28px !important;").withClass("form-control").withType("number").withValue("10").withName(LIMIT_FIELD)
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

        System.out.println("Starting to load base finder...");
        initialize();
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
            pool.execute(similarityEngine);
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


    public static void main(String[] args) throws Exception {
        loadStuff();
        Database.getConn().setAutoCommit(true);

        HelpPage.helpPage(false);

    }
}
