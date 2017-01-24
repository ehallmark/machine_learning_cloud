package seeding;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultCharArrayNodeFactory;
import com.googlecode.concurrenttrees.suffix.ConcurrentSuffixTree;
import com.googlecode.concurrenttrees.suffix.SuffixTree;
import edu.stanford.nlp.util.Pair;
import tools.AssigneeTrimmer;
import tools.Emailer;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;


public class Database {
	private static Map<String,Set<String>> patentToClassificationMap;
	private static Map<String,List<String>> patentToOriginalAssigneeMap;
	private static Map<String,String> patentToInventionTitleMap;
	private static Map<String,List<String>> patentToLatestAssigneeMap;
	private static Map<String,Set<String>>  assigneeToPatentsMap;
	private static Set<String> expiredPatentSet;
	private static Set<String> allAssignees;
	private static Set<String> valuablePatents;
	private static Set<String> allClassCodes;
	private static File patentToClassificationMapFile = new File("patent_to_classification_map.jobj");
	private static File patentToInventionTitleMapFile = new File("patent_to_invention_title_map.jobj");
	private static File patentToLatestAssigneeMapFile = new File("patent_to_assignee_map_latest.jobj");
	private static File patentToOriginalAssigneeMapFile = new File("patent_to_original_assignee_map.jobj");
	private static File assigneeToPatentsMapFile = new File("assignee_to_patents_map.jobj");
	private static File expiredPatentSetFile = new File("expired_patents_set.jobj");
	public static final String gatherTechnologyPrefix = "Gather Updated Tech -";
	private static final String patentDBUrl = "jdbc:postgresql://localhost/patentdb?user=postgres&password=&tcpKeepAlive=true";
	private static final String compDBUrl = "jdbc:postgresql://localhost/compdb_production?user=postgres&password=&tcpKeepAlive=true";
	private static final String gatherDBUrl = "jdbc:postgresql://localhost/gather_production?user=postgres&password=&tcpKeepAlive=true";
	private static Connection seedConn;
	private static Connection compDBConn;
	private static Connection gatherDBConn;
	private static final String selectGatherRatingsQuery = "select a.patent_rating,array_agg(p.number) as avg_patent_rating from assessments as a join patents as p on (p.id=a.patent_id) where patent_rating is not null and a.type = 'PublishedAssessment'  group by a.patent_rating";
	private static final String selectAllCandidateSets = "SELECT name, id FROM candidate_sets";
	private static final String selectGatherTechnologiesQuery = "select array_agg(distinct number), upper(name) from (select case when t.name like '%rs' then substring(t.name from 1 for char_length(t.name)-1) else replace(t.name,'-','') end as name, p.number as number from patents as p join assessments as a on (p.id=a.patent_id) join assessment_technologies as at on (a.id=at.assessment_id) join technologies as t on (at.technology_id=t.id) where char_length(coalesce(t.name,'')) > 0 and (not upper(t.name)='AUDIT')) as temp group by upper(name) having array_length(array_agg(number), 1) > 0";
	private static final Set<Integer> badCompDBTechnologyIds = new HashSet<>(Arrays.asList(136,182,301,316,519,527));

	private static Object tryLoadObject(File file) {
		try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) {
			Object toReturn = ois.readObject();
			ois.close();
			return toReturn;
		} catch(Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Unable to open file: "+file.getPath());
		}

	}

	static {
		patentToClassificationMap = Collections.unmodifiableMap((Map<String,Set<String>>)tryLoadObject(patentToClassificationMapFile));
		patentToInventionTitleMap = Collections.unmodifiableMap((Map<String,String>)tryLoadObject(patentToInventionTitleMapFile));
		patentToLatestAssigneeMap = Collections.unmodifiableMap((Map<String,List<String>>)tryLoadObject(patentToLatestAssigneeMapFile));
		patentToOriginalAssigneeMap = Collections.unmodifiableMap((Map<String,List<String>>)tryLoadObject(patentToOriginalAssigneeMapFile));
		assigneeToPatentsMap = Collections.unmodifiableMap((Map<String,Set<String>>)tryLoadObject(assigneeToPatentsMapFile));
		expiredPatentSet = Collections.unmodifiableSet((Set<String>)tryLoadObject(expiredPatentSetFile));

		// load dependent objects
		valuablePatents=new HashSet<>();
		patentToInventionTitleMap.keySet().forEach(patent->{
			if(!expiredPatentSet.contains(patent)) valuablePatents.add(patent);
		});
		allAssignees=assigneeToPatentsMap.keySet();
		allClassCodes=new HashSet<>();
		patentToClassificationMap.values().forEach(classSet->{
			classSet.forEach(cpcClass->{
				allClassCodes.add(cpcClass);
			});
		});
	}

	public static Set<String> getClassCodes() { return new HashSet<>(allClassCodes); }

	public static Set<String> getAssignees() {return new HashSet<>(allAssignees);}

	public static void setupGatherConn() throws SQLException {
		gatherDBConn = DriverManager.getConnection(gatherDBUrl);
		gatherDBConn.setAutoCommit(false);
	}

	public static void setupSeedConn() throws SQLException {
		if(seedConn==null) {
			seedConn = DriverManager.getConnection(patentDBUrl);
			seedConn.setAutoCommit(false);
		}
	}

	public static void setupCompDBConn() throws SQLException {
		compDBConn = DriverManager.getConnection(compDBUrl);
		compDBConn.setAutoCommit(false);
	}

	public static int getAssetCountFor(String assignee) {
		return selectPatentNumbersFromAssignee(assignee).size();
	}

	public static String getInventionTitleFor(String patent) {
		if(patentToInventionTitleMap.containsKey(patent)) {
			return patentToInventionTitleMap.get(patent);
		} else {
			return "";
		}
	}

	public static Set<String> possibleNamesForAssignee(String base) {
		final String cleanBase = AssigneeTrimmer.standardizedAssignee(base);
		Set<String> possible = new HashSet<>();
		allAssignees.forEach(assignee->{
			if(assignee.startsWith(cleanBase)) {
				possible.add(assignee);
			}
		});
		return possible;
	}

	public static Set<String> classificationsFor(String patent) {
		Set<String> classifications = new HashSet<>();
		if(patentToClassificationMap.containsKey(patent)) {
			classifications.addAll(patentToClassificationMap.get(patent));
		}
		return classifications;
	}

	public static Set<String> assigneesFor(String patent) {
		Set<String> assignees = new HashSet<>();
		if(patentToLatestAssigneeMap.containsKey(patent)) {
			assignees.addAll(patentToLatestAssigneeMap.get(patent));
		} else if (patentToOriginalAssigneeMap.containsKey(patent)) {
			assignees.addAll(patentToOriginalAssigneeMap.get(patent));
		}
		return assignees;
	}

	public static List<Pair<String,Integer>> selectGatherCandidateSetsWithIds() throws SQLException {
		List<Pair<String,Integer>> pairs = new ArrayList<>();
		ResultSet rs = selectAllCandidateSets();
		while(rs.next()) {
			String name = rs.getString(1);
			if(name==null||!name.startsWith(gatherTechnologyPrefix)) continue;
			else {
				pairs.add(new Pair<>(name.replaceFirst(gatherTechnologyPrefix,""),rs.getInt(2)));
			}
		}
		return pairs;
	}


	public static int createCandidateSetAndReturnId(String name) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement("INSERT INTO candidate_sets (name) VALUES (?)");
		ps.setString(1,name);
		ps.executeUpdate();
		seedConn.commit();
		ps = seedConn.prepareStatement("SELECT max(id) from candidate_sets");
		ResultSet rs = ps.executeQuery();
		if(rs.next()) {
			return rs.getInt(1);
		} else {
			throw new RuntimeException("No maximum id found");
		}
	}

	public static void createCandidateGroup(String groupPrefix) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement("INSERT INTO candidate_set_groups (group_prefix) VALUES (?)");
		ps.setString(1, groupPrefix);
		ps.executeUpdate();
		ps.close();
		seedConn.commit();
	}


	public static ResultSet selectAllCandidateSets() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(selectAllCandidateSets);
		return ps.executeQuery();
	}

	public static ResultSet selectGroupedCandidateSets() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement("select group_prefix from candidate_set_groups");
		return ps.executeQuery();
	}

	public static Integer tryFindGatherTechnologyCandidateSetId(String tech) {
		try {
			if(!tech.startsWith(gatherTechnologyPrefix)) tech = gatherTechnologyPrefix + " " + tech;
			PreparedStatement ps = seedConn.prepareStatement("select id from candidate_sets where name ilike ?");
			ps.setString(1,tech);
			ResultSet rs = ps.executeQuery();
			if(rs.next()) {
				return rs.getInt(1);
			} else {
				return null;
			}
		} catch(Exception e) {
			e.printStackTrace();
			new Emailer("In tryFindGatherTechnologyCandidateSetId: "+e.toString());
			throw new RuntimeException(e);
		}
	}


	public static Collection<String> getValuablePatents() {
		return new HashSet<>(valuablePatents);
	}


	public static Collection<String> selectPatentNumbersFromAssignee(String assignee){
		final String cleanAssignee = AssigneeTrimmer.standardizedAssignee(assignee);
		Set<String> patents = new HashSet<>();
		// try fuzzy search
		assigneeToPatentsMap.keySet().forEach(a->{
			if(a.startsWith(cleanAssignee)) {
				patents.addAll(assigneeToPatentsMap.get(a));
			}
		});
		return patents;
	}

	public static Set<String> patentsWithKeywords(List<String> patents, String[] keywords) throws SQLException {
		Set<String> validPatents = new HashSet<>();
		List<String> cleanKeywords = Arrays.stream(keywords).filter(keyword->keyword!=null&&keyword.trim().length()>0).map(keyword->keyword.trim().toLowerCase()).collect(Collectors.toList());
		PreparedStatement ps = seedConn.prepareStatement("SELECT distinct pub_doc_number FROM paragraph_tokens WHERE pub_doc_number=ANY(?) and tokens && ?");
		ps.setFetchSize(5);
		ps.setArray(1,seedConn.createArrayOf("varchar",patents.toArray()));
		ps.setArray(2,seedConn.createArrayOf("varchar",cleanKeywords.toArray()));
		System.out.println(ps.toString());

		ResultSet rs = ps.executeQuery();
		while(rs.next()) {
			String patent = rs.getString(1);
			validPatents.add(patent);
		}
		return validPatents;
	}

	public static Set<String> patentsWithAllKeywords(List<String> patents, String[] keywords) throws SQLException {
		Set<String> validPatents = new HashSet<>();
		List<String> cleanKeywords = Arrays.stream(keywords).filter(keyword->keyword!=null&&keyword.trim().length()>0).map(keyword->keyword.trim().toLowerCase()).collect(Collectors.toList());
		PreparedStatement ps = seedConn.prepareStatement("SELECT pub_doc_number FROM paragraph_tokens WHERE pub_doc_number=ANY(?) and tokens @> ?");
		ps.setFetchSize(5);
		ps.setArray(1,seedConn.createArrayOf("varchar",patents.toArray()));
		ps.setArray(2,seedConn.createArrayOf("varchar",cleanKeywords.toArray()));
		System.out.println(ps.toString());
		ResultSet rs = ps.executeQuery();
		while(rs.next()) {
			String patent = rs.getString(1);
			validPatents.add(patent);
		}
		return validPatents;
	}

	public static boolean isExpired(String patent) {
		return expiredPatentSet.contains(patent);
	}

	public static void close(){
		try {
			if(seedConn!=null && !seedConn.isClosed())seedConn.close();
			if(compDBConn!=null && !compDBConn.isClosed())compDBConn.close();
			if(gatherDBConn!=null && !gatherDBConn.isClosed()) gatherDBConn.close();
		} catch(SQLException sql) {
			sql.printStackTrace();
		}
	}

	private static Array getCompDBPatents() throws SQLException {
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


	private static Map<Integer,String> compdbTechnologyMap() throws SQLException {
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

	public static Map<String,List<String>> getGatherTechMap() throws SQLException {
		Database.setupGatherConn();
		//Database.setupSeedConn();
		Map<String, List<String>> techToPatentMap = new HashMap<>();
		PreparedStatement ps = gatherDBConn.prepareStatement(selectGatherTechnologiesQuery);
		ps.setFetchSize(10);
		//ps.setArray(1, gatherDBConn.createArrayOf("int4",badTech.toArray()));
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			techToPatentMap.put(rs.getString(2),Arrays.asList((String[])rs.getArray(1).getArray()));
		}
		ps.close();
		Database.close();
		return techToPatentMap;
	}

	public static org.deeplearning4j.berkeley.Pair<Map<String,List<String>>,Map<String,List<String>>> getGatherTechTestAndTrain() throws SQLException {
		Database.setupGatherConn();
		//Database.setupSeedConn();
		Random random = new Random(41);
		Map<String, List<String>> testTechToPatentMap = new HashMap<>();
		Map<String, List<String>> trainTechToPatentMap = new HashMap<>();
		PreparedStatement ps = gatherDBConn.prepareStatement(selectGatherTechnologiesQuery);
		ps.setFetchSize(10);
		//ps.setArray(1, gatherDBConn.createArrayOf("int4",badTech.toArray()));
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			List<String> patents = Arrays.asList((String[])rs.getArray(1).getArray());
			Collections.shuffle(patents,random);
			if(patents.size() >=2) {
				String label = rs.getString(2);
				List<String> testPatents = new ArrayList<>(patents.size()/2+1);
				List<String> trainPatents = new ArrayList<>(patents.size()/2+1);
				for(int i = 0; i < patents.size(); i++) {
					if(i%2==0) {
						testPatents.add(patents.get(i));
					} else {
						trainPatents.add(patents.get(i));
					}
				}
				testTechToPatentMap.put(label, testPatents);
				trainTechToPatentMap.put(label, trainPatents);
			}
		}
		ps.close();
		Database.close();
		return new org.deeplearning4j.berkeley.Pair<>(trainTechToPatentMap,testTechToPatentMap);
	}

	public static Map<String,List<String>> getGatherRatingsMap() throws SQLException {
		Database.setupGatherConn();
		Database.setupSeedConn();
		Map<String, List<String>> map = new HashMap<>();
		PreparedStatement ps = gatherDBConn.prepareStatement(selectGatherRatingsQuery);
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			map.put(String.valueOf(rs.getInt(1)),Arrays.asList((String[])rs.getArray(2).getArray()));
		}
		ps.close();
		Database.close();
		return map;
	}

	public static Map<String, List<String>> getCompDBMap() throws SQLException {
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


}
