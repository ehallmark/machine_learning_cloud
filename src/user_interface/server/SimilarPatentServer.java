package user_interface.server;

import com.google.gson.Gson;
import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import elasticsearch.DataIngester;
import lombok.Getter;

import models.keyphrase_prediction.models.NewestModel;
import user_interface.ui_models.attributes.computable_attributes.OverallEvaluator;
import org.nd4j.linalg.api.ndarray.INDArray;
import spark.Session;
import user_interface.server.tools.PasswordHandler;
import user_interface.server.tools.SimpleAjaxMessage;
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.attributes.computable_attributes.*;
import user_interface.ui_models.attributes.computable_attributes.asset_graphs.BackwardCitationAttribute;
import user_interface.ui_models.attributes.computable_attributes.asset_graphs.RelatedAssetsAttribute;
import user_interface.ui_models.attributes.hidden_attributes.*;
import user_interface.ui_models.attributes.script_attributes.*;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import j2html.tags.ContainerTag;
import user_interface.server.tools.AjaxChartMessage;
import user_interface.ui_models.charts.*;
import user_interface.ui_models.engines.*;
import user_interface.ui_models.excel.ExcelHandler;
import user_interface.ui_models.templates.*;
import models.similarity_models.AbstractSimilarityModel;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import models.dl4j_neural_nets.tools.MyPreprocessor;
import j2html.tags.Tag;

import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import seeding.Constants;
import seeding.Database;
import models.classification_models.ClassificationAttr;
import models.classification_models.TechTaggerNormalizer;
import user_interface.ui_models.filters.*;
import user_interface.ui_models.portfolios.items.Item;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import user_interface.ui_models.portfolios.PortfolioList;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static j2html.TagCreator.*;
import static j2html.TagCreator.head;
import static spark.Spark.*;

/**
 * Created by ehallmark on 7/27/16.
 */
public class SimilarPatentServer {
    private static final boolean debug = false;
    static final String GENERATE_REPORTS_FORM_ID = "generate-reports-form";
    private static final String PROTECTED_URL_PREFIX = "/secure";
    public static final String EXCEL_SESSION = "excel_data";
    public static final String PATENTS_TO_SEARCH_FOR_FIELD = "patentsToSearchFor";
    public static final String LINE_CHART_MAX = "lineChartMax";
    public static final String LINE_CHART_MIN = "lineChartMin";
    public static final String ASSIGNEES_TO_SEARCH_FOR_FIELD = "assigneesToSearchFor";
    public static final String ATTRIBUTES_ARRAY_FIELD = "attributes[]";
    public static final String PRE_FILTER_ARRAY_FIELD = "attributes[]Nested_filter[]";
    public static final String LIMIT_FIELD = "limit";
    public static final String COMPARATOR_FIELD = "comparator";
    public static final String NOT_IMPLEMENTED_STRING = "This functionality is not yet implemented.";
    public static final String SORT_DIRECTION_FIELD = "sortDirection";
    public static final String CHARTS_GROUPED_BY_FIELD = "chartsGroupedBy";
    public static final String CHART_MODELS_ARRAY_FIELD = "chartModels[]";
    public static final String REPORT_URL = PROTECTED_URL_PREFIX+"/patent_recommendation_engine";
    public static final String HOME_URL = PROTECTED_URL_PREFIX+"/home";
    public static final String SAVE_TEMPLATE_URL = PROTECTED_URL_PREFIX+"/save_template";
    public static final String DOWNLOAD_URL = PROTECTED_URL_PREFIX+"/excel_generation";
    public static final String DELETE_TEMPLATE_URL = PROTECTED_URL_PREFIX+"/delete_template";
    public static final String SHOW_DATATABLE_URL = PROTECTED_URL_PREFIX+"/dataTable.json";
    public static final String SHOW_CHART_URL = PROTECTED_URL_PREFIX+"/charts";
    public static final String RANDOM_TOKEN = "<><><>";
    public static final String SUPER_USER = "form_creator";
    public static final String ANALYST_USER = "analyst";
    public static final String INTERNAL_USER = "internal";
    public static final List<String> USER_ROLES = Arrays.asList(ANALYST_USER,INTERNAL_USER);
    private static AbstractSimilarityModel DEFAULT_SIMILARITY_MODEL;
    private static TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
    public static Map<String,AbstractSimilarityModel> similarityModelMap = new HashMap<>();
    public static SimilarityEngineController similarityEngine;
    public static Map<String,AbstractFilter> preFilterModelMap = new HashMap<>();
    public static Map<String,AbstractAttribute> attributesMap = new HashMap<>();
    private static Map<String,ChartAttribute> chartModelMap = new HashMap<>();
    private static Map<String,Function<String,Boolean>> roleToAttributeFunctionMap = new HashMap<>();
    private static final Function<String,Boolean> DEFAULT_ROLE_TO_ATTR_FUNCTION = (str) -> false;
    private static final String PLATFORM_STARTER_IP_ADDRESS = "104.196.199.81";
    private static NestedAttribute allAttributes;
    private static AbstractNestedFilter allFilters;
    private static NestedAttribute allCharts;

    static {
        roleToAttributeFunctionMap.put(ANALYST_USER, str -> !str.startsWith("gather"));
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
            humanAttrToJavaAttrMap.put("Asset Number", Constants.NAME);
            humanAttrToJavaAttrMap.put("Similarity", Constants.SIMILARITY);
            humanAttrToJavaAttrMap.put("Technology Similarity", Constants.TECHNOLOGY_SIMILARITY);
            humanAttrToJavaAttrMap.put("Assignee Similarity", Constants.ASSIGNEE_SIMILARITY);
            humanAttrToJavaAttrMap.put("Asset Similarity", Constants.PATENT_SIMILARITY);
            humanAttrToJavaAttrMap.put("Total Asset Count", Constants.TOTAL_ASSET_COUNT);
            humanAttrToJavaAttrMap.put("Assignee Name", Constants.ASSIGNEE);
            humanAttrToJavaAttrMap.put("Invention Title", Constants.INVENTION_TITLE);
            humanAttrToJavaAttrMap.put("AI Value", Constants.AI_VALUE);
            humanAttrToJavaAttrMap.put("Reinstated", Constants.REINSTATED);
            humanAttrToJavaAttrMap.put("Result Type", Constants.DOC_TYPE);
            humanAttrToJavaAttrMap.put("Expired", Constants.EXPIRED);
            humanAttrToJavaAttrMap.put("Technology", Constants.COMPDB_TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Deal ID", Constants.COMPDB_DEAL_ID);
            humanAttrToJavaAttrMap.put("GTT Group Technology", Constants.TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Assignee Entity Type", Constants.ASSIGNEE_ENTITY_TYPE);
            humanAttrToJavaAttrMap.put("Assignee Divestments (CompDB)", Constants.COMPDB_ASSETS_SOLD);
            humanAttrToJavaAttrMap.put("Assignee Acquisitions (CompDB)", Constants.COMPDB_ASSETS_PURCHASED);
            humanAttrToJavaAttrMap.put("Portfolio Size", Constants.PORTFOLIO_SIZE);
            humanAttrToJavaAttrMap.put("Patents",PortfolioList.Type.patents.toString());
            humanAttrToJavaAttrMap.put("Applications",PortfolioList.Type.applications.toString());
            humanAttrToJavaAttrMap.put("Pie Chart", Constants.PIE_CHART);
            humanAttrToJavaAttrMap.put("Cited Date", Constants.CITED_DATE);
            humanAttrToJavaAttrMap.put("Asset Referenced By", Constants.BACKWARD_CITATION);
            humanAttrToJavaAttrMap.put("Means Present", Constants.MEANS_PRESENT);
            humanAttrToJavaAttrMap.put("Gather", Constants.GATHER);
            humanAttrToJavaAttrMap.put("Stage Complete", Constants.GATHER_STAGE);
            humanAttrToJavaAttrMap.put("Gather Technology", Constants.GATHER_TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Patent Rating", Constants.GATHER_VALUE);
            humanAttrToJavaAttrMap.put("Relation Type", Constants.RELATION_TYPE);
            humanAttrToJavaAttrMap.put("Filing Name", Constants.FILING_NAME);
            humanAttrToJavaAttrMap.put("CompDB", Constants.COMPDB);
            humanAttrToJavaAttrMap.put("Granted", Constants.GRANTED);
            humanAttrToJavaAttrMap.put("Filing Date", Constants.FILING_DATE);
            humanAttrToJavaAttrMap.put("Histogram",Constants.HISTOGRAM);
            humanAttrToJavaAttrMap.put("Assignee Role", Constants.ASSIGNEE_ROLE);
            humanAttrToJavaAttrMap.put("WIPO Technology",Constants.WIPO_TECHNOLOGY);
            humanAttrToJavaAttrMap.put("Est. Remaining Life (Years)",Constants.REMAINING_LIFE);
            humanAttrToJavaAttrMap.put("Filing Country", Constants.FILING_COUNTRY);
            humanAttrToJavaAttrMap.put("Original Expiration Date", Constants.EXPIRATION_DATE);
            humanAttrToJavaAttrMap.put("Term Adjustments (Days)", Constants.PATENT_TERM_ADJUSTMENT);
            humanAttrToJavaAttrMap.put("CPC Codes", Constants.CPC_CODES);
            humanAttrToJavaAttrMap.put("Original Priority Date", Constants.PRIORITY_DATE);
            humanAttrToJavaAttrMap.put("Recorded Date", Constants.RECORDED_DATE);
            humanAttrToJavaAttrMap.put("Publication Date", Constants.PUBLICATION_DATE);
            humanAttrToJavaAttrMap.put("Timeline Chart", Constants.LINE_CHART);
            humanAttrToJavaAttrMap.put("Reel Frames", Constants.REEL_FRAME);
            humanAttrToJavaAttrMap.put("Include All", AbstractFilter.FilterType.Include.toString());
            humanAttrToJavaAttrMap.put("Include By Prefix", AbstractFilter.FilterType.PrefixInclude.toString());
            humanAttrToJavaAttrMap.put("Exclude By Prefix", AbstractFilter.FilterType.PrefixExclude.toString());
            humanAttrToJavaAttrMap.put("Exclude All", AbstractFilter.FilterType.Exclude.toString());
            humanAttrToJavaAttrMap.put("Advanced Keyword Filter", AbstractFilter.FilterType.AdvancedKeyword.toString());
            humanAttrToJavaAttrMap.put("Include", AbstractFilter.FilterType.BoolTrue.toString());
            humanAttrToJavaAttrMap.put("Exclude", AbstractFilter.FilterType.BoolFalse.toString());
            humanAttrToJavaAttrMap.put("Between Filter", AbstractFilter.FilterType.Between.toString());
            humanAttrToJavaAttrMap.put("Greater Than Filter", AbstractFilter.FilterType.GreaterThan.toString());
            humanAttrToJavaAttrMap.put("Less Than Filter", AbstractFilter.FilterType.LessThan.toString());
            humanAttrToJavaAttrMap.put("Filters", AbstractFilter.FilterType.Nested.toString());
            humanAttrToJavaAttrMap.put("Execution Date", Constants.EXECUTION_DATE);
            humanAttrToJavaAttrMap.put("First Name", Constants.FIRST_NAME);
            humanAttrToJavaAttrMap.put("Number of Assignments", Constants.NUM_ASSIGNMENTS);
            humanAttrToJavaAttrMap.put("Number of Related Docs", Constants.NUM_RELATED_ASSETS);
            humanAttrToJavaAttrMap.put("Number of Backward Citations", Constants.NUM_BACKWARD_CITATIONS);
            humanAttrToJavaAttrMap.put("Last Name", Constants.LAST_NAME);
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
            humanAttrToJavaAttrMap.put("Exists in CompDB", Constants.EXISTS_IN_COMPDB_FILTER);
            humanAttrToJavaAttrMap.put("Exists in Gather", Constants.EXISTS_IN_GATHER_FILTER);
            humanAttrToJavaAttrMap.put("Related Docs", Constants.ALL_RELATED_ASSETS);
            // nested attrs
            humanAttrToJavaAttrMap.put("Latest Assignee", Constants.LATEST_ASSIGNEE);
            humanAttrToJavaAttrMap.put("Original Assignee", Constants.ASSIGNEES);
            humanAttrToJavaAttrMap.put("Applicants", Constants.APPLICANTS);
            humanAttrToJavaAttrMap.put("Assignors", Constants.ASSIGNORS);
            humanAttrToJavaAttrMap.put("Inventors", Constants.INVENTORS);
            humanAttrToJavaAttrMap.put("Agents", Constants.AGENTS);
            humanAttrToJavaAttrMap.put("Forward Citations", Constants.CITATIONS);
            humanAttrToJavaAttrMap.put("Claims", Constants.CLAIMS);
            humanAttrToJavaAttrMap.put("Prior Related Docs", Constants.PATENT_FAMILY);
            humanAttrToJavaAttrMap.put("Assignments", Constants.ASSIGNMENTS);

            buildJavaToHumanAttrMap();

        }
    }

    private static void buildJavaToHumanAttrMap() {
        // inverted version to get human readables back
        javaAttrToHumanAttrMap = new HashMap<>();
        humanAttrToJavaAttrMap.forEach((k, v) -> javaAttrToHumanAttrMap.put(v, k));
    }

    private static Map<String,NestedAttribute> nestedAttrMap;
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
        loadAttributes(loadHidden);
        if(!onlyAttributes) {
            loadFilterModels();
            loadChartModels();
            loadSimilarityModels();
        }
    }

    public static void loadChartModels() {
        chartModelMap.put(Constants.PIE_CHART, new AbstractDistributionChart());
        chartModelMap.put(Constants.HISTOGRAM, new AbstractHistogramChart());
        chartModelMap.put(Constants.LINE_CHART, new AbstractLineChart());

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
                preFilterModelMap.put(Constants.EXISTS_IN_COMPDB_FILTER, new ExistsInCompDBFilter());
                preFilterModelMap.put(Constants.EXISTS_IN_GATHER_FILTER, new ExistsInGatherFilter());
                buildJavaToHumanAttrMap();
                List<AbstractAttribute> nestedAttributes = new ArrayList<>(allAttributes.getAttributes());
                nestedAttributes.addAll(similarityEngine.getEngineMap().values().stream().map(engine->(AbstractAttribute)engine).collect(Collectors.toList()));
                NestedAttribute attributeWithSimilarity = new NestedAttribute(nestedAttributes,false) {
                    @Override
                    public String getName() {
                        return allAttributes.getName();
                    }
                };
                allFilters = new AbstractNestedFilter(attributeWithSimilarity,false, new ExistsInCompDBFilter(), new ExistsInGatherFilter());
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void filterNameHelper(AbstractFilter filter) {
        String filterHumanName = AbstractFilter.isPrefix(filter.getFilterType()) ? humanAttributeFor(filter.getFilterType().toString()) + " " + humanAttributeFor(filter.getPrerequisite()) + " Filter" : humanAttributeFor(filter.getPrerequisite())+ " " + humanAttributeFor(filter.getFilterType().toString());
        System.out.println("Name for "+filter.getName()+": "+filterHumanName);
        while(humanAttrToJavaAttrMap.containsKey(filterHumanName)) {
            // already exists
            filterHumanName = filterHumanName+RANDOM_TOKEN;
        }
        humanAttrToJavaAttrMap.put(filterHumanName, filter.getName());
        if(filter instanceof AbstractNestedFilter) {
            ((AbstractNestedFilter)filter).getFilters().forEach(_nestedFilter->{
                AbstractFilter nestedFilter = (AbstractFilter)_nestedFilter;
                filterNameHelper(nestedFilter);
            });
        }
    }

    public static void loadSimilarityModels() {
        if(similarityModelMap.isEmpty()) {
            if(DEFAULT_SIMILARITY_MODEL==null) DEFAULT_SIMILARITY_MODEL = new SimilarPatentFinder(Collections.emptyList());
            similarityModelMap.put(Constants.PARAGRAPH_VECTOR_MODEL, DEFAULT_SIMILARITY_MODEL);
        }
    }

    public static void loadAttributes(boolean loadHidden) {
        if(attributesMap.isEmpty()) {
            attributesMap.put(Constants.EXPIRED, new ExpiredAttribute());
            attributesMap.put(Constants.INVENTION_TITLE, new InventionTitleAttribute());
            attributesMap.put(Constants.TECHNOLOGY, TechnologyAttribute.getOrCreate(new NewestModel()));
            attributesMap.put(Constants.NAME, new AssetNumberAttribute());
            attributesMap.put(Constants.WIPO_TECHNOLOGY, new WIPOTechnologyAttribute());
            attributesMap.put(Constants.AI_VALUE, new OverallEvaluator(false));
            attributesMap.put(Constants.REMAINING_LIFE, new RemainingLifeAttribute());
            attributesMap.put(Constants.CPC_CODES, new CPCAttribute());
            attributesMap.put(Constants.EXPIRATION_DATE, new ExpirationDateAttribute());
            attributesMap.put(Constants.ESTIMATED_EXPIRATION_DATE, new CalculatedExpirationDateAttribute());
            attributesMap.put(Constants.ESTIMATED_PRIORITY_DATE, new CalculatedPriorityDateAttribute());
            attributesMap.put(Constants.PATENT_TERM_ADJUSTMENT, new PatentTermAdjustmentAttribute());
            attributesMap.put(Constants.SIMILARITY, new SimilarityAttribute());
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

            // nested attrs
            attributesMap.put(Constants.LATEST_ASSIGNEE, new LatestAssigneeNestedAttribute());
            attributesMap.put(Constants.ASSIGNEES, new AssigneesNestedAttribute());
            attributesMap.put(Constants.APPLICANTS, new ApplicantsNestedAttribute());
            attributesMap.put(Constants.INVENTORS, new InventorsNestedAttribute());
            attributesMap.put(Constants.AGENTS, new AgentsNestedAttribute());
            attributesMap.put(Constants.CITATIONS, new CitationsNestedAttribute());
            attributesMap.put(Constants.CLAIMS, new ClaimsNestedAttribute());
            attributesMap.put(Constants.PATENT_FAMILY, new RelatedDocumentsNestedAttribute());
            attributesMap.put(Constants.GATHER, new GatherNestedAttribute());
            attributesMap.put(Constants.COMPDB, new CompDBNestedAttribute());

            // include count
            Constants.NESTED_ATTRIBUTES.forEach(attr->{
                attributesMap.put(attr+Constants.COUNT_SUFFIX,new CountAttribute(attr + Constants.COUNT_SUFFIX));
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

            if(DEFAULT_SIMILARITY_MODEL==null) DEFAULT_SIMILARITY_MODEL = new SimilarPatentFinder(Collections.emptyList());
            // similarity engine
            similarityEngine = new SimilarityEngineController(Arrays.asList(new PatentSimilarityEngine(DEFAULT_SIMILARITY_MODEL), new AssigneeSimilarityEngine(DEFAULT_SIMILARITY_MODEL)));

            allTopLevelAttributes = new ArrayList<>(attributesMap.values());


            allAttributes = new NestedAttribute(allTopLevelAttributes,false) {
                @Override
                public String getName() {
                    return ATTRIBUTES_ARRAY_FIELD;
                }
            };
        }
    }


    public static void loadAndIngestAllItemsWithAttributes(Collection<ComputableAttribute<?>> attributes, Map<String,INDArray> lookupTable) {
        List<String> applications = new AssetToFilingMap().getApplicationDataMap().keySet().stream().collect(Collectors.toList());
        System.out.println("Num applications found: "+applications.size());
        handleItemsList(applications, attributes, PortfolioList.Type.applications,lookupTable);
        DataIngester.finishCurrentMongoBatch();
        List<String> patents = new AssetToFilingMap().getPatentDataMap().keySet().stream().collect(Collectors.toList());
        System.out.println("Num patents found: "+patents.size());
        handleItemsList(patents, attributes, PortfolioList.Type.patents,lookupTable);
    }

    public static Map<String,Float> vectorToElasticSearchObject(INDArray vector) {
        float[] data = vector.data().asFloat();
        Map<String, Float> obj = new HashMap<>();
        for (int i = 0; i < data.length; i++) {
            obj.put(String.valueOf(i), data[i]);
        }
        return obj;
    }

    public static void handleItemsList(List<String> inputs, Collection<ComputableAttribute<?>> attributes, PortfolioList.Type type, Map<String,INDArray> lookupTable) {
        Map<String,String> assetToFiling = type.equals(PortfolioList.Type.patents) ? new AssetToFilingMap().getPatentDataMap() : new AssetToFilingMap().getApplicationDataMap();
        AtomicInteger cnt = new AtomicInteger(0);
        inputs.parallelStream().forEach(label->{
            String filing = assetToFiling.get(label);
            // vec
            if(filing!=null) {
                Item item = new Item(label);
                attributes.forEach(model -> {
                    Object obj = ((ComputableAttribute)model).attributesFor(Arrays.asList(item.getName()), 1);
                    if(obj!=null) {
                        item.addData(model.getMongoDBName(),obj);
                    }
                });
                INDArray vec = lookupTable.get(filing);
                if(vec!=null) {
                    item.addData("vector_obj", vectorToElasticSearchObject(vec));
                }
                DataIngester.ingestItem(item,filing);
                if(debug) System.out.println("Item: "+item.getName());
            }

            if(cnt.getAndIncrement()%100000==99999) {
                System.out.println("Seen "+cnt.get());
            }
        });
    }

    private static boolean canCreateUser(String creatorRole, String childRole) {
        if(creatorRole==null||childRole==null) return false;
        if(creatorRole.equals(ANALYST_USER) && childRole.equals(ANALYST_USER)) return true;
        if(creatorRole.equals(INTERNAL_USER) && (childRole.equals(ANALYST_USER) || childRole.equals(INTERNAL_USER))) return true;
        if(creatorRole.equals(SUPER_USER)) return true;
        return false;
    }

    private static Stream<Collection<?>> chunked(List<?> items, int batchSize) {
        int numItems = items.size();
        int numBatches = numItems / batchSize;
        if(numItems % numBatches > 0) {
            // remaining
            numBatches++;
        }
        return IntStream.range(0,numBatches).parallel().mapToObj(i->{
            int startIdx = i * batchSize;
            int endIdx = Math.min(numItems,startIdx + batchSize);
            return items.subList(startIdx,endIdx);
        });
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
        staticFiles.externalLocation("/home/ehallmark1122/machine_learning_cloud/public");

        PasswordHandler passwordHandler = new PasswordHandler();

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
            res.status(200);
            res.redirect(HOME_URL);
            return null;
        });

        get("/logout", (req,res)->{
            req.session(true).attribute("authorized",false);
            req.session().removeAttribute("role");
            req.session().removeAttribute("username");
            res.redirect("/");
            res.status(200);
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
                    ), br(), br(), button("Create User").withClass("btn btn-secondary")
            );
            return templateWrapper(true, req, res, form);
        });

        get("/", (req, res)->{
            return templateWrapper(false, req, res, form().withClass("form-group").withMethod("POST").withAction("/login").attr("style","margin-top: 100px;").with(
                    p("Log in"),
                    label("Username").with(
                            input().withType("text").withClass("form-control").withName("username")
                    ), br(), br(), label("Password").with(
                            input().withType("password").withClass("form-control").withName("password")
                    ), br(), br(), button("Login").withType("submit").withClass("btn btn-secondary")
            ));
        });

        get(HOME_URL, (req, res) -> {
            if(softAuthorize(req,res)) {
                return templateWrapper(true, req, res, candidateSetModelsForm(req.session().attribute("role")));
            } else {
                return null;
            }
        });

        post(REPORT_URL, (req, res) -> {
            authorize(req,res);
            return handleReport(req,res);
        });

        post(DOWNLOAD_URL, (req, res) -> {
            authorize(req,res);
            return handleExcel(req,res);
        });

        post(SAVE_TEMPLATE_URL, (req, res) -> {
            authorize(req,res);
            return handleSaveForm(req,res);
        });

        post(DELETE_TEMPLATE_URL, (req, res) -> {
            authorize(req,res);
            return handleDeleteForm(req,res);
        });

        get(SHOW_DATATABLE_URL, (req, res) -> {
            authorize(req,res);
            return handleDataTable(req,res);
        });

        post(SHOW_CHART_URL, (req, res) -> {
            authorize(req,res);
            return handleCharts(req,res);
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
    }

    private static Object handleExcel(Request req, Response res) {
        try {
            System.out.println("Received excel request");
            long t0 = System.currentTimeMillis();
            HttpServletResponse raw = res.raw();
            Map<String,Object> map = req.session(false).attribute(EXCEL_SESSION);
            if(map==null) return null;
            List<String> headers = (List<String>)map.getOrDefault("headers",Collections.emptyList());
            System.out.println("Number of excel headers: "+headers.size());
            List<Map<String,String>> data = (List<Map<String,String>>)map.getOrDefault("rows",Collections.emptyList());
            res.header("Content-Disposition", "attachment; filename=download.xls");
            res.type("application/force-download");
            ExcelHandler.writeDefaultSpreadSheetToRaw(raw, "Data", "Data", data,  headers);
            long t1 = System.currentTimeMillis();
            System.out.println("Time to create excel sheet: "+(t1-t0)/1000+ " seconds");
            return raw;
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            return new Gson().toJson(new AjaxChartMessage("ERROR "+e.getClass().getName()+": " + e.getMessage(), 0));
        }
    }

    private static Object handleDataTable(Request req, Response res) {
        System.out.println("Received data table request.....");
        Map<String,Object> response = new HashMap<>();
        try {
            int perPage = extractInt(req,"perPage",10);
            int page = extractInt(req, "page", 1);
            int offset = extractInt(req,"offset",0);
            System.out.println("Received datatable request");
            Map<String,Object> map = req.session(false).attribute(EXCEL_SESSION);
            if(map==null) return null;
            List<String> headers = (List<String>)map.getOrDefault("headers",Collections.emptyList());
            System.out.println("Number of headers: "+headers.size());
            List<Map<String,String>> data = (List<Map<String,String>>)map.getOrDefault("rows",Collections.emptyList());
            long totalCount = data.size();
            // check for search
            List<Map<String,String>> queriedData;
            String searchStr;
            if(req.queryMap("queries")!=null&&req.queryMap("queries").hasKey("search")) {
                String previousSearch = req.session().attribute("previousSearch");
                searchStr = req.queryMap("queries").value("search").toLowerCase();
                if(searchStr==null||searchStr.trim().isEmpty()) {
                    queriedData = data;
                } else if(previousSearch!=null&&previousSearch.toLowerCase().equals(searchStr.toLowerCase())) {
                    queriedData = req.session().attribute("queriedData");

                } else {
                    queriedData = new ArrayList<>(data.stream().filter(m -> m.values().stream().anyMatch(val -> val.toLowerCase().contains(searchStr))).collect(Collectors.toList()));
                    req.session().attribute("previousSearch",searchStr);
                    req.session().attribute("queriedData", queriedData);
                }
            } else {
                searchStr = "";
                queriedData = data;
            }
            long queriedCount = queriedData.size();
            // check for sorting
            String previousSort = req.session().attribute("previousSort");
            if(req.queryMap("sorts")!=null) {
                req.queryMap("sorts").toMap().forEach((k,v)->{
                    if(v==null||k==null) return;
                    String sortStr = k+String.join("",v)+searchStr;
                    if(previousSort==null||!sortStr.equals(previousSort)) {
                        Comparator<Map<String, String>> comp = (d1, d2) -> {
                            try {
                                return Double.valueOf(d1.get(k)).compareTo(Double.valueOf(d2.get(k)));
                            } catch (Exception nfe) {
                                return d1.get(k).compareTo(d2.get(k));
                            }
                        };
                        if (v.length > 0 && v[0].equals("-1")) {
                            comp = comp.reversed();
                        }
                        queriedData.sort(comp);
                    }
                    req.session().attribute("previousSort",sortStr);
                });
            }
            List<Map<String,String>> dataPage;
            if(offset < totalCount) {
                dataPage = queriedData.subList(offset, Math.min(queriedData.size(), offset + perPage));
            } else {
                dataPage = Collections.emptyList();
            }
            response.put("totalRecordCount",totalCount);
            response.put("queryRecordCount",queriedCount);
            response.put("records", dataPage);
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
                ret.put("chartId", "chart-"+chartNum);
                return new JsonRenderer().toJson(ret);
            } else {
                return new Gson().toJson(new SimpleAjaxMessage("Unable to create chart"));
            }
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            return new Gson().toJson(new SimpleAjaxMessage("ERROR "+e.getClass().getName()+": " + e.getMessage()));
        }
    }



    private static Object handleSaveForm(Request req, Response res) {
        String attributesMap = req.queryParams("attributesMap");
        String searchOptionsMap = req.queryParams("searchOptionsMap");
        String filtersMap = req.queryParams("filtersMap");
        String chartsMap = req.queryParams("chartsMap");
        String name = req.queryParams("name");
        String message;
        Random random = new Random(System.currentTimeMillis());
        Map<String,Object> responseMap = new HashMap<>();
        if(attributesMap!=null&&searchOptionsMap!=null&&chartsMap!=null&&filtersMap!=null&&name!=null&&name.length()>0) {
            System.out.println("Form "+name+" attributes: "+attributesMap);
            Map<String,String> formMap = new HashMap<>();
            formMap.put("name",name);
            formMap.put("attributesMap",attributesMap);
            formMap.put("searchOptionsMap",searchOptionsMap);
            formMap.put("filtersMap",filtersMap);
            formMap.put("chartsMap",chartsMap);
            String username = req.session().attribute("username");
            if(username!=null&&username.length()>0) {
                String templateFolderStr = Constants.DATA_FOLDER+Constants.USER_TEMPLATE_FOLDER+username+"/";
                File templateFolder = new File(templateFolderStr);
                if(!templateFolder.exists()) templateFolder.mkdirs();
                File file = null;
                while(file == null || file.exists()) {
                    file = new File(templateFolderStr+Math.abs(random.nextInt()));
                }
                Database.trySaveObject(formMap,file);
                message = "Saved sucessfully.";
                responseMap.put("file",file.getName());
            } else {
                message = "Unable to find user.";
            }
            responseMap.putAll(formMap);
        } else {
            if(name==null||name.isEmpty()) {
                message = "Please enter a name for the new template.";
            } else {
                message = "Unable to create form. Data missing.";
            }
        }
        responseMap.put("message", message);
        return new Gson().toJson(responseMap);
    };

    private static Object handleDeleteForm(Request req, Response res) {
        String fileName = req.queryParams("path_to_remove");
        String message;
        if(fileName!=null && fileName.replaceAll("[^0-9]","").length() > 0) {
            fileName = fileName.replaceAll("[^0-9]","");
            try {
                String username = req.session().attribute("username");
                if(username==null||username.isEmpty()) {
                    message = "Unable to locate user.";
                } else {
                    File toDelete = new File(Constants.DATA_FOLDER+Constants.USER_TEMPLATE_FOLDER+username+"/"+fileName);
                    if(toDelete.exists() && toDelete.isFile()) {
                        toDelete.delete();
                        message = String.valueOf("Success: " + toDelete.delete());
                    } else {
                        message = "Unable to locate file.";
                    }
                }
            } catch(Exception e) {
                message = e.getMessage();
            }
        } else {
            message = "Did not provide path.";
        }
        return new Gson().toJson(new SimpleAjaxMessage(message));
    };

    private static Object handleReport(Request req, Response res) {
        try {
            ProcessBuilder ps = new ProcessBuilder("/bin/bash", "-c", "curl http://"+PLATFORM_STARTER_IP_ADDRESS+":8080/ping");
            ps.start();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("While pinging platform starter...");
        }
        try {
            System.out.println("Getting parameters...");
            System.out.println("Getting models...");
            long timeStart = System.currentTimeMillis();
            // Sorted by
            // Get Models to use
            List<String> attributes = extractArray(req, ATTRIBUTES_ARRAY_FIELD);
            List<String> nestedAttributes = getNestedAttrMap().keySet().stream().filter(attr->attributes.contains(attr)).flatMap(attr->extractArray(req, attr+"[]").stream()).collect(Collectors.toList());
            List<String> itemAttributes = Stream.of(attributes.stream().filter(attr->!getNestedAttrMap().containsKey(attr)),nestedAttributes.stream()).flatMap(stream->stream).collect(Collectors.toList());

            System.out.println("FOUND ATTRIBUTES: "+String.join("; ",itemAttributes));
            System.out.println("FOUND NESTED ATTRIBUTES: "+String.join("; ",nestedAttributes));
            List<String> chartModels = extractArray(req, CHART_MODELS_ARRAY_FIELD);

            similarityEngine.extractRelevantInformationFromParams(req);
            PortfolioList portfolioList = similarityEngine.getPortfolioList();

            List<String> tableHeaders = new ArrayList<>(itemAttributes);

            res.type("application/json");


            System.out.println("Rendering table...");
            List<Map<String,String>> tableData = new ArrayList<>(getTableRowData(portfolioList.getItemList(), tableHeaders));

            boolean onlyExcel = extractBool(req, "onlyExcel");
            String html;

            Map<String,Object> excelRequestMap = new HashMap<>();
            excelRequestMap.put("headers", tableHeaders);
            excelRequestMap.put("rows", tableData);
            req.session().attribute(EXCEL_SESSION, excelRequestMap);

            if(onlyExcel) {
                System.out.println("ONLY EXCEL:: Skipping chart building and html building...");
                Map<String,String> results = new HashMap<>();
                results.put("message","success");
                html = new Gson().toJson(results);
            } else {
                // add chart futures
                List<ChartAttribute> charts = chartModels.stream().map(chart->chartModelMap.get(chart).dup()).collect(Collectors.toList());
                charts.forEach(chart->chart.extractRelevantInformationFromParams(req));

                AtomicInteger totalChartCnt = new AtomicInteger(0);
                charts.forEach(chart->{
                    for(int i = 0; i < chart.getAttributes().size(); i++) {
                        final int idx = i;
                        RecursiveTask<List<? extends AbstractChart>> chartTask = new RecursiveTask<List<? extends AbstractChart>>() {
                            @Override
                            protected List<? extends AbstractChart> compute() {
                                return chart.create(portfolioList, idx);
                            }
                        };
                        chartTask.fork();
                        req.session().attribute("chart-" + totalChartCnt.getAndIncrement(), chartTask);
                    }
                });

                List<String> chartTypes = new ArrayList<>();
                charts.forEach(chart->{
                    for(int i = 0; i < chart.getAttributes().size(); i++) {
                        chartTypes.add(chart.getType());
                    }
                });

                AtomicInteger chartCnt = new AtomicInteger(0);
                Tag chartTag = totalChartCnt.get() == 0 ? div() : div().withClass("row").attr("style", "margin-bottom: 10px;").with(
                        h4("Charts").withClass("collapsible-header").attr("data-target", "#data-charts"),
                        span().withId("data-charts").withClass("collapse show").with(
                                chartTypes.stream().map(type -> div().attr("style", "width: 80%; margin-left: 10%; margin-bottom: 30px;").withClass(type).withId("chart-" + chartCnt.getAndIncrement())).collect(Collectors.toList())
                        )
                );
                Tag tableTag = portfolioList == null ? div() : div().withClass("row").attr("style", "margin-top: 10px;").with(
                        h4("Data").withClass("collapsible-header").attr("data-target", "#data-table"),
                        tableFromPatentList(Collections.emptyList(), tableHeaders)
                );
                long timeEnd = System.currentTimeMillis();
                double timeSeconds = new Double(timeEnd-timeStart)/1000;
                html = new Gson().toJson(new AjaxChartMessage(div().with(
                        p("Matched "+tableData.size()+" results in "+timeSeconds+" seconds."), br(),
                        chartTag, br(),
                        tableTag, br()

                ).render(), totalChartCnt.get()));
            }

            return html;
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            return new Gson().toJson(new AjaxChartMessage("ERROR "+e.getClass().getName()+": " + e.getMessage(), 0));
        }
    }


    static Tag tableFromPatentList(List<List<String>> data, List<String> attributes) {
        return span().withClass("collapse show").withId("data-table").with(
                form().withMethod("post").withTarget("_blank").withAction(DOWNLOAD_URL).with(
                        button("Download to Excel").withType("submit").withClass("btn btn-secondary div-button").attr("style","margin-left: 35%; margin-right: 35%; margin-bottom: 20px;")
                ),
                dataTableFromHeadersAndData(data,attributes)
        );
    }

    static Tag dataTableFromHeadersAndData(List<List<String>> data, List<String> attributes) {
        return table().withClass("table table-striped").attr("style","margin-left: 3%; margin-right: 3%; width: 94%;").with(
                thead().with(
                        tr().with(
                                attributes.stream().map(attr -> th(humanAttributeFor(attr)).attr("data-dynatable-column", attr)).collect(Collectors.toList())
                        )
                ), tbody().with(
                        dataTableBodyFromData(data,attributes)
                )
        );
    }

    static List<Tag> dataTableBodyFromData(List<List<String>> data, List<String> attributes) {
        return data.stream().map(results -> {
            return addAttributesToRow(tr().with(
                    results.stream().map(value -> td(value)).collect(Collectors.toList())
            ), results, attributes);
        }).collect(Collectors.toList());
    }

    static List<Map<String,String>> getTableRowData(Item[] items, List<String> attributes) {
        return Arrays.stream(items).map(item -> item.getDataAsMap(attributes)).collect(Collectors.toList());
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

    public static List<Tag> getTemplatesForUser(String username, boolean deletable) {
        if(username!=null && username.length()>0) {
            File folder = new File(Constants.DATA_FOLDER+Constants.USER_TEMPLATE_FOLDER+username+"/");
            if(!folder.exists()) folder.mkdirs();
            return Arrays.stream(folder.listFiles()).sorted(Comparator.comparingLong(file->file.lastModified())).map(file->{
                Map<String,Object> templateMap = (Map<String,Object>)Database.tryLoadObject(file);
                Object name = templateMap.get("name");
                Object searchObjectsMap = templateMap.get("searchOptionsMap");
                Object attributesMap = templateMap.get("attributesMap");
                Object chartsMap = templateMap.get("chartsMap");
                Object filtersMap = templateMap.get("filtersMap");
                if(name!=null&&searchObjectsMap!=null&&attributesMap!=null&&chartsMap!=null&&filtersMap!=null) {
                    FormTemplate template = new FormTemplate(name.toString(), searchObjectsMap.toString(), attributesMap.toString(), filtersMap.toString(), chartsMap.toString());
                    return templateHelper(template, deletable ? file : null);
                } else return null;
            }).filter(t->t!=null).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    static Tag templateWrapper(boolean authorized, Request req, Response res, Tag form) {
        res.type("text/html");
        String message = req.session().attribute("message");
        req.session().removeAttribute("message");
        return html().with(
                head().with(
                        title("AI Platform"),
                        script().withSrc("https://code.jquery.com/jquery-3.1.0.js"),
                        script().withSrc("https://code.jquery.com/ui/1.12.1/jquery-ui.js"),
                        script().withSrc("http://code.highcharts.com/stock/highstock.js"),
                        script().withSrc("http://code.highcharts.com/modules/exporting.js"),
                        script().withSrc("http://code.highcharts.com/modules/offline-exporting.js"),
                        script().withSrc("/js/customEvents.js"),
                        script().withSrc("/js/jquery.dynatable.js"),
                        script().withSrc("/js/defaults.js"),
                        script().withSrc("/js/jquery.miniTip.js"),
                        script().withSrc("https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.3/js/select2.min.js"),
                        script().withSrc("https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.6/js/bootstrap.min.js"),
                        script().withSrc("https://cdnjs.cloudflare.com/ajax/libs/tether/1.4.0/js/tether.min.js"),
                        link().withRel("stylesheet").withHref("https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.6/css/bootstrap.min.css"),
                        link().withRel("stylesheet").withHref("https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.3/css/select2.min.css"),
                        link().withRel("stylesheet").withHref("/css/defaults.css"),
                        link().withRel("stylesheet").withHref("/css/jquery.dynatable.css"),
                        link().withRel("stylesheet").withHref("/css/miniTip.css"),
                        link().withRel("stylesheet").withHref("http://code.jquery.com/ui/1.12.1/themes/base/jquery-ui.css"),
                        script().withText("function disableEnterKey(e){var key;if(window.event)key = window.event.keyCode;else key = e.which;return (key != 13);}")
                ),
                body().with(
                        div().withClass("container-fluid text-center").attr("style","height: 100%;").with(
                                div().withClass("row").attr("style","height: 100%;").with(
                                        nav().withClass("col-3 sidebar").attr("style","height: 100%; position: fixed; padding: 0px; padding-top: 75px;").with(
                                                (authorized ? a("Sign Out").attr("style","padding: 0;").withHref("/logout").withClass("nav-link") : a("Log In").withHref("/").withClass("nav-link").attr("style","padding: 0;")),
                                                (authorized ? a("Create User").attr("style","padding: 0;").withHref("/create_user").withClass("nav-link") : span()),
                                                (authorized ? p("Signed in as "+req.session().attribute("username")+". Role: "+req.session().attribute("role")+".") : p("Not signed in.")),
                                                h4("Saved Forms"),
                                                (!authorized) ? div() : ul().withClass("nav nav-pills flex-column").with(
                                                        div().with(
                                                                h5("Save Current Form"),
                                                                form().withAction(SAVE_TEMPLATE_URL).withId("save-template-form-id").withMethod("post").with(
                                                                        input().withType("hidden").withName("chartsMap").withId("chartsMap"),
                                                                        input().withType("hidden").withName("filtersMap").withId("filtersMap"),
                                                                        input().withType("hidden").withName("attributesMap").withId("attributesMap"),
                                                                        input().withType("hidden").withName("searchOptionsMap").withId("searchOptionsMap"),
                                                                        input().withType("text").withClass("form-control").attr("placeholder","Template Name").withName("name").withId("template_name").attr("style","width: 80%; margin-left: 10%; margin-right: 10%; display: inline-block; text-align: center;"),
                                                                        button().withType("submit").withText("Save").withClass("btn btn-secondary").withId("save-template-form-id-button")
                                                                )
                                                        ), div().attr("style","max-height: 50%; overflow-y: auto;").with(
                                                                h5("Default Forms"),
                                                                div().with(
                                                                        getTemplatesForUser(SUPER_USER,false)
                                                                ),
                                                                h5("My Forms"),
                                                                div().withId("my-templates").with(
                                                                        getTemplatesForUser(req.session().attribute("username"),true)
                                                                )
                                                        )
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

    public static Tag templateHelper(FormTemplate template, File file) {
        return li().withClass("nav-item").with(
                button(template.getName()).withClass("btn btn-secondary template-show-button").attr("style","width: "+(file==null?80:70)+"%;").attr("data-name",template.getName()).attr("data-chartsMap", template.getChartsMap())
                        .attr("data-attributesMap", template.getAttributesMap()).attr("data-filtersMap", template.getFiltersMap()).attr("data-searchOptionsMap", template.getSearchOptionsMap()),
                file==null?span():span("X").attr("data-action",DELETE_TEMPLATE_URL).attr("data-file",file.getName()).withClass("template-remove-button")
        );
    }


    public static Tag technologySelect(String name, Collection<String> orderedClassifications) {
        return technologySelectWithCustomClass(name, "multiselect", orderedClassifications);
    }

    public static Tag technologySelectWithCustomClass(String name, String clazz, Collection<String> orderedClassifications) {
        return select().attr("style","width:100%;").withName(name).withId(("multiselect-"+clazz+"-"+name).replaceAll("[\\[\\] ]","")).withClass(clazz).attr("multiple","multiple").with(
                orderedClassifications.stream().map(technology->{
                    return div().with(option(humanAttributeFor(technology)).withValue(technology));
                }).collect(Collectors.toList())
        );
    }

    public static Tag technologySelectWithCustomClass(String name, String clazz, Map<String,List<String>> orderedClassifications) {
        return select().attr("style","width:100%;").withName(name).withId(("multiselect-"+clazz+"-"+name).replaceAll("[\\[\\] ]","")).withClass(clazz).attr("multiple","multiple").with(
                orderedClassifications.entrySet().stream().map(e-> {
                    String optGroup = e.getKey();
                    return optgroup().attr("label",humanAttributeFor(optGroup)).attr("name",optGroup).with(
                            e.getValue().stream().map(technology->{
                                return div().with(option(humanAttributeFor(technology)).withValue(technology));
                            }).collect(Collectors.toList())
                    );
                }).collect(Collectors.toList())
        );
    }

    private static Tag candidateSetModelsForm(String role) {
        if(role==null) return null;
        Function<String,Boolean> userRoleFunction = roleToAttributeFunctionMap.getOrDefault(role,DEFAULT_ROLE_TO_ATTR_FUNCTION);
        return div().withClass("row").attr("style","margin-left: 0px; margin-right: 0px;").with(
                span().withId("main-content-id").withClass("collapse").with(
                        form().withAction(REPORT_URL).withMethod("post").attr("style","margin-bottom: 0px;").withId(GENERATE_REPORTS_FORM_ID).with(
                                input().withType("hidden").withName("onlyExcel").withId("only-excel-hidden-input"),
                                div().withClass("col-12").with(
                                        div().withClass("row").with(
                                                div().withClass("col-6 form-left form-top").withId("searchOptionsForm").with(
                                                        mainOptionsRow()
                                                ),div().withClass("col-6 form-right form-top").withId("chartsForm").with(
                                                        customFormRow("charts",allCharts, userRoleFunction)
                                                )
                                        ), div().withClass("row").with(
                                                div().withClass("col-6 form-left form-bottom").withId("attributesForm").with(
                                                        customFormRow("attributes", allAttributes, userRoleFunction)
                                                ),div().withClass("col-6 form-right form-bottom").withId("filtersForm").with(
                                                        customFormRow("filters", allFilters, userRoleFunction)
                                                )
                                        )
                                ),div().withClass("btn-group").attr("style","margin-left: 20%; margin-right: 20%;").with(
                                        div().withText("Generate Report").withClass("btn btn-secondary div-button").withId(GENERATE_REPORTS_FORM_ID+"-button"),
                                        div().withText("Download to Excel").withClass("btn btn-secondary div-button").withId("download-to-excel-button")
                                )
                        )
                ),
                div().withClass("col-12").withId("results").attr("style","padding-top: 20px;")
        );
    }


    private static Tag customFormHeader() {
        return div().withClass("row header-main header-top").with(
                div().withClass("col-3").attr("style","padding-top: 30px; border-right: 1px lightgray solid;").with(
                        a().attr("href", "/").with(
                                img().attr("style","display: block; margin-left: auto; margin-right: auto; margin-top: -20px")
                                        .attr("src", "/images/brand.png")
                        )
                ),div().withClass("col-9").attr("style","padding-top: 30px; background-color: #c4c4c4;").with(
                        div().withClass("row").with(
                                 div().withClass("col-12").with(
                                        h3("Artificial Intelligence Platform").withClass("collapsible-header")
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
                        h5(text).attr("style","margin-bottom: 15px;")
                )
        );
    }

    private static Tag customFormRow(String type, AbstractAttribute attribute, Function<String,Boolean> userRoleFunction) {
        String shortTitle = type.substring(0,1).toUpperCase()+type.substring(1);
        String groupID = type+"-row";
        return span().with(
                toggleButton(groupID, shortTitle),
                span().withId(groupID).with(
                        div().withClass("collapsible-form row").with(
                                div().withClass("col-12").with(
                                        div().withClass("attributeElement").with(
                                                attribute.getOptionsTag(userRoleFunction)
                                        )
                                )
                        )
                )
        );
    }

    public static Tag createAttributeElement(String modelName, String optGroup, String collapseId, Tag optionTag, boolean notImplemented, String description) {
        return div().attr("data-model",modelName).withClass("attributeElement draggable " + (notImplemented ? " not-implemented" : "")).with(
                div().attr("style","width: 100%;").attr("title", notImplemented ? NOT_IMPLEMENTED_STRING : description).withClass("collapsible-header").attr("data-target","#"+collapseId).with(
                        label(humanAttributeFor(modelName)).attr("opt-group",optGroup),
                        span().withClass("remove-button").withText("x")
                ), span().withClass("collapse show").withId(collapseId).with(optionTag)
        );
    }

    private static Tag mainOptionsRow() {
        return div().withClass("row").with(
                div().withClass("col-12").with(
                        h5("Search Options")
                ), div().withClass("col-12").with(
                        div().withClass("row collapsible-form").with(
                                div().withClass("col-12 attributeElement").with(
                                        label("Sort By"),br(),select().withId("main-options-"+COMPARATOR_FIELD).withClass("form-control single-select2").withName(COMPARATOR_FIELD).with(
                                                Stream.of(Stream.of(Constants.SIMILARITY, Constants.AI_VALUE, Constants.LATEST_ASSIGNEE+"."+Constants.PORTFOLIO_SIZE, Constants.REMAINING_LIFE, Constants.LATEST_ASSIGNEE+"."+Constants.COMPDB_ASSETS_PURCHASED, Constants.LATEST_ASSIGNEE+"."+Constants.COMPDB_ASSETS_SOLD),
                                                        getAllTopLevelAttributes().stream().filter(attr->attr.getFullName().endsWith(Constants.COUNT_SUFFIX)).map(attr->attr.getFullName())).flatMap(stream->stream)
                                                        .map(key->option(humanAttributeFor(key)).withValue(key)).collect(Collectors.toList())
                                        )
                                ),
                                div().withClass("col-6 attributeElement").with(
                                        label("Sort Direction"),br(),
                                        select().withId("main-options-"+SORT_DIRECTION_FIELD).withClass("form-control single-select2").withName(SORT_DIRECTION_FIELD).with(
                                                option("Ascending").withValue("asc"),
                                                option("Descending").withValue("desc").attr("selected","selected")
                                        )
                                ),
                                div().withClass("col-6 attributeElement").with(
                                        label("Result Limit"),br(),input().withId("main-options-"+LIMIT_FIELD).withClass("form-control").attr("style","height: 28px;").withType("number").withValue("10").withName(LIMIT_FIELD)
                                )
                        )
                )
        );
    }

    public static List<String> extractArray(Request req, String param) {
        try {
            String[] array = req.queryParamsValues(param);
            if (array != null) {
                List<String> list = Arrays.stream(array).map(str->str.replace("\r","").trim()).collect(Collectors.toList());
                return list;
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

    static boolean extractBool(Request req, String param) {
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

    public static void main(String[] args) throws Exception {
        boolean testFilterNames = false;
        if(testFilterNames) {
            loadAttributes(false);
            loadFilterModels();
            return;
        }
        long t1 = System.currentTimeMillis();
        //Database.setupSeedConn();
        boolean preLoad = true;
        boolean initDatabase = false;

        if(initDatabase) Database.initializeDatabase();

        System.out.println("Starting to load base finder...");
        initialize(false,false);
        System.out.println("Finished loading base finder.");
        System.out.println("Starting user_interface.server...");
        server();
        System.out.println("Finished starting user_interface.server.");
        if(preLoad)Database.preLoad();
        GatherClassificationServer.StartServer();
        long t2 = System.currentTimeMillis();
        System.out.println("Time to start user_interface.server: "+ ((t2-t1)/(1000*60)) + " minutes");
    }
}
