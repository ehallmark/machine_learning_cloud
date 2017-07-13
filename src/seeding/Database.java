package seeding;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;

import lombok.Getter;
import net.lingala.zip4j.core.ZipFile;
import seeding.ai_db_updater.handlers.LineHandler;
import seeding.ai_db_updater.handlers.MaintenanceEventHandler;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;
import tools.AssigneeTrimmer;
import models.classification_models.TechTaggerNormalizer;
import user_interface.ui_models.portfolios.PortfolioList;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class Database {
	private static Map<String,Set<String>> patentToClassificationMap;
	private static Map<String,Set<String>> appToClassificationMap;
	private static Map<String,List<String>> appToOriginalAssigneeMap;
	private static Map<String,List<String>> patentToOriginalAssigneeMap;
	private static Map<String,String> appToInventionTitleMap;
	private static Map<String,String> patentToInventionTitleMap;
	private static Map<String,String> classCodeToClassTitleMap;
	private static Map<String,List<String>> patentToLatestAssigneeMap;
	private static Map<String,Set<String>>  assigneeToPatentsMap;
	private static Map<String,Set<String>> assigneeToAppsMap;
	private static Map<String,Collection<String>> etsiStandardToPatentsMap;
	private static RadixTree<String> assigneePrefixTrie;
	private static RadixTree<String> classCodesPrefixTrie;
	private static Map<String,Collection<String>> patentToRelatedPatentsMap;
	private static Set<String> expiredPatentSet;
	private static Set<String> lapsedPatentSet;
	public static Set<String> lapsedAppSet;
	public static Set<String> allAssignees;
	public static Set<String> valuablePatents;
	public static Set<String> valuableApps;
	public static Set<String> allClassCodes;
	public static Set<String> smallEntityPatents;
	public static Set<String> largeEntityPatents;
	public static Set<String> microEntityPatents;
	public static Set<String> japaneseCompanies;
	private static Map<String,Integer> assigneeToAssetsSoldCountMap;
	private static Map<String,Integer> assigneeToAssetsPurchasedCountMap;
	private static Map<String,Integer> compDBAssigneeToAssetsSoldCountMap;
	private static Map<String,Integer> compDBAssigneeToAssetsPurchasedCountMap;
	private static Map<String,Set<String>> patentToCitedPatentsMap;
	private static Map<String,Set<String>> appToCitedPatentsMap;
	@Getter
	public static Map<String,Set<String>> classCodeToPatentMap;
	public static Map<String,LocalDate> patentToPubDateMap;
	public static Map<String,LocalDate> patentToPriorityDateMap;
	public static Map<String,LocalDate> appToPubDateMap;
	public static Map<String,LocalDate> appToPriorityDateMap;
	public static final File patentToPubDateMapFile = new File(Constants.DATA_FOLDER+"patent_to_pubdate_map_file.jobj");
	public static final File patentToPriorityDateMapFile = new File(Constants.DATA_FOLDER+"patent_to_priority_date_map.jobj");
	public static final File patentToClassificationMapFile = new File(Constants.DATA_FOLDER+"patent_to_classification_map.jobj");
	public static final File classCodeToPatentMapFile = new File(Constants.DATA_FOLDER+"class_code_to_patent_map.jobj");
	public static final File patentToInventionTitleMapFile = new File(Constants.DATA_FOLDER+"patent_to_invention_title_map.jobj");
	public static File patentToLatestAssigneeMapFile = new File(Constants.DATA_FOLDER+"patent_to_assignee_map_latest.jobj");
	public static final File patentToOriginalAssigneeMapFile = new File(Constants.DATA_FOLDER+"patent_to_original_assignee_map.jobj");
	public static File assigneeToAppsMapFile = new File(Constants.DATA_FOLDER+"assignee_to_apps_map.jobj");
	public static File assigneeToPatentsMapFile = new File(Constants.DATA_FOLDER+"assignee_to_patents_map.jobj");
	public static File lapsedAppSetFile = new File(Constants.DATA_FOLDER+"lapsed_apps_set.jobj");
	public static File expiredPatentSetFile = new File(Constants.DATA_FOLDER+"expired_patents_set.jobj");
	public static File lapsedPatentSetFile = new File(Constants.DATA_FOLDER+"lapsed_patents_set.jobj");
	public static File allClassCodesFile = new File(Constants.DATA_FOLDER+"all_class_codes.jobj");
	public static File valuablePatentsFile = new File(Constants.DATA_FOLDER+"valuable_patents.jobj");
	public static File valuableAppsFile = new File(Constants.DATA_FOLDER+"valuable_apps.jobj");
	public static File classCodeToClassTitleMapFile = new File(Constants.DATA_FOLDER+"class_code_to_class_title_map.jobj");
	private static File patentToRelatedPatentsMapFile = new File(Constants.DATA_FOLDER+"patent_to_related_docs_map_file.jobj");
	private static final String patentDBUrl = "jdbc:postgresql://localhost/patentdb?user=postgres&password=password&tcpKeepAlive=true";
	private static final String compDBUrl = "jdbc:postgresql://data.gttgrp.com/compdb_production?user=postgres&password=&tcpKeepAlive=true";
	private static final String gatherDBUrl = "jdbc:postgresql://localhost/gather_production?user=postgres&password=&tcpKeepAlive=true";
	public static Connection seedConn;
	private static Connection compDBConn;
	private static Connection gatherDBConn;
	private static final String selectGatherRatingsQuery = "select a.patent_rating,array_agg(p.number) as avg_patent_rating from assessments as a join patents as p on (p.id=a.patent_id) where patent_rating is not null and a.type = 'PublishedAssessment'  group by a.patent_rating";
	private static final String selectGatherTechnologiesQuery = "select array_agg(distinct(number)), upper(name) from (select case when t.name like '%rs' then substring(t.name from 1 for char_length(t.name)-1) else replace(t.name,'-','') end as name, (string_to_array(regexp_replace(p.number,'[^0-9 ]',''),' '))[1] as number from patents as p join assessments as a on (p.id=a.patent_id) join assessment_technologies as at on (a.id=at.assessment_id) join technologies as t on (at.technology_id=t.id) where char_length(coalesce(t.name,'')) > 0 and (not upper(t.name)='AUDIT')) as temp group by upper(name)";
	private static final Set<Integer> badCompDBTechnologyIds = new HashSet<>(Arrays.asList(136,182,301,316,519,527));
	private static final File gatherTechMapFile = new File(Constants.DATA_FOLDER+"gather_technology_to_patent_map.jobj");
	private static Map<String,Collection<String>> gatherTechMap;
	private static final File gatherValueMapFile = new File(Constants.DATA_FOLDER+"gather_patent_to_value_bool_map.jobj");
	private static Map<String,Boolean> gatherValueMap;
	private static Set<String> gatherPatentSet;
	private static volatile boolean init=false;


	private static Connection conn;
	public static File patentToReferencedByMapFile = new File(Constants.DATA_FOLDER+"patent_to_referenced_by_map.jobj");
	public static File patentToAppDateMapFile = new File(Constants.DATA_FOLDER+"patent_to_appdate_map_file.jobj");
	public static File patentToRelatedDocMapFile = new File(Constants.DATA_FOLDER+"patent_to_related_docs_map_file.jobj");
	public static File pubDateToPatentMapFile = new File(Constants.DATA_FOLDER+"pubdate_to_patent_map.jobj");
	public static File patentToCitedPatentsMapFile = new File(Constants.DATA_FOLDER+"patent_to_cited_patents_map.jobj");
	public static File appToClassificationMapFile = new File(Constants.DATA_FOLDER+"app_to_classification_map.jobj");
	public static File appToInventionTitleMapFile = new File(Constants.DATA_FOLDER+"app_to_invention_title_map.jobj");
	public static File appToOriginalAssigneeMapFile = new File(Constants.DATA_FOLDER+"app_to_original_assignee_map.jobj");
	public static File appToPubDateMapFile = new File(Constants.DATA_FOLDER+"app_to_pubdate_map_file.jobj");
	public static File appToAppDateMapFile = new File(Constants.DATA_FOLDER+"app_to_appdate_map_file.jobj");
	public static File appToRelatedDocMapFile = new File(Constants.DATA_FOLDER+"app_to_related_docs_map_file.jobj");
	public static File appToCitedPatentsMapFile = new File(Constants.DATA_FOLDER+"app_to_cited_patents_map.jobj");
	public static File appToPriorityDateMapFile = new File(Constants.DATA_FOLDER+"app_to_priority_date_map.jobj");
	static {
		try {
			conn = DriverManager.getConnection(patentDBUrl);
			conn.setAutoCommit(false);
		} catch(Exception e) {
			e.printStackTrace();
		}
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

	public static void loadAndIngestMaintenanceFeeData(File zipFile, File destinationFile, MaintenanceEventHandler handler) throws Exception {
		// should be one at least every other month
		// Load file from Google
		try {
			String url = handler.getUrl(); // "https://bulkdata.uspto.gov/data2/patent/maintenancefee/MaintFeeEvents.zip";
			URL website = new URL(url);
			System.out.println("Trying: " + website.toString());
			ReadableByteChannel rbc = Channels.newChannel(website.openStream());
			FileOutputStream fos = new FileOutputStream(zipFile);
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			fos.close();

			new ZipFile(zipFile).extractAll(destinationFile.getAbsolutePath());

		} catch (Exception e) {
			//e.printStackTrace();
			System.out.println("Not found");
		}
		Arrays.stream(destinationFile.listFiles()).forEach(file -> {
			if (!file.getName().endsWith(".txt")) {
				file.delete();
				return;
			}
			try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
				String line = reader.readLine();
				while (line != null) {
					handler.handleLine(line);
					line = reader.readLine();
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				file.delete();
			}
		});
		handler.save();
	}

	public static Map<String,Set<String>> loadPatentToClassificationMap() throws IOException,ClassNotFoundException {
		Map<String,Set<String>> map;
		ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(patentToClassificationMapFile)));
		map = (Map<String,Set<String>>)ois.readObject();
		ois.close();
		return map;
	}

	public static Set<String> loadExpiredPatentsSet() throws IOException, ClassNotFoundException {
		Set<String> set;
		ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(expiredPatentSetFile)));
		set = (Set<String>)ois.readObject();
		ois.close();
		return set;
	}

	public static void setupClassificationsHash(File zipFile, File destinationFile, UrlCreator urlCreator, LineHandler handler) {
		// should be one at least every other month
		// Load file from Google
		boolean found = false;
		LocalDate date = LocalDate.now();
		while (!found) {
			try {
				// String dateStr = String.format("%04d", date.getYear()) + "-" + String.format("%02d", date.getMonthValue()) + "-" + String.format("%02d", date.getDayOfMonth());
				String url = urlCreator.create(date); //"http://patents.reedtech.com/downloads/PatentClassInfo/ClassData/US_Grant_CPC_MCF_Text_" + dateStr + ".zip";
				URL website = new URL(url);
				System.out.println("Trying: " + website.toString());
				ReadableByteChannel rbc = Channels.newChannel(website.openStream());
				FileOutputStream fos = new FileOutputStream(zipFile);
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
				fos.close();

				new ZipFile(zipFile).extractAll(destinationFile.getAbsolutePath());

				found = true;
			} catch (Exception e) {
				//e.printStackTrace();
				System.out.println("Not found");
			}
			date = date.minusDays(1);
			if(date.isBefore(LocalDate.now().minusYears(20))) throw new RuntimeException("Url does not work: "+urlCreator.create(date));
		}

		Arrays.stream(destinationFile.listFiles(File::isDirectory)[0].listFiles()).forEach(file -> {
			try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
				String line = reader.readLine();
				while (line != null) {
					handler.handleLine(line);
					line = reader.readLine();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		handler.save();

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
			if(!file.exists() && new File(Constants.DATA_FOLDER+file.getName()).exists()) file = new File(Constants.DATA_FOLDER+file.getName());
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
			file = new File(Constants.DATA_FOLDER+file.getName());
			ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
			oos.writeObject(obj);
			oos.flush();
			oos.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized static int getLifeRemaining(String patent) {
		if(!valuablePatents.contains(patent)) return 0;

		Set<String> related = new HashSet<>();
		related.add(patent);
		Collection<String> family = Database.getPatentToRelatedPatentsMap().get(patent);
		if (family!=null) related.addAll(family);

		Collection<LocalDate> dates = related.stream().map(rel->Database.getPatentToPriorityDateMap().get(rel)).filter(date->date!=null).collect(Collectors.toList());

		if (dates.isEmpty()) return 0;

		LocalDate priorityDate = dates.stream().min(LocalDate::compareTo).get();

		// determine life remaining
		return Math.max(0,20 - LocalDate.now().getYear() + priorityDate.getYear());
	}

	public synchronized static boolean isJapaneseAssignee(String assignee) {
		if(japaneseCompanies==null) {
			// japanese companies
			japaneseCompanies = (Set<String>)Database.tryLoadObject(LoadJapaneseCompaniesSet.FILE);
		}
		return japaneseCompanies.contains(assignee);
	}

	public synchronized static Collection<String> getNonJapaneseCompanies() {
		Set<String> allCompanies = new HashSet<>(allAssignees);
		allCompanies.removeAll(getJapaneseCompanies());
		return allCompanies;
	}

	public synchronized static Set<String> getJapaneseCompanies() {
		if(japaneseCompanies==null) {
			// japanese companies
			japaneseCompanies = (Set<String>)Database.tryLoadObject(LoadJapaneseCompaniesSet.FILE);
		}
		return new HashSet<>(japaneseCompanies);
	}

	public synchronized static Set<String> loadJapaneseCompaniesSetFromDB() throws SQLException {
		PreparedStatement ps = seedConn.prepareStatement("select distinct orgname from patent_grant_assignee where country='JP' and orgname is not null");
		ResultSet rs = ps.executeQuery();
		Set<String> japanese = new HashSet<>();
		while(rs.next()) {
			String assignee = rs.getString(1);
			if(assignee!=null && assignee.trim().length()>0) {
				String cleanAssignee = AssigneeTrimmer.standardizedAssignee(assignee);
				if(cleanAssignee!=null&&cleanAssignee.length()>0) {
					japanese.add(cleanAssignee);
				}
			}
		}
		return japanese;
	}

	public static void preLoad() {
		getAssignees();
		getClassCodeToClassTitleMap();
		getPatentToClassificationMap();
		getPatentToLatestAssigneeMap();
		getPatentToOriginalAssigneeMap();
		getCopyOfAllPatents();
		getCopyOfAllApplications();
		getLapsedAppSet();
		getAppToClassificationMap();
		getAppToOriginalAssigneeMap();
		getAppToInventionTitleMap();
		getAppToPriorityDateMap();
		getAppToPubDateMap();
		getLapsedPatentSet();
		getExpiredPatentSet();
		getJapaneseCompanies();
		getCompDBAssigneeToAssetsPurchasedCountMap();
		getCompDBAssigneeToAssetsSoldCountMap();
		TechTaggerNormalizer.getDefaultTechTagger();
	}

	public synchronized static void initializeDatabase() {
		if(init==true)return;
		init=true;

		// gather tech
		gatherTechMap = (Map<String, Collection<String>>) Database.tryLoadObject(gatherTechMapFile);

		gatherPatentSet = new HashSet<>();
		gatherTechMap.forEach((tech,patents)->{
			gatherPatentSet.addAll(patents);
		});

		// gather value
		gatherValueMap = (Map<String, Boolean>) Database.tryLoadObject(gatherValueMapFile);

		getExpiredPatentSet();
		getLapsedPatentSet();
		getLapsedAppSet();
		largeEntityPatents = Collections.unmodifiableSet((Set<String>)tryLoadObject(new File(Constants.DATA_FOLDER+"large_entity_patents_set.jobj")));
		smallEntityPatents = Collections.unmodifiableSet((Set<String>)tryLoadObject(new File(Constants.DATA_FOLDER+"small_entity_patents_set.jobj")));
		microEntityPatents = Collections.unmodifiableSet((Set<String>)tryLoadObject(new File(Constants.DATA_FOLDER+"micro_entity_patents_set.jobj")));

		// load dependent objects
		allClassCodes=(Set<String>)tryLoadObject(allClassCodesFile);

		System.out.println("Building class code trie...");
		// class codes trie
		classCodesPrefixTrie = new ConcurrentRadixTree<>(new DefaultByteArrayNodeFactory());
		allClassCodes.forEach(code->{
			classCodesPrefixTrie.put(code,code);
		});

		classCodeToPatentMap = (HashMap<String,Set<String>>)tryLoadObject(classCodeToPatentMapFile);

		// assignee stuff
		{
			allAssignees=new HashSet<>(getAssigneeToAppsMap().keySet());
			allAssignees.addAll(getAssigneeToPatentsMap().keySet());
			// prefix trie for assignees
			System.out.println("Building assignee trie...");
			assigneePrefixTrie = new ConcurrentRadixTree<>(new DefaultByteArrayNodeFactory());
			allAssignees.forEach(assignee->{
				assigneePrefixTrie.put(assignee,assignee);
			});
		}

		valuablePatents=(Set<String>)tryLoadObject(valuablePatentsFile);
		valuableApps=(Set<String>)tryLoadObject(valuableAppsFile);

	}

	public static Map<String,Set<String>> getAssigneeToPatentsMap() {
		if(assigneeToPatentsMap==null) {
			assigneeToPatentsMap = Collections.unmodifiableMap((Map<String,Set<String>>)tryLoadObject(assigneeToPatentsMapFile));
		}
		return assigneeToPatentsMap;
	}

	public static Map<String,Set<String>> getAssigneeToAppsMap() {
		if(assigneeToAppsMap==null) {
			assigneeToAppsMap = Collections.unmodifiableMap((Map<String,Set<String>>)tryLoadObject(assigneeToAppsMapFile));
		}
		return assigneeToAppsMap;
	}

	public synchronized static boolean hasClassifications(String pat) {
		return getPatentToClassificationMap().containsKey(pat);
	}

	public synchronized static Set<String> getClassCodes() {
		if(allClassCodes==null) initializeDatabase();
		return new HashSet<>(allClassCodes);
	}


	public synchronized static Set<String> getAssignees() {
		if(allAssignees==null) initializeDatabase();
		return new HashSet<>(allAssignees);
	}

	public synchronized static void setupGatherConn() throws SQLException {
		gatherDBConn = DriverManager.getConnection(gatherDBUrl);
		gatherDBConn.setAutoCommit(false);
	}

	public synchronized static Map<String,Set<String>> getPatentToClassificationMap() {
		if(patentToClassificationMap==null) {
			patentToClassificationMap = Collections.unmodifiableMap((Map<String,Set<String>>)tryLoadObject(patentToClassificationMapFile));
		}
		return patentToClassificationMap;
	}

	public synchronized static Map<String,Set<String>> getAppToClassificationMap() {
		if(appToClassificationMap==null) {
			appToClassificationMap = Collections.unmodifiableMap((Map<String,Set<String>>)tryLoadObject(appToClassificationMapFile));
		}
		return appToClassificationMap;
	}

	public synchronized static Map<String,List<String>> getPatentToLatestAssigneeMap() {
		if(patentToLatestAssigneeMap==null) {
			patentToLatestAssigneeMap = Collections.unmodifiableMap((Map<String,List<String>>)tryLoadObject(patentToLatestAssigneeMapFile));
		}
		return patentToLatestAssigneeMap;
	}

	public synchronized static Map<String,LocalDate> getPatentToPubDateMap() {
		if(patentToPubDateMap==null) {
			patentToPubDateMap = Collections.unmodifiableMap((Map<String,LocalDate>)tryLoadObject(patentToPubDateMapFile));
		}
		return patentToPubDateMap;
	}


	public synchronized static Map<String,LocalDate> getAppToPubDateMap() {
		if(appToPubDateMap==null) {
			appToPubDateMap = Collections.unmodifiableMap((Map<String,LocalDate>)tryLoadObject(appToPubDateMapFile));
		}
		return appToPubDateMap;
	}


	public synchronized static Map<String,LocalDate> getPatentToPriorityDateMap() {
		if(patentToPriorityDateMap==null) {
			patentToPriorityDateMap = Collections.unmodifiableMap((Map<String,LocalDate>)tryLoadObject(patentToPriorityDateMapFile));
		}
		return patentToPriorityDateMap;
	}

	public synchronized static Map<String,LocalDate> getAppToPriorityDateMap() {
		if(appToPriorityDateMap==null) {
			appToPriorityDateMap = Collections.unmodifiableMap((Map<String,LocalDate>)tryLoadObject(appToPriorityDateMapFile));
		}
		return appToPriorityDateMap;
	}

	public synchronized static Map<String,List<String>> getPatentToOriginalAssigneeMap() {
		if(patentToOriginalAssigneeMap==null) {
			patentToOriginalAssigneeMap = Collections.unmodifiableMap((Map<String,List<String>>)tryLoadObject(patentToOriginalAssigneeMapFile));
		}
		return patentToOriginalAssigneeMap;
	}

	public synchronized static Map<String,List<String>> getAppToOriginalAssigneeMap() {
		if(appToOriginalAssigneeMap==null) {
			appToOriginalAssigneeMap = Collections.unmodifiableMap((Map<String,List<String>>)tryLoadObject(appToOriginalAssigneeMapFile));
		}
		return appToOriginalAssigneeMap;
	}

	public synchronized static Map<String,String> getPatentToInventionTitleMap() {
		if(patentToInventionTitleMap==null) {
			patentToInventionTitleMap = Collections.unmodifiableMap((Map<String,String>)tryLoadObject(patentToInventionTitleMapFile));
		}
		return patentToInventionTitleMap;
	}

	public synchronized static Map<String,String> getAppToInventionTitleMap() {
		if(appToInventionTitleMap==null) {
			appToInventionTitleMap = Collections.unmodifiableMap((Map<String,String>)tryLoadObject(appToInventionTitleMapFile));
		}
		return appToInventionTitleMap;
	}

	public synchronized static void setupSeedConn() throws SQLException {
		if(seedConn==null) {
			seedConn = DriverManager.getConnection(patentDBUrl);
			seedConn.setAutoCommit(false);
		}
	}

	public synchronized static Set<String> getLapsedPatentSet() {
		if(lapsedPatentSet==null) lapsedPatentSet = Collections.unmodifiableSet((Set<String>)tryLoadObject(lapsedPatentSetFile));
		return lapsedPatentSet;
	}

	public synchronized static Set<String> getLapsedAppSet() {
		if(lapsedAppSet==null) lapsedAppSet = Collections.unmodifiableSet((Set<String>)tryLoadObject(lapsedAppSetFile));
		return lapsedAppSet;
	}


	public synchronized static Set<String> getExpiredPatentSet() {
		if(expiredPatentSet==null) expiredPatentSet = Collections.unmodifiableSet((Set<String>)tryLoadObject(expiredPatentSetFile));
		return expiredPatentSet;
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
		if(patentToRelatedPatentsMap==null) {
			patentToRelatedPatentsMap = Collections.unmodifiableMap((Map<String,Collection<String>>)tryLoadObject(patentToRelatedPatentsMapFile));
		}
		return patentToRelatedPatentsMap;
	}

	public synchronized static Map<String,Collection<String>> getAppToRelatedPatentsMap() {
		if(patentToRelatedPatentsMap==null) {
			patentToRelatedPatentsMap = Collections.unmodifiableMap((Map<String,Collection<String>>)tryLoadObject(appToRelatedDocMapFile));
		}
		return patentToRelatedPatentsMap;
	}

	public static Map<String,Set<String>> getPatentToCitedPatentsMap() {
		if(patentToCitedPatentsMap==null) {
			patentToCitedPatentsMap=(Map<String,Set<String>>) Database.tryLoadObject(Database.patentToCitedPatentsMapFile);
		}
		return patentToCitedPatentsMap;
	}

	public static Map<String,Set<String>> getAppToCitedPatentsMap() {
		if(appToCitedPatentsMap==null) {
			appToCitedPatentsMap=(Map<String,Set<String>>) Database.tryLoadObject(Database.appToCitedPatentsMapFile);
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
			compDBAssigneeToAssetsSoldCountMap = (Map<String,Integer>)Database.tryLoadObject(new File(Constants.DATA_FOLDER+"compdb_assignee_to_assets_sold_count_map.jobj"));
		}
		return compDBAssigneeToAssetsSoldCountMap;
	}
	public synchronized static Map<String,Integer> getCompDBAssigneeToAssetsPurchasedCountMap() {
		if(compDBAssigneeToAssetsPurchasedCountMap==null) {
			compDBAssigneeToAssetsPurchasedCountMap = (Map<String,Integer>)Database.tryLoadObject(new File(Constants.DATA_FOLDER+"compdb_assignee_to_assets_purchased_count_map.jobj"));
		}
		return compDBAssigneeToAssetsPurchasedCountMap;
	}

	public synchronized static int numPatentsWithCpcClassifications() {
		return patentToClassificationMap.size();
	}

	public synchronized static int getAssetCountFor(String assignee) {
		AtomicInteger count = new AtomicInteger(0);
		// try fuzzy search thru trie
		possibleNamesForAssignee(assignee).forEach(name->{
			if(assigneeToPatentsMap.containsKey(name)) {
				count.getAndAdd(assigneeToPatentsMap.get(name).size());
			}
		});
		return count.get();
	}

	public synchronized static int getAssetsSoldCountFor(String assignee) {
		AtomicInteger count = new AtomicInteger(0);
		// try fuzzy search thru trie
		possibleNamesForAssignee(assignee).forEach(name->{
			if(getAssigneeToAssetsSoldCountMap().containsKey(name)) {
				count.getAndAdd(getAssigneeToAssetsSoldCountMap().get(name));
			}
		});
		return count.get();
	}

	public synchronized static int getAssetsPurchasedCountFor(String assignee) {
		AtomicInteger count = new AtomicInteger(0);
		// try fuzzy search thru trie
		possibleNamesForAssignee(assignee).forEach(name->{
			if(getAssigneeToAssetsPurchasedCountMap().containsKey(name)) {
				count.getAndAdd(getAssigneeToAssetsPurchasedCountMap().get(name));
			}
		});
		return count.get();
	}

	public synchronized static int getExactAssetCountFor(String assignee) {
		if(assigneeToPatentsMap.containsKey(assignee)) {
			return assigneeToPatentsMap.get(assignee).size();
		} else {
			return 0;
		}
	}

	public synchronized static boolean isApplication(String application) {
		if(application.length()!=11) return false;
		try {
			Integer.valueOf(application);
		} catch(Exception e) {
			return false;
		}
		return true;
	}
	public synchronized static boolean isPatent(String patent) {
		return !(isAssignee(patent) || isApplication(patent));
	}
	public synchronized static boolean isAssignee(String assignee) {
		if(assignee.replaceAll("[0-9]","").trim().isEmpty()) return false;
		return assigneePrefixTrie.getValuesForKeysStartingWith(assignee).iterator().hasNext();
	}

	public synchronized static String getInventionTitleFor(String patent) {
		if(getPatentToInventionTitleMap().containsKey(patent)) {
			return patentToInventionTitleMap.get(patent);
		} else {
			return "";
		}
	}

	public synchronized static Set<String> possibleNamesForAssignee(String base) {
		if(base==null||base.isEmpty()) return new HashSet<>();
		final String cleanBase = AssigneeTrimmer.standardizedAssignee(base);
		if(cleanBase.isEmpty()) return new HashSet<>();
		Set<String> possible = new HashSet<>();
		if(allAssignees.contains(cleanBase)) possible.add(cleanBase);
		assigneePrefixTrie.getValuesForKeysStartingWith(cleanBase+" ").forEach(a->possible.add(a));
		return possible;
	}

	public synchronized static Set<String> subClassificationsForClass(String formattedCPC) {
		if(formattedCPC==null||formattedCPC.isEmpty()) return new HashSet<>();
		Set<String> possible = new HashSet<>();
		classCodesPrefixTrie.getValuesForKeysStartingWith(formattedCPC).forEach(a->possible.add(a));
		return possible;
	}

	public static Collection<String> getCopyOfAllPatents() {
		if(valuablePatents==null||lapsedPatentSet==null||expiredPatentSet==null) initializeDatabase();
		Set<String> everything = new HashSet<>(valuablePatents.size()+lapsedPatentSet.size()+expiredPatentSet.size());
		everything.addAll(valuablePatents);
		everything.addAll(expiredPatentSet);
		everything.addAll(lapsedPatentSet);
		return everything;
	}


	public static Collection<String> getCopyOfAllApplications() {
		if(valuableApps==null||lapsedAppSet==null) initializeDatabase();
		Set<String> everything = new HashSet<>(valuableApps.size()+lapsedAppSet.size());
		everything.addAll(valuableApps);
		everything.addAll(lapsedAppSet);
		return everything;
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

	public synchronized static String entityTypeForPatent(String patent) {
		if(patent==null) throw new NullPointerException("patent");
		if(microEntityPatents.contains(patent)) return "Micro";
		if(smallEntityPatents.contains(patent)) return "Small";
		if(largeEntityPatents.contains(patent)) return "Large";
		return "Unknown";
	}
	public synchronized static String assigneeEntityType(String assignee) {
		int sampleSize = 100;
		Collection<String> assets = selectPatentNumbersFromAssignee(assignee);
		Map<String,AtomicInteger> entityTypeToScoreMap = new HashMap<>();
		entityTypeToScoreMap.put("Small",new AtomicInteger(0));
		entityTypeToScoreMap.put("Large",new AtomicInteger(0));
		entityTypeToScoreMap.put("Micro",new AtomicInteger(0));
		if(assets.isEmpty()) return "Unknown";
		AtomicBoolean shouldStop = new AtomicBoolean(false);
		assets.stream().sorted((a1,a2)->a2.compareTo(a1)).forEach(asset-> {
			// stop conditions
			if(Math.abs(entityTypeToScoreMap.get("Small").get()-entityTypeToScoreMap.get("Large").get())>sampleSize) {
				shouldStop.set(true);
			}
			if(Math.abs(entityTypeToScoreMap.get("Micro").get()-entityTypeToScoreMap.get("Large").get())>sampleSize) {
				shouldStop.set(true);
			}
			if(Math.abs(entityTypeToScoreMap.get("Small").get()-entityTypeToScoreMap.get("Micro").get())>sampleSize) {
				shouldStop.set(true);
			}
			if(shouldStop.get()) return;

			if (microEntityPatents.contains(asset)) {
				entityTypeToScoreMap.get("Micro").getAndIncrement();
			} else if(smallEntityPatents.contains(asset)) {
				entityTypeToScoreMap.get("Small").getAndIncrement();
			} else if(largeEntityPatents.contains(asset)) {
				entityTypeToScoreMap.get("Large").getAndIncrement();
			}
		});
		return entityTypeToScoreMap.entrySet().stream().sorted((e1,e2)->Integer.compare(e2.getValue().get(),e1.getValue().get())).findFirst().get().getKey();
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

	public synchronized static Collection<String> selectPatentNumbersFromETSIStandard(String etsiStandard) {

		if(getEtsiStandardToPatentsMap().containsKey(etsiStandard)) {
			return new ArrayList<>(etsiStandardToPatentsMap.get(etsiStandard));
		} else {
			return Collections.emptySet();
		}
	}

	public synchronized static Set<String> selectPatentNumbersFromClassAndSubclassCodes(String cpcCode) {
		Set<String> set = new HashSet<>();
		subClassificationsForClass(cpcCode).forEach(subClass->{
			if (classCodeToPatentMap.containsKey(subClass)) {
				set.addAll(classCodeToPatentMap.get(subClass));
			}
		});
		return set;
	}

	public synchronized static Set<String> selectPatentNumbersFromExactClassCode(String cpcCode) {
		Set<String> set = new HashSet<>();
		if (classCodeToPatentMap.containsKey(cpcCode)) {
			set.addAll(classCodeToPatentMap.get(cpcCode));
		}
		return set;
	}

	public synchronized static Set<String> assigneesFor(String patent) {
		Set<String> assignees = new HashSet<>();
		if(isApplication(patent)) {
			if(getAppToOriginalAssigneeMap().containsKey(patent)) {
				assignees.addAll(appToOriginalAssigneeMap.get(patent));
			}
		} else {
			if (getPatentToLatestAssigneeMap().containsKey(patent)) {
				assignees.addAll(patentToLatestAssigneeMap.get(patent));
			} else if (getPatentToOriginalAssigneeMap().containsKey(patent)) {
				assignees.addAll(patentToOriginalAssigneeMap.get(patent));
			}
		}
		return assignees;
	}

	public synchronized static Collection<String> getValuablePatents() {
		if(valuablePatents==null) initializeDatabase();
		return valuablePatents;
	}

	public synchronized static Collection<String> selectPatentNumbersFromAssignee(String assignee){
		Set<String> patents = new HashSet<>();
		// try fuzzy search thru trie
		possibleNamesForAssignee(assignee).forEach(name->{
			patents.addAll(assigneeToPatentsMap.get(name));
		});
		return patents;
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


	public synchronized static Set<String> patentsWithKeywords(Collection<String> patents, PortfolioList.Type type, String keywordsToInclude, String keywordsToExclude) throws SQLException {
		int limit = 10000;
		boolean searchFullDatabase = patents==null;
		Set<String> validPatents = new HashSet<>();
		PreparedStatement ps;
		String keywordQuery = "(tokens @@ (plainto_tsquery('english',?) && !!plainto_tsquery('english',?)))";
		if(searchFullDatabase) {
			ps = seedConn.prepareStatement("SELECT pub_doc_number FROM patents_and_applications WHERE doc_type=? and "+keywordQuery+" limit "+limit);
			ps.setString(1, type.toString());
			ps.setString(2,keywordsToInclude);
			ps.setString(3,keywordsToExclude);
		}
		else {
			ps = seedConn.prepareStatement("SELECT pub_doc_number FROM patents_and_applications WHERE pub_doc_number=ANY(?) and doc_type=? and "+keywordQuery+" limit "+ limit);
			ps.setArray(1,seedConn.createArrayOf("varchar",patents.toArray()));
			ps.setString(2, type.toString());
			ps.setString(3,keywordsToInclude);
			ps.setString(4,keywordsToExclude);
		}
		ps.setFetchSize(100);

		ResultSet rs = ps.executeQuery();
		while(rs.next()) {
			String patent = rs.getString(1);
			validPatents.add(patent);
		}
		ps.close();
		return validPatents;
	}

	public synchronized static Collection<String> getGatherPatents() {
		return new ArrayList<>(gatherPatentSet);
	}

	public synchronized static boolean isExpired(String patent) {
		return expiredPatentSet.contains(patent)||lapsedPatentSet.contains(patent);
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

	private synchronized static Map<String,Collection<String>> loadGatherTechMap() throws SQLException {
		Database.setupGatherConn();
		//Database.setupSeedConn();
		Map<String, Collection<String>> techToPatentMap = new HashMap<>();
		PreparedStatement ps = gatherDBConn.prepareStatement(selectGatherTechnologiesQuery);
		ps.setFetchSize(10);
		//ps.setArray(1, gatherDBConn.createArrayOf("int4",badTech.toArray()));
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			String[] data = (String[])rs.getArray(1).getArray();
			if(data.length<3) continue;
			techToPatentMap.put(rs.getString(2),new HashSet<>(Arrays.asList(data)));
		}
		ps.close();
		Database.close();
		return techToPatentMap;
	}

	public synchronized static Map<String,Collection<String>> getGatherTechMap() {
		if(gatherTechMap==null) {
			gatherTechMap=(Map<String,Collection<String>>)Database.tryLoadObject(gatherTechMapFile);
		}
		return new HashMap<>(gatherTechMap);
	}

	public synchronized static Map<String,Boolean> getGatherValueMap() {
		if(gatherValueMap==null) {
			gatherValueMap=(Map<String,Boolean>)Database.tryLoadObject(gatherValueMapFile);
		}
		return new HashMap<>(gatherValueMap);
	}

	public synchronized static Map<String,Boolean> loadGatherValueMap() throws SQLException {
		Database.setupGatherConn();
		Database.setupSeedConn();
		Map<String, Boolean> map = new HashMap<>();
		PreparedStatement ps = gatherDBConn.prepareStatement(selectGatherRatingsQuery);
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			String[] patents = (String[])rs.getArray(2).getArray();
			Boolean value = rs.getInt(1) >= 4;
			Arrays.stream(patents).forEach(patent->{
				map.put(patent,value);
			});
		}
		ps.close();
		Database.close();
		return map;
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
			gatherTechMap = loadGatherTechMap();
			Database.trySaveObject(gatherTechMap,gatherTechMapFile);
		} catch(SQLException sql) {
			sql.printStackTrace();
		}

		try {
			gatherValueMap = loadGatherValueMap();
			Database.trySaveObject(gatherValueMap,gatherValueMapFile);
		} catch(SQLException sql) {
			sql.printStackTrace();
		}

		allClassCodes = new HashSet<>();
		getPatentToClassificationMap().values().forEach(classSet -> {
			classSet.forEach(cpcClass -> {
				allClassCodes.add(cpcClass);
			});
		});
		trySaveObject(allClassCodes,allClassCodesFile);

		classCodeToPatentMap = new HashMap<>();
		getPatentToClassificationMap().forEach((patent, classes) -> {
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

		System.out.println("Starting to build valuable patents...");
		Set<String> allPatents = new HashSet<>();
		allPatents.addAll(getPatentToInventionTitleMap().keySet());
		allPatents.addAll(getPatentToPubDateMap().keySet());
		allPatents.addAll(getPatentToOriginalAssigneeMap().keySet());
		valuablePatents=allPatents.stream().filter(patent -> !(getExpiredPatentSet().contains(patent)||getLapsedPatentSet().contains(patent))).collect(Collectors.toSet());
		trySaveObject(valuablePatents,valuablePatentsFile);


		Set<String> allApplications = new HashSet<>();
		allApplications.addAll(getAppToInventionTitleMap().keySet());
		allApplications.addAll(getAppToPubDateMap().keySet());
		allApplications.addAll(getAppToOriginalAssigneeMap().keySet());
		valuableApps=allApplications.stream().filter(patent -> !(getLapsedAppSet().contains(patent))).collect(Collectors.toSet());
		trySaveObject(valuableApps,valuableAppsFile);
	}

}
