package seeding;

import java.sql.*;
import java.util.*;


public class Database {
	private static final String patentDBUrl = "jdbc:postgresql://192.168.1.148/patentdb?user=postgres&password=&tcpKeepAlive=true";
	private static final String compDBUrl = "jdbc:postgresql://192.168.1.148/compdb_production?user=postgres&password=&tcpKeepAlive=true";
	private static Connection seedConn;
	private static Connection mainConn;
	private static Connection compDBConn;
	private static Connection insertConn;
	private static final String addOrUpdateWord = "INSERT INTO patent_words (word,count) VALUES (?,1) ON CONFLICT (word) DO UPDATE SET (count)=(patent_words.count+1) WHERE patent_words.word=?";
	private static final String valuablePatentsQuery = "SELECT distinct r.pub_doc_number from patent_assignment as p join patent_assignment_property_document as q on (p.assignment_reel_frame=q.assignment_reel_frame) join patent_grant as r on (q.doc_number=r.pub_doc_number) join patent_grant_maintenance as m on (r.pub_doc_number=m.pub_doc_number) where conveyance_text like 'ASSIGNMENT OF ASSIGNOR%' and pub_date > to_char(now()::date, 'YYYYMMDD')::int-100000 AND (doc_kind='B1' or doc_kind='B2') group by r.pub_doc_number having (not array_agg(trim(trailing ' ' from maintenance_event_code))&&'{\"EXP.\"}'::text[]) AND array_length(array_agg(distinct recorded_date),1) >= 1";
	//private static final String unValuablePatentsQuery = "SELECT p.pub_doc_number  from patent_grant as p join patent_grant_maintenance as q on (p.pub_doc_number=q.pub_doc_number) and pub_date > to_char(now()::date, 'YYYYMMDD')::int-150000 group by p.pub_doc_number having (array_agg(trim(trailing ' ' from maintenance_event_code))&&'{\"EXP.\"}'::text[])";
	private static final String patentVectorStatement = "SELECT pub_doc_number, substring(description FROM 1 FOR ?) FROM patent_grant WHERE pub_doc_number=any(?) AND description IS NOT NULL";
	private static final String claimVectorStatement = "SELECT pub_doc_number||'_claim_'||number::text, claim_text FROM patent_grant_claim WHERE pub_doc_number=any(?) AND claim_text IS NOT NULL and parent_claim_id is NULL";
	private static final String patentVectorDataByPubDocNumbers = "SELECT pub_doc_number, pub_date, invention_title, abstract, substring(description FROM 1 FOR ?) FROM patent_grant WHERE pub_doc_number = ANY(?) AND (abstract is NOT NULL OR description IS NOT NULL OR invention_title IS NOT NULL)";
	private static final String distinctClassificationsStatement = "SELECT distinct main_class FROM us_class_titles";
	private static final String classificationsFromPatents = "SELECT pub_doc_number, array_agg(distinct substring(classification_code FROM 1 FOR 3)), array_to_string(array_agg(class), ' '), array_to_string(array_agg(subclass), ' ') FROM patent_grant_uspto_classification WHERE pub_doc_number=ANY(?) AND classification_code IS NOT NULL group by pub_doc_number";
	private static final String claimsFromPatents = "SELECT pub_doc_number, array_agg(claim_text), array_agg(number) FROM patent_grant_claim WHERE pub_doc_number=ANY(?) AND claim_text IS NOT NULL and parent_claim_id is null group by pub_doc_number";
	private static final String claimsFromPatent = "SELECT claim_text FROM patent_grant_claim WHERE uid > 55000000 AND claim_text IS NOT NULL and parent_claim_id is null";
	private static final String allPatentsAfterGivenDate = "SELECT array_agg(pub_doc_number), pub_date FROM patent_grant where pub_date >= ? group by pub_date order by pub_date";
	private static final String allPatentsArray = "SELECT array_agg(pub_doc_number) FROM patent_grant where pub_date >= ?";
	private static final String insertPatentVectorsQuery = "INSERT INTO patent_vectors (pub_doc_number,pub_date,invention_title_vectors,abstract_vectors,description_vectors) VALUES (?,?,?,?,?) ON CONFLICT(pub_doc_number) DO UPDATE SET (pub_date,invention_title_vectors,abstract_vectors,description_vectors)=(?,?,?,?) WHERE patent_vectors.pub_doc_number=?";
	private static final String insertClassificationVectorsQuery = "INSERT INTO patent_vectors (pub_doc_number,pub_date,class_softmax,class_vectors,subclass_vectors) VALUES (?,?,?,?,?) ON CONFLICT(pub_doc_number) DO UPDATE SET (pub_date,class_softmax,class_vectors,subclass_vectors)=(?,?,?,?) WHERE patent_vectors.pub_doc_number=?";
	private static final String insertClaimsVectorQuery = "INSERT INTO patent_vectors (pub_doc_number,pub_date,claims_vectors,claims_numbers) VALUES (?,?,?,?) ON CONFLICT(pub_doc_number) DO UPDATE SET (pub_date,claims_vectors,claims_numbers)=(?,?,?) WHERE patent_vectors.pub_doc_number=?";
	private static final String updateTestingData = "UPDATE patent_vectors SET is_testing='t' WHERE pub_doc_number like '%7'";
	private static final String updateTrainingData = "UPDATE patent_vectors SET is_testing='f' WHERE is_testing!='f'";
	private static final String updateDateStatement = "UPDATE last_vectors_ingest SET pub_date=? WHERE program_name=?";
	private static final String selectDateStatement = "SELECT pub_date FROM last_vectors_ingest WHERE program_name=?";
	//private static final String selectAssigneeStatement = "SELECT distinct on (p.doc_number) p.doc_number,name,q.uid from patent_assignment_property_document as p join patent_assignment_assignee as q on (p.assignment_reel_frame=q.assignment_reel_frame) where (p.doc_kind='B1' or p.doc_kind='B2') and doc_number = ANY(?) and name is not null order by p.doc_number,q.uid desc";
	private static final String selectVectorsStatement = "SELECT pub_doc_number,"+ String.join(",",Constants.DEFAULT_1D_VECTORS)+","+String.join(",",Constants.DEFAULT_2D_VECTORS)+",claims_numbers FROM patent_vectors WHERE pub_doc_number=ANY(?)";
	private static final String selectAllVectorsStatement = "SELECT pub_doc_number,"+ String.join(",",Constants.DEFAULT_1D_VECTORS)+","+String.join(",",Constants.DEFAULT_2D_VECTORS)+",claims_numbers FROM patent_vectors";
	private static final String selectSingleVectorStatement = "SELECT "+ String.join(",",Constants.DEFAULT_1D_VECTORS)+","+String.join(",",Constants.DEFAULT_2D_VECTORS)+",claims_numbers FROM patent_vectors WHERE pub_doc_number=?";
	private static final String selectAllCandidateSets = "SELECT name, id FROM candidate_sets";
	private static final String selectPatentNumbersByAssignee = "select distinct doc_number from (select distinct on (p.doc_number) p.doc_number,name,q.uid from patent_assignment_property_document as p join patent_assignment_assignee as q on (p.assignment_reel_frame=q.assignment_reel_frame) where (p.doc_kind='B1' or p.doc_kind='B2') and upper(name) like upper(?)||'%' order by p.doc_number,q.uid desc) as temp join patent_assignment_assignee as a on (temp.uid=a.uid and upper(a.name) like upper(?)||'%')";
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

	public static int selectCandidateIdByName(String name) {
		try {
			PreparedStatement ps = mainConn.prepareStatement("SELECT id FROM candidate_sets WHERE name = ? LIMIT 1");
			ps.setString(1, name);
			ResultSet rs = ps.executeQuery();
			if(rs.next()) {
				return rs.getInt(1);
			}
		} catch(SQLException sql) {
			sql.printStackTrace();
		}
		return 0;
	}

	public static void insertRawPatent(String label, String text) throws SQLException {
		PreparedStatement ps = insertConn.prepareStatement("insert into raw_patents (name,raw_text) values (?,?) on conflict (name) do update set raw_text=? where raw_patents.name=?");
		ps.setString(1,label);
		ps.setString(2,text);
		ps.setString(3,text);
		ps.setString(4,label);
		ps.executeUpdate();
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

	public static void resetValuablePatents() throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement("UPDATE patent_vectors SET is_valuable='f' WHERE is_valuable='t'");
		ps.executeUpdate();
	}

	/*public static ResultSet selectAssignees(List<String> pubDocNumbers) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(selectAssigneeStatement);
		ps.setArray(1, seedConn.createArrayOf("varchar", pubDocNumbers.toArray()));
		ps.setFetchSize(5);
		return ps.executeQuery();
	}*/

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

	public static void updateLastDate(String programType, int pubDate) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement(updateDateStatement);
		ps.setInt(1, pubDate);
		ps.setString(2, programType);
		ps.executeUpdate();
	}

	public static int selectLastDate(String programType) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement(selectDateStatement);
		ps.setString(1, programType);
		ResultSet rs = ps.executeQuery();
		if(rs.next()) return rs.getInt(1);
		else throw new RuntimeException("Unable to get last date from Database!");
	}

	public static ResultSet selectAllCandidateSets() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(selectAllCandidateSets);
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
		ps.setFetchSize(5);
		System.out.println(ps);
		return ps.executeQuery();
	}

	public static ResultSet selectAllPatentVectors() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(selectAllVectorsStatement);
		ps.setFetchSize(5);
		System.out.println(ps);
		return ps.executeQuery();
	}

	public static ResultSet getBaseVectorFor(String patent)throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(selectSingleVectorStatement);
		ps.setString(1, patent);
		return ps.executeQuery();
	}

	public static ResultSet executeQuery(String query) throws SQLException{
		PreparedStatement ps = seedConn.prepareStatement(query);
		ps.setFetchSize(5);
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
		PreparedStatement ps = seedConn.prepareStatement("SELECT name,raw_text FROM raw_patents");
		ps.setFetchSize(5);
		return ps.executeQuery();
	}

	public static void insertClaims(String pubDocNumber, Integer pubDate, Double[][] claimVector, Integer[] claimNumbers) throws SQLException {
		if(claimVector==null || claimNumbers==null) return;
		PreparedStatement ps = mainConn.prepareStatement(insertClaimsVectorQuery);
		Array claimArray, claimNumberArray;
		assert claimVector.length==Constants.VECTOR_LENGTH && claimVector.length==claimNumbers.length;
		claimArray = mainConn.createArrayOf("float8", claimVector);
		claimNumberArray = mainConn.createArrayOf("int4", claimNumbers);
		ps.setString(1, pubDocNumber);
		ps.setInt(2, pubDate);
		ps.setArray(3, claimArray);
		ps.setArray(4, claimNumberArray);
		ps.setInt(5, pubDate);
		ps.setArray(6, claimArray);
		ps.setArray(7, claimNumberArray);
		ps.setString(8, pubDocNumber);
		ps.executeUpdate();
	}

	public static void insertClassifications(String pubDocNumber, Integer pubDate, Double[] classSoftMax, Double[] classVector, Double[] subClassVector) throws SQLException{
		PreparedStatement ps = mainConn.prepareStatement(insertClassificationVectorsQuery);
		Array softMaxArray = null;
		if(classSoftMax!=null) softMaxArray = mainConn.createArrayOf("float8", classSoftMax);
		Array classArray = null;
		if(classVector!=null) {
			assert classVector.length==Constants.VECTOR_LENGTH;
			classArray = mainConn.createArrayOf("float8", classVector);
		}
		Array subClassArray = null;
		if(subClassVector!=null) {
			assert subClassVector.length == Constants.VECTOR_LENGTH;
			subClassArray = mainConn.createArrayOf("float8", subClassVector);
		}
		ps.setString(1, pubDocNumber);
		ps.setInt(2, pubDate);
		ps.setArray(3, softMaxArray);
		ps.setArray(4, classArray);
		ps.setArray(5, subClassArray);
		ps.setInt(6, pubDate);
		ps.setArray(7, softMaxArray);
		ps.setArray(8, classArray);
		ps.setArray(9, subClassArray);
		ps.setString(10, pubDocNumber);
		ps.executeUpdate();
	}

	public static void insertPatentVectors(String pub_doc_number,int pub_date, Double[] invention_title, Double[] abstract_vectors, Double[] description) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement(insertPatentVectorsQuery);
		Array invention_array = null;
		if(invention_title!=null) {
			assert invention_title.length==Constants.VECTOR_LENGTH;
			invention_array = mainConn.createArrayOf("float8", invention_title);
		}
		Array abstract_array = null;
		if(abstract_vectors!=null) {
			assert abstract_vectors.length==Constants.VECTOR_LENGTH;
			abstract_array = mainConn.createArrayOf("float8", abstract_vectors);
		}
		Array description_array = null;
		if(description!=null) {
			assert description.length==Constants.VECTOR_LENGTH;
			description_array = mainConn.createArrayOf("float8", description);
		}
		ps.setString(1, pub_doc_number);
		ps.setInt(2, pub_date);
		ps.setArray(3, invention_array);
		ps.setArray(4, abstract_array);
		ps.setArray(5, description_array);
		ps.setInt(6, pub_date);
		ps.setArray(7, invention_array);
		ps.setArray(8, abstract_array);
		ps.setArray(9, description_array);
		ps.setString(10, pub_doc_number);
		ps.executeUpdate();
	}

	public static ResultSet getPatentsBetween(int start) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(allPatentsAfterGivenDate);
		ps.setInt(1, start);
		ps.setFetchSize(10);
		System.out.println(ps);
		return ps.executeQuery();
	}

	public static ResultSet allPatentsArray(int start) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(allPatentsArray);
		ps.setInt(1, start);
		System.out.println(ps);
		return ps.executeQuery();
	}


	public static ResultSet getClassificationsFromPatents(Array patentArray) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(classificationsFromPatents);
		ps.setArray(1,patentArray);
		ps.setFetchSize(10);
		System.out.println(ps);
		return ps.executeQuery();
	}

	public static ResultSet getClaimsFromPatents(Array patentArray) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(claimsFromPatents);
		ps.setArray(1,patentArray);
		ps.setFetchSize(10);
		System.out.println(ps);
		return ps.executeQuery();
	}

	public static ResultSet getPatentClaimVectorData() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(claimsFromPatent);
		ps.setFetchSize(5);
		return ps.executeQuery();
	}

	public static ResultSet getMainVectorsFromPatentArray(Array patentArray) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(patentVectorDataByPubDocNumbers);
		ps.setInt(1, Constants.MAX_DESCRIPTION_LENGTH);
		ps.setArray(2,patentArray);
		ps.setFetchSize(10);
		System.out.println(ps);
		return ps.executeQuery();
	}

	public static ResultSet compdbPatentsGroupedByDate() throws SQLException{
		// patents loaded
		PreparedStatement ps2 = seedConn.prepareStatement("SELECT array_agg(pub_doc_number), pub_date FROM patent_grant WHERE pub_doc_number = ANY(?) group by pub_date");
		ps2.setArray(1, getCompDBPatents());
		return ps2.executeQuery();
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

	public static Map<String, Integer[]> getCompDBTechnologyMap() throws SQLException {
		Map<String, Integer[]> patentToTechnologyHash = new HashMap<>();
		PreparedStatement ps = compDBConn.prepareStatement("SELECT array_agg(distinct t.id) as technologies, array_agg(distinct (reel||':'||frame)) AS reelframes, r.deal_id FROM recordings as r inner join deals_technologies as dt on (r.deal_id=dt.deal_id) INNER JOIN technologies AS t ON (t.id=dt.technology_id)  WHERE inactive='f' AND asset_count < 25 AND r.deal_id IS NOT NULL AND t.name is not null AND t.id!=ANY(?) GROUP BY r.deal_id");
		ps.setArray(1, compDBConn.createArrayOf("int4",badTech.toArray()));
		ResultSet rs = ps.executeQuery();
		while(rs.next()) {
			List<Integer> technologies = new ArrayList<>();
			boolean valid = true;
			for(Integer tech : (Integer[])rs.getArray(1).getArray()) {
				if(badTech.contains(tech)) { valid=false; break;}
				technologies.add(tech);
			}
			if(!valid)continue;

			Array reelFrames = rs.getArray(2);
			PreparedStatement ps2 = seedConn.prepareStatement("SELECT DISTINCT doc_number FROM patent_assignment_property_document WHERE (doc_kind='B1' OR doc_kind='B2') AND doc_number IS NOT NULL AND assignment_reel_frame=ANY(?)");
			ps2.setArray(1, reelFrames);
			ps2.setFetchSize(10);
			ResultSet rs2 = ps2.executeQuery();
			// Collect patent numbers
			while(rs2.next()) {
				String docNumber = rs2.getString(1);
				if(docNumber!=null) {
					patentToTechnologyHash.put(docNumber, technologies.toArray(new Integer[]{}));
				}
			}
			rs2.close();
			ps2.close();
		}
		rs.close();
		ps.close();
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

	/*public static ResultSet getUnValuablePatents() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(unValuablePatentsQuery);
		ps.setFetchSize(10);
		return ps.executeQuery();
	}*/


	public static void addOrUpdateWord(String word) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement(addOrUpdateWord);
		ps.setString(1, word);
		ps.setString(2, word);
		ps.executeUpdate();
	}
	
	public static ResultSet getStopWords(int limit) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement("SELECT word FROM patent_words ORDER BY count DESC limit ?");
		ps.setInt(1,limit);
		return ps.executeQuery();
	}


}
