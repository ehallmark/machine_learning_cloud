package seeding.google.postgres;

import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

public class UpdateAIValuesFromPostgres {
    private static String[] splitClaims(String claim) {
        return claim.split("(\\s*\\n\\s*[0-9]*\\s*\\.)");
    }

    public static void main(String[] args) throws SQLException {
        Function<String,Integer> lengthOfSmallestIndependentClaimFunction = claimsText -> {
            String[] claims = splitClaims(claimsText);
            Integer length = null;
            for(String claim : claims) {
                claim = claim.trim();
                if(claim.isEmpty()) continue;
                if(!claim.contains("(canceled)")&&!claim.endsWith(":")) {
                    if(claim.replaceFirst("( claim [0-9])","").length()==claim.length()) {
                        // independent
                        String[] words = claim.split("\\s+");
                        if(length==null||length>words.length) {
                            length = words.length;
                            //System.out.println("Independent claim ("+length+"): "+claim);
                        }
                    }
                }
            }
            return length;
        };

        Function<String,Boolean> meansPresentFunction = claimsText -> {
            String[] claims = splitClaims(claimsText);
            boolean meansPresent = true;
            boolean found = false;
            for(String claim : claims) {
                claim = claim.trim();
                if(claim.isEmpty()) continue;
                if(!claim.contains("(canceled)")&&!claim.endsWith(":")) {
                    found = true;
                    if(claim.replaceFirst("( claim [0-9])","").length()==claim.length()) {
                        // independent
                        if(!claim.contains(" means ")) {
                            meansPresent = false;
                        }
                    }
                }
            }
            if(found) {
                return meansPresent;
            } else {
                return false;
            }
        };

        Function<String,Integer> numberOfClaimsFunctionNaive = claimsText -> {
            return Math.max(1,splitClaims(claimsText).length);
        };
        Function<String,Integer> numberOfClaimsFunction = claimsText -> {
            String[] claims = splitClaims(claimsText);
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
                    //System.out.println("Could not parse: "+num);
                }
            }
            return numberOfClaimsFunctionNaive.apply(claimsText);
        };

        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("select publication_number_full, claims, claims_lang from patents_global where claims is not null and claims_lang is not null limit 10000");
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
                        Integer numClaims = numberOfClaimsFunction.apply(englishClaim);
                        Integer lengthOfSmallestIndependentClaim = lengthOfSmallestIndependentClaimFunction.apply(englishClaim);
                        Boolean meansPresent = meansPresentFunction.apply(englishClaim);
                        //System.out.println("Results for "+number+": "+numClaims+", "+lengthOfSmallestIndependentClaim+", "+meansPresent);
                        if(lengthOfSmallestIndependentClaim==null||lengthOfSmallestIndependentClaim<5) {
                            System.out.println("Likely error for "+number+" ("+lengthOfSmallestIndependentClaim+"): "+englishClaim);
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
