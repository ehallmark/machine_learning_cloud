package scrape_patexia;

import elasticsearch.IngestMongoIntoElasticSearch;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import seeding.Database;
import seeding.google.attributes.Constants;

import java.io.File;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ReadScrapedData {
    private static Date toSqlDate(LocalDate date) {
        return Date.valueOf(date);
    }

    public static void main(String[] args) throws Exception {
        File inputDir = new File("patexia_dump");
        AtomicInteger total = new AtomicInteger(0);

        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("insert into patexia_litigation (case_number,case_name,plaintiff,defendant,case_date) values (?,?,?,?,?) on conflict (case_number) do update set (case_name,plaintiff,defendant,case_date) = (?,?,?,?)");

        for(File inputFile : inputDir.listFiles()) {
            String document = FileUtils.readFileToString(inputFile);
            Stream.of(document.split("\n   \n  \n \n\n")).forEach(line -> {
                if(line.isEmpty()) return;

                Document parsedDoc = Jsoup.parse(line);
                //System.out.println("lines: "+line.split("<tr").length);
                Elements elements = parsedDoc.select("#resultContainer table tr");
                for (Element element : elements) {

                    Elements tds = element.select("td");

                    if(tds.size()!=3) continue;

                    String caseNumber = tds.get(0).text().trim();
                    String caseName = tds.get(1).text().trim();
                    String caseDate = tds.get(2).text().trim();
                    LocalDate caseDateFormatted = LocalDate.parse(caseDate, DateTimeFormatter.ofPattern("MMM dd, yyyy"));
                    String[] caseNameSplit = caseName.split(" [vV]\\. ");

                    if(caseNameSplit.length<2) {
                        System.out.println("WARNING::Could not split case name. Case Name: "+caseName);
                        continue;
                    }
                    String plaintiff = caseNameSplit[0].trim();
                    String defendant = caseNameSplit[1].trim();

                    //System.out.println("Case number: "+caseNumber);
                    //System.out.println("Case name: "+caseName);
                    //System.out.println("Case plaintiff: "+plaintiff);
                    //System.out.println("Case defendant: "+defendant);
                    //System.out.println("Case date: "+caseDate);
                    //System.out.println("Case date (formatted): "+caseDateFormatted.format(DateTimeFormatter.ISO_DATE));
                    try {
                        ps.setString(1,caseNumber);
                        ps.setString(2,caseName);
                        ps.setString(3,plaintiff);
                        ps.setString(4,defendant);
                        ps.setDate(5, toSqlDate(caseDateFormatted));
                        ps.setString(6,caseName);
                        ps.setString(7,plaintiff);
                        ps.setString(8,defendant);
                        ps.setDate(9, toSqlDate(caseDateFormatted));
                        ps.executeUpdate();
                    } catch(Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                    }

                    total.getAndIncrement();
                }
            });
            System.out.println("Finished file: "+inputFile.getName());
        }

        System.out.println("Total: "+total.get());

        ps.close();
        conn.close();
    }
}
