package pair_bulk_data;

import seeding.Database;
import seeding.GetEtsiPatentsList;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Evan on 8/6/2017.
 */
public class DataMassager {
    public static void main(String[] args) throws Exception {
        getDistinctInventorData(args);
    }

    public static void getApplicationNumbersFromGrantsAndPublications(String[] args) throws Exception {
        if(args.length == 0) throw new RuntimeException("Please include excel filename");
        final int offset = 7;
        final int colIdx = 2;
        Collection<String> assets = GetEtsiPatentsList.getExcelList(new File(args[0]),offset,colIdx);
        Collection<String> patents = assets.stream().filter(asset->!Database.isApplication(asset)).collect(Collectors.toList());
        Collection<String> publications = assets.stream().filter(asset->Database.isApplication(asset)).collect(Collectors.toList());
        Connection conn = Database.getConn();

        PreparedStatement ps = conn.prepareStatement("select application_number,filing_date from pair_applications where filing_date is not null and ( grant_number = any(?) or publication_number like any (?) )");
        ps.setArray(1, conn.createArrayOf("varchar",patents.toArray()));
        ps.setArray(2, conn.createArrayOf("varchar",addWildCards(publications).toArray()));
        ps.setFetchSize(10);
        System.out.println("Starting to run query");
        ResultSet rs = ps.executeQuery();

        System.out.println("Count,Year");
        while(rs.next()) {
            System.out.println(""+rs.getString(1)+","+rs.getDate(2));
        }

        rs.close();
    }


    public static void getDistinctInventorData(String[] args) throws Exception {
        if(args.length == 0) throw new RuntimeException("Please include excel filename");
        final int offset = 7;
        final int colIdx = 2;
        Collection<String> assets = GetEtsiPatentsList.getExcelList(new File(args[0]),offset,colIdx);
        Collection<String> patents = assets.stream().filter(asset->!Database.isApplication(asset)).collect(Collectors.toList());
        Collection<String> publications = assets.stream().filter(asset->Database.isApplication(asset)).collect(Collectors.toList());
        Connection conn = Database.getConn();

        PreparedStatement ps = conn.prepareStatement("select distinct (first_name,last_name,city,country) from pair_applications as a join pair_application_inventors as i on (a.application_number=i.application_number) where first_name is not null and last_name is not null and city is not null and country is not null and ( grant_number = any(?) or publication_number like any (?) )");
        ps.setArray(1, conn.createArrayOf("varchar",patents.toArray()));
        ps.setArray(2, conn.createArrayOf("varchar",addWildCards(publications).toArray()));
        ps.setFetchSize(10);
        System.out.println("Starting to run query");
        ResultSet rs = ps.executeQuery();

        System.out.println("First Name, Last Name, City, Country");
        while(rs.next()) {
            System.out.println(""+rs.getString(1)+","+rs.getString(2)+","+rs.getString(3)+","+rs.getString(4));
        }

        rs.close();
    }

    private static List<String> addWildCards(Collection<String> list) {
        return list.stream().map(item->"%"+item+"%").collect(Collectors.toList());
    }
}
