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
        getApplicationNumbersFromGrantsAndPublications(args);
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

        System.out.println("Starting to run query");
        ResultSet rs = ps.executeQuery();

        System.out.println("Count,Year");
        while(rs.next()) {
            System.out.println(""+rs.getString(1)+","+rs.getDate(2));
        }

        rs.close();
    }


    private static List<String> addWildCards(Collection<String> list) {
        return list.stream().map(item->"%"+item+"%").collect(Collectors.toList());
    }
}
