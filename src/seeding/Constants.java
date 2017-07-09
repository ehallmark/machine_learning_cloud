package seeding;


import java.util.*;

public class Constants {

	public static final String ASSIGNEE = "assignee";
	public static final String NAME = "name";
	public static final String SIMILARITY = "similarity";
	public static final String TOTAL_ASSET_COUNT = "totalAssetCountValue";
	public static final String ASSIGNEES_TO_REMOVE_FILTER = "assigneesToRemoveFilter";
	public static final String AI_VALUE = "aiValue";
	public static final String COMPDB_ASSETS_PURCHASED = "compdbAssetsPurchased";
	public static final String COMPDB_ASSETS_SOLD = "compDBAssetsSold";
	public static final String SIMILARITY_THRESHOLD_FILTER = "similarityThresholdFilter";
	public static final String PORTFOLIO_SIZE_MINIMUM_FILTER = "portfolioSizeMinimumFilter";
	public static final String PORTFOLIO_SIZE_MAXIMUM_FILTER = "portfolioSizeMaximumFilter";
	public static final String EXPIRATION_FILTER = "expirationFilter";
	public static final String LABEL_FILTER = "labelFilter";
	public static final String PARAGRAPH_VECTOR_MODEL = "paragraphVectorModel";
	public static final String SIM_RANK_MODEL = "simRankModel";
	public static final String CPC_MODEL = "cpcModel";
	public static final String WIPO_MODEL = "wipoModel";
	public static final String TECHNOLOGY = "technologyValue";
	public static final String ASSIGNEE_ENTITY_TYPE = "assigneeEntityTypeValue";
	public static final String INVENTION_TITLE = "inventionTitleValue";
	public static final String NO_JAPANESE_FILTER = "noJapaneseFilter";
	public static final String VALUE_THRESHOLD_FILTER = "valueThresholdFilter";
	public static final String JAPANESE_ONLY_FILTER = "japaneseOnlyFilter";
	public static final String PIE_CHART = "pieChart[]";
	public static final String HISTOGRAM = "histogram[]";
	public static final String PORTFOLIO_SIZE = "portfolioSize";
	public static final String PATENT_SEARCH_SCOPE_FILTER = "patentSearchScopeFilter";
	public static final String ASSIGNEE_SEARCH_SCOPE_FILTER = "assigneeSearchScopeFilter";
	public static final String WIPO_TECHNOLOGY = "wipoTechnology";
	public static final String PORTFOLIO_ASSESSMENT = "Portfolio Assessment";
	public static final String SIMILAR_PATENT_SEARCH = "Similar Patent Search";
	public static final String SIMILAR_ASSIGNEE_SEARCH = "Similar Assignee Search";
	public static final String PATENT_SIMILARITY = "patentSimilarity";
	public static final String ASSIGNEE_SIMILARITY = "assigneeSimilarity";
	public static final String TECHNOLOGY_SIMILARITY = "technologySimilarity";
	public static final String REMAINING_LIFE = "remainingLife";
	public static final String REMAINING_LIFE_FILTER = "remainingLifeFilter";
	public static final String PATENT_FAMILY = "patentFamily";
	public static final String REQUIRE_KEYWORD_FILTER = "requireKeywordFilter";
	public static final String DATA_FOLDER = "data/";
	public static final int MIN_WORDS_PER_SENTENCE = 3;

	public static String[] CLAIM_STOP_WORDS = new String[]{
			"n","s","fig","performs","figure","drawing","claim","claims","performing","perform","performed","according","invention","belong","belongs","said","relates","describe","consists","comprises","comprise","consist","related","relating","describing","describes","description","include","includes","comprising","including","patent","consisting","a", "about", "above", "above", "across", "after", "afterwards", "again", "against", "all", "almost", "alone", "along", "already", "also", "although", "always", "am", "among", "amongst", "amoungst", "amount", "an", "and", "another", "any", "anyhow", "anyone", "anything", "anyway", "anywhere", "are", "around", "as", "at", "back", "be", "became", "because", "become", "becomes", "becoming", "been", "before", "beforehand", "behind", "being", "below", "beside", "besides", "between", "beyond", "bill", "both", "bottom", "but", "by", "call", "can", "cannot", "cant", "co", "con", "could", "couldnt", "cry", "de", "describe", "detail", "do", "done", "down", "due", "during", "each", "eg", "eight", "either", "eleven", "else", "elsewhere", "empty", "enough", "etc", "even", "ever", "every", "everyone", "everything", "everywhere", "except", "few", "fifteen", "fify", "fill", "find", "fire", "first", "five", "for", "former", "formerly", "forty", "found", "four", "from", "front", "full", "further", "get", "give", "go", "had", "has", "hasnt", "have", "he", "hence", "her", "here", "hereafter", "hereby", "herein", "hereupon", "hers", "herself", "him", "himself", "his", "how", "however", "hundred", "ie", "if", "in", "inc", "indeed", "interest", "into", "is", "it", "its", "itself", "keep", "last", "latter", "latterly", "least", "less", "ltd", "made", "many", "may", "me", "meanwhile", "might", "mill", "mine", "more", "moreover", "most", "mostly", "move", "much", "must", "my", "myself", "name", "namely", "neither", "never", "nevertheless", "next", "nine", "no", "nobody", "none", "noone", "nor", "not", "nothing", "now", "nowhere", "of", "off", "often", "on", "once", "one", "only", "onto", "or", "other", "others", "otherwise", "our", "ours", "ourselves", "out", "over", "own", "part", "per", "perhaps", "please", "put", "rather", "re", "same", "see", "seem", "seemed", "seeming", "seems", "serious", "several", "she", "should", "show", "side", "since", "sincere", "six", "sixty", "so", "some", "somehow", "someone", "something", "sometime", "sometimes", "somewhere", "still", "such", "system", "take", "ten", "than", "that", "the", "their", "them", "themselves", "then", "thence", "there", "thereafter", "thereby", "therefore", "therein", "thereupon", "these", "they", "thick", "thin", "third", "this", "those", "though", "three", "through", "throughout", "thru", "thus", "to", "together", "too", "top", "toward", "towards", "twelve", "twenty", "two", "un", "under", "until", "up", "upon", "us", "very", "via", "was", "we", "well", "were", "what", "whatever", "when", "whence", "whenever", "where", "whereafter", "whereas", "whereby", "wherein", "whereupon", "wherever", "whether", "which", "while", "whither", "who", "whoever", "whole", "whom", "whose", "why", "will", "with", "within", "without", "would", "yet", "you", "your", "yours", "yourself", "yourselves", "the","the","a","of","to","and","wherein","said","is","in","claim","first","an","second","for","at","one","from","or","with","method","comprising","least","by","on","device","that","data","according","system","as","plurality","each","signal","further","are","comprises","portion","which","having","information","between","surface","c","layer","unit","apparatus","image","includes","control","configured","more","being","be","end","member","r","based","when","circuit","computer","group","value","has","light","user","set","than","output","including","position","element","time","means","selected","side","input","memory","power","into","material","n","two","through","receiving","connected","third","communication","storage","processing","substrate","associated","source","direction","formed","about","body","using","within","region","step","display","corresponding","coupled","determining","other","optical","part","process","current","assembly","number","section","predetermined","s","base","structure","line","forming","terminal","such","component","provided","provides","disposed","b","not","processor","channel","area","module","response","frame","medium","frequency","non","state","so","received","claimed","program","i","support","housing","sensor","providing","level","operation","signals","o","lower","contact","different","generating","object","type","fluid","plate","substantially","if","d","access","transmission","form","node","axis","interface","controller","outer","message","extending","film","content","request","cell","over","flow","pressure","reference","thereof","metal","where","vehicle","code","server","respective","upper","point","opening","mode","defined","consisting","via","h","location","wall","acid","gas","arranged","composition","amount","x","gate","elements","m","during","application","along","stored","receive","inner","phase","can","path","mobile","compound","target","edge","connection","digital","air","electrical","block","drive","service","chamber","transistor","values","valve","relative","switch","supply","function","address","readable","top","range","instructions","pattern","e","adjacent","pair","used","after","located","product","filter","field","portions","determined","shaft","whether","adapted","less","sequence","another","devices","pixel","may","length","mechanism","video","high","recording","detecting","t","storing","operating","containing","threshold","rate","steps","weight","packet","same","file","transmitting","bottom","beam","main","p","lens","period","fourth","detection","media","magnetic","heat","head","front","have","positioned","substituted","color","machine","water","panel","engine","port","determine","ring","bit","provide","station","upon","generate","recited","use","array","no","electrically","single","generated","distance","clock","connecting","mounted","yl","parallel","f","out","motor","ch","key","space","transfer","greater","test","stream","computing","angle","order","any","performing","face","connector","client","guide","attached","sheet","active","carrier","cover","configuration","controlling","change","fuel","switching","external","accordance","multiple","energy","sub","include","driving","parameter","identifying","opposite","present","respect","speed","sample","members","table","cells","low","hole","agent","respectively","comprise","movement","container","shape","up","condition","emitting","receiver","g","formula","size","y","tube","integrated","both","selecting","mounting","ratio","error","equal","identifier","shaped","link","internal","coupling","rotation","plane","center","components","while","l","electric","lines","force","logic","all","arm","width","defining","audio","operable","setting","load","been","fixed","management","central","longitudinal","difference","result","carbon","three","k","id","applying","particular","virtual","event","insulating","polymer","antenna","performed","core","command","back","oxide"};

	public static final Set<String> CLAIM_STOP_WORD_SET = new HashSet<>(Arrays.asList(CLAIM_STOP_WORDS));
	public static final Set<String> STOP_WORD_SET = CLAIM_STOP_WORD_SET;


}