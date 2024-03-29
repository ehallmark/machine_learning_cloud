package seeding;


import org.nd4j.linalg.primitives.Pair;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;
import seeding.google.elasticsearch.Attributes;
import seeding.google.elasticsearch.attributes.*;
import user_interface.acclaim_compatibility.USParser;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.attributes.computable_attributes.GatherTechnologyAttribute;
import user_interface.ui_models.attributes.computable_attributes.TechnologyAttribute;
import user_interface.ui_models.attributes.computable_attributes.WIPOTechnologyAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Constants {
	public static final int DEFAULT_MAX_SYNONYMS = 10;
	public static final double DEFAULT_MIN_SIMILARITY = 0.5;
	public static final String CPC_CODE_AJAX_URL = "/data/cpc/02395328523985293085923dgkld0938592385902835902";
	public static final String ASSIGNEE_NAME_AJAX_URL = "/data/assignee/02395328523985293085923dgkld0938592385902835902";
	public static final String DATASET_NAME_AJAX_URL = "/data/dataset_name/02395328523985293085923dgkld0938592385902835902";
	public static final String NORMALIZED_ASSIGNEE_NAME_AJAX_URL = "/data/normalized_assignee/02395328523985293085923dgkld0938592385902835902";
	public static final String GTT_TECHNOLOGY_AJAX_URL = "/data/gtt_tech/02395328523985293085923dgkld0938592385902835902";
	public static final String GATHER = "gather";
	public static final String GATHER_VALUE = "gatherValue";
	public static final String GATHER_STAGE = "gatherStage";
	public static final String GATHER_TECHNOLOGY = "gatherTechnology";
	public static final String COMPDB = "compDB";
	public static final String SCORE = "score";
	public static final String MAINTENANCE_EVENT = "maintenanceEvent";
	public static final String COMPDB_TECHNOLOGY = "compDBTechnology";
	public static final String COMPDB_DEAL_ID = "compDBDealId";
	public static final String SMALL = "small";
	public static final String MICRO = "micro";
	public static final String DATASET_NAME = "datasetName";
	public static final String DATASET2_NAME = "dataset2Name";
	public static final String TEXT_SIMILARITY = "textSimilarity";
	public static final String LARGE = "large";
	public static final String MINIMUM_SHOULD_MATCH_SUFFIX = "_minShouldMatch";
	public static final String CPC_SIMILARITY = "cpcSimilarity";
	public static final String NORMALIZED_PORTFOLIO_SIZE = "normalizedPortfolioSize";
	public static final String REEL_NO = "reelNo";
	public static final String IS_HUMAN = "isHuman";
	public static final String CORRESPONDENT_ADDRESS = "correspondentAddress";
	public static final String NORMALIZED_LATEST_ASSIGNEE = "normalizedAssignee";
	public static final String USER_TEMPLATE_FOLDER = Constants.DATA_FOLDER+"userTemplates/";
	public static final String USER_DEFAULT_ATTRIBUTES_FOLDER =  Constants.DATA_FOLDER+"userDefaultAttributes/";
	public static final String USER_DATASET_FOLDER =  Constants.DATA_FOLDER+"userDatasets/";
	public static final String REINSTATED = "reinstated";
	public static final String FRAME_NO = "frameNo";
	public static final String BUYER = "buyer";
	public static final String SELLER = "seller";
	public static final String ADDRESS_1 = "addressOne";
	public static final String HIGHLIGHTED = "highlighted";
	public static final String BACKWARD_CITATION = "backwardCitation";
	public static final String ADDRESS_2 = "addressTwo";
	public static final String DATA_DOWNLOADERS_FOLDER = "dataDownloaders/";
	public static final String ADDRESS_3 = "addressThree";
	public static final String PURGE_INDICATOR = "purgeIndicator";
	public static final String CORRESPONDENT = "correspondent";
	public static final String CORRESPONDENT_ADDRESS_ID = "correspondentAddressId";
	public static final String APPLICATION_TYPE = "applicationType";
	public static final String APPLICATION_CONFIRMATION_NUM = "applicationConfirmationNumber";
	public static final String MIN_SIMILARITY_FIELD_ID = "minSimilarityFieldId";
	public static final String MAX_SYNONYMS_FIELD_ID = "maxSimilarityFieldId";
	public static final String APPLICANT_FILE_REFERENCE = "applicantFileReference";
	public static final String APPLICATION_STATUS = "applicationStatus";
	public static final String APPLICATION_STATUS_DATE = "applicationStatusDate";
	public static final String GRANT_NAME = "grantName";
	public static final String PAGE_COUNT = "pageCount";
	public static final String GRANTED = "granted";
	public static final String INACTIVE_DEAL = "inactiveDeal";
	public static final String ACQUISITION_DEAL = "acquisitionDeal";
	public static final String POSTAL_CODE = "postalCode";
	public static final String LAPSED = "lapsed";
	public static final String ASSIGNMENTS = "assignments";
	public static final String PUBLICATION_NAME = "publicationName";
	public static final String FULL_NAME = "fullName";
	public static final String REEL_FRAME = "reelFrame";
	public static final String EXECUTION_DATE = "executionDate";
	public static final String CONVEYANCE_TEXT = "conveyanceText";
	public static final String PATENT_DATA_FOLDER = "patentDataFolder/";
	public static final String APPLICATION_DATA_FOLDER = "applicationDataFolder/";
	public static final String CLAIM_STATEMENT = "claimStatement";
	public static final String CLAIM = "claim";
	public static final String CLAIM_NUM = "claimNum";
	public static final String PARENT_CLAIM_NUM = "parentClaimNum";
	public static final String RELATION_TYPE = "relationType";
	public static final String CLAIM_LENGTH = "claimLength";
	public static final String SMALLEST_INDEPENDENT_CLAIM_LENGTH = "smallestIndClaimLength";
	public static final String LAST_NAME = "lastName";
	public static final String FIRST_NAME = "firstName";
	public static final String CITY = "city";
	public static final String STATE = "state";
	public static final String ASSIGNEE_ROLE = "assigneeRole";
	public static final String PAGE_RANK_VALUE = "pageRankValue";
	public static final String ASSIGNEE = "assignee";
	public static final String MEANS_PRESENT = "meansPresent";
	public static final String CLAIM_VALUE = "claimValue";
	public static final String CLAIMS = "claims";
	public static final String ASSIGNEES = "assignees";
	public static final String INVENTORS = "inventors";
	public static final String AGENTS = "agents";
	public static final String APPLICANTS = "applicants";
	public static final String CITATIONS = "citations";
	public static final String DOC_TYPE = "docType";
	public static final String FILTER_SUFFIX = "_filter[]";
	public static final String LENGTH_OF_GRANT = "lengthOfGrant";
	public static final String LATEST_ASSIGNEE = "latestAssignee";
	public static final String RECORDED_DATE = "recordedDate";
	public static final String NAME = "name";
	public static final String FILING_NAME = "filingName";
	public static final String FILING_COUNTRY = "filingCountry";
	public static final String COUNT_SUFFIX = "_countSuffix";
	public static final String SIMILARITY = "similarityOld";
	public static final String SIMILARITY_FAST = "similarity";
	public static final String TOTAL_ASSET_COUNT = "totalAssetCount";
	public static final String AI_VALUE = "aiValue";
	public static final String COMPDB_ASSETS_PURCHASED = "compdbAssetsPurchased";
	public static final String COMPDB_ASSETS_SOLD = "compDBAssetsSold";
	public static final String EXPIRED = "expired";
	public static final String CITED_DATE = "citedDate";
	public static final String LINE_CHART = "lineChart[]";
	public static final String GROUPED_TABLE_CHART = "groupedTableChart[]";
	public static final String GROUPED_FUNCTION_TABLE_CHART = "groupedFunctionTableChart[]";
	public static final String PIVOT_COUNT_TABLE_CHART = "pivotCountTableChart[]";
	public static final String PIVOT_FUNCTION_TABLE_CHART = "pivotFunctionTableChart[]";
	public static final String COUNTRY = "country";
	public static final String CITATION_CATEGORY = "citationCategory";
	public static final String DOC_KIND = "docKind";
	public static final String NO_SORT = "noSort";
	public static final String RANDOM_SORT = "randomSort";
	public static final String PRIORITY_DATE = "priorityDate";
	public static final String PUBLICATION_DATE = "publicationDate";
	public static final String FILING_DATE = "filingDate";
	public static final String ABSTRACT = "abstract";
	public static final String CPC_CODES = "cpcCodes";
	public static final String NESTED_CPC_CODES = "nestedCpcCodes";
	public static final String CPC_SECTION = "cpcSection";
	public static final String CPC_CLASS = "cpcClass";
	public static final String CPC_SUBCLASS = "cpcSubclass";
	public static final String CPC_MAIN_GROUP = "cpcMainGroup";
	public static final String CPC_SUBGROUP = "cpcSubgroup";
	public static final String CPC_TITLE = "cpcTitle";
	public static final String ESTIMATED_EXPIRATION_DATE = "estimatedExpirationDate";
	public static final String ESTIMATED_PRIORITY_DATE = "estimatedPriorityDate";
	public static final String PARAGRAPH_VECTOR_MODEL = "paragraphVectorModel";
	public static final String TECHNOLOGY = "technology";
	public static final String EXPIRATION_DATE = "expirationDate";
	public static final String ASSIGNEE_ENTITY_TYPE = "assigneeEntityType";
	public static final String INVENTION_TITLE = "inventionTitle";
	public static final String PIE_CHART = "pieChart[]";
	public static final String HISTOGRAM = "histogram[]";
	public static final String HEAT_MAP = "heatmap[]";
	public static final String WORD_CLOUD = "wordcloud[]";
	public static final String ALL_RELATED_ASSETS = "allRelatedAssets";
	public static final String PORTFOLIO_SIZE = "portfolioSize";
	public static final String PATENT_TERM_ADJUSTMENT = "patentTermAdjustment";
	public static final String NUM_ASSIGNMENTS = REEL_FRAME+COUNT_SUFFIX;
	public static final String NUM_RELATED_ASSETS = ALL_RELATED_ASSETS+COUNT_SUFFIX;
	public static final String NUM_BACKWARD_CITATIONS = BACKWARD_CITATION+COUNT_SUFFIX;
	public static final String NUM_ASSETS = NAME+COUNT_SUFFIX;
	public static final String WIPO_TECHNOLOGY = "wipoTechnology";
	public static final String PATENT_SIMILARITY = "patentSimilarity";
	public static final String ASSIGNEE_SIMILARITY = "assigneeSimilarity";
	public static final String TECHNOLOGY_SIMILARITY = "technologySimilarity";
	public static final String REMAINING_LIFE = "remainingLife";
	public static final String PATENT_FAMILY = "patentFamily";
	public static final String ASSIGNORS = "assignors";
	public static final String ASSIGNOR = "assignor";
	public static final String DATA_FOLDER = "data/";
	public static final int MIN_WORDS_PER_SENTENCE = 3;

	public static String[] CLAIM_STOP_WORDS = new String[]{
			"andor","purpose","disclosure","problem","disclosed","disclose","solved","making","reproducing","reproduce","reproduction","reproduced","worn","tool","kind","driven","thereto","hereto","thereby","wherefore","whereby","therefore","quantity","without","like", "retorts", "engagement","transmitted","transmit","designated", "derived", "surrounding","conducting","acquired","acquire","generated","generate","obtained","article","comparing","compare","entitlement","blank","quick","establish","establishing","specified","specify","established","comparing","therethough","known","examples","example","involving","subject","following","embodiment","n","s","fig","performs","figure","drawing","claim","claims","performing","perform","performed","according","invention","belong","belongs","said","relates","describe","consists","comprises","comprise","consist","related","relating","describing","describes","description","include","includes","comprising","including","patent","consisting","a", "about", "above", "above", "across", "after", "afterwards", "again", "against", "all", "almost", "alone", "along", "already", "also", "although", "always", "am", "among", "amongst", "amoungst", "amount", "an", "and", "another", "any", "anyhow", "anyone", "anything", "anyway", "anywhere", "are", "around", "as", "at", "back", "be", "became", "because", "become", "becomes", "becoming", "been", "before", "beforehand", "behind", "being", "below", "beside", "besides", "between", "beyond", "bill", "both", "bottom", "but", "by", "call", "can", "cannot", "cant", "co", "con", "could", "couldnt", "cry", "de", "describe", "detail", "do", "done", "down", "due", "during", "each", "eg", "eight", "either", "eleven", "else", "elsewhere", "empty", "enough", "etc", "even", "ever", "every", "everyone", "everything", "everywhere", "except", "few", "fifteen", "fify", "fill", "find", "fire", "first", "five", "for", "former", "formerly", "forty", "found", "four", "from", "front", "full", "further", "get", "give", "go", "had", "has", "hasnt", "have", "he", "hence", "her", "here", "hereafter", "hereby", "herein", "hereupon", "hers", "herself", "him", "himself", "his", "how", "however", "hundred", "ie", "if", "in", "inc", "indeed", "interest", "into", "is", "it", "its", "itself", "keep", "last", "latter", "latterly", "least", "less", "ltd", "made", "many", "may", "me", "meanwhile", "might", "mill", "mine", "more", "moreover", "most", "mostly", "move", "much", "must", "my", "myself", "name", "namely", "neither", "never", "nevertheless", "next", "nine", "no", "nobody", "none", "noone", "nor", "not", "nothing", "now", "nowhere", "of", "off", "often", "on", "once", "one", "only", "onto", "or", "other", "others", "otherwise", "our", "ours", "ourselves", "out", "over", "own", "part", "per", "perhaps", "please", "put", "rather", "re", "same", "see", "seem", "seemed", "seeming", "seems", "serious", "several", "she", "should", "show", "side", "since", "sincere", "six", "sixty", "so", "some", "somehow", "someone", "something", "sometime", "sometimes", "somewhere", "still", "such", "system", "take", "ten", "than", "that", "the", "their", "them", "themselves", "then", "thence", "there", "thereafter", "thereby", "therefore", "therein", "thereupon", "these", "they", "thick", "thin", "third", "this", "those", "though", "three", "through", "throughout", "thru", "thus", "to", "together", "too", "top", "toward", "towards", "twelve", "twenty", "two", "un", "under", "until", "up", "upon", "us", "very", "via", "was", "we", "well", "were", "what", "whatever", "when", "whence", "whenever", "where", "whereafter", "whereas", "whereby", "wherein", "whereupon", "wherever", "whether", "which", "while", "whither", "who", "whoever", "whole", "whom", "whose", "why", "will", "with", "within", "without", "would", "yet", "you", "your", "yours", "yourself", "yourselves", "the","the","a","of","to","and","wherein","said","is","in","claim","methods","systems","first","an","second","for","at","one","from","or","with","method","comprising","least","by","on","device","that","data","according","system","as","plurality","each","signal","further","are","comprises","portion","which","having","information","between","surface","c","layer","unit","apparatus","image","includes","control","configured","more","being","be","end","member","r","based","when","group","value","has","light","user","set","than","output","including","position","element","time","means","selected","side","input","power","into","material","n","two","through","receiving","connected","third","storage","processing","associated","source","direction","formed","about","body","using","within","region","step","display","corresponding","coupled","determining","other","optical","part","process","current","assembly","number","section","predetermined","s","base","structure","line","forming","terminal","such","component","provided","provides","disposed","b","not","channel","area","module","response","frame","medium","frequency","non","state","so","received","claimed","program","i","support","housing","providing","level","operation","signals","o","lower","contact","different","generating","object","type","fluid","plate","substantially","if","d","access","form","node","axis","interface","controller","outer","message","extending","content","request","cell","over","flow","pressure","reference","thereof","metal","where","code","respective","upper","point","opening","mode","defined","consisting","via","h","wall","arranged","composition","amount","x","gate","elements","m","during","application","along","stored","receive","inner","phase","can","path","compound","target","edge","connection","digital","block","service","values","valve","relative","supply","function","address","readable","top","range","instructions","pattern","e","adjacent","pair","used","after","located","product","filter","field","portions","determined","shaft","whether","adapted","less","sequence","another","devices","may","length","mechanism","high","recording","detecting","t","storing","operating","containing","threshold","rate","steps","weight","packet","same","bottom","beam","main","p","lens","period","fourth","detection","media","head","front","have","positioned","substituted","color","machine","water","panel","determine","ring","bit","provide","station","upon","generate","recited","use","array","no","single","generated","distance","clock","connecting","mounted","yl","f","out","motor","ch","key","space","transfer","greater","test","angle","order","any","performing","face","client","guide","attached","sheet","active","cover","configuration","controlling","change","external","accordance","multiple","sub","include","parameter","identifying","opposite","present","respect","speed","sample","members","table","cells","low","hole","agent","respectively","comprise","movement","container","shape","up","condition","g","formula","size","y","tube","integrated","both","selecting","ratio","error","equal","identifier","shaped","link","internal","coupling","rotation","plane","center","components","while","l","lines","force","logic","all","width","defining","operable","setting","load","been","fixed","central","difference","result","three","k","id","applying","particular","event","performed","core","command","back"};

	public static final Set<String> CLAIM_STOP_WORD_SET = new HashSet<>(Arrays.asList(CLAIM_STOP_WORDS));
	public static final Set<String> STOP_WORD_SET = CLAIM_STOP_WORD_SET;

	public static final String GOOGLE_URL = "http://storage.googleapis.com/patents/grant_full_text";
	public static final String USPTO_URL = "https://bulkdata.uspto.gov/data/patent/grant/redbook/fulltext";
	public static final String GOOGLE_APP_URL = "http://storage.googleapis.com/patents/appl_full_text";
	public static final String USPTO_APP_URL = "https://bulkdata.uspto.gov/data/patent/application/redbook/fulltext";
	public static final UrlCreator GOOGLE_URL_CREATOR = defaultPatentUrlCreator(GOOGLE_URL);
	public static final UrlCreator USPTO_URL_CREATOR = defaultPatentUrlCreator(USPTO_URL);
	public static final UrlCreator GOOGLE_APP_URL_CREATOR = defaultAppUrlCreator(GOOGLE_APP_URL);
	public static final UrlCreator USPTO_APP_URL_CREATOR = defaultAppUrlCreator(USPTO_APP_URL);
	public static final UrlCreator PATENT_CPC_URL_CREATOR = maintenanceUrlCreator("https://bulkdata.uspto.gov/data/patent/classification/cpc/US_Grant_CPC_MCF_Text_");
	public static final UrlCreator APP_CPC_URL_CREATOR = maintenanceUrlCreator("https://bulkdata.uspto.gov/data/patent/classification/cpc/US_PGPub_CPC_MCF_Text_");
	public static final UrlCreator PAIR_BULK_URL_CREATOR = date -> "https://ped.uspto.gov/api/full-download?fileName=2000-2019-pairbulk-full-20210404-xml";

	public static final LocalDate DEFAULT_ASSIGNMENT_START_DATE = LocalDate.of(2016, Month.JANUARY, 1);
	public static final LocalDate DEFAULT_START_DATE = LocalDate.of(2005, Month.JANUARY, 1);
	public static final String USB_DRIVE_FOLDER = "/usb/";
	public static final String PATENT_ZIP_FOLDER = USB_DRIVE_FOLDER+Constants.DATA_FOLDER + "patents/";
	public static final String APP_ZIP_FOLDER = USB_DRIVE_FOLDER+Constants.DATA_FOLDER + "applications/";
	public static final String PTAB_ZIP_FOLDER = USB_DRIVE_FOLDER+Constants.DATA_FOLDER + "ptab/";
	public static final String ASSIGNMENT_ZIP_FOLDER = USB_DRIVE_FOLDER+Constants.DATA_FOLDER + "assignments/";

	private static UrlCreator defaultPatentUrlCreator(String baseUrl) {
		return defaultCreator(baseUrl, "ipg");
	}

	private static UrlCreator defaultAppUrlCreator(String baseUrl) {
		return defaultCreator(baseUrl, "ipa");
	}

	private static UrlCreator defaultCreator(String baseUrl, String prefix) {
		return date -> baseUrl + "/" + date.getYear() + "/" + prefix + date.toString().replace("-", "").substring(2) + ".zip";
	}

	public static final UrlCreator WIPO_TECHNOLOGY_URL_CREATOR = date -> {
		String baseUrl = "http://s3.amazonaws.com/data.patentsview.org/?/download/wipo.tsv.zip";
		String url = baseUrl.replace("?", date.format(DateTimeFormatter.BASIC_ISO_DATE));
		return url;
	};

	public static final UrlCreator MAINTENANCE_FEE_URL_CREATOR = date-> {
		String url = "https://bulkdata.uspto.gov/data/patent/maintenancefee/MaintFeeEvents.zip";
		return url;
	};


	private static UrlCreator maintenanceUrlCreator(String baseUrl) {
		return date -> {
			String dateStr = String.format("%04d", date.getYear()) + "-" + String.format("%02d", date.getMonthValue()) + "-" + String.format("%02d", date.getDayOfMonth());
			return baseUrl + dateStr + ".zip";
		};
	}

	public static final List<Class> SIMILARITY_ATTRIBUTE_CLASSES = Arrays.asList(
			GatherTechnologyAttribute.class,
			CompDBTechnologyAttribute.class,
			WIPOTechnologyAttribute.class,
			CPCTitleAttribute.class,
			CPCAttribute.class,
			TechnologyAttribute.class,
			ClaimTextAttribute.class,
			InventionTitleAttribute.class,
			AbstractTextAttribute.class,
			Claims.class,
			Abstract.class,
			InventionTitle.class,
			SimilarityAttribute.class,
			GttTechnology.class,
			GttTechnology2.class,
			Description.class
	);

	public static final String[] RELATED_DOC_TYPES = new String[]{
			"addition",
			"division",
			"continuation",
			"continuation-in-part",
			"continuing-reissue",
			"reissue",
			"us-divisional-reissue",
			"reexamination",
			"us-reexamination-reissue-merger",
			"substitution",
			"us-provisional-application",
			"utility-model-basis",
			"correction",
			"related-publication"
	};

	public static final List<String> RELATED_DOC_TYPE_LIST = Arrays.asList(RELATED_DOC_TYPES);

	public static final Collection<String> NESTED_ATTRIBUTES = Arrays.asList(
			ASSIGNEES,
			APPLICANTS,
			INVENTORS,
			AGENTS,
			CITATIONS,
			CLAIMS,
			PATENT_FAMILY,
			ASSIGNMENTS,
			NESTED_CPC_CODES
	);


	public static final String GATHER_IS = "is";
	public static final String GATHER_MA = "ma";
	public static final String GATHER_SA = "sa";
	public static final String GATHER_PC = "pc";
	public static final String GATHER_PH = "ph";
	public static final String GATHER_FC = "fc";
	public static final String GATHER_QC = "qc";

	public static final Collection<String> GATHER_STAGES = Arrays.asList(
			GATHER_IS,
			GATHER_MA,
			GATHER_SA,
			GATHER_PC,
			GATHER_PH,
			GATHER_FC,
			GATHER_QC
	);

	public static final String DOC_TYPE_INCLUDE_FILTER_STR = DOC_TYPE + AbstractFilter.FilterType.Include.toString() + FILTER_SUFFIX;

	public static final Map<String,String> ATTRIBUTE_DESCRIPTION_MAP = Collections.synchronizedMap(new HashMap<>());
	static {
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.CLAIMS, "full claim text.");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.LENGTH_OF_SMALLEST_IND_CLAIM, "number of words in the document's smallest independent claim.");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.LAPSED,"lapsed.");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.GRANTED,"granted.");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.EXPIRED,"expired.");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.REINSTATED,"reinstated.");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.PUBLICATION_DATE, "publication date of the asset.");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.FILING_DATE, "date of the patent filing.");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.EXPIRATION_DATE_ESTIMATED, "expiration date computed from the priority date, term length, and any available patent term adjustments (PTA). Warning: PTA information is missing for many patents, and the calculation does not include Terminal Disclaimers.");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.PRIORITY_DATE_ESTIMATED, "priority date computed from the original priority date and any available patent term adjustments (PTA). Warning: PTA information is missing for many patents, and the calculation does not include Terminal Disclaimers.");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.TREE, "cpc code (includes child classes)");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.AI_VALUE, "AI Value of the document. Ranges from 0-1. Developed by GTT Group.");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.CITATIONS+"."+Attributes.CITED_CATEGORY, "category of the citation. Available categories: (CH2 = Chapter 2; SUP = Supplementary search report; ISR = International search report; SEA = Search report; APP = Applicant; EXA = Examiner; OPP = Opposition; 115 = article 115; PRS = Pre-grant pre-search; APL = Appealed; FOP = Filed opposition)");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.RCITATIONS+"."+Attributes.RCITE_CATEGORY, "category of the citation. Available categories: (CH2 = Chapter 2; SUP = Supplementary search report; ISR = International search report; SEA = Search report; APP = Applicant; EXA = Examiner; OPP = Opposition; 115 = article 115; PRS = Pre-grant pre-search; APL = Appealed; FOP = Filed opposition)");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.RCITATIONS+"."+Attributes.RCITE_TYPE, "type of the citation. Available types: (A = technological background; D = document cited in application; E = earlier patent document; 1 = document cited for other reasons; O = Non-written disclosure; P = Intermediate document; T = theory or principle; X = relevant if taken alone; Y = relevant if combined with other documents)");
		ATTRIBUTE_DESCRIPTION_MAP.put(Attributes.CITATIONS+"."+Attributes.CITED_TYPE, "type of the citation. Available types: (A = technological background; D = document cited in application; E = earlier patent document; 1 = document cited for other reasons; O = Non-written disclosure; P = Intermediate document; T = theory or principle; X = relevant if taken alone; Y = relevant if combined with other documents)");
		ATTRIBUTE_DESCRIPTION_MAP.put("pie", "Create a pie chart.");
		ATTRIBUTE_DESCRIPTION_MAP.put("heatmap", "Create a heat map chart.");
		ATTRIBUTE_DESCRIPTION_MAP.put("wordcloud", "Create a word cloud chart.");
		ATTRIBUTE_DESCRIPTION_MAP.put("line", "Create a timeline chart.");
		ATTRIBUTE_DESCRIPTION_MAP.put("histogram", "Create a histogram.");
		ATTRIBUTE_DESCRIPTION_MAP.put("pivot", "Create an pivot table.");
	}

	public static final Map<String,String> PG_NAME_MAP = Collections.synchronizedMap(new HashMap<>());
	static {
		PG_NAME_MAP.put(NAME,"pub_doc_number");
		PG_NAME_MAP.put(FILING_NAME, "app_doc_number");
		PG_NAME_MAP.put(PUBLICATION_DATE,"pub_date");
		PG_NAME_MAP.put(FILING_DATE,"app_date");
		PG_NAME_MAP.put(DOC_KIND,"pub_kind");
		PG_NAME_MAP.put(COUNTRY, "pub_country");
		PG_NAME_MAP.put(ABSTRACT,"abstract");
		PG_NAME_MAP.put(INVENTION_TITLE,"invention_title");
		PG_NAME_MAP.put(CLAIM_STATEMENT,"us_claim_statement");
		PG_NAME_MAP.put(LENGTH_OF_GRANT,"us_length_of_grant");
		PG_NAME_MAP.put(FILING_COUNTRY,"app_country");
		PG_NAME_MAP.put(CLAIM,"claim_text");
		PG_NAME_MAP.put(CLAIM_NUM,"number");
		PG_NAME_MAP.put(CLAIM_LENGTH,"word_count");
		PG_NAME_MAP.put(PARENT_CLAIM_NUM, "parent_claim_id");
		PG_NAME_MAP.put(CITATIONS+"."+NAME,"patent_cited_doc_number");
		PG_NAME_MAP.put(CITATIONS+"."+CITED_DATE,"patent_cited_date");
		PG_NAME_MAP.put(CITATIONS+"."+DOC_KIND,"patent_cited_kind");
		PG_NAME_MAP.put(CITATIONS+"."+COUNTRY,"patent_cited_country");
		PG_NAME_MAP.put(ASSIGNEE,"orgname");
		PG_NAME_MAP.put(FIRST_NAME,"first_name");
		PG_NAME_MAP.put(CITATION_CATEGORY, "category");
		PG_NAME_MAP.put(LAST_NAME,"last_name");
		PG_NAME_MAP.put(RELATION_TYPE, "relation");
		PG_NAME_MAP.put(PATENT_FAMILY+"."+NAME,"doc_number");
		PG_NAME_MAP.put(ASSIGNEES+"."+COUNTRY, "country");
		PG_NAME_MAP.put(LATEST_ASSIGNEE+"."+COUNTRY, "country");
		PG_NAME_MAP.put(PATENT_FAMILY+"."+COUNTRY, "country");
		PG_NAME_MAP.put(AGENTS+"."+COUNTRY, "country");
		PG_NAME_MAP.put(APPLICANTS+"."+COUNTRY, "country");
		PG_NAME_MAP.put(PATENT_FAMILY+"."+DOC_KIND, "kind");
		PG_NAME_MAP.put(CITY,"city");
		PG_NAME_MAP.put(STATE,"state");
		PG_NAME_MAP.put(ASSIGNEE_ROLE,"role");
	}

	public static final List<String> PG_PATENT_GRANT_ATTRIBUTES = Arrays.asList(
			NAME,
			FILING_COUNTRY,
			FILING_DATE,
			FILING_NAME,
			PUBLICATION_NAME,
			PUBLICATION_DATE,
			DOC_KIND,
			ABSTRACT,
			INVENTION_TITLE,
			COUNTRY,
			CLAIM_STATEMENT,
			LENGTH_OF_GRANT
	);

	public static final Map<String,String> ACCLAIM_IP_TO_ATTR_NAME_MAP = Stream.of(
			Arrays.asList("ANG",LATEST_ASSIGNEE+"."+NORMALIZED_LATEST_ASSIGNEE),
			Arrays.asList("ANG_F",LATEST_ASSIGNEE+"."+NORMALIZED_LATEST_ASSIGNEE),
			Arrays.asList("ANC",LATEST_ASSIGNEE+"."+NORMALIZED_LATEST_ASSIGNEE),
			Arrays.asList("ANC_F",LATEST_ASSIGNEE+"."+NORMALIZED_LATEST_ASSIGNEE),
			Arrays.asList("ANO",ASSIGNEES+"."+ASSIGNEE),
			Arrays.asList("AN_ORIG",ASSIGNEES+"."+ASSIGNEE),
			Arrays.asList("AC", ASSIGNEES+"."+CITY),
			Arrays.asList("AS", ASSIGNEES+"."+STATE),
			Arrays.asList("ACN", ASSIGNEES+"."+COUNTRY),
			Arrays.asList("PRIRD", PRIORITY_DATE),
			Arrays.asList("APD", FILING_DATE),
			Arrays.asList("ISD", PUBLICATION_DATE),
			Arrays.asList("PD", PUBLICATION_DATE),
			Arrays.asList("EXP", EXPIRATION_DATE),
			Arrays.asList("APN", FILING_NAME),
			Arrays.asList("DN", NAME),
			Arrays.asList("PEND", GRANTED),
			Arrays.asList("GPN", NAME),
			Arrays.asList("PN", NAME),
			Arrays.asList("CC", FILING_COUNTRY),
			Arrays.asList("PT", DOC_KIND),
			Arrays.asList("DT", DOC_TYPE),
			Arrays.asList("SMALLENT", Constants.LATEST_ASSIGNEE+"."+ASSIGNEE_ENTITY_TYPE),
			Arrays.asList("CPC", NESTED_CPC_CODES+"."+CPC_CODES),
			Arrays.asList("IN", INVENTORS+"."+LAST_NAME),
			Arrays.asList("IC", INVENTORS+"."+CITY),
			Arrays.asList("IS", INVENTORS+"."+STATE),
			Arrays.asList("ICN", INVENTORS+"."+COUNTRY),
			Arrays.asList("IADR", INVENTORS+"."+ADDRESS_1),
			Arrays.asList("RCITE", CITATIONS+"."+NAME),
			Arrays.asList("FCITE", BACKWARD_CITATION),
			Arrays.asList("RCITE_CT", CITATIONS+COUNT_SUFFIX),
			Arrays.asList("FCITE_CT", NUM_BACKWARD_CITATIONS),
			Arrays.asList("ANA_SFAM_CT", NUM_RELATED_ASSETS),
			Arrays.asList("ANA_INV_CT", INVENTORS+COUNT_SUFFIX),
			Arrays.asList("ANA_AS_CT", ASSIGNEES+COUNT_SUFFIX),
			Arrays.asList("ANA_CLM_CT", CLAIMS+COUNT_SUFFIX),
			Arrays.asList("KCOD", DOC_KIND),
			Arrays.asList("AGT", AGENTS+"."+LAST_NAME),
			Arrays.asList("AGTN", AGENTS+"."+FIRST_NAME),
			Arrays.asList("AGTS", AGENTS+"."+STATE),
			Arrays.asList("AGTCN", AGENTS+"."+COUNTRY),
			Arrays.asList("AGTC", AGENTS+"."+CITY),
			Arrays.asList("ANA_EXT_DAYS", PATENT_TERM_ADJUSTMENT),
			Arrays.asList("ANA_FCLM_TW", CLAIMS+"."+CLAIM_LENGTH),
			Arrays.asList("ANA_CLM_TW", CLAIMS+"."+CLAIM_LENGTH),
			Arrays.asList("ANA_ANRE_EXE_CT",ASSIGNMENTS+COUNT_SUFFIX),
			Arrays.asList("REC_DT", ASSIGNMENTS+"."+RECORDED_DATE),
			Arrays.asList("EXE_DT", ASSIGNMENTS+"."+EXECUTION_DATE),
			Arrays.asList("ANRE_EXE_DT", LATEST_ASSIGNEE+"."+EXECUTION_DATE),
			Arrays.asList("REFN",NAME),
			Arrays.asList("REF",NAME),
			Arrays.asList("EVENTCODE",MAINTENANCE_EVENT),
			Arrays.asList("RVI", AI_VALUE),
			Arrays.asList("TTL", INVENTION_TITLE),
			Arrays.asList("ABST", ABSTRACT),
			Arrays.asList("ACLM", CLAIMS+"."+CLAIM),
			Arrays.asList("ANRE_CUR",LATEST_ASSIGNEE+"."+ASSIGNEE),
			Arrays.asList("SFAM", PATENT_FAMILY+"."+NAME)
	).collect(Collectors.toMap(e->e.get(0),e->e.get(1)));

	public static final List<Pair<String,String>> acclaimAttrs = Collections.synchronizedList(new ArrayList<>());
	static {
		Map<String, String> primaryAcclaimMap = Constants.ACCLAIM_IP_TO_ATTR_NAME_MAP;
		primaryAcclaimMap.forEach((k, v) -> acclaimAttrs.add(new Pair<>(k, v)));
		Set<String> values = new HashSet<>(primaryAcclaimMap.values());
		USParser.transformationsForAttr.forEach((k, v) -> {
			if (!values.contains(k) && !primaryAcclaimMap.containsKey(k)) {
				acclaimAttrs.add(new Pair<>(k, SimilarPatentServer.humanAttributeFor(k)));
			}
		});
	}
}