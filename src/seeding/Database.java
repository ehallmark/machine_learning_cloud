package seeding;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;


public class Database {
	private static String patentDBUrl = "jdbc:postgresql://192.168.1.148/patentdb?user=postgres&password=&tcpKeepAlive=true";

	private static Connection seedConn;
	private static Connection mainConn;
	private static final String addOrUpdateWord = "INSERT INTO patent_words (word,count) VALUES (?,1) ON CONFLICT (word) DO UPDATE SET (count)=(patent_words.count+1) WHERE patent_words.word=?";
	private static final String selectCitations = "SELECT p.pub_doc_number, patent_cited_doc_number, invention_title, abstract from patent_grant as p join patent_grant_citation as q on (p.pub_doc_number=q.pub_doc_number) where p.pub_doc_number=ANY(?) AND (invention_title IS NOT NULL OR abstract IS NOT NULL) and q.pub_doc_number=ANY(?)";
	private static final String updateCitation = "UPDATE patent_grant_citation SET (patent_cited_invention_title,patent_cited_abstract)=(?,?) WHERE pub_doc_number=? AND patent_cited_doc_number=?";
	public static void setupMainConn() throws SQLException {
		mainConn = DriverManager.getConnection(patentDBUrl);
		mainConn.setAutoCommit(false);
	}

	public static void setupSeedConn() throws SQLException {
		seedConn = DriverManager.getConnection(patentDBUrl);
		seedConn.setAutoCommit(false);
	}

	public static void commit() {
		try {
			mainConn.commit();
		} catch(SQLException sql) {
			sql.printStackTrace();
		}
	}

	public static void close(){
		try {
			if(mainConn!=null)mainConn.close();
			if(seedConn!=null)seedConn.close();
		} catch(SQLException sql) {
			sql.printStackTrace();
		}
	}

	
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

	public static ResultSet selectCitationTitleAndAbstract(Collection<String> patents) throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement(selectCitations);
		Array p = seedConn.createArrayOf("VARCHAR", patents.toArray());
		ps.setArray(1, p);
		ps.setArray(2, p);
		ps.setFetchSize(10);
		System.out.println(ps);
		return ps.executeQuery();
	}

	public static void updateCitation(String pubDocNumber, String citedPubDocNumber, String citedTitle, String citedAbstract) throws SQLException {
		PreparedStatement ps = mainConn.prepareStatement(updateCitation);
		ps.setString(1, citedTitle); ps.setString(2, citedAbstract);
		ps.setString(3, pubDocNumber); ps.setString(4, citedPubDocNumber);
		ps.executeUpdate();
	}

}
