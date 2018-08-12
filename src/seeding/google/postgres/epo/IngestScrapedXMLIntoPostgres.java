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

public class IngestScrapedXMLIntoPostgres {
    private static Pair<String,String> extractDataFromXML(String xml) {
        Document document = Jsoup.parse(xml);
        String docNumber = document.select("ops|patent-family").get(0).children().first().select("doc-number").text();
        System.out.print("Doc number: "+docNumber);
        if(docNumber.length()==10) {
            // fix docdb format
            docNumber = docNumber.substring(0,4)+"0"+docNumber.substring(4);
        }
        String familyId = document.select("ops|patent-family ops|family-member").get(0).attr("family-id");
        System.out.println(" -> "+familyId);
        return new Pair<>(docNumber,familyId);
    }

    public static void main(String[] args) throws SQLException {
        // this class takes the scraped xml files and updates family ids
        File dataFolder = ScrapeEPO.dataDir;
        if(!dataFolder.exists()) {
            System.out.println("Error... ScrapeEPO.dataDir does not exist...");
            return;
        }

        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("update patents_global set family_id = ? where family_id='-1' and publication_number=? and country_code='US'");
        for(File file : dataFolder.listFiles()) {
            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().forEach(line->{
                    Pair<String,String> publicationToFamilyId = extractDataFromXML(line);
                    try {
                        ps.setString(1,publicationToFamilyId.getSecond());
                        ps.setString(2,publicationToFamilyId.getFirst());
                        ps.executeUpdate();
                    } catch(Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException("Unable to ingest: "+publicationToFamilyId.getFirst()+", "+publicationToFamilyId.getSecond());
                    }
                });
                Database.commit();
            }catch(Exception e) {
                e.printStackTrace();
                System.out.println("Error on file: "+file.getName());
                System.exit(1);
            }
        }

        Database.commit();
    }
}
