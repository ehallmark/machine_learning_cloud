package compdb_adapter;

import seeding.Database;
import tools.AssigneeTrimmer;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 3/28/17.
 */
public class CreateCompDBAssigneeTransactionData {
    public static final File sellerFile = new File("compdb_assignee_to_assets_sold_count_map.jobj");
    public static final File buyerFile = new File("compdb_assignee_to_assets_purchased_count_map.jobj");
    private static final String buyerQuery = "select buyer_name, sum(asset_count) as num_assets_purchased from (select case when (buyer.normalized_company_id is null) then buyer.name else (select name from normalized_companies where id = buyer.normalized_company_id) end as buyer_name, asset_count from recordings as r join recordings_buyers as rb on (r.id=rb.recording_id) join companies as buyer on (buyer.id=rb.company_id) where deal_id is not null) as temp group by buyer_name";
    private static final String sellerQuery = "select seller_name, sum(asset_count) as num_assets_sold from (select case when (seller.normalized_company_id is null) then seller.name else (select name from normalized_companies where id = seller.normalized_company_id) end as seller_name, asset_count from recordings as r join recordings_sellers as rb on (r.id=rb.recording_id) join companies as seller on (seller.id=rb.company_id) where deal_id is not null) as temp group by seller_name";

    public static void main(String[] args) throws Exception {
        Connection conn = Database.getCompDBConnection();
        // Buyers
        System.out.println("Starting buyers...");
        PreparedStatement buyerStatement = conn.prepareStatement(buyerQuery);
        mergeAndSaveMap(loadMapFromPreparedStatement(buyerStatement),buyerFile);
        buyerStatement.close();
        // Sellers
        System.out.println("Starting sellers... ");
        PreparedStatement sellerStatement = conn.prepareStatement(sellerQuery);
        mergeAndSaveMap(loadMapFromPreparedStatement(sellerStatement),sellerFile);
        sellerStatement.close();
        System.out.println("Finished.");
    }

    private static Map<String,Integer> loadMapFromPreparedStatement(PreparedStatement ps) throws SQLException {
        Map<String,Integer> map = new HashMap();
        ResultSet rs = ps.executeQuery();
        while(rs.next()) {
            map.put(rs.getString(1),rs.getInt(2));
        }
        return map;
    }

    private static void mergeAndSaveMap(Map<String,Integer> map, File toSave) {
        Map<String,Integer> newMap = new HashMap<>(map.size());
        map.forEach((k,v)->{
            String assignee = AssigneeTrimmer.standardizedAssignee(k);
            if(newMap.containsKey(assignee)) {
                newMap.put(assignee,newMap.get(assignee)+v);
            } else {
                newMap.put(assignee,v);
            }
        });
        System.out.println("Size: "+newMap.size());
        Database.trySaveObject(newMap,toSave);
    }
}
