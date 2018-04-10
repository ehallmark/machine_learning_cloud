package seeding.google.postgres;

import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UpdateAIValuesFromPostgres {
    public static void main(String[] args) throws SQLException {
        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("select publication_number, claims_localized, claims_localized_lang from patents_global limit 10000");
        ResultSet rs = ps.executeQuery();
        while(rs.next()) {
            String number = rs.getString(1);
            String[] claims = (String[])rs.getArray(2).getArray();
            String[] claimLangs = (String[])rs.getArray(3).getArray();
            if(claims!=null&&claimLangs!=null) {
                for(int i = 0; i < Math.min(claims.length,claimLangs.length); i++) {
                    if(claimLangs[i].toLowerCase().equals("en")) {
                        String englishClaim = claims[i];
                        System.out.println("Claim for "+number+": "+englishClaim);
                    }
                }
            }
        }

        rs.close();
        ps.close();
        conn.close();
    }
}
