package seeding.google.postgres;

import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

public class UpdateAIValuesFromPostgres {
    public static void main(String[] args) throws SQLException {
        Function<String,Integer> numberOfClaimsFunction = claimsText -> {
            return claimsText.split("(\\n\\s+\\n\\s+)").length;
        };

        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("select publication_number, claims, claims_lang from patents_global where claims is not null and claims_lang is not null limit 10000");
        ps.setFetchSize(10);
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
                        System.out.println("Number of claims: "+numberOfClaimsFunction.apply(englishClaim));
                    }
                }
            }
        }

        rs.close();
        ps.close();
        conn.close();
    }
}
