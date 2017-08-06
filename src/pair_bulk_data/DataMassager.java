package pair_bulk_data;

import seeding.Database;
import seeding.GetEtsiPatentsList;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 8/6/2017.
 */
public class DataMassager {
    public static void main(String[] args) throws Exception {
        if(args.length < 2) throw new RuntimeException("Please include excel filename and output filename ");
        getApplicationNumbersFromGrantsAndPublications(args[0],args[1]);
    }

    private static final String whereGrantsAndPublicationsQuery = "( grant_number = any(?) or string_to_array(publication_number,' ')::varchar[] && (?::varchar[]) )";
    private static final String whereFilingsQuery = "( application_number = any(?) )";

    public static void getApplicationNumbersFromGrantsAndPublications(String inputFile, String outputFile) throws Exception {
        final int offset = 7;
        final int colIdx = 2;
        Collection<String> assets = GetEtsiPatentsList.getExcelList(new File(inputFile),offset,colIdx);
        Collection<String> patents = assets.stream().filter(asset->!Database.isApplication(asset)).collect(Collectors.toList());
        Collection<String> publications = assets.stream().filter(asset->Database.isApplication(asset)).collect(Collectors.toList());
        Connection conn = Database.getConn();

        PreparedStatement ps = conn.prepareStatement("select application_number,filing_date from pair_applications where filing_date is not null and "+whereGrantsAndPublicationsQuery);
        ps.setArray(1, conn.createArrayOf("varchar",patents.toArray()));
        ps.setArray(2, conn.createArrayOf("varchar",publications.toArray()));
        ps.setFetchSize(10);
        System.out.println("Starting to run query");
        ResultSet rs = ps.executeQuery();

        System.out.println("App Number,Date");
        File file = new File(outputFile);
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        AtomicInteger cnt = new AtomicInteger(0);
        while(rs.next()) {
            writer.write(rs.getString(1)+","+rs.getDate(2)+"\n");
            if(cnt.getAndIncrement()%1000==999) {
                System.out.println(cnt.get());
                writer.flush();
            }
        }
        writer.flush();
        writer.close();

        rs.close();
    }


    private static Collection<String> loadCSVColumn(File file, int colIdx) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        return reader.lines().map(line->{
            String[] fields = line.split(",");
            if(fields.length > colIdx) {
                return fields[colIdx];
            } else return null;
        }).filter(field->field!=null).collect(Collectors.toList());
    }

    public static void getDistinctInventorData(String[] args) throws Exception {
        if(args.length == 0) throw new RuntimeException("Please include excel filename");
        final int colIdx = 0;
        Collection<String> assets = loadCSVColumn(new File(args[0]),colIdx);
        Connection conn = Database.getConn();

        PreparedStatement ps = conn.prepareStatement("select distinct (first_name,last_name,city,country) from pair_applications as a join pair_application_inventors as i on (a.application_number=i.application_number) where first_name is not null and last_name is not null and city is not null and country is not null and "+whereFilingsQuery);
        ps.setArray(1, conn.createArrayOf("varchar",assets.toArray()));
        ps.setFetchSize(10);
        System.out.println("Starting to run query");
        ResultSet rs = ps.executeQuery();

        System.out.println("First Name, Last Name, City, Country");
        while(rs.next()) {
            System.out.println(""+rs.getString(1)+","+rs.getString(2)+","+rs.getString(3)+","+rs.getString(4));
        }

        rs.close();
    }

    public static void getDistinctCorrespondenceAddressIds(String[] args) throws Exception {
        if(args.length == 0) throw new RuntimeException("Please include excel filename");
        final int colIdx = 0;
        Collection<String> assets = loadCSVColumn(new File(args[0]),colIdx);
        Connection conn = Database.getConn();

        PreparedStatement ps = conn.prepareStatement("select correspondence_address_id from pair_applications where correspondence_address_id is not null and "+whereFilingsQuery);
        ps.setArray(1, conn.createArrayOf("varchar",assets.toArray()));
        ps.setFetchSize(10);
        System.out.println("Starting to run query");
        ResultSet rs = ps.executeQuery();

        System.out.println("Correspondence Address ID");
        while(rs.next()) {
            System.out.println(rs.getString(1));
        }

        rs.close();
    }

    private static List<String> addWildCards(Collection<String> list) {
        return list.stream().map(item->"%"+item+"%").collect(Collectors.toList());
    }
}
