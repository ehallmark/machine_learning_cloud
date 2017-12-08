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

        String g1 = code.substring(0,4);

        String g2 = code.substring(4,8).trim();

        String g3 = code.substring(8,14).trim();

        return g1 + g2 + "/" + g3;
    }

}
