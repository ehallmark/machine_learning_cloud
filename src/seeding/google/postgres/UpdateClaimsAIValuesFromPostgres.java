package seeding.google.postgres;

import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

public class UpdateClaimsAIValuesFromPostgres {
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

        Connection seedConn = Database.newSeedConn();
        Connection updateConn = Database.getConn();
        PreparedStatement ps = seedConn.prepareStatement("select distinct on (family_id) family_id, publication_number_full, claims, claims_lang from patents_global where claims is not null and claims_lang is not null order by family_id, country_code='US' desc nulls last, publication_date desc nulls last");
        PreparedStatement updater = updateConn.prepareStatement("insert into big_query_ai_value_claims (family_id,publication_number_full,means_present,num_claims,length_smallest_ind_claim) values (?,?,?::integer,?::integer,?::integer) on conflict do nothing");
        ps.setFetchSize(10);
        ResultSet rs = ps.executeQuery();
        long total = 0;
        long valid = 0;
        while(rs.next()) {
            String familyId = rs.getString(1);
            String docNum = rs.getString(2);
            String[] claims = (String[])rs.getArray(3).getArray();
            String[] claimLangs = (String[])rs.getArray(4).getArray();
            if(claims!=null&&claimLangs!=null) {
                for(int i = 0; i < Math.min(claims.length,claimLangs.length); i++) {
                    if(claimLangs[i].toLowerCase().equals("en")) {
                        String englishClaim = claims[i];
                        Integer numClaims = numberOfClaimsFunction.apply(englishClaim);
                        Integer lengthOfSmallestIndependentClaim = lengthOfSmallestIndependentClaimFunction.apply(englishClaim);
                        Boolean meansPresent = meansPresentFunction.apply(englishClaim);
                        //System.out.println("Results for "+number+": "+numClaims+", "+lengthOfSmallestIndependentClaim+", "+meansPresent);
                        if(lengthOfSmallestIndependentClaim==null||lengthOfSmallestIndependentClaim<=2) {
                            lengthOfSmallestIndependentClaim=null;
                        }

                        // update
                        updater.setString(1, familyId);
                        updater.setString(2, docNum);
                        updater.setObject(3, meansPresent==null?null:meansPresent?1:0);
                        updater.setObject(4, numClaims);
                        updater.setObject(5, lengthOfSmallestIndependentClaim);
                        updater.executeUpdate();
                        valid++;
                        break;
                    }
                }
            }
            total++;
            if(total%10000==9999) {
                System.out.println("Ingested: "+total+" (Valid: "+valid+")");
                Database.commit();
            }
        }

        System.out.println("Valid: "+valid+" out of "+total);

        Database.commit();

        updater.close();
        rs.close();
        ps.close();
        seedConn.close();
        updateConn.close();
    }
}
