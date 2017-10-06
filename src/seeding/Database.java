package seeding;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;

import elasticsearch.DataSearcher;
import org.elasticsearch.search.sort.SortOrder;
import seeding.ai_db_updater.handlers.flags.Flag;
import seeding.compdb.CreateCompDBAssigneeTransactionData;
import tools.AssigneeTrimmer;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.BuyerAttribute;
import user_interface.ui_models.attributes.ReelFrameAttribute;
import user_interface.ui_models.attributes.SellerAttribute;
import user_interface.ui_models.attributes.hidden_attributes.*;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.items.Item;

import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Database {
	private static final File compDBTechnologiesFile = new File(Constants.DATA_FOLDER+"compdb_technologies_set.jobj");
	private static Collection<String> compDBTechnologies;
	private static File compDBBuyersFile = new File(Constants.DATA_FOLDER+"compdb_buyers_map");
	private static File compDBSellersFile = new File(Constants.DATA_FOLDER+"compdb_sellers_map");
	private static Map<String,List<String>> compDBReelFramesToBuyersMap;
	private static Map<String,List<String>> compDBReelFramesToSellersMap;
	private static Map<String,Set<String>> patentToClassificationMap;
	private static Map<String,Set<String>> appToClassificationMap;
	private static Map<String,String> classCodeToClassTitleMap;
	private static Map<String,String> technologyMap;
	private static Map<String,Collection<String>>  assigneeToPatentsMap;
	private static Map<String,Collection<String>> assigneeToAppsMap;
	private static Map<String,Collection<String>> etsiStandardToPatentsMap;
	private static RadixTree<String> assigneePrefixTrie;
	private static RadixTree<String> classCodesPrefixTrie;
	public static Set<String> allAssignees;
	public static Set<String> gatherAssets;
	public static final File gatherAssetsFile = new File(Constants.DATA_FOLDER+"gather_assets_set.jobj");
	public static Set<String> compdbReelFrames;
	public static final File compdbReelFramesFile = new File(Constants.DATA_FOLDER+"compdb_assets_set.jobj");
	public static Set<String> allClassCodes;
	public static Map<String,LocalDate> expirationDateMap;
	public static final File expirationDateMapFile = new File(Constants.DATA_FOLDER+"expiration_date_map.jobj");
	private static Map<String,Integer> assigneeToAssetsSoldCountMap;
	private static Map<String,Integer> assigneeToAssetsPurchasedCountMap;
	private static Map<String,Integer> compDBAssigneeToAssetsSoldCountMap;
	private static Map<String,Integer> compDBAssigneeToAssetsPurchasedCountMap;
	private static Map<String,Collection<String>> patentToCitedPatentsMap;
	private static Map<String,Collection<String>> appToCitedPatentsMap;
	private static Map<String,Collection<String>> gatherPatentToStagesCompleteMap;
	private static Map<String,List<Map<String,Object>>> compDBAssetToNestedDataMap;
	private static final File compDBAssetToNestedDataMapFile = new File(Constants.DATA_FOLDER+"compdb_technology_to_assets_map.jobj");
	private static Collection<String> compDBPAssets;
	private static final File compDBPAssetsFile = new File(Constants.DATA_FOLDER+"compdb_assets_set.jobj");
	private static Map<String,Boolean> gatherBoolValueMap;
	private static final File gatherBoolValueFile = new File(Constants.DATA_FOLDER+"gather_patent_to_value_bool_map.jobj");
	private static File gatherPatentToStagesCompleteFile = new File(Constants.DATA_FOLDER+"gather_patent_to_stages_complete_map.jobj");
	public static Map<String,LocalDate> patentToPubDateMap;
	public static Map<String,LocalDate> appToPriorityDateMap;
	public static final File patentToPubDateMapFile = new File(Constants.DATA_FOLDER+"patent_to_pubdate_map_file.jobj");
	public static File allClassCodesFile = new File(Constants.DATA_FOLDER+"all_class_codes.jobj");
	public static File technologyMapFile = new File(Constants.DATA_FOLDER+"item_to_technology_map.jobj");
	public static final File classCodeToClassTitleMapFile = new File(Constants.DATA_FOLDER+"class_code_to_class_title_map.jobj");
	private static final String patentDBUrl = "jdbc:postgresql://localhost/patentdb?user=postgres&password=password&tcpKeepAlive=true";
	private static final String compDBUrl = "jdbc:postgresql://localhost/compdb_production?user=postgres&password=password&tcpKeepAlive=true";
	private static final String gatherDBUrl = "jdbc:postgresql://localhost/gather_production?user=postgres&password=password&tcpKeepAlive=true";
	public static Connection seedConn;
	private static Connection compDBConn;
	private static Connection gatherDBConn;

	private static final String selectGatherRatingsQuery = "select a.patent_rating,array_agg(p.number) as avg_patent_rating from assessments as a join patents as p on (p.id=a.patent_id) where patent_rating is not null and a.type = 'PublishedAssessment'  group by a.patent_rating";
	private static final String selectGatherTechnologiesQuery = "select array_agg(distinct(number)), upper(name) from (select case when t.name like '%rs' then substring(t.name from 1 for char_length(t.name)-1) else replace(t.name,'-','') end as name, (string_to_array(regexp_replace(p.number,'[^0-9 ]',''),' '))[1] as number from patents as p join assessments as a on (p.id=a.patent_id) join assessment_technologies as at on (a.id=at.assessment_id) join technologies as t on (at.technology_id=t.id) where char_length(coalesce(t.name,'')) > 0 and (not upper(t.name)='AUDIT')) as temp group by upper(name)";
	private static final Set<Integer> badCompDBTechnologyIds = new HashSet<>(Arrays.asList(136,182,301,316,519,527));
	private static final File gatherTechnologyToPatentFile = new File(Constants.DATA_FOLDER+"gather_technology_to_patent_map.jobj");
	private static Map<String,Collection<String>> gatherTechnologyToPatentMap;
	private static final File gatherPatentToTechnologyFile = new File(Constants.DATA_FOLDER+"gather_patent_to_technology_map.jobj");
	private static Map<String,Collection<String>> gatherPatentToTechnologyMap;
	private static final File gatherValueMapFile = new File(Constants.DATA_FOLDER+"gather_patent_to_value_int_map.jobj");
	private static Map<String,Integer> gatherValueMap;
	private static volatile boolean init=false;


	private static Connection conn;

	public static File appToPriorityDateMapFile = new File(Constants.DATA_FOLDER+"app_to_priority_date_map.jobj");

	static {
		resetConn();
	}

	public static synchronized void resetConn() {
		try {
			conn = DriverManager.getConnection(patentDBUrl);
			conn.setAutoCommit(false);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	public static Connection getConn() {
		return conn;
	}

	public static Object loadObject(File file) {
		return tryLoadObject(file);
	}

	public static void saveObject(Object obj, File file) {
		trySaveObject(obj,file);
	}


	public static Collection<String> getAllPatentsAndApplications() {
		Collection<String> all = getCopyOfAllPatents();
		all.addAll(getCopyOfAllApplications());
		return all;
	}


	public synchronized static Map<String,LocalDate> getExpirationDateMap() {
		if(expirationDateMap==null) {
			expirationDateMap = (Map<String,LocalDate>) loadObject(expirationDateMapFile);
		}
		return expirationDateMap;
	}


	public static void ingestPairRecords(Map<Flag,String> data, String tableName) throws SQLException {
		List<Map.Entry<Flag,String>> entries = data.entrySet().stream().collect(Collectors.toList());
		String queryPrefix = "INSERT INTO "+tableName+" ("+String.join(",",entries.stream().map(e->e.getKey().dbName).collect(Collectors.toList()))+") VALUES ";
		String querySuffix = " ON CONFLICT DO NOTHING";
		StringJoiner joiner = new StringJoiner(",","(",")");
		for(int i = 0; i < entries.size(); i++) {
			joiner.add("?::"+entries.get(i).getKey().type);
		}
		String query = queryPrefix + joiner.toString() + querySuffix;
		PreparedStatement ps = conn.prepareStatement(query);
		for(int i = 0; i < entries.size(); i++) {
			ps.setString(i+1, entries.get(i).getValue());
		}
		ps.executeUpdate();
		ps.close();
	}


	public static synchronized void commit() throws SQLException {
		conn.commit();
	}

	public static Object tryLoadObject(File file) {
		System.out.println("Starting to load file: "+file.getName()+"...");
		try {
			ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
			Object toReturn = ois.readObject();
			ois.close();
			System.out.println("Successfully loaded "+file.getName()+".");
			return toReturn;
		} catch(Exception e) {
			e.printStackTrace();
			//throw new RuntimeException("Unable to open file: "+file.getPath());
			return null;
		}
	}

	public static void trySaveObject(Object obj, File file) {
		try {
			ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
			oos.writeObject(obj);
			oos.flush();
			oos.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}


	public synchronized static void preLoad() {
		getAssigneePrefixTrie();
		getAssigneeToPatentsMap();
		getAssigneeToAppsMap();
		getPatentToPubDateMap();
		getAppToPriorityDateMap();
		getExpirationDateMap();
	}

	public synchronized static void initializeDatabase() {
		if(init==true)return;
		init=true;
		// nothing to do
	}

	public synchronized static Map<String,Collection<String>> getAssigneeToPatentsMap() {
		if(assigneeToPatentsMap==null) {
			assigneeToPatentsMap = new AssigneeToAssetsMap().getPatentDataMap();
		}
		return assigneeToPatentsMap;
	}

	public synchronized static Map<String,Collection<String>> getAssigneeToAppsMap() {
		if(assigneeToAppsMap==null) {
			assigneeToAppsMap = new AssigneeToAssetsMap().getApplicationDataMap();
		}
		return assigneeToAppsMap;
	}


	public synchronized static Set<String> getClassCodes() {
		if(allClassCodes==null) {
			allClassCodes = (Set<String>) Database.tryLoadObject(allClassCodesFile);
		}
		return allClassCodes;
	}


	public synchronized static Set<String> getAssignees() {
		if(allAssignees==null) {
			allAssignees=new HashSet<>(getAssigneeToAppsMap().keySet());
			allAssignees.addAll(getAssigneeToPatentsMap().keySet());
		}
		return Collections.unmodifiableSet(allAssignees);
	}

	public synchronized static void setupGatherConn() throws SQLException {
		gatherDBConn = DriverManager.getConnection(gatherDBUrl);
		gatherDBConn.setAutoCommit(false);
	}

	public synchronized static Map<String,Set<String>> getPatentToClassificationMap() {
		if(patentToClassificationMap==null) {
			patentToClassificationMap = new AssetToCPCMap().getPatentDataMap();
		}
		return patentToClassificationMap;
	}

	public synchronized static Map<String,Set<String>> getAppToClassificationMap() {
		if(appToClassificationMap==null) {
			appToClassificationMap = new AssetToCPCMap().getApplicationDataMap();
		}
		return appToClassificationMap;
	}

	public synchronized static Map<String,LocalDate> getPatentToPubDateMap() {
		if(patentToPubDateMap==null) {
			patentToPubDateMap = Collections.unmodifiableMap((Map<String,LocalDate>)tryLoadObject(patentToPubDateMapFile));
		}
		return patentToPubDateMap;
	}


	public synchronized static Map<String,String> getItemToTechnologyMap() {
		if(technologyMap==null) {
			technologyMap = Collections.unmodifiableMap((Map<String,String>)tryLoadObject(technologyMapFile));
		}
		return technologyMap;
	}


	public synchronized static Map<String,LocalDate> getAppToPriorityDateMap() {
		if(appToPriorityDateMap==null) {
			appToPriorityDateMap = Collections.unmodifiableMap((Map<String,LocalDate>)tryLoadObject(appToPriorityDateMapFile));
		}
		return appToPriorityDateMap;
	}


	public synchronized static void setupSeedConn() throws SQLException {
		if(seedConn==null) {
			seedConn = DriverManager.getConnection(patentDBUrl);
			seedConn.setAutoCommit(false);
		}
	}

	public synchronized static Connection newSeedConn() throws SQLException {
		Connection seedConn = DriverManager.getConnection(patentDBUrl);
		seedConn.setAutoCommit(false);
		return seedConn;
	}

	public synchronized static void setupCompDBConn() throws SQLException {
		compDBConn = DriverManager.getConnection(compDBUrl);
		compDBConn.setAutoCommit(false);
	}

	public synchronized static Map<String,String> getClassCodeToClassTitleMap() {
		if(classCodeToClassTitleMap==null) {
			classCodeToClassTitleMap = Collections.unmodifiableMap((Map<String,String>)tryLoadObject(classCodeToClassTitleMapFile));
		}
		return classCodeToClassTitleMap;
	}

	public synchronized static Map<String,Collection<String>> getPatentToRelatedPatentsMap() {
		return new AssetToRelatedAssetsMap().getPatentDataMap();

	}

	public synchronized static Map<String,Collection<String>> getAppToRelatedPatentsMap() {
		return new AssetToRelatedAssetsMap().getApplicationDataMap();
	}

	public static Map<String,Collection<String>> getPatentToCitedPatentsMap() {
		if(patentToCitedPatentsMap==null) {
			patentToCitedPatentsMap= new AssetToCitedAssetsMap().getPatentDataMap();
		}
		return patentToCitedPatentsMap;
	}

	public static Map<String,Collection<String>> getAppToCitedPatentsMap() {
		if(appToCitedPatentsMap==null) {
			appToCitedPatentsMap= new AssetToCitedAssetsMap().getApplicationDataMap();
		}
		return appToCitedPatentsMap;
	}

	public synchronized static String getClassTitleFromClassCode(String formattedCode) {
		formattedCode=formattedCode.toUpperCase().replaceAll(" ","");
		if(getClassCodeToClassTitleMap().containsKey(formattedCode)) return classCodeToClassTitleMap.get(formattedCode);
		return "";
	}

	public synchronized static Connection getCompDBConnection() throws SQLException {
		if(compDBConn==null)setupCompDBConn();
		return compDBConn;
	}

	public synchronized static Map<String,Integer> getAssigneeToAssetsSoldCountMap() {
		if(assigneeToAssetsSoldCountMap==null) {
			assigneeToAssetsSoldCountMap = (Map<String,Integer>)Database.tryLoadObject(new File(Constants.DATA_FOLDER+"assignee_to_assets_sold_count_map.jobj"));
		}
		return assigneeToAssetsSoldCountMap;
	}
	public synchronized static Map<String,Integer> getAssigneeToAssetsPurchasedCountMap() {
		if(assigneeToAssetsPurchasedCountMap==null) {
			assigneeToAssetsPurchasedCountMap = (Map<String,Integer>)Database.tryLoadObject(new File(Constants.DATA_FOLDER+"assignee_to_assets_purchased_count_map.jobj"));
		}
		return assigneeToAssetsPurchasedCountMap;
	}

	public synchronized static Map<String,Integer> getCompDBAssigneeToAssetsSoldCountMap() {
		if(compDBAssigneeToAssetsSoldCountMap==null) {
			compDBAssigneeToAssetsSoldCountMap = (Map<String,Integer>)Database.tryLoadObject(CreateCompDBAssigneeTransactionData.sellerFile);
		}
		return compDBAssigneeToAssetsSoldCountMap;
	}
	public synchronized static Map<String,Integer> getCompDBAssigneeToAssetsPurchasedCountMap() {
		if(compDBAssigneeToAssetsPurchasedCountMap==null) {
			compDBAssigneeToAssetsPurchasedCountMap = (Map<String,Integer>)Database.tryLoadObject(CreateCompDBAssigneeTransactionData.buyerFile);
		}
		return compDBAssigneeToAssetsPurchasedCountMap;
	}

	public synchronized static int numPatentsWithCpcClassifications() {
		return patentToClassificationMap.size();
	}

	public synchronized static int getAssetCountFor(String assignee) {
		return getAssigneeToAppsMap().getOrDefault(assignee,Collections.emptySet()).size() + getAssigneeToPatentsMap().getOrDefault(assignee,Collections.emptySet()).size();
	}

	public synchronized static int getAssetsSoldCountFor(String assignee) {
		return getAssigneeToAssetsSoldCountMap().getOrDefault(assignee,0);
	}

	public synchronized static int getAssetsPurchasedCountFor(String assignee) {
		return getAssigneeToAssetsPurchasedCountMap().getOrDefault(assignee, 0);
	}

	public synchronized static boolean isApplication(String application) {
		if(application.length()<10||application.length() > 13) return false;
		for(int i = 0; i < application.length(); i++) {
			if(!Character.isDigit(application.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	public synchronized static boolean isAssignee(String assignee) {
		if(assignee.length() > 11 || assignee.length() < 7) return true;
		for(int i = 2; i < assignee.length(); i++) {
			char c = assignee.charAt(i);
			if(Character.isAlphabetic(c)) {
				return true;
			}
		}
		return false;
	}


	public static RadixTree<String> getAssigneePrefixTrie() {
		// prefix trie for assignees
		if(assigneePrefixTrie==null) {
			System.out.println("Building assignee trie...");
			assigneePrefixTrie = new ConcurrentRadixTree<>(new DefaultByteArrayNodeFactory());
			getAssignees().forEach(assignee -> {
				if(assignee!=null&&assignee.length() > 0) {
					assigneePrefixTrie.put(assignee, assignee);
				}
			});
		}
		return assigneePrefixTrie;
	}

	public static RadixTree<String> getClassCodesPrefixTrie() {
		if(classCodesPrefixTrie==null) {
			System.out.println("Building class code trie...");
			// class codes trie
			classCodesPrefixTrie = new ConcurrentRadixTree<>(new DefaultByteArrayNodeFactory());
			getClassCodes().forEach(code->{
				if(code!=null&&code.length() >0) {
					classCodesPrefixTrie.put(code, code);
				}
			});
		}
		return classCodesPrefixTrie;
	}

	public synchronized static Set<String> possibleNamesForAssignee(String base) {
		if(base==null||base.isEmpty()) return new HashSet<>();
		final String cleanBase = AssigneeTrimmer.standardizedAssignee(base);
		if(cleanBase.isEmpty()) return new HashSet<>();
		Set<String> possible = new HashSet<>();
		if(getAssignees().contains(cleanBase)) possible.add(cleanBase);
		getAssigneePrefixTrie().getValuesForKeysStartingWith(cleanBase).forEach(a->possible.add(a));
		return possible;
	}

	public synchronized static Set<String> subClassificationsForClass(String formattedCPC) {
		if(formattedCPC==null||formattedCPC.isEmpty()) return new HashSet<>();
		Set<String> possible = new HashSet<>();
		if(getClassCodes().contains(formattedCPC)) {
			possible.add(formattedCPC);
		}
		getClassCodesPrefixTrie().getValuesForKeysStartingWith(formattedCPC).forEach(a->possible.add(a));
		return possible;
	}

	public static Collection<String> getCopyOfAllPatents() {
		return new HashSet<>(new AssetToFilingMap().getPatentDataMap().keySet());
	}


	public static Collection<String> getCopyOfAllApplications() {
		return new HashSet<>(new AssetToFilingMap().getApplicationDataMap().keySet());
	}

	public synchronized static Set<String> classificationsFor(String patent) {
		Set<String> classifications = new HashSet<>();
		if(isApplication(patent)) {
			if(getAppToClassificationMap().containsKey(patent)) {
				classifications.addAll(appToClassificationMap.get(patent));
			}
		} else {
			if (getPatentToClassificationMap().containsKey(patent)) {
				classifications.addAll(patentToClassificationMap.get(patent));
			}
		}
		return classifications;
	}

	public synchronized static Set<String> subClassificationsForPatent(String patent) {
		Set<String> classifications = new HashSet<>();
		classificationsFor(patent).forEach(clazz->{
			classifications.add(clazz);
			classifications.addAll(subClassificationsForClass(clazz));
		});
		return classifications;
	}

	public synchronized static Map<String,Collection<String>> getEtsiStandardToPatentsMap() {
		if(etsiStandardToPatentsMap==null) {
			// ETSI
			System.out.println("Handling ETSI patents...");
			try {
				etsiStandardToPatentsMap = GetEtsiPatentsList.getETSIPatentMap();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return etsiStandardToPatentsMap;
	}


	public synchronized static String assigneeFor(String patent) {
		String assignee;
		if(isApplication(patent)) {
			assignee = new AssetToAssigneeMap().getApplicationDataMap().get(patent);
		} else {
			assignee = new AssetToAssigneeMap().getPatentDataMap().get(patent);
		}
		return assignee;
	}

	public synchronized static Collection<String> selectPatentNumbersFromAssignee(String assignee){
		Set<String> patents = new HashSet<>();
		// try fuzzy search thru trie
		possibleNamesForAssignee(assignee).forEach(name->{
			if(getAssigneeToPatentsMap().containsKey(name)) {
				patents.addAll(getAssigneeToPatentsMap().get(name));
			}
		});
		return patents;
	}

	public synchronized static Collection<String> selectApplicationNumbersFromAssignee(String assignee){
		Set<String> apps = new HashSet<>();
		// try fuzzy search thru trie
		possibleNamesForAssignee(assignee).forEach(name->{
			if(getAssigneeToAppsMap().containsKey(name)) {
				apps.addAll(getAssigneeToAppsMap().get(name));
			}
		});
		return apps;
	}

	public synchronized static Collection<String> selectPatentNumbersFromExactAssignee(String assignee){
		Set<String> patents = new HashSet<>();
		if(assigneeToPatentsMap == null) getAssigneeToPatentsMap();
		if(assigneeToPatentsMap.containsKey(assignee)) {
			patents.addAll(assigneeToPatentsMap.get(assignee));
		}
		return patents;
	}

	public synchronized static Collection<String> selectApplicationNumbersFromExactAssignee(String assignee) {
		Set<String> apps = new HashSet<>();
		if(assigneeToAppsMap == null) getAssigneeToAppsMap();
		if(assigneeToAppsMap.containsKey(assignee)) {
			apps.addAll(assigneeToAppsMap.get(assignee));
		}
		return apps;
	}


	public synchronized static void close(){
		try {
			if(seedConn!=null && !seedConn.isClosed())seedConn.close();
			if(compDBConn!=null && !compDBConn.isClosed())compDBConn.close();
			if(gatherDBConn!=null && !gatherDBConn.isClosed()) gatherDBConn.close();
			if(conn!=null && !conn.isClosed()) conn.close();
		} catch(SQLException sql) {
			sql.printStackTrace();
		}
	}

	public synchronized static Collection<String> getCompDBAssets()  {
		if(compDBPAssets==null) {
			compDBPAssets=(Collection<String>)Database.tryLoadObject(compDBPAssetsFile);
		}
		return compDBPAssets;
	}


	private synchronized static Map<Integer,String> compdbTechnologyMap() throws SQLException {
		PreparedStatement ps = compDBConn.prepareStatement("select distinct id,name from technologies");
		ResultSet rs = ps.executeQuery();
		Map<Integer,String> map = new HashMap<>();
		while(rs.next()) {
			System.out.println(""+rs.getInt(1) + " => "+rs.getString(2));
			map.put(rs.getInt(1),rs.getString(2));
		}
		ps.close();
		return map;
	}

	private synchronized static void loadGatherTechMap() throws SQLException {
		Database.setupGatherConn();
		//Database.setupSeedConn();
		gatherTechnologyToPatentMap = Collections.synchronizedMap(new HashMap<>());
		PreparedStatement ps = gatherDBConn.prepareStatement(selectGatherTechnologiesQuery);
		ps.setFetchSize(10);
		//ps.setArray(1, gatherDBConn.createArrayOf("int4",badTech.toArray()));
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			String[] data = (String[])rs.getArray(1).getArray();
			if(data.length<3) continue;
			gatherTechnologyToPatentMap.put(rs.getString(2),Collections.synchronizedCollection(new HashSet<>(Arrays.asList(data))));
		}
		gatherPatentToTechnologyMap = Collections.synchronizedMap(new HashMap<>());
		gatherTechnologyToPatentMap.entrySet().parallelStream().forEach(e->{
			e.getValue().forEach(patent->{
				if(gatherPatentToTechnologyMap.containsKey(patent)) {
					gatherPatentToTechnologyMap.get(patent).add(e.getKey());
				} else {
					Collection<String> set = Collections.synchronizedSet(new HashSet<>());
					set.add(e.getKey());
					gatherPatentToTechnologyMap.put(patent,set);
				}
			});
		});
		ps.close();
	}

	private synchronized static void loadGatherPatentToStages() throws SQLException {
		gatherPatentToStagesCompleteMap = Collections.synchronizedMap(new HashMap<>());
		Database.setupGatherConn();
		PreparedStatement ps = gatherDBConn.prepareStatement("select * from assessments as a join patents as p on (p.id=a.patent_id)");
		ps.setFetchSize(10);
		ResultSet rs = ps.executeQuery();
		while(rs.next()) {
			Set<String> stages = Collections.synchronizedSet(new HashSet<>());
			String asset = rs.getString("number");
			for(String stage : Constants.GATHER_STAGES) {
				if(rs.getBoolean(stage+"_complete")) {
					stages.add(stage);
				}
			}
			if(asset.startsWith("US")) asset = asset.replace("US","");
			if(stages.size() > 0) {
				gatherPatentToStagesCompleteMap.put(asset, stages);
			}
		}
	}

	public synchronized static Map<String,Collection<String>> getGatherPatentToStagesCompleteMap() {
		if(gatherPatentToStagesCompleteMap==null) {
			gatherPatentToStagesCompleteMap=(Map<String,Collection<String>>)Database.tryLoadObject(gatherPatentToStagesCompleteFile);
		}
		return gatherPatentToStagesCompleteMap;
	}

	public synchronized static Map<String,Collection<String>> getGatherTechnologyToPatentMap() {
		if(gatherTechnologyToPatentMap==null) {
			gatherTechnologyToPatentMap=(Map<String,Collection<String>>)Database.tryLoadObject(gatherTechnologyToPatentFile);
		}
		return gatherTechnologyToPatentMap;
	}

	public synchronized static Map<String,Collection<String>> getGatherPatentToTechnologyMap() {
		if(gatherPatentToTechnologyMap==null) {
			gatherPatentToTechnologyMap=(Map<String,Collection<String>>)Database.tryLoadObject(gatherPatentToTechnologyFile);
		}
		return gatherPatentToTechnologyMap;
	}

	public synchronized static Map<String,Boolean> getGatherValueMap() {
		if(gatherBoolValueMap==null) {
			gatherBoolValueMap=(Map<String,Boolean>)Database.tryLoadObject(gatherBoolValueFile);
		}
		return gatherBoolValueMap;
	}

	public synchronized static Map<String,Integer> getGatherIntValueMap() {
		if(gatherValueMap==null) {
			gatherValueMap=(Map<String,Integer>)Database.tryLoadObject(gatherValueMapFile);
		}
		return gatherValueMap;
	}

	public synchronized static Map<String,Integer> loadGatherValueMap() throws SQLException {
		Database.setupGatherConn();
		Map<String, Integer> map = Collections.synchronizedMap(new HashMap<>());
		PreparedStatement ps = gatherDBConn.prepareStatement(selectGatherRatingsQuery);
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			String[] patents = (String[])rs.getArray(2).getArray();
			Integer value = rs.getInt(1);
			Arrays.stream(patents).forEach(patent->{
				map.put(patent,value);
			});
		}
		ps.close();
		return map;
	}

	public synchronized static Set<String> getGatherAssets() {
		if(gatherAssets==null) {
			gatherAssets=(Set<String>)Database.tryLoadObject(gatherAssetsFile);
		}
		return gatherAssets;
	}

	public synchronized static Set<String> loadGatherAssets() throws SQLException {
		Database.setupGatherConn();
		PreparedStatement ps = gatherDBConn.prepareStatement("select distinct number from patents where number is not null");
		ResultSet rs = ps.executeQuery();
		Set<String> set = Collections.synchronizedSet(new HashSet<>());
		while (rs.next()) {
			String patent = rs.getString(1);
			set.add(patent);
		}
		ps.close();
		return set;
	}

	public synchronized static Set<String> getCompDBReelFrames() {
		if(compdbReelFrames==null) {
			compdbReelFrames=(Set<String>)Database.tryLoadObject(compdbReelFramesFile);
		}
		return compdbReelFrames;
	}

	public synchronized static Set<String> loadCompDBReelFrames() throws SQLException {
		Database.setupCompDBConn();
		PreparedStatement ps = compDBConn.prepareStatement("select distinct reel::text||':'||frame as rf from recordings where deal_id is not null and reel is not null and frame is not null");
		ResultSet rs = ps.executeQuery();
		Set<String> set = Collections.synchronizedSet(new HashSet<>());
		while (rs.next()) {
			String patent = rs.getString(1);
			set.add(patent);
		}
		ps.close();
		return set;
	}

	public synchronized static void loadReelFramesToCompDBBuyersAndSellers() throws SQLException {
		Database.setupCompDBConn();
		Map<String,List<String>> buyers = Collections.synchronizedMap(new HashMap<>());
		Map<String,List<String>> sellers = Collections.synchronizedMap(new HashMap<>());
		PreparedStatement ps = compDBConn.prepareStatement("select reel::text||':'||frame as rf, b.name as buyer, s.name as seller from recordings as r join recordings_buyers as rb on (rb.recording_id=r.id) join companies as b on (b.id=rb.company_id)" +
				" join recordings_sellers as rs on (rs.recording_id=r.id) join companies as s on (s.id=rs.company_id) where deal_id is not null and reel is not null and frame is not null");
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			String patent = rs.getString(1);
			String buyer = rs.getString(2);
			String seller = rs.getString(3);
			if(buyer!=null) {
				buyers.putIfAbsent(patent,new ArrayList<>());
				List<String> b = buyers.get(patent);
				if(!b.contains(buyer)) b.add(buyer);
			}
			if(seller!=null) {
				sellers.putIfAbsent(patent,new ArrayList<>());
				List<String> s = sellers.get(patent);
				if(!s.contains(seller)) s.add(seller);
			}
		}
		ps.close();
		// save stuff
		Database.trySaveObject(buyers,compDBBuyersFile);
		Database.trySaveObject(sellers,compDBSellersFile);
	}

	public synchronized static Map<String,List<Map<String,Object>>> getCompDBAssetToNestedDataMap() {
		if(compDBAssetToNestedDataMap==null) {
			compDBAssetToNestedDataMap = (Map<String,List<Map<String,Object>>>) Database.tryLoadObject(compDBAssetToNestedDataMapFile);
		}
		return compDBAssetToNestedDataMap;
	}

	public synchronized static Map<String,List<String>> getCompDBReelFrameToBuyersMap() {
		if(compDBReelFramesToBuyersMap==null) {
			compDBReelFramesToBuyersMap = (Map<String,List<String>>) Database.tryLoadObject(compDBBuyersFile);
		}
		return compDBReelFramesToBuyersMap;
	}

	public synchronized static Map<String,List<String>> getCompDBReelFrameToSellersMap() {
		if(compDBReelFramesToSellersMap==null) {
			compDBReelFramesToSellersMap = (Map<String,List<String>>) Database.tryLoadObject(compDBSellersFile);
		}
		return compDBReelFramesToSellersMap;
	}

	public synchronized static Collection<String> getCompDBTechnologies() {
		if(compDBTechnologies==null) {
			compDBTechnologies = (Collection<String>) Database.tryLoadObject(compDBTechnologiesFile);
		}
		return compDBTechnologies;
	}


	public synchronized static void loadCompDBData() throws SQLException {
		Database.setupCompDBConn();
		Database.setupSeedConn();

		// buyers and sellers
		Map<String,List<String>> rfToBuyersMap = getCompDBReelFrameToBuyersMap();
		Map<String,List<String>> rfToSellersMap = getCompDBReelFrameToSellersMap();
		Map<String,List<String>> patentToBuyersMap = Collections.synchronizedMap(new HashMap<>());
		Map<String,List<String>> patentToSellersMap = Collections.synchronizedMap(new HashMap<>());
		compDBAssetToNestedDataMap = Collections.synchronizedMap(new HashMap<>());

		// get Reelframes Map
		Set<String> allCompDBReelFrames = getCompDBReelFrames();
		// Collect patent numbers
		Item[] items = DataSearcher.searchForAssets(Arrays.asList(new AssetNumberAttribute(), new ReelFrameAttribute()),Arrays.asList(new AbstractIncludeFilter(new ReelFrameAttribute(), AbstractFilter.FilterType.Include, AbstractFilter.FieldType.Text, allCompDBReelFrames)),null, SortOrder.ASC, 100000, Collections.emptyMap(),false);
		Map<String,Collection<String>> reelFrameToAssetsMap = Collections.synchronizedMap(new HashMap<>());
		Stream.of(items).parallel().forEach(item->{
			Object reelFrameStr = item.getData(Constants.REEL_FRAME);
			if(reelFrameStr==null) return;
			String[] reelFramesStr = reelFrameStr.toString().split("; ");
			if(reelFramesStr.length==0) return;

			List<String> buyers = Stream.of(reelFramesStr).flatMap(rf->rfToBuyersMap.getOrDefault(rf,new ArrayList<>()).stream()).distinct().collect(Collectors.toList());
			if(buyers.size()>0) {
				patentToBuyersMap.put(item.getName(),buyers);
			}
			List<String> sellers = Stream.of(reelFramesStr).flatMap(rf->rfToSellersMap.getOrDefault(rf,new ArrayList<>()).stream()).distinct().collect(Collectors.toList());
			if(sellers.size()>0) {
				patentToSellersMap.put(item.getName(),sellers);
			}
			Stream.of(reelFramesStr).forEach(reelFrame->{
				if(reelFrameToAssetsMap.containsKey(reelFrame)) {
					reelFrameToAssetsMap.get(reelFrame).add(item.getName());
				} else {
					Set<String> set = Collections.synchronizedSet(new HashSet<>());
					set.add(item.getName());
					reelFrameToAssetsMap.put(reelFrame,set);
				}
			});
		});
		PreparedStatement ps = compDBConn.prepareStatement("SELECT array_agg(distinct t.id) as technologies, array_agg(distinct (reel||':'||frame)) AS reelframes, min(r.recording_date) as recording_date, bool_or(coalesce(r.inactive,'t')) as inactive, bool_and(coalesce(d.acquisition_deal,'f')) as acquisition, r.deal_id FROM recordings as r inner join deals_technologies as dt on (r.deal_id=dt.deal_id) INNER JOIN technologies AS t ON (t.id=dt.technology_id) join deals as d on(r.deal_id=d.id)  WHERE r.deal_id IS NOT NULL AND t.name is not null AND t.id!=ANY(?) and recording_date is not null GROUP BY r.deal_id");
		ps.setArray(1, compDBConn.createArrayOf("int4",badCompDBTechnologyIds.toArray()));
		Map<Integer, String> technologyMap = Database.compdbTechnologyMap();
		ResultSet rs = ps.executeQuery();

		System.out.println("Finished collecting reelframe to assets map.");
		compDBTechnologies = new HashSet<>();
		while(rs.next()) {
			Collection<String> technologies = new HashSet<>();
			for(Integer tech : (Integer[])rs.getArray(1).getArray()) {
				if(badCompDBTechnologyIds.contains(tech)) continue;
				technologies.add(technologyMap.get(tech));
			}
			if(technologies.isEmpty()) continue;
			compDBTechnologies.addAll(technologies);
			Integer dealID = rs.getInt(6);
			if(dealID==null) continue;

			boolean acquisition = rs.getBoolean(5);
			boolean inactive = rs.getBoolean(4);
			String recordingDate = rs.getDate(3).toLocalDate().format(DateTimeFormatter.ISO_DATE);

			Array reelFramesForDeal = rs.getArray(2);
			if(reelFramesForDeal==null) continue;
			String[] reelFramesStr = (String[]) reelFramesForDeal.getArray();
			if(reelFramesStr.length>0) {
				Collection<String> patents = Stream.of(reelFramesStr).flatMap(reelFrame->reelFrameToAssetsMap.getOrDefault(reelFrame,Collections.emptyList()).stream()).collect(Collectors.toList());
				if(patents.size()>0) {
					patents.forEach(patent -> {
						Map<String,Object> nestedData = Collections.synchronizedMap(new HashMap<>());
						// add nested attrs
						nestedData.put(Constants.COMPDB_DEAL_ID, dealID.toString());
						nestedData.put(Constants.COMPDB_TECHNOLOGY, technologies.stream().collect(Collectors.toList()));
						nestedData.put(Constants.REEL_FRAME, (List<String>) Arrays.asList(reelFramesStr));
						nestedData.put(Constants.NAME, patents);
						nestedData.put(Constants.NUM_ASSIGNMENTS, reelFramesStr.length);
						nestedData.put(Constants.NUM_ASSETS, patents.size());
						nestedData.put(Constants.RECORDED_DATE, recordingDate);
						nestedData.put(Constants.INACTIVE_DEAL, inactive);
						nestedData.put(Constants.ACQUISITION_DEAL, acquisition);

						List<String> buyers = patentToBuyersMap.get(patent);
						List<String> sellers = patentToSellersMap.get(patent);
						if(buyers!=null)nestedData.put(Constants.BUYER, buyers);
						if(sellers!=null)nestedData.put(Constants.SELLER, sellers);
						compDBAssetToNestedDataMap.putIfAbsent(patent, Collections.synchronizedList(new ArrayList<>()));
						compDBAssetToNestedDataMap.get(patent).add(nestedData);
					});
				}
			}

		}

		// patent set
		compDBPAssets = Collections.synchronizedCollection(new HashSet<>(compDBAssetToNestedDataMap.keySet()));

		rs.close();
		ps.close();

		Database.trySaveObject(compDBAssetToNestedDataMap,compDBAssetToNestedDataMapFile);
		Database.trySaveObject(compDBPAssets,compDBPAssetsFile);
		Database.trySaveObject(compDBTechnologies,compDBTechnologiesFile);
		System.out.println("Total number of technologies: "+compDBTechnologies.size());
	}


	public static void main(String[] args) {
		// updates the database
		try {
			loadGatherPatentToStages();
			Database.trySaveObject(gatherPatentToStagesCompleteMap,gatherPatentToStagesCompleteFile);
		} catch(Exception e) {
			e.printStackTrace();
		}


		try {
			loadGatherTechMap();
			Database.trySaveObject(gatherTechnologyToPatentMap,gatherTechnologyToPatentFile);
			Database.trySaveObject(gatherPatentToTechnologyMap,gatherPatentToTechnologyFile);
		} catch(SQLException sql) {
			sql.printStackTrace();
		}

		try {
			gatherValueMap = loadGatherValueMap();
			Database.trySaveObject(gatherValueMap,gatherValueMapFile);
		} catch(SQLException sql) {
			sql.printStackTrace();
		}

		gatherBoolValueMap = Collections.synchronizedMap(new HashMap<>());
		gatherValueMap.entrySet().parallelStream().forEach(e->{
			boolean val = false;
			if(e.getValue()>=4 || (e.getValue()==3 && gatherPatentToStagesCompleteMap.getOrDefault(e.getKey(), Collections.emptyList()).contains(Constants.GATHER_MA))) {
				val = true;
			}
			gatherBoolValueMap.put(e.getKey(),val);
		});
		Database.trySaveObject(gatherBoolValueMap,gatherBoolValueFile);

		try {
			gatherAssets = loadGatherAssets();
			Database.trySaveObject(gatherAssets,gatherAssetsFile);
		} catch(SQLException sql) {
			sql.printStackTrace();
		}

		try {
			compdbReelFrames = loadCompDBReelFrames();
			Database.trySaveObject(compdbReelFrames,compdbReelFramesFile);
		} catch(SQLException sql) {
			sql.printStackTrace();
		}

		try {
			loadReelFramesToCompDBBuyersAndSellers();
		} catch(SQLException sql) {
			sql.printStackTrace();
		}

		allClassCodes = Collections.synchronizedSet(new HashSet<>());
		getPatentToClassificationMap().values().parallelStream().forEach(classSet -> {
			classSet.forEach(cpcClass -> {
				allClassCodes.add(cpcClass);
			});
		});
		trySaveObject(allClassCodes,allClassCodesFile);
	}

	public static void check(String name, Collection<String> collection) {
		System.out.println(name+": "+collection.stream().collect(Collectors.summingInt(str->str.length()!=11?1:0))+ ", Total: "+collection.size());
	}
}
