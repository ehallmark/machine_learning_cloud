package seeding.google.predict_wipo;

import seeding.Database;
import seeding.google.elasticsearch.attributes.WipoTechnology;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class WIPOPredictionModel {
    /*
    This class is designed to predict WIPO codes given a CPC tree
     */
    public static void main(String[] args) throws Exception {
        // get wipo
        Connection conn = Database.getConn();
        PreparedStatement wipoPs = conn.prepareStatement("select publication_number, wipo_technology from big_query_wipo");
        PreparedStatement cpcPs = conn.prepareStatement("select publication_number_full, tree from big_query_cpc_tree");


    }

}
