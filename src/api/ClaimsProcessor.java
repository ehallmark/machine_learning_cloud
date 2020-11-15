package api;

import com.google.gson.Gson;

import java.util.*;

public class ClaimsProcessor {

    public static List<Map<String, Object>> processClaimsString(String claimsText) {
        if (claimsText==null) {
            return Collections.emptyList();
        }

        String[] claims = (claimsText
                .trim()
                .replaceFirst("^CLAIM\\n","") + "\n")
                .replaceAll("\\n(\\s|\\n)+", "\n")
                .replaceAll("\\n(\\D)", " $1")
                .replaceAll("\\n(\\d\\))", " $1")
                .replaceFirst("^([^0-9])*([0-9\\\\.])*", "")
                .replace("\n", " \n")
                .replace("  ", " ")
                .replaceFirst("\\\\n$", "")
                .trim()
                .split("\\\\n(\\d+)\\.\\s*");

        List<Map<String,Object>> ret = new ArrayList<>(claims.length);
        for(int i = 0; i < claims.length; i++) {
            Map<String, Object> elem = new HashMap<>();

            elem.put("number", (i+1));
            elem.put("text", claims[i]);
            elem.put("independent", checkIndependence(claims[i]));

            System.out.println("Claim data: "+new Gson().toJson(elem));

            ret.add(elem);
        }
        return ret;
    }

    private static boolean checkIndependence(String claim) {

        return true;
    }
}
