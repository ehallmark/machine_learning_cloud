package seeding;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultCharArrayNodeFactory;
import elasticsearch.DataSearcher;
import models.assignee.normalization.name_correction.AssigneeTrimmer;
import org.elasticsearch.search.sort.SortOrder;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.ai_db_updater.handlers.flags.Flag;
import seeding.compdb.CreateCompDBAssigneeTransactionData;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.ReelFrameAttribute;
import user_interface.ui_models.attributes.hidden_attributes.*;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.items.Item;

import java.io.*;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.RecursiveAction;
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
	private static Map<String,Collection<String>>  assigneeToPatentsMap;
	private static Map<String,Collection<String>>  normalizedAssigneeToPatentsMap;
	private static Map<String,Collection<String>> assigneeToAppsMap;
	private static Map<String,Collection<String>> normalizedAssigneeToAppsMap;
	private static Map<String,Collection<String>> etsiStandardToPatentsMap;
	private static RadixTree<String> assigneePrefixTrie;
	private static RadixTree<String> normalizedAssigneePrefixTrie;
	private static RadixTree<String> classCodesPrefixTrie;
	public static Set<String> allAssignees;
	private static Set<String> allNormalizedAssignees;
	public static Set<String> gatherAssets;
	public static final File gatherAssetsFile = new File(Constants.DATA_FOLDER+"gather_assets_set.jobj");
	public static Set<String> compdbReelFrames;
	public static final File compdbReelFramesFile = new File(Constants.DATA_FOLDER+"compdb_reel_frames_set.jobj");
	public static Set<String> allClassCodes;
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
	public static File allClassCodesFile = new File(Constants.DATA_FOLDER+"all_class_codes.jobj");
	public static final File classCodeToClassTitleMapFile = new File(Constants.DATA_FOLDER+"class_code_to_class_title_map.jobj");
	private static final String patentDBUrl = "jdbc:postgresql://localhost/patentdb?user=postgres&password=password&tcpKeepAlive=true";
	private static final String compDBUrl = "jdbc:postgresql://localhost/compdb_production?user=postgres&password=password&tcpKeepAlive=true";
	private static final String gatherDBUrl = "jdbc:postgresql://localhost/gather_production?user=postgres&password=password&tcpKeepAlive=true";
	private static final String assigneeDBUrl = "jdbc:postgresql://localhost/assigneedb?user=postgres&password=password&tcpKeepAlive=true";
	public static Connection seedConn;
	private static Connection compDBConn;
	private static Connection gatherDBConn;
	private static Connection assigneeConn;

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

	public static List<String> searchBigQuery(String tableName, String search, String desiredField, int limit, String... fields) {
		StringJoiner where = new StringJoiner(" and ", "", "");
		StringJoiner order = new StringJoiner(", ", "", "");
		for (String field : fields) {
			where.add(field + " ilike '%' || ? || '%'");
			order.add("ts_rank(to_tsvector(?),to_tsquery('english', ?))");
		}
		order.add(desiredField);
		List<String> results = new ArrayList<>(limit);
		try {
			PreparedStatement ps = conn.prepareStatement("select " + desiredField + " from " + tableName + " where " + where.toString() + " order by " + order.toString() + " limit " + limit);
			ps.setFetchSize(limit);
			for(int i = 0; i < fields.length; i++) {
				ps.setString(1+i,fields[i]);
				ps.setString(1+fields.length+2*i,fields[i]);
				ps.setString(2+fields.length+2*i, search);
			}
			ResultSet rs = ps.executeQuery();
			while(rs.next()) {
				results.add(rs.getString(1));
			}
			rs.close();
			ps.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
		return results;
	}

	public static List<String> loadKeysFromDatabaseTable(Connection conn, String tableName, String field) {
		List<String> data = new LinkedList<>();
		try {
			PreparedStatement ps = conn.prepareStatement("select " + field + " from " + tableName);
			ps.setFetchSize(1000);
			ResultSet rs = ps.executeQuery();
			while(rs.next()) {
				data.add(rs.getString(1));
			}
			rs.close();
			ps.close();
		} catch(Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Error loading key{"+field+"} from table{"+tableName+"}");
		}
		return data;
	}

	public static Collection<String> getAllFilings() {
		Set<String> collection = Collections.synchronizedSet(new HashSet<>(new FilingToAssetMap().getPatentDataMap().keySet()));
		collection.addAll(new FilingToAssetMap().getApplicationDataMap().keySet());
		return collection;
	}

	public static Map<String,INDArray> loadCPCVaeVectorsFor(List<String> assets) {
		return loadVectorsFor("big_query_embedding1","cpc_vae",assets);
	}

	public static Map<String,INDArray> loadVectorsFor(String tableName, String fieldName, List<String> assets) {
		Map<String,INDArray> data = new HashMap<>();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = conn.prepareStatement("select publication_number_full,"+fieldName+" from big_query_family_id as p join "+tableName+" as e on (p.family_id=e.family_id) where publication_number_full in ('" + String.join("','", assets) + "')");
			ps.setFetchSize(10);
			rs = ps.executeQuery();
			while(rs.next()) {
				data.put(rs.getString(1),Nd4j.create(Stream.of((Float[])rs.getArray(2).getArray()).mapToDouble(d->d).toArray()));
			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs!=null) {
					rs.close();
				}
				if(ps!=null) {
					ps.close();
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		return data;
	}

	public static Map<String,List<String>> loadTechnologiesFor(List<String> assets) {
		Map<String,List<String>> data = new HashMap<>();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = conn.prepareStatement("select publication_number_full,technology2  from big_query_family_id as p join big_query_technologies2 as t on (p.family_id=t.family_id) where technology2 is not null and publication_number_full in ('" + String.join("','", assets) + "')");
			ps.setFetchSize(10);
			rs = ps.executeQuery();
			while(rs.next()) {
				data.put(rs.getString(1),Collections.singletonList(rs.getString(2)));
			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs!=null) {
					rs.close();
				}
				if(ps!=null) {
					ps.close();
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		return data;
	}


	public static List<String> loadAllFilingsWithVectors() throws SQLException {

		PreparedStatement ps = conn.prepareStatement("select filing from sim_vectors");
		List<String> list = new LinkedList<>();
		ps.setFetchSize(100);
		ResultSet rs = ps.executeQuery();
		while(rs.next()) {
			list.add(rs.getString(1));
		}
		rs.close();
		ps.close();
		return list;
	}

	private static INDArray toINDArray(Double[] vec) {
	    double[] d = new double[vec.length];
	    for(int i = 0; i < d.length; i++) {
	        d[i]=vec[i];
        }
        return Nd4j.create(d);
    }

	public static Map<String,INDArray> loadVectorPredictions() throws SQLException {
	    PreparedStatement ps = conn.prepareStatement("select filing,vector from sim_vectors");
	    ps.setFetchSize(1000);
	    Map<String,INDArray> map = Collections.synchronizedMap(new HashMap<>());
	    ResultSet rs = ps.executeQuery();
	    int cnt = 0;
	    while(rs.next()) {
	        INDArray vec = toINDArray((Double[])rs.getArray(2).getArray());
	        map.put(rs.getString(1),vec);
	        cnt++;
	        if(cnt%10000==9999) {
	        	System.out.println("Loaded "+cnt+" vectors...");
			}
        }
	    return map;
    }

	public static RecursiveAction upsertVectors(Map<String,Double[]> map) {
		RecursiveAction action = new RecursiveAction() {
			@Override
			protected void compute() {
				try {
					List<Map.Entry<String, Double[]>> entries = new ArrayList<>(map.entrySet());
					PreparedStatement ps = conn.prepareStatement("insert into sim_vectors (filing,vector) values (?,?) on conflict (filing) do update set vector=?");
					for (int i = 0; i < entries.size(); i++) {
						Map.Entry<String, Double[]> entry = entries.get(i);
						ps.setString(1, entry.getKey());
						Array array = conn.createArrayOf("float8", entry.getValue());
						ps.setArray(2, array);
						ps.setArray(3, array);
						ps.executeUpdate();
						array.free();
					}
					ps.close();
					commit();
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		};
		return action;

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
		return tryLoadObject(file,true);
	}

	public static Object tryLoadObject(File file, boolean print) {
		if(print)System.out.println("Starting to load file: "+file.getName()+"...");
		try {
			ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
			Object toReturn = ois.readObject();
			ois.close();
			if(print)System.out.println("Successfully loaded "+file.getName()+".");
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
		getAssignees();
		getAssigneePrefixTrie();
		getAssigneeToPatentsMap();
		getAssigneeToAppsMap();
		getNormalizedAssignees();
		getNormalizedAssigneePrefixTrie();
		getNormalizedAssigneeToAppsMap();
		getNormalizedAssigneeToPatentsMap();
		getClassCodesPrefixTrie();
		getClassCodes();
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

	public synchronized static Map<String,Collection<String>> getNormalizedAssigneeToPatentsMap() {
		if(normalizedAssigneeToPatentsMap==null) {
			normalizedAssigneeToPatentsMap = new NormalizedAssigneeToAssetsMap().getPatentDataMap();
		}
		return normalizedAssigneeToPatentsMap;
	}

	public synchronized static Map<String,Collection<String>> getNormalizedAssigneeToAppsMap() {
		if(normalizedAssigneeToAppsMap==null) {
			normalizedAssigneeToAppsMap = new NormalizedAssigneeToAssetsMap().getApplicationDataMap();
		}
		return normalizedAssigneeToAppsMap;
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

	public synchronized static Set<String> getNormalizedAssignees() {
		if(allNormalizedAssignees==null) {
			allNormalizedAssignees=new HashSet<>(getNormalizedAssigneeToAppsMap().keySet());
			allNormalizedAssignees.addAll(getNormalizedAssigneeToPatentsMap().keySet());
		}
		return Collections.unmodifiableSet(allNormalizedAssignees);
	}


	public synchronized static void setupGatherConn() throws SQLException {
		gatherDBConn = DriverManager.getConnection(gatherDBUrl);
		gatherDBConn.setAutoCommit(false);
	}

	public synchronized static Connection getOrSetupAssigneeConn() throws SQLException {
		if(assigneeConn==null||assigneeConn.isClosed()) {
			assigneeConn = DriverManager.getConnection(assigneeDBUrl);
		}
		return assigneeConn;
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
		return Math.max(getAssigneeToAppsMap().getOrDefault(assignee,Collections.emptySet()).size(),getAssigneeToPatentsMap().getOrDefault(assignee,Collections.emptySet()).size());
	}

	public synchronized static int getNormalizedAssetCountFor(String assignee) {
		return Math.max(getNormalizedAssigneeToAppsMap().getOrDefault(assignee,Collections.emptySet()).size(),getNormalizedAssigneeToPatentsMap().getOrDefault(assignee,Collections.emptySet()).size());
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


	public static RadixTree<String> getAssigneePrefixTrie() {
		// prefix trie for assignees
		if(assigneePrefixTrie==null) {
			System.out.println("Building assignee trie...");
			assigneePrefixTrie = new ConcurrentRadixTree<>(new DefaultCharArrayNodeFactory());
			getAssignees().forEach(assignee -> {
				if(assignee!=null&&assignee.length() > 0) {
					assigneePrefixTrie.put(assignee, assignee);
				}
			});
		}
		return assigneePrefixTrie;
	}

	public static RadixTree<String> getNormalizedAssigneePrefixTrie() {
		// prefix trie for assignees
		if(normalizedAssigneePrefixTrie==null) {
			System.out.println("Building assignee trie...");
			normalizedAssigneePrefixTrie = new ConcurrentRadixTree<>(new DefaultCharArrayNodeFactory());
			getNormalizedAssignees().forEach(assignee -> {
				if(assignee!=null&&assignee.length() > 0) {
					normalizedAssigneePrefixTrie.put(assignee, assignee);
				}
			});
		}
		return normalizedAssigneePrefixTrie;
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

	public synchronized static List<String> sortedPossibleAssignees(String base) {
		if(base==null||base.isEmpty()) return Collections.emptyList();
		final String cleanBase = AssigneeTrimmer.standardizedAssignee(base);
		if(cleanBase.isEmpty()) return Collections.emptyList();
		SortedSet<String> possible = new TreeSet<>();
		if(getAssignees().contains(cleanBase)) possible.add(cleanBase);
		getAssigneePrefixTrie().getValuesForKeysStartingWith(cleanBase).forEach(a->possible.add(a));
		return new ArrayList<>(possible);
	}

	public synchronized static List<String> sortedPossibleClassCodes(String base) {
		if(base==null||base.isEmpty()) return Collections.emptyList();
		base = base.toUpperCase();
		final String cleanBase = ClassCodeHandler.convertToLabelFormat(base);
		if(cleanBase.isEmpty()) return Collections.emptyList();
		SortedSet<String> possible = new TreeSet<>();
		if(getClassCodes().contains(cleanBase)) possible.add(cleanBase);
		getClassCodesPrefixTrie().getValuesForKeysStartingWith(cleanBase).forEach(a->possible.add(a));
		return new ArrayList<>(possible);
	}

	public synchronized static List<String> sortedPossibleNormalizedAssignees(String base) {
		if(base==null||base.isEmpty()) return Collections.emptyList();
		final String cleanBase = AssigneeTrimmer.standardizedAssignee(base);
		if(cleanBase.isEmpty()) return Collections.emptyList();
		SortedSet<String> possible = new TreeSet<>();
		if(getNormalizedAssignees().contains(cleanBase)) possible.add(cleanBase);
		getNormalizedAssigneePrefixTrie().getValuesForKeysStartingWith(cleanBase).forEach(a->possible.add(a));
		return new ArrayList<>(possible);
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

	public synchronized static Collection<String> selectPatentNumbersFromExactNormalizedAssignee(String assignee){
		Set<String> patents = new HashSet<>();
		if(normalizedAssigneeToPatentsMap == null) getNormalizedAssigneeToPatentsMap();
		if(normalizedAssigneeToPatentsMap.containsKey(assignee)) {
			patents.addAll(normalizedAssigneeToPatentsMap.get(assignee));
		}
		return patents;
	}

	public synchronized static Collection<String> selectApplicationNumbersFromExactNormalizedAssignee(String assignee) {
		Set<String> apps = new HashSet<>();
		if(normalizedAssigneeToAppsMap == null) getNormalizedAssigneeToAppsMap();
		if(normalizedAssigneeToAppsMap.containsKey(assignee)) {
			apps.addAll(normalizedAssigneeToAppsMap.get(assignee));
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


	public synchronized static Map<Integer,String> compdbTechnologyMap() throws SQLException {
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
		List<Item> items = DataSearcher.searchForAssets(Arrays.asList(new AssetNumberAttribute(), new ReelFrameAttribute()),Arrays.asList(new AbstractIncludeFilter(new ReelFrameAttribute(), AbstractFilter.FilterType.Include, AbstractFilter.FieldType.Text, allCompDBReelFrames)),null, SortOrder.ASC, 100000, Collections.emptyMap(),false,false);
		Map<String,Collection<String>> reelFrameToAssetsMap = Collections.synchronizedMap(new HashMap<>());
		items.parallelStream().forEach(item->{
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
			allClassCodes.addAll(classSet);
		});
		getAppToClassificationMap().values().parallelStream().forEach(classSet -> {
			allClassCodes.addAll(classSet);
		});
		trySaveObject(allClassCodes,allClassCodesFile);
	}

	public static void check(String name, Collection<String> collection) {
		System.out.println(name+": "+collection.stream().collect(Collectors.summingInt(str->str.length()!=11?1:0))+ ", Total: "+collection.size());
	}

	public static Object tryLoadObjectOld(File file) {
		System.out.println("Starting to load file: "+file.getName()+"...");
		try {
			ObjectInputStream ois = new DecompressibleInputStream(new BufferedInputStream(new FileInputStream(file)));
			Object toReturn = ois.readObject();
			ois.close();
			return toReturn;
		} catch(Exception e) {
			e.printStackTrace();
			//throw new RuntimeException("Unable to open file: "+file.getPath());
			return null;
		}
	}
}


class DecompressibleInputStream extends ObjectInputStream {

	public DecompressibleInputStream(InputStream in) throws IOException {
		super(in);
	}


	protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
		ObjectStreamClass resultClassDescriptor = super.readClassDescriptor(); // initially streams descriptor
		Class localClass = Class.forName(resultClassDescriptor.getName()); // the class in the local JVM that this descriptor represents.
		if (localClass == null) {
			System.out.println("No local class for " + resultClassDescriptor.getName());
			return resultClassDescriptor;
		}
		ObjectStreamClass localClassDescriptor = ObjectStreamClass.lookup(localClass);
		if (localClassDescriptor != null) { // only if class implements serializable
			final long localSUID = localClassDescriptor.getSerialVersionUID();
			final long streamSUID = resultClassDescriptor.getSerialVersionUID();
			if (streamSUID != localSUID) { // check for serialVersionUID mismatch.
				final StringBuffer s = new StringBuffer("Overriding serialized class version mismatch: ");
				s.append("local serialVersionUID = ").append(localSUID);
				s.append(" stream serialVersionUID = ").append(streamSUID);
				Exception e = new InvalidClassException(s.toString());
				System.out.println("Potentially Fatal Deserialization Operation. " + e);
				resultClassDescriptor = localClassDescriptor; // Use local class descriptor for deserialization
			}
		}
		return resultClassDescriptor;
	}
}
