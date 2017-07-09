package ai_db_updater.database;

import ai_db_updater.tools.*;
import net.lingala.zip4j.core.ZipFile;
import ui_models.portfolios.PortfolioList;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by ehallmark on 1/3/17.
 */
public class Database {
    private static final String patentDBUrl = "jdbc:postgresql://localhost/patentdb?user=postgres&password=password&tcpKeepAlive=true";
    private static Connection conn;
    private static String CPC_ZIP_FILE_NAME = "patent_grant_classifications.zip";
    private static String CPC_DESTINATION_FILE_NAME = "patent_grant_classifications_folder";
    private static String MAINT_ZIP_FILE_NAME = "patent_grant_maint_fees.zip";
    private static String MAINT_DESTINATION_FILE_NAME = "patent_grant_maint_fees_folder";
    public static File expiredPatentsSetFile = new File("expired_patents_set.jobj");
    public static File patentToClassificationMapFile = new File("patent_to_classification_map.jobj");
    public static File patentToInventionTitleMapFile = new File("patent_to_invention_title_map.jobj");
    public static File patentToOriginalAssigneeMapFile = new File("patent_to_original_assignee_map.jobj");
    public static File patentToReferencedByMapFile = new File("patent_to_referenced_by_map.jobj");
    public static File patentToPubDateMapFile = new File("patent_to_pubdate_map_file.jobj");
    public static File patentToAppDateMapFile = new File("patent_to_appdate_map_file.jobj");
    public static File patentToRelatedDocMapFile = new File("patent_to_related_docs_map_file.jobj");
    public static File pubDateToPatentMapFile = new File("pubdate_to_patent_map.jobj");
    public static File patentToCitedPatentsMapFile = new File("patent_to_cited_patents_map.jobj");
    public static File lapsedPatentsSetFile = new File("lapsed_patents_set.jobj");
    public static File patentToPriorityDateMapFile = new File("patent_to_priority_date_map.jobj");
    public static File expiredAppsSetFile = new File("expired_apps_set.jobj");
    public static File appToClassificationMapFile = new File("app_to_classification_map.jobj");
    public static File appToInventionTitleMapFile = new File("app_to_invention_title_map.jobj");
    public static File appToOriginalAssigneeMapFile = new File("app_to_original_assignee_map.jobj");
    public static File appToPubDateMapFile = new File("app_to_pubdate_map_file.jobj");
    public static File appToAppDateMapFile = new File("app_to_appdate_map_file.jobj");
    public static File appToRelatedDocMapFile = new File("app_to_related_docs_map_file.jobj");
    public static File appToCitedPatentsMapFile = new File("app_to_cited_patents_map.jobj");
    public static File lapsedAppsSetFile = new File("lapsed_apps_set.jobj");
    public static File appToPriorityDateMapFile = new File("app_to_priority_date_map.jobj");
    static {
        try {
            conn = DriverManager.getConnection(patentDBUrl);
            conn.setAutoCommit(false);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static Object loadObject(File file) {
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

    public static void saveObject(Object obj, File file) {
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

    public static void saveExpiredPatentsSet(Set<String> expiredPatentsSet) throws IOException {
        if(expiredPatentsSet!=null) {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(expiredPatentsSetFile)));
            oos.writeObject(expiredPatentsSet);
            oos.flush();
            oos.close();
        }
    }

    public static void savePatentToClassificationHash(Map<String,Set<String>> patentToClassificationHash) throws IOException {
        if(patentToClassificationHash!=null) {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(patentToClassificationMapFile)));
            oos.writeObject(patentToClassificationHash);
            oos.flush();
            oos.close();
        }
    }

    public static void loadAndIngestMaintenanceFeeData() throws Exception {
        // should be one at least every other month
        // Load file from Google
        Map<String,Integer> patentToMaintenanceFeeReminderCount = new HashMap<>();
        Set<String> expiredPatentsSet = new HashSet<>();
        Set<String> largeEntityPatents = new HashSet<>();
        Set<String> smallEntityPatents = new HashSet<>();
        Set<String> microEntityPatents = new HashSet<>();
        if (!(new File(MAINT_DESTINATION_FILE_NAME).exists())) {
            try {
                String url = "https://bulkdata.uspto.gov/data2/patent/maintenancefee/MaintFeeEvents.zip";
                URL website = new URL(url);
                System.out.println("Trying: " + website.toString());
                ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                FileOutputStream fos = new FileOutputStream(MAINT_ZIP_FILE_NAME);
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                fos.close();

                ZipFile zipFile = new ZipFile(MAINT_ZIP_FILE_NAME);
                zipFile.extractAll(MAINT_DESTINATION_FILE_NAME);

            } catch (Exception e) {
                //e.printStackTrace();
                System.out.println("Not found");
            }
        }
        Arrays.stream(new File(MAINT_DESTINATION_FILE_NAME).listFiles()).forEach(file -> {
            if (!file.getName().endsWith(".txt")) return;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();
                while (line != null) {
                    if (line.length() >= 50) {
                        String patNum = line.substring(0, 7);
                        try {
                            String maintenanceCode = line.substring(46, 51).trim();
                            if (patNum != null && maintenanceCode != null ) {
                                if(maintenanceCode.equals("EXP.")) {
                                    System.out.println(patNum + " has expired... Updating database now.");
                                    expiredPatentsSet.add(patNum);
                                } else if (maintenanceCode.equals("EXPX")) {
                                    // reinstated
                                    System.out.println(patNum+" was reinstated!");
                                    if(expiredPatentsSet.contains(patNum)) {
                                        expiredPatentsSet.remove(patNum);
                                    }
                                } else if (maintenanceCode.equals("REM.")) {
                                    // reminder
                                    if(patentToMaintenanceFeeReminderCount.containsKey(patNum)) {
                                        patentToMaintenanceFeeReminderCount.put(patNum,patentToMaintenanceFeeReminderCount.get(patNum)+1);
                                    } else {
                                        patentToMaintenanceFeeReminderCount.put(patNum,1);
                                    }
                                } else if (maintenanceCode.startsWith("M2")||maintenanceCode.startsWith("SM")||maintenanceCode.equals("LTOS")||maintenanceCode.equals("MTOS")) {
                                    smallEntityPatents.add(patNum);
                                    if(largeEntityPatents.contains(patNum)) largeEntityPatents.remove(patNum);
                                    if(microEntityPatents.contains(patNum)) microEntityPatents.remove(patNum);
                                } else if (maintenanceCode.startsWith("M1")||maintenanceCode.startsWith("LSM")) {
                                    largeEntityPatents.add(patNum);
                                    if(smallEntityPatents.contains(patNum)) smallEntityPatents.remove(patNum);
                                    if(microEntityPatents.contains(patNum)) microEntityPatents.remove(patNum);
                                } else if(maintenanceCode.startsWith("M3")||maintenanceCode.equals("STOM")) {
                                    microEntityPatents.add(patNum);
                                    if(largeEntityPatents.contains(patNum)) largeEntityPatents.remove(patNum);
                                    if(smallEntityPatents.contains(patNum)) smallEntityPatents.remove(patNum);
                                }
                            }
                        } catch (NumberFormatException nfe) {
                            // not a utility patent
                            // skip...
                        }
                    }
                    line = reader.readLine();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        saveExpiredPatentsSet(expiredPatentsSet);

        File largeEntityPatentFile = new File("large_entity_patents_set.jobj");
        Database.saveObject(largeEntityPatents,largeEntityPatentFile);

        File smallEntityPatentFile = new File("small_entity_patents_set.jobj");
        Database.saveObject(smallEntityPatents,smallEntityPatentFile);

        File microEntityPatentFile = new File("micro_entity_patents_set.jobj");
        Database.saveObject(microEntityPatents,microEntityPatentFile);

        File patentToFeeReminderMapFile = new File("patent_to_fee_reminder_count_map.jobj");
        Database.saveObject(patentToMaintenanceFeeReminderCount,patentToFeeReminderMapFile);


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
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(expiredPatentsSetFile)));
        set = (Set<String>)ois.readObject();
        ois.close();
        return set;
    }

    public static void setupClassificationsHash() throws Exception{
        // should be one at least every other month
        // Load file from Google
        Map<String,Set<String>> patentToClassificationHash = new HashMap<>();
        if (!(new File(CPC_DESTINATION_FILE_NAME).exists())) {
            boolean found = false;
            LocalDate date = LocalDate.now();
            while (!found) {
                try {
                    String dateStr = String.format("%04d", date.getYear()) + "-" + String.format("%02d", date.getMonthValue()) + "-" + String.format("%02d", date.getDayOfMonth());
                    String url = "http://patents.reedtech.com/downloads/PatentClassInfo/ClassData/US_Grant_CPC_MCF_Text_" + dateStr + ".zip";
                    URL website = new URL(url);
                    System.out.println("Trying: " + website.toString());
                    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                    FileOutputStream fos = new FileOutputStream(CPC_ZIP_FILE_NAME);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();

                    ZipFile zipFile = new ZipFile(CPC_ZIP_FILE_NAME);
                    zipFile.extractAll(CPC_DESTINATION_FILE_NAME);

                    found = true;
                } catch (Exception e) {
                    //e.printStackTrace();
                    System.out.println("Not found");
                }
                date = date.minusDays(1);
            }
        }

        Arrays.stream(new File(CPC_DESTINATION_FILE_NAME).listFiles(File::isDirectory)[0].listFiles()).forEach(file -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();
                while (line != null) {
                    if (line.length() >= 32) {
                        String patNum = line.substring(10, 17).trim();
                        try {
                            if (Integer.valueOf(patNum) >= 6000000) {
                                String cpcSection = line.substring(17, 18);
                                String cpcClass = cpcSection + line.substring(18, 20);
                                String cpcSubclass = cpcClass + line.substring(20, 21);
                                String cpcMainGroup = cpcSubclass + line.substring(21, 25);
                                String cpcSubGroup = cpcMainGroup + line.substring(26, 32);
                                System.out.println("Data for " + patNum + ": " + String.join(", ", cpcSubGroup));
                                if (patentToClassificationHash != null) {
                                    if (patentToClassificationHash.containsKey(patNum)) {
                                        patentToClassificationHash.get(patNum).add(cpcSubGroup);
                                    } else {
                                        Set<String> data = new HashSet<>();
                                        data.add(cpcSubGroup);
                                        patentToClassificationHash.put(patNum, data);
                                    }
                                }
                            }
                        } catch (NumberFormatException nfe) {
                            // not a utility patent
                            // skip...
                        }
                    }
                    line = reader.readLine();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        savePatentToClassificationHash(patentToClassificationHash);

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
            if(i!=documents.size()-1) query += " || ";
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

    public static synchronized void close() throws SQLException {
        conn.close();
    }

}
