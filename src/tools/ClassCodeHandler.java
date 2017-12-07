package tools;


/**
 * Created by Evan on 1/22/2017.
 */
public class ClassCodeHandler {
    public static String convertToLabelFormat(String code) {
        return code.replace(" ","");
    }

    public static String convertToHumanFormat(String code) {
        if(code==null)return null;
        if(code.length()<14) return null;

        String g2 = code.substring(4,8).trim();
        while(g2.startsWith("0")&&g2.length()>0) g2 = g2.substring(1);

        String g3 = code.substring(8,14).trim();
        while(g3.startsWith("0")&&g3.length()>0) g3 = g3.substring(1);

        String cpc = code.substring(0,4) + g2 + "/" + g3;
        if(cpc.endsWith("/")) cpc = cpc.substring(0,cpc.length()-1);
        return cpc;
    }

}
