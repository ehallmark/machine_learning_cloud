package tools;


/**
 * Created by Evan on 1/22/2017.
 */
public class ClassCodeHandler {
    public static String convertToLabelFormat(String code) {
        return code.replace("/","").replace(" ","");
    }

    public static String convertToHumanFormat(String code) {
        if(code==null)return null;
        if(code.contains("/")) return code;
        if(code.length()<14) return code;
        return code.substring(0,4).trim()+" "+code.substring(4,8).trim() + "/" + code.substring(8,14).trim();
    }

}
