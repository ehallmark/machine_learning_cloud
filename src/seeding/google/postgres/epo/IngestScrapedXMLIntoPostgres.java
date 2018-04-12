package seeding.google.postgres.epo;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.nd4j.linalg.primitives.Pair;
import seeding.Database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class IngestScrapedXMLIntoPostgres {
    private static Set<String> filesIngested;
    private static final File filesIngestedFile = new File("epo_xml_files_ingested_set.jobj");
    private static void load() {
        if(filesIngested==null) {
            filesIngested = (Set<String>) Database.tryLoadObject(filesIngestedFile);
        }
        if(filesIngested==null) {
            filesIngested = Collections.synchronizedSet(new HashSet<>());
        }

    }

    private static Pair<String,String> extractDataFromXML(String xml) {
        Document document = Jsoup.parse(xml);
        String docNumber = document.select("ops|patent-family").get(0).children().first().select("doc-number").text();
        System.out.print("Doc number: "+docNumber);
        String familyId = document.select("ops|patent-family ops|family-member").get(0).attr("family-id");
        System.out.println(" -> "+familyId);
        return new Pair<>(docNumber,familyId);
    }

    public static void main(String[] args) throws SQLException {
        load();
        // this class takes the scraped xml files and updates family ids
        File dataFolder = ScrapeEPO.dataDir;

        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("update patents_global set family_id = ? where family_id='-1' and publication_number=? and country_code='US'");
        for(File file : dataFolder.listFiles()) {
            if(filesIngested.contains(file.getName())) continue;

            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().forEach(line->{
                    Pair<String,String> publicationToFamilyId = extractDataFromXML(line);
                    if(publicationToFamilyId!=null) {
                        try {
                            ps.setString(1,publicationToFamilyId.getSecond());
                            ps.setString(2,publicationToFamilyId.getFirst());
                            ps.executeUpdate();
                        } catch(Exception e) {
                            e.printStackTrace();
                            throw new RuntimeException("Unable to ingest: "+publicationToFamilyId.getFirst()+", "+publicationToFamilyId.getSecond());
                        }
                    }
                });
                filesIngested.add(file.getName());
            }catch(Exception e) {
                e.printStackTrace();
            }
        }


        // save files ingested set
        Database.trySaveObject(filesIngested,filesIngestedFile);

        Database.commit();
        conn.close();
    }
}
