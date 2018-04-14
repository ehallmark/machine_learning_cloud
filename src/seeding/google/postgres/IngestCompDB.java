package seeding.google.postgres;

import seeding.Constants;
import seeding.Database;

import java.sql.Array;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class IngestCompDB {

    public static void main(String[] args) throws Exception {
        Connection compDBConn = Database.getCompDBConnection();
        PreparedStatement ps = compDBConn.prepareStatement("SELECT array_agg(distinct t.id) as technologies, array_agg(distinct (reel||':'||frame)) AS reelframes, min(r.recording_date) as recording_date, bool_or(coalesce(r.inactive,'t')) as inactive, bool_and(coalesce(d.acquisition_deal,'f')) as acquisition, r.deal_id FROM recordings as r inner join deals_technologies as dt on (r.deal_id=dt.deal_id) INNER JOIN technologies AS t ON (t.id=dt.technology_id) join deals as d on(r.deal_id=d.id)  WHERE r.deal_id IS NOT NULL AND t.name is not null and recording_date is not null GROUP BY r.deal_id");
        Map<Integer, String> technologyMap = Database.compdbTechnologyMap();
        ResultSet rs = ps.executeQuery();

        Connection seedConn = Database.getConn();
        PreparedStatement insert = seedConn.prepareStatement("insert into big_query_compdb_deals (deal_id,recorded_date,technology,inactive,acquisition,reel_frame) values (?,?,?,?,?,?) on conflict (deal_id) do update set (technology,recorded_date,inactive,acquisition,reel_frame)=(?,?,?,?,?)");

        System.out.println("Finished collecting reelframe to assets map.");
        while(rs.next()) {
            Collection<String> technologies = new HashSet<>();
            for(Integer tech : (Integer[])rs.getArray(1).getArray()) {
                technologies.add(technologyMap.get(tech));
            }
            Integer dealID = rs.getInt(6);
            boolean acquisition = rs.getBoolean(5);
            boolean inactive = rs.getBoolean(4);
            Date recordingDate = rs.getDate(3);//.toLocalDate().format(DateTimeFormatter.ISO_DATE);

            Array reelFramesForDeal = rs.getArray(2);
            if(reelFramesForDeal==null) continue;
            String[] reelFramesStr = (String[]) reelFramesForDeal.getArray();
            if(reelFramesStr.length>0) {
                insert.setString(1,dealID.toString());
                insert.setDate(2,recordingDate);
                insert.setArray(3,seedConn.createArrayOf("varchar",technologies.toArray(new String[technologies.size()])));
                insert.setBoolean(4, inactive);
                insert.setBoolean(5, acquisition);
                insert.setArray(6,seedConn.createArrayOf("varchar",reelFramesStr));
                insert.setDate(7,recordingDate);
                insert.setArray(8,seedConn.createArrayOf("varchar",technologies.toArray(new String[technologies.size()])));
                insert.setBoolean(9, inactive);
                insert.setBoolean(10, acquisition);
                insert.setArray(11,seedConn.createArrayOf("varchar",reelFramesStr));
                insert.executeUpdate();
            }

        }
        Database.commit();
        compDBConn.close();
        Database.close();
    }
}
