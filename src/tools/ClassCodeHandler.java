package tools;

import java.util.StringJoiner;

/**
 * Created by Evan on 1/22/2017.
 */
public class ClassCodeHandler {
    public static String convertToLabelFormat(String code) {
        if(code==null)return null;
        if(!code.contains("/")||code.indexOf("/")<5) return code;
        if(code.length()<7)return code;
        String mainGroup = code.substring(4,code.indexOf("/")).trim();
        String lPad = "";
        for(int i = 0; i < 4 - mainGroup.length(); i++) {
            lPad+=" ";
        }
        String subGroup = code.substring(code.indexOf("/"),code.length()).trim();
        String rPad = "";
        for(int i = 0; i < 6 - subGroup.length(); i++) {
            rPad+=" ";
        }
        String formattedCode = code.substring(0,4)+ lPad + mainGroup + subGroup + rPad;
        if(formattedCode.length()!=14) throw new RuntimeException("Error converting class code: "+code);
        return formattedCode;
    }

    public static String convertToHumanFormat(String code) {
        if(code==null)return null;
        if(code.contains("/")) return code;
        if(code.length()<14) return code;
        return code.substring(0,4).trim()+" "+code.substring(4,8).trim() + "/" + code.substring(8,14).trim();
    }

    public static boolean isClassCode(String code) {
        return code.length()==14&&code.replaceAll("[^0-9]","").length()>0;
    }
}
