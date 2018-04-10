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
            return Math.max(1,claimsText.split("(\\n\\s+\\n\\s+)").length);
        };
        Function<String,Integer> numberOfClaimsFunction2 = claimsText -> {
            String[] claims = claimsText.split("(\\n\\s+\\n\\s+)");
            String lastClaim = claims[claims.length-1].trim();
            if(lastClaim.isEmpty()&&claims.length>1) {
                lastClaim = claims[claims.length-2].trim();
            }
            int idx = lastClaim.indexOf(".");
            if(idx>0) {
                String num = lastClaim.substring(0,idx).trim();
                int dashIndex = num.indexOf("-");
                if(dashIndex>0&&num.length()>dashIndex+1) {
                    num = num.substring(dashIndex+1,num.length()).trim();
                }
                try {
                    return Integer.valueOf(num);
                } catch(Exception e) {
                    System.out.println("Could not parse: "+num);
                }
            }
            return numberOfClaimsFunction.apply(claimsText);
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
                        int numClaims1 = numberOfClaimsFunction.apply(englishClaim);
                        int numClaims2 = numberOfClaimsFunction2.apply(englishClaim);
                        if(numClaims1!=numClaims2) {
                            System.out.println("Mismatched claim length: "+numClaims1+" != "+numClaims2);
                            System.out.println("Claim: "+englishClaim);
                        }
                    }
                }
            }
        }

        rs.close();
        ps.close();
        conn.close();
    }
}
