package seeding;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;


public class Database {
	private static final String patentDBUrl = "jdbc:postgresql://192.168.1.148/patentdb?user=postgres&password=&tcpKeepAlive=true";
	private static final String compDBUrl = "jdbc:postgresql://192.168.1.148/compdb_production?user=postgres&password=&tcpKeepAlive=true";
	private static Connection seedConn;
	private static Connection mainConn;
	private static Connection compDBConn;
	private static Connection insertConn;
	private static PreparedStatement insertStatement;
	private static PreparedStatement createTokensStatement;
	private static final String addOrUpdateWord = "INSERT INTO patent_words (word,count) VALUES (?,1) ON CONFLICT (word) DO UPDATE SET (count)=(patent_words.count+1) WHERE patent_words.word=?";
	private static final String valuablePatentsQuery = "SELECT distinct r.pub_doc_number from patent_assignment as p join patent_assignment_property_document as q on (p.assignment_reel_frame=q.assignment_reel_frame) join patent_grant as r on (q.doc_number=r.pub_doc_number) join patent_grant_maintenance as m on (r.pub_doc_number=m.pub_doc_number) where conveyance_text like 'ASSIGNMENT OF ASSIGNOR%' and pub_date > to_char(now()::date, 'YYYYMMDD')::int-100000 AND (doc_kind='B1' or doc_kind='B2') group by r.pub_doc_number having (not array_agg(trim(trailing ' ' from maintenance_event_code))&&'{\"EXP.\"}'::text[]) AND array_length(array_agg(distinct recorded_date),1) >= 1";
	private static final String patentVectorStatement = "SELECT pub_doc_number, substring(description FROM 1 FOR ?) FROM patent_grant WHERE pub_doc_number=any(?) AND description IS NOT NULL";
	private static final String claimVectorStatement = "SELECT pub_doc_number||'_claim_'||number::text, claim_text FROM patent_grant_claim WHERE pub_doc_number=any(?) AND claim_text IS NOT NULL and parent_claim_id is NULL";
	private static final String patentVectorDataByPubDocNumbers = "SELECT pub_doc_number, pub_date, invention_title, abstract, substring(description FROM 1 FOR ?) FROM patent_grant WHERE pub_doc_number = ANY(?) AND (abstract is NOT NULL OR description IS NOT NULL OR invention_title IS NOT NULL)";
	private static final String distinctClassificationsStatement = "SELECT distinct main_class FROM us_class_titles";
	private static final String classificationsFromPatents = "SELECT pub_doc_number, array_agg(distinct substring(classification_code FROM 1 FOR 3)), array_to_string(array_agg(class), ' '), array_to_string(array_agg(subclass), ' ') FROM patent_grant_uspto_classification WHERE pub_doc_number=ANY(?) AND classification_code IS NOT NULL group by pub_doc_number";
	private static final String claimsFromPatents = "SELECT pub_doc_number, array_agg(claim_text), array_agg(number) FROM patent_grant_claim WHERE pub_doc_number=ANY(?) AND claim_text IS NOT NULL and parent_claim_id is null group by pub_doc_number";
	private static final String claimsFromPatent = "SELECT claim_text FROM patent_grant_claim WHERE uid > 55000000 AND claim_text IS NOT NULL and parent_claim_id is null";
	private static final String allPatentsAfterGivenDate = "SELECT array_agg(pub_doc_number), pub_date FROM patent_grant where pub_date >= ? group by pub_date order by pub_date";
	private static final String allPatentsArray = "SELECT array_agg(pub_doc_number) FROM patent_grant where pub_date >= ?";
	private static final String insertPatentVectorsQuery = "INSERT INTO raw_patents_clone (name,vector) VALUES (?,?) ON CONFLICT(name) DO UPDATE SET vector=? WHERE raw_patents_clone.name=?";
	private static final String updateTestingData = "UPDATE patent_vectors SET is_testing='t' WHERE pub_doc_number like '%7'";
	private static final String updateTrainingData = "UPDATE patent_vectors SET is_testing='f' WHERE is_testing!='f'";
	private static final String updateDateStatement = "UPDATE last_vectors_ingest SET pub_date=? WHERE program_name=?";
	private static final String selectDateStatement = "SELECT pub_date FROM last_vectors_ingest WHERE program_name=?";
	private static final String selectVectorsStatement = "SELECT p.pub_doc_number, coalesce(abstract, ''), coalesce(description, ''), array_to_string(array_agg(claim_text), ' ') FROM patent_grant as p join patent_grant_claim as q on (p.pub_doc_number=q.pub_doc_number) WHERE p.pub_doc_number=ANY(?) and q.pub_doc_number=ANY(?) group by p.pub_doc_number";
	//private static final String selectAllVectorsStatement = "SELECT pub_doc_number, vector FROM raw_patents";
	private static final String selectSingleVectorStatement = "SELECT coalesce(abstract, ''), coalesce(description, ''), array_to_string(array_agg(coalesce(claim_text, '')), ' '), p.pub_doc_number FROM patent_grant as p join patent_grant_claim as q on (p.pub_doc_number=q.pub_doc_number) WHERE p.pub_doc_number=? and q.pub_doc_number=? group by p.pub_doc_number";

	private static final String selectAllCandidateSets = "SELECT name, id FROM candidate_sets";
	private static final String selectPatentNumbersByAssignee = "select distinct doc_number from (select distinct on (p.doc_number) p.doc_number,name,q.uid from patent_assignment_property_document as p join patent_assignment_assignee as q on (p.assignment_reel_frame=q.assignment_reel_frame) where (p.doc_kind='B1' or p.doc_kind='B2') and upper(name) like upper(?)||'%' order by p.doc_number,q.uid desc) as temp join patent_assignment_assignee as a on (temp.uid=a.uid and upper(a.name) like upper(?)||'%')";
	private static PreparedStatement updateParagraphVectorStatement;
	private static PreparedStatement insertBOWStatement;
	private static PreparedStatement updateTFIDFStatement;
	private static final Set<Integer> badTech = new HashSet<>(Arrays.asList(136,182,301,316,519,527));

	public static void setupMainConn() throws SQLException {
		mainConn = DriverManager.getConnection(patentDBUrl);
		mainConn.setAutoCommit(false);
	}


	public static void setupInsertConn() throws SQLException {
		insertConn = DriverManager.getConnection(patentDBUrl);
		insertConn.setAutoCommit(false);
	}

	public static void setupSeedConn() throws SQLException {
		seedConn = DriverManager.getConnection(patentDBUrl);
		seedConn.setAutoCommit(false);
	}

	public static void setupCompDBConn() throws SQLException {
		compDBConn = DriverManager.getConnection(compDBUrl);
		compDBConn.setAutoCommit(false);
	}

	public static ResultSet selectMainClassWords() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement("SELECT title FROM us_class_titles");
		ps.setFetchSize(5);
		return ps.executeQuery();
	}

	public static ResultSet selectSubClassWords() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement("SELECT title FROM us_subclass_titles");
		ps.setFetchSize(5);
		return ps.executeQuery();
	}

	public static int selectRawPatentCount() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement("SELECT count(temp.*) from (select distinct split_part(name, '_', 1) from raw_patents) as temp");
		System.out.println(ps);
		ResultSet rs = ps.executeQuery();
		if(rs.next()) {
			Integer result = rs.getInt(1);
			if(result!=null) {
				System.out.println("Total number of documents: "+result);
				return result;
			}
		}
		return 0;
	}

	public static int lengthOfBOW() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement("select array_length(bow,1) from bag_of_words limit 1");
		ResultSet rs = ps.executeQuery();
		if(rs.next()) {
			return rs.getInt(1);
		} else {
			throw new RuntimeException("NO ARRAYS FOUND IN BOW DATABASE MODEL!");
		}
	}

	public static void insertBOW(String name, Integer[] values) throws SQLException {
		if(insertBOWStatement==null)insertBOWStatement = insertConn.prepareStatement("insert into bag_of_words (name,bow) values (?,?) on conflict (name) do update set bow=vec_add(bag_of_words.bow,?) where bag_of_words.name=?");
		Array array = insertConn.createArrayOf("int4", values);
		insertBOWStatement.setString(1,name);
		insertBOWStatement.setArray(2, array);
		insertBOWStatement.setArray(3, array);
		insertBOWStatement.setString(4,name);
		insertBOWStatement.executeUpdate();
	}

	public static ResultSet selectBOW(int limit) throws SQLException {
		if(limit <= 0) {
			PreparedStatement ps = seedConn.prepareStatement("SELECT name,bow FROM bag_of_words");
			ps.setFetchSize(10);
			return ps.executeQuery();
		} else {
			PreparedStatement ps = seedConn.prepareStatement("SELECT name,bow FROM bag_of_words limit ?");
			ps.setFetchSize(10);
			ps.setInt(1,limit);
			return ps.executeQuery();
		}

	}

	public static ResultSet selectBOW() throws SQLException {
		return selectBOW(-1);
	}

	public static void updateTFIDF(String name, Float[] vector) throws SQLException {
		if(updateTFIDFStatement==null) updateTFIDFStatement = insertConn.prepareStatement("UPDATE bag_of_words SET tfidf=? WHERE name=?");
		updateTFIDFStatement.setArray(1, insertConn.createArrayOf("float4", vector));
		updateTFIDFStatement.setString(2, name);
		updateTFIDFStatement.executeUpdate();
	}

	public static ResultSet getTitleFor(String patentNumber) throws SQLException{
		PreparedStatement ps = seedConn.prepareStatement("SELECT invention_title FROM patent_grant where pub_doc_number=?");
		ps.setString(1, patentNumber);
		return ps.executeQuery();
	}

	public static String getTitleFromDB(String patentNumber) throws SQLException {
		ResultSet rs = Database.getTitleFor(patentNumber);
		if(!rs.next()) {
			return ""; // nothing found
		}
		String title = rs.getString(1);
		rs.close();
		return title;
	}

	public static void insertCommit() {
		try {
			insertConn.commit();
		}catch(SQLException sql) {
			sql.printStackTrace();
		}
	}

	public static void commit() {
		try {
			mainConn.commit();
		} catch(SQLException sql) {
			sql.printStackTrace();
		}
	}

	public static ResultSet selectRawPatentNames() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement("select name from raw_patents_clone");
		ps.setFetchSize(10);
		return ps.executeQuery();
	}

	public static void updateParagraphVectorFor(String patent, Float[] vector) throws SQLException {
		if(updateParagraphVectorStatement==null)updateParagraphVectorStatement = insertConn.prepareStatement("update raw_patents_clone set vector=? where name=?");
		updateParagraphVectorStatement.setArray(1, insertConn.createArrayOf("float4",vector));
		updateParagraphVectorStatement.setString(2,patent);
		updateParagraphVectorStatement.executeUpdate();
	}


	public static synchronized void insertRawPatent(String label, List<String> words) throws SQLException {
		if(insertStatement==null)insertStatement = insertConn.prepareStatement("insert into raw_patents_clone (name,words) values (?,?) on conflict (name) do update set words=? where raw_patents_clone.name=?");
		Array patents = insertConn.createArrayOf("text",words.toArray());
		insertStatement.setString(1,label);
		insertStatement.setArray(2,patents);
		insertStatement.setArray(3,patents);
		insertStatement.setString(4,label);
		insertStatement.executeUpdate();
	}

	public static int createCandidateSetAndReturnId(String name) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement("INSERT INTO candidate_sets (name) VALUES (?)");
		ps.setString(1,name);
		ps.executeUpdate();
		Database.commit();
		ps = mainConn.prepareStatement("SELECT max(id) from candidate_sets");
		ResultSet rs = ps.executeQuery();
		if(rs.next()) {
			return rs.getInt(1);
		} else {
			throw new RuntimeException("No maximum id found");
		}
	}

	public static void createCandidateGroup(String groupPrefix) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement("INSERT INTO candidate_set_groups (group_prefix) VALUES (?)");
		ps.setString(1, groupPrefix);
		ps.executeUpdate();
		ps.close();
		Database.commit();
	}

	public static void resetValuablePatents() throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement("UPDATE patent_vectors SET is_valuable='f' WHERE is_valuable='t'");
		ps.executeUpdate();
	}


	public static ResultSet selectAllCandidateSets() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(selectAllCandidateSets);
		return ps.executeQuery();
	}

	public static ResultSet selectGroupedCandidateSets() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement("select group_prefix from candidate_set_groups");
		return ps.executeQuery();
	}

	public static List<String> selectPatentNumbersFromAssignee(String assignee) throws SQLException{
		PreparedStatement ps = seedConn.prepareStatement(selectPatentNumbersByAssignee);
		ps.setString(1, assignee);
		ps.setString(2, assignee);
		System.out.println(ps);
		ResultSet rs = ps.executeQuery();
		List<String> patents = new LinkedList<>();
		while(rs.next()) {
			patents.add(rs.getString(1));
		}
		return patents;
	}


	public static ResultSet selectPatentVectors(List<String> patents) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(selectVectorsStatement);
		ps.setArray(1, seedConn.createArrayOf("varchar", patents.toArray()));
		ps.setArray(2, seedConn.createArrayOf("varchar", patents.toArray()));
		ps.setFetchSize(5);
		System.out.println(ps);
		return ps.executeQuery();
	}
	

	public static ResultSet getBaseVectorFor(String patent)throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(selectSingleVectorStatement);
		ps.setString(1, patent);
		ps.setString(2, patent);
		return ps.executeQuery();
	}


	public static void close(){
		try {
			if(mainConn!=null && !mainConn.isClosed())mainConn.close();
			if(seedConn!=null && !seedConn.isClosed())seedConn.close();
			if(compDBConn!=null && !compDBConn.isClosed())compDBConn.close();
			if(insertConn!=null && !insertConn.isClosed())insertConn.close();
		} catch(SQLException sql) {
			sql.printStackTrace();
		}
	}

	public static void updateTestingData() throws SQLException {
		PreparedStatement ps2 = mainConn.prepareStatement(updateTrainingData);
		ps2.executeUpdate();
		PreparedStatement ps = mainConn.prepareStatement(updateTestingData);
		ps.executeUpdate();
	}

	public static void updateValuablePatents(String patentNumber, boolean isValuable) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement("UPDATE patent_vectors SET is_valuable=? WHERE pub_doc_number=?");
		ps.setBoolean(1, isValuable);
		ps.setString(2, patentNumber);
		ps.executeUpdate();
	}

	public static ResultSet selectRawPatents() throws SQLException {
		return selectRawPatents(null);
	}

	public static ResultSet selectRawPatents(Collection<String> patents) throws SQLException {
		if(patents==null) {
			PreparedStatement ps = seedConn.prepareStatement("select name, words from raw_patents_clone");
			ps.setFetchSize(5);
			return ps.executeQuery();
		} else {
			PreparedStatement ps = seedConn.prepareStatement("select name, words from raw_patents_clone where name like ANY(?)");
			ps.setArray(1, seedConn.createArrayOf("varchar",patents.stream().map(p->p+"%").collect(Collectors.toList()).toArray()));
			ps.setFetchSize(5);
			return ps.executeQuery();
		}
	}

	public static void insertPatentParagraphVector(String pub_doc_number,Float[] paragraphVector) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement(insertPatentVectorsQuery);
		Array vectors = mainConn.createArrayOf("float4", paragraphVector);
		ps.setString(1, pub_doc_number);
		ps.setArray(2, vectors);
		ps.setArray(3, vectors);
		ps.setString(4, pub_doc_number);
		ps.executeUpdate();
	}

	public static ResultSet getPatentsBetween(int start) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(allPatentsAfterGivenDate);
		ps.setInt(1, start);
		ps.setFetchSize(10);
		System.out.println(ps);
		return ps.executeQuery();
	}

	// Only for the BasePatentIterator Class
	public static ResultSet getPatentVectorData(String[] array, boolean claims) throws SQLException {
		PreparedStatement ps;
		if(claims) {
			ps = seedConn.prepareStatement(claimVectorStatement);
			ps.setArray(1, seedConn.createArrayOf("varchar", array));
		}
		else {
			ps = mainConn.prepareStatement(patentVectorStatement);
			ps.setInt(1, Constants.MAX_DESCRIPTION_LENGTH);
			ps.setArray(2, mainConn.createArrayOf("varchar", array));
		}
		ps.setFetchSize(10);
		//System.out.println(ps);
		return ps.executeQuery();
	}


	private static Array getCompDBPatents() throws SQLException {
		Set<String> pubDocNums = new HashSet<>();
		PreparedStatement ps = compDBConn.prepareStatement("SELECT array_agg(distinct t.id) as technologies, array_agg(distinct (reel||':'||frame)) AS reelframes, r.deal_id FROM recordings as r inner join deals_technologies as dt on (r.deal_id=dt.deal_id) INNER JOIN technologies AS t ON (t.id=dt.technology_id)  WHERE inactive='f' AND asset_count < 25 AND r.deal_id IS NOT NULL AND t.name is not null AND t.id!=ANY(?) GROUP BY r.deal_id");
		ps.setArray(1, compDBConn.createArrayOf("int4",badTech.toArray()));
		ResultSet rs = ps.executeQuery();
		while(rs.next()) {
			boolean valid = true;
			for (Integer tech : (Integer[]) rs.getArray(1).getArray()) {
				if (badTech.contains(tech)) {
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

	public static ResultSet compdbPatentsGroupedByDate() throws SQLException{
		// patents loaded
		PreparedStatement ps2 = seedConn.prepareStatement("SELECT array_agg(pub_doc_number), pub_date FROM patent_grant WHERE pub_doc_number = ANY(?) group by pub_date");
		ps2.setArray(1, getCompDBPatents());
		return ps2.executeQuery();
	}


	public static List<Integer> getDistinctCompDBTechnologyIds() throws SQLException {
		List<Integer> technologies = new LinkedList<>();
		PreparedStatement ps = compDBConn.prepareStatement("SELECT DISTINCT id FROM technologies WHERE name is not null and char_length(name) > 0 and id != ANY(?) ORDER BY id");
		ps.setArray(1, compDBConn.createArrayOf("int4",Constants.BAD_TECHNOLOGY_IDS.toArray()));
		System.out.println(ps);
		ResultSet rs = ps.executeQuery();
		while(rs.next()) {
			technologies.add(rs.getInt(1));
		}
		return technologies;
	}

	public static void updateCompDBTechnologies(String patent, Double[] softmax) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement("UPDATE patent_vectors SET compdb_technologies=? WHERE pub_doc_number=?");
		ps.setArray(1,mainConn.createArrayOf("float8", softmax));
		ps.setString(2, patent);
		ps.executeUpdate();
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

	public static Map<String, List<String>> getCompDBMap() throws SQLException {
		Database.setupCompDBConn();
		Database.setupSeedConn();
		Map<String, List<String>> patentToTechnologyHash = new HashMap<>();
		PreparedStatement ps = compDBConn.prepareStatement("SELECT array_agg(distinct t.id) as technologies, array_agg(distinct (reel||':'||frame)) AS reelframes, r.deal_id FROM recordings as r inner join deals_technologies as dt on (r.deal_id=dt.deal_id) INNER JOIN technologies AS t ON (t.id=dt.technology_id)  WHERE inactive='f' AND asset_count < 25 AND r.deal_id IS NOT NULL AND t.name is not null AND t.id!=ANY(?) GROUP BY r.deal_id");
		ps.setArray(1, compDBConn.createArrayOf("int4",badTech.toArray()));
		Map<Integer, String> technologyMap = Database.compdbTechnologyMap();
		ResultSet rs = ps.executeQuery();
		while(rs.next()) {
			List<String> technologies = new ArrayList<>();
			boolean valid = true;
			for(Integer tech : (Integer[])rs.getArray(1).getArray()) {
				if(badTech.contains(tech)) { valid=false; break;}
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

	public static int getNumberOfCompDBClassifications() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement("select array_length(compdb_technologies, 1) from patent_vectors where compdb_technologies is not null limit 1");
		System.out.println(ps);
		ResultSet rs = ps.executeQuery();
		if(rs.next()) {
			return rs.getInt(1);
		} else {
			throw new RuntimeException("Unable to get number of compdb classifications!");
		}
	}

	public static List<String> getDistinctClassifications() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(distinctClassificationsStatement);
		ps.setFetchSize(100);
		ResultSet rs = ps.executeQuery();
		List<String> distinctClassifications = new ArrayList<>();
		Set<String> duplicationCheck = new HashSet<>();
		while(rs.next()) {
			String klass = rs.getString(1).trim();
			if(!duplicationCheck.contains(klass)) {
				distinctClassifications.add(klass);
				duplicationCheck.add(klass);
			}
		}
		ps.close();
		Collections.sort(distinctClassifications);
		return distinctClassifications;
	}


	private static ResultSet getValuablePatents() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(valuablePatentsQuery);
		ps.setFetchSize(10);
		return ps.executeQuery();
	}

	public static List<String> getValuablePatentsToList() throws SQLException {
		ResultSet rs = getValuablePatents();
		List<String> patentList = new LinkedList<>();
		while(rs.next()) {
			patentList.add(rs.getString(1));
		}
		return patentList;
	}


}
