package seeding;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;

import lombok.Getter;
import net.lingala.zip4j.core.ZipFile;
import org.deeplearning4j.berkeley.Pair;
import seeding.ai_db_updater.handlers.flags.Flag;
import seeding.compdb.CreateCompDBAssigneeTransactionData;
import tools.AssigneeTrimmer;
import user_interface.ui_models.attributes.hidden_attributes.*;
import user_interface.ui_models.portfolios.PortfolioList;

import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


public class Database {
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
	private static File gatherPatentToStagesCompleteFile = new File(Constants.DATA_FOLDER+"gather_patent_to_stages_complete_map.jobj");
	@Getter
	public static Map<String,Set<String>> classCodeToPatentMap;
	public static Map<String,LocalDate> patentToPubDateMap;
	public static Map<String,LocalDate> appToPriorityDateMap;
	public static final File patentToPubDateMapFile = new File(Constants.DATA_FOLDER+"patent_to_pubdate_map_file.jobj");
	public static final File classCodeToPatentMapFile = new File(Constants.DATA_FOLDER+"class_code_to_patent_map.jobj");
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
	private static final File gatherValueMapFile = new File(Constants.DATA_FOLDER+"gather_patent_to_value_bool_map.jobj");
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


	public static void ingestRecords(String patentNumber, Collection<String> assigneeData, List<List<String>> documents) throws SQLException {
		if(patentNumber==null)return;
		String queryPrefix = "INSERT INTO paragraph_tokens (pub_doc_number,assignees,tokens) VALUES ";
		String querySuffix = " ON CONFLICT DO NOTHING";
		String query = queryPrefix;
		for(int i = 0; i < documents.size(); i++) {
			query += "(?,?,?)";
			if(i!=documents.size()-1)
				query += ", ";
		}
		query += querySuffix;
		PreparedStatement ps = conn.prepareStatement(query);
		final Collection<String> cleanAssigneeData = assigneeData==null ? Collections.emptySet() : assigneeData;
		Array assigneeArray = conn.createArrayOf("varchar", cleanAssigneeData.toArray());
		for(int i = 0; i < documents.size(); i++) {
			List<String> doc = documents.get(i);
			ps.setString(1+(i*3), patentNumber);
			ps.setArray(2+(i*3), assigneeArray);
			ps.setArray(3+(i*3), conn.createArrayOf("varchar", doc.toArray()));
		}
		ps.executeUpdate();
		ps.close();
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

	public static void ingestTextRecords(String patentNumber, PortfolioList.Type type, List<String> documents) throws SQLException {
		if(patentNumber==null||documents.isEmpty())return;
		String query = "INSERT INTO patents_and_applications (pub_doc_number,doc_type,tokens) VALUES (?,?,to_tsvector('english', ";
		for(int i = 0; i < documents.size(); i++) {
			query += "coalesce(?,' ')";
			if(i!=documents.size()-1) query += " || ' ' || ";
		}
		query+= ")) ON CONFLICT DO NOTHING";
		PreparedStatement ps = conn.prepareStatement(query);
		ps.setString(1,patentNumber);
		ps.setString(2,type.toString());
		for(int i = 0; i < documents.size(); i++) {
			ps.setString(3+i, documents.get(i));
		}
		//    System.out.println("Prepared Query: "+ps);
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
			// load dependent objects
			File classCodeTempFile = new File(Constants.DATA_FOLDER+"all_cpc_codes.jobj");
			if(!classCodeTempFile.exists()) {
				allClassCodes = new AssetToCPCMap().getApplicationDataMap().values().parallelStream().flatMap(set->set.stream()).distinct().collect(Collectors.toSet());
				Database.trySaveObject(allClassCodes,classCodeTempFile);
			} else {
				allClassCodes = (Set<String>) Database.tryLoadObject(classCodeTempFile);
			}
			// save to file
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
			//seedConn.setAutoCommit(false);
		}
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
		if(getAssigneeToPatentsMap().containsKey(assignee)) {
			patents.addAll(getAssigneeToPatentsMap().get(assignee));
		}
		return patents;
	}

	public synchronized static Collection<String> selectApplicationNumbersFromExactAssignee(String assignee) {
		Set<String> apps = new HashSet<>();
		if(getAssigneeToAppsMap().containsKey(assignee)) {
			apps.addAll(getAssigneeToAppsMap().get(assignee));
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

	private synchronized static Array getCompDBPatents() throws SQLException {
		Set<String> pubDocNums = new HashSet<>();
		PreparedStatement ps = compDBConn.prepareStatement("SELECT array_agg(distinct t.id) as technologies, array_agg(distinct (reel||':'||frame)) AS reelframes, r.deal_id FROM recordings as r inner join deals_technologies as dt on (r.deal_id=dt.deal_id) INNER JOIN technologies AS t ON (t.id=dt.technology_id)  WHERE inactive='f' AND asset_count < 25 AND r.deal_id IS NOT NULL AND t.name is not null AND t.id!=ANY(?) GROUP BY r.deal_id");
		ps.setArray(1, compDBConn.createArrayOf("int4",badCompDBTechnologyIds.toArray()));
		ResultSet rs = ps.executeQuery();
		while(rs.next()) {
			boolean valid = true;
			for (Integer tech : (Integer[]) rs.getArray(1).getArray()) {
				if (badCompDBTechnologyIds.contains(tech)) {
					valid = false;
					break;
				}
			}
			if (!valid) continue;

			Array reelFrames = rs.getArray(2);
			PreparedStatement ps2 = seedConn.prepareStatement("SELECT DISTINCT doc_number FROM patent_assignment_property_document WHERE (doc_kind='B1' OR doc_kind='B2') AND doc_number IS NOT NULL AND assignment_reel_frame=ANY(?)");
			ps2.setArray(1, reelFrames);
			ps2.setFetchSize(10);
			ResultSet inner = ps2.executeQuery();
			while (inner.next()) {
				pubDocNums.add(inner.getString(1));
			}
		}
		return seedConn.createArrayOf("varchar", pubDocNums.toArray());
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

	public synchronized static Map<String,Integer> getGatherValueMap() {
		if(gatherValueMap==null) {
			gatherValueMap=(Map<String,Integer>)Database.tryLoadObject(gatherValueMapFile);
		}
		return new HashMap<>(gatherValueMap);
	}

	public synchronized static Map<String,Integer> loadGatherValueMap() throws SQLException {
		Database.setupGatherConn();
		Database.setupSeedConn();
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
		Database.setupSeedConn();
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
		Database.setupSeedConn();
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



	public synchronized static Map<String, List<String>> getCompDBMap() throws SQLException {
		Database.setupCompDBConn();
		Database.setupSeedConn();
		Map<String, List<String>> patentToTechnologyHash = new HashMap<>();
		PreparedStatement ps = compDBConn.prepareStatement("SELECT array_agg(distinct t.id) as technologies, array_agg(distinct (reel||':'||frame)) AS reelframes, r.deal_id FROM recordings as r inner join deals_technologies as dt on (r.deal_id=dt.deal_id) INNER JOIN technologies AS t ON (t.id=dt.technology_id)  WHERE inactive='f' AND asset_count < 25 AND r.deal_id IS NOT NULL AND t.name is not null AND t.id!=ANY(?) GROUP BY r.deal_id");
		ps.setArray(1, compDBConn.createArrayOf("int4",badCompDBTechnologyIds.toArray()));
		Map<Integer, String> technologyMap = Database.compdbTechnologyMap();
		ResultSet rs = ps.executeQuery();
		while(rs.next()) {
			List<String> technologies = new ArrayList<>();
			boolean valid = true;
			for(Integer tech : (Integer[])rs.getArray(1).getArray()) {
				if(badCompDBTechnologyIds.contains(tech)) { valid=false; break;}
				technologies.add(technologyMap.get(tech));
			}
			if(!valid)continue;

			Array reelFrames = rs.getArray(2);
			PreparedStatement ps2 = seedConn.prepareStatement("SELECT array_agg(DISTINCT doc_number) FROM patent_assignment_property_document WHERE (doc_kind='B1' OR doc_kind='B2') AND doc_number IS NOT NULL AND assignment_reel_frame=ANY(?)");
			ps2.setArray(1, reelFrames);
			ps2.setFetchSize(10);
			// Collect patent numbers
			ResultSet rs2 = ps2.executeQuery();
			if(rs2.next()) {
				Array sqlArray = rs2.getArray(1);
				if(sqlArray!=null) {
					List<String> toAdd = Arrays.asList((String[])sqlArray.getArray());
					if(toAdd!=null && !toAdd.isEmpty()) {
						technologies.forEach(tech->{
							if(!patentToTechnologyHash.containsKey(tech)) {
								patentToTechnologyHash.put(tech, new ArrayList<>());
							}
							List<String> list = patentToTechnologyHash.get(tech);
							list.addAll(toAdd);
						});
					}
				}

			}
			rs2.close();
			ps2.close();
		}
		rs.close();
		ps.close();
		Database.close();
		return patentToTechnologyHash;
	}


	public static void main(String[] args) {
		// updates the database

		try {
			loadGatherPatentToStages();
			Database.trySaveObject(gatherPatentToStagesCompleteMap,gatherPatentToStagesCompleteFile);
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
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

		allClassCodes = Collections.synchronizedSet(new HashSet<>());
		getPatentToClassificationMap().values().parallelStream().forEach(classSet -> {
			classSet.forEach(cpcClass -> {
				allClassCodes.add(cpcClass);
			});
		});
		trySaveObject(allClassCodes,allClassCodesFile);

		classCodeToPatentMap = Collections.synchronizedMap(new HashMap<>());
		getPatentToClassificationMap().entrySet().parallelStream().forEach((e) -> {
			String patent = e.getKey();
			Collection<String> classes = e.getValue();
			classes.forEach(klass -> {
				if (classCodeToPatentMap.containsKey(klass)) {
					classCodeToPatentMap.get(klass).add(patent);
				} else {
					Set<String> patents = new HashSet<>();
					patents.add(patent);
					classCodeToPatentMap.put(klass, patents);
				}
			});
		});
		trySaveObject(classCodeToPatentMap,classCodeToPatentMapFile);

	}

	public static void check(String name, Collection<String> collection) {
		System.out.println(name+": "+collection.stream().collect(Collectors.summingInt(str->str.length()!=11?1:0))+ ", Total: "+collection.size());
	}
}
