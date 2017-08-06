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
        if(args.length == 0) throw new RuntimeException("Please include assignee filename");
        getApplicationNumbersFromGrantsAndPublications(args[0]);
        getDistinctCorrespondenceAddressIds((args[0]));
        getDistinctInventorData(args[0]);
    }

    private static final String whereAssigneeQuery = "( upper(assignee) like upper(?) || '%' )";

    public static void getApplicationNumbersFromGrantsAndPublications(String assignee) throws Exception {
        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("select count(application_number) as count,date_part('year',filing_date) as year from pair_applications where filing_date is not null and assignee is not null and "+whereAssigneeQuery+" group by date_part('year',filing_date) order by date_part('year',filing_date)");
        ps.setString(1,assignee);
        ps.setFetchSize(10);
        System.out.println(ps);
        ResultSet rs = ps.executeQuery();
        System.out.println("Count,Year");
        while(rs.next()) {
            System.out.println(rs.getString(1)+","+rs.getString(2));
        }
        rs.close();
        ps.close();
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

    public static void getDistinctInventorData(String assignee) throws Exception {
        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("select distinct first_name,last_name,city,country from pair_applications as a join pair_application_inventors as i on (a.application_number=i.application_number) where first_name is not null and last_name is not null and city is not null and country is not null and "+whereAssigneeQuery);
        ps.setString(1, assignee);
        ps.setFetchSize(10);
        System.out.println("Starting to run query");
        ResultSet rs = ps.executeQuery();

        System.out.println("First Name, Last Name, City, Country");
        while(rs.next()) {
            System.out.println(""+rs.getString(1)+","+rs.getString(2)+","+rs.getString(3)+","+rs.getString(4));
        }

        rs.close();
        ps.close();
    }

    public static void getDistinctCorrespondenceAddressIds(String assignee) throws Exception {
        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("select distinct correspondence_address_id from pair_applications where correspondence_address_id is not null and "+whereAssigneeQuery);
        ps.setString(1, assignee);
        ps.setFetchSize(10);
        System.out.println("Starting to run query");
        ResultSet rs = ps.executeQuery();

        System.out.println("Correspondence Address ID");
        while(rs.next()) {
            System.out.println(rs.getString(1));
        }

        rs.close();
        ps.close();
    }
}
