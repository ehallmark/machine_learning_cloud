package seeding;


import seeding.ai_db_updater.iterators.AssignmentIterator;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;

import java.io.File;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;

public class Constants {
	public static final String MAINTENANCE_FEE_REMINDERS = "maintenanceFeeReminders";
	public static final String SMALL = "small";
	public static final String MICRO = "micro";
	public static final String LARGE = "large";
	public static final String REEL_NO = "reelNo";
	public static final String REINSTATED = "reinstated";
	public static final String FRAME_NO = "frameNo";
	public static final String ADDRESS_1 = "addressOne";
	public static final String ADDRESS_2 = "addressTwo";
	public static final String ADDRESS_3 = "addressThree";
	public static final String PURGE_INDICATOR = "purgeIndicator";
	public static final String CORRESPONDENT = "correspondent";
	public static final String CORRESPONDENT_ADDRESS_ID = "correspondentAddressId";
	public static final String APPLICATION_TYPE = "applicationType";
	public static final String APPLICATION_CONFIRMATION_NUM = "applicationConfirmationNumber";
	public static final String APPLICANT_FILE_REFERENCE = "applicantFileReference";
	public static final String APPLICATION_STATUS = "applicationStatus";
	public static final String APPLICATION_STATUS_DATE = "applicationStatusDate";
	public static final String GRANT_NAME = "grantName";
	public static final String PAGE_COUNT = "pageCount";
	public static final String POSTAL_CODE = "postalCode";
	public static final String ASSIGNMENTS = "assignments";
	public static final String ASSIGNOR = "assignor";
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
	public static final String CITATION_VALUE = "citationValue";
	public static final String CLAIM_VALUE = "claimValue";
	public static final String CLAIMS = "claims";
	public static final String ASSIGNEES = "assignees";
	public static final String INDEPENDENT_CLAIM = "independentClaim";
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
	public static final String SIMILARITY = "similarity";
	public static final String OVERALL_SCORE = "overallScore";
	public static final String TOTAL_ASSET_COUNT = "totalAssetCount";
	public static final String AI_VALUE = "aiValue";
	public static final String COMPDB_ASSETS_PURCHASED = "compdbAssetsPurchased";
	public static final String COMPDB_ASSETS_SOLD = "compDBAssetsSold";
	public static final String EXPIRED = "expired";
	public static final String CITED_DATE = "citedDate";
	public static final String LINE_CHART = "lineChart[]";
	public static final String COUNTRY = "country";
	public static final String CITATION_CATEGORY = "citationCategory";
	public static final String DOC_KIND = "docKind";
	public static final String PRIORITY_DATE = "priorityDate";
	public static final String PUBLICATION_DATE = "publicationDate";
	public static final String FILING_DATE = "filingDate";
	public static final String ABSTRACT = "abstract";
	public static final String CPC_CODES = "cpcCodes";
	public static final String JAPANESE_ASSIGNEE = "japaneseAssignee";
	public static final String PARAGRAPH_VECTOR_MODEL = "paragraphVectorModel";
	public static final String SIM_RANK_MODEL = "simRankModel";
	public static final String CPC_MODEL = "cpcModel";
	public static final String WIPO_MODEL = "wipoModel";
	public static final String TECHNOLOGY = "technology";
	public static final String EXPIRATION_DATE = "expirationDate";
	public static final String ASSIGNEE_ENTITY_TYPE = "assigneeEntityType";
	public static final String INVENTION_TITLE = "inventionTitle";
	public static final String PIE_CHART = "pieChart[]";
	public static final String HISTOGRAM = "histogram[]";
	public static final String PORTFOLIO_SIZE = "portfolioSize";
	public static final String PATENT_TERM_ADJUSTMENT = "patentTermAdjustment";
	public static final String WIPO_TECHNOLOGY = "wipoTechnology";
	public static final String CPC_TECHNOLOGY = "cpcTechnology";
	public static final String PORTFOLIO_ASSESSMENT = "Portfolio Assessment";
	public static final String SIMILAR_PATENT_SEARCH = "Similar Asset Search";
	public static final String PATENT_SIMILARITY = "patentSimilarity";
	public static final String ASSIGNEE_SIMILARITY = "assigneeSimilarity";
	public static final String TECHNOLOGY_SIMILARITY = "technologySimilarity";
	public static final String REMAINING_LIFE = "remainingLife";
	public static final String PATENT_FAMILY = "patentFamily";
	public static final String ASSIGNORS = "assignors";
	public static final String DATA_FOLDER = "data/";
	public static final int MIN_WORDS_PER_SENTENCE = 3;

	public static String[] CLAIM_STOP_WORDS = new String[]{
			"n","s","fig","performs","figure","drawing","claim","claims","performing","perform","performed","according","invention","belong","belongs","said","relates","describe","consists","comprises","comprise","consist","related","relating","describing","describes","description","include","includes","comprising","including","patent","consisting","a", "about", "above", "above", "across", "after", "afterwards", "again", "against", "all", "almost", "alone", "along", "already", "also", "although", "always", "am", "among", "amongst", "amoungst", "amount", "an", "and", "another", "any", "anyhow", "anyone", "anything", "anyway", "anywhere", "are", "around", "as", "at", "back", "be", "became", "because", "become", "becomes", "becoming", "been", "before", "beforehand", "behind", "being", "below", "beside", "besides", "between", "beyond", "bill", "both", "bottom", "but", "by", "call", "can", "cannot", "cant", "co", "con", "could", "couldnt", "cry", "de", "describe", "detail", "do", "done", "down", "due", "during", "each", "eg", "eight", "either", "eleven", "else", "elsewhere", "empty", "enough", "etc", "even", "ever", "every", "everyone", "everything", "everywhere", "except", "few", "fifteen", "fify", "fill", "find", "fire", "first", "five", "for", "former", "formerly", "forty", "found", "four", "from", "front", "full", "further", "get", "give", "go", "had", "has", "hasnt", "have", "he", "hence", "her", "here", "hereafter", "hereby", "herein", "hereupon", "hers", "herself", "him", "himself", "his", "how", "however", "hundred", "ie", "if", "in", "inc", "indeed", "interest", "into", "is", "it", "its", "itself", "keep", "last", "latter", "latterly", "least", "less", "ltd", "made", "many", "may", "me", "meanwhile", "might", "mill", "mine", "more", "moreover", "most", "mostly", "move", "much", "must", "my", "myself", "name", "namely", "neither", "never", "nevertheless", "next", "nine", "no", "nobody", "none", "noone", "nor", "not", "nothing", "now", "nowhere", "of", "off", "often", "on", "once", "one", "only", "onto", "or", "other", "others", "otherwise", "our", "ours", "ourselves", "out", "over", "own", "part", "per", "perhaps", "please", "put", "rather", "re", "same", "see", "seem", "seemed", "seeming", "seems", "serious", "several", "she", "should", "show", "side", "since", "sincere", "six", "sixty", "so", "some", "somehow", "someone", "something", "sometime", "sometimes", "somewhere", "still", "such", "system", "take", "ten", "than", "that", "the", "their", "them", "themselves", "then", "thence", "there", "thereafter", "thereby", "therefore", "therein", "thereupon", "these", "they", "thick", "thin", "third", "this", "those", "though", "three", "through", "throughout", "thru", "thus", "to", "together", "too", "top", "toward", "towards", "twelve", "twenty", "two", "un", "under", "until", "up", "upon", "us", "very", "via", "was", "we", "well", "were", "what", "whatever", "when", "whence", "whenever", "where", "whereafter", "whereas", "whereby", "wherein", "whereupon", "wherever", "whether", "which", "while", "whither", "who", "whoever", "whole", "whom", "whose", "why", "will", "with", "within", "without", "would", "yet", "you", "your", "yours", "yourself", "yourselves", "the","the","a","of","to","and","wherein","said","is","in","claim","first","an","second","for","at","one","from","or","with","method","comprising","least","by","on","device","that","data","according","system","as","plurality","each","signal","further","are","comprises","portion","which","having","information","between","surface","c","layer","unit","apparatus","image","includes","control","configured","more","being","be","end","member","r","based","when","circuit","computer","group","value","has","light","user","set","than","output","including","position","element","time","means","selected","side","input","memory","power","into","material","n","two","through","receiving","connected","third","communication","storage","processing","substrate","associated","source","direction","formed","about","body","using","within","region","step","display","corresponding","coupled","determining","other","optical","part","process","current","assembly","number","section","predetermined","s","base","structure","line","forming","terminal","such","component","provided","provides","disposed","b","not","processor","channel","area","module","response","frame","medium","frequency","non","state","so","received","claimed","program","i","support","housing","sensor","providing","level","operation","signals","o","lower","contact","different","generating","object","type","fluid","plate","substantially","if","d","access","transmission","form","node","axis","interface","controller","outer","message","extending","film","content","request","cell","over","flow","pressure","reference","thereof","metal","where","vehicle","code", "user_interface/server","respective","upper","point","opening","mode","defined","consisting","via","h","location","wall","acid","gas","arranged","composition","amount","x","gate","elements","m","during","application","along","stored","receive","inner","phase","can","path","mobile","compound","target","edge","connection","digital","air","electrical","block","drive","service","chamber","transistor","values","valve","relative","switch","supply","function","address","readable","top","range","instructions","pattern","e","adjacent","pair","used","after","located","product","filter","field","portions","determined","shaft","whether","adapted","less","sequence","another","devices","pixel","may","length","mechanism","video","high","recording","detecting","t","storing","operating","containing","threshold","rate","steps","weight","packet","same","file","transmitting","bottom","beam","main","p","lens","period","fourth","detection","media","magnetic","heat","head","front","have","positioned","substituted","color","machine","water","panel","engine","port","determine","ring","bit","provide","station","upon","generate","recited","use","array","no","electrically","single","generated","distance","clock","connecting","mounted","yl","parallel","f","out","motor","ch","key","space","transfer","greater","test","stream","computing","angle","order","any","performing","face","connector","client","guide","attached","sheet","active","carrier","cover","configuration","controlling","change","fuel","switching","external","accordance","multiple","energy","sub","include","driving","parameter","identifying","opposite","present","respect","speed","sample","members","table","cells","low","hole","agent","respectively","comprise","movement","container","shape","up","condition","emitting","receiver","g","formula","size","y","tube","integrated","both","selecting","mounting","ratio","error","equal","identifier","shaped","link","internal","coupling","rotation","plane","center","components","while","l","electric","lines","force","logic","all","arm","width","defining","audio","operable","setting","load","been","fixed","management","central","longitudinal","difference","result","carbon","three","k","id","applying","particular","virtual","event","insulating","polymer","antenna","performed","core","command","back","oxide"};

	public static final Set<String> CLAIM_STOP_WORD_SET = new HashSet<>(Arrays.asList(CLAIM_STOP_WORDS));
	public static final Set<String> STOP_WORD_SET = CLAIM_STOP_WORD_SET;

	public static final String GOOGLE_URL = "http://storage.googleapis.com/patents/grant_full_text";
	public static final String USPTO_URL = "https://bulkdata.uspto.gov/data2/patent/grant/redbook/fulltext";
	public static final String GOOGLE_APP_URL = "http://storage.googleapis.com/patents/appl_full_text";
	public static final String USPTO_APP_URL = "https://bulkdata.uspto.gov/data2/patent/application/redbook/fulltext";
	public static final UrlCreator GOOGLE_URL_CREATOR = defaultPatentUrlCreator(GOOGLE_URL);
	public static final UrlCreator USPTO_URL_CREATOR = defaultPatentUrlCreator(USPTO_URL);
	public static final UrlCreator GOOGLE_APP_URL_CREATOR = defaultAppUrlCreator(GOOGLE_APP_URL);
	public static final UrlCreator USPTO_APP_URL_CREATOR = defaultAppUrlCreator(USPTO_APP_URL);
	public static final UrlCreator PATENT_CPC_URL_CREATOR = maintenanceUrlCreator("https://bulkdata.uspto.gov/data2/patent/classification/cpc/US_Grant_CPC_MCF_Text_");
	public static final UrlCreator APP_CPC_URL_CREATOR = maintenanceUrlCreator("https://bulkdata.uspto.gov/data2/patent/classification/cpc/US_PGPub_CPC_MCF_Text_");

	public static final LocalDate DEFAULT_START_DATE = LocalDate.of(2005, Month.JANUARY, 1);
	public static final String PATENT_DESTINATION_PREFIX = "patent-grant-destinations";
	public static final String APP_DESTINATION_PREFIX = "app-grant-destinations";
	public static final String ASSIGNMENT_DESTINATION_PREFIX = "assignment-grant-destinations";
	public static final String PATENT_ZIP_FOLDER = "data/patents/";
	public static final String APP_ZIP_FOLDER = "data/applications/";
	public static final String ASSIGNMENT_ZIP_FOLDER = "data/assignments/";
	public static final PatentGrantIterator DEFAULT_PATENT_GRANT_ITERATOR = new PatentGrantIterator(new File(PATENT_ZIP_FOLDER), PATENT_DESTINATION_PREFIX);
	public static final PatentGrantIterator DEFAULT_PATENT_APPLICATION_ITERATOR = new PatentGrantIterator(new File(APP_ZIP_FOLDER), APP_DESTINATION_PREFIX);
	public static final AssignmentIterator DEFAULT_ASSIGNMENT_ITERATOR = new AssignmentIterator(new File(ASSIGNMENT_ZIP_FOLDER), ASSIGNMENT_DESTINATION_PREFIX);

	private static UrlCreator defaultPatentUrlCreator(String baseUrl) {
		return defaultCreator(baseUrl, "ipg");
	}

	private static UrlCreator defaultAppUrlCreator(String baseUrl) {
		return defaultCreator(baseUrl, "ipa");
	}

	private static UrlCreator defaultCreator(String baseUrl, String prefix) {
		return date -> baseUrl + "/" + date.getYear() + "/" + prefix + date.toString().replace("-", "").substring(2) + ".zip";
	}

	private static UrlCreator maintenanceUrlCreator(String baseUrl) {
		return date -> {
			String dateStr = String.format("%04d", date.getYear()) + "-" + String.format("%02d", date.getMonthValue()) + "-" + String.format("%02d", date.getDayOfMonth());
			return baseUrl + dateStr + ".zip";
		};
	}

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

	}