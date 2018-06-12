package seeding.google.postgres;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Util {
    public static final Function<String,String[]> textToWordFunction = text -> {
        if(text==null)return null;
        return text.toLowerCase().replaceAll("[^a-z ]"," ").split("\\s+");
    };

    public static String getValueStrFor(String[] fields, Collection<String> arrayFields, Collection<String> booleanFields) {
        int numFields = fields.length;
        return "("+String.join(",", IntStream.range(0,numFields).mapToObj(i->{
            String field = fields[i];
            String parentField;
            String childField;
            if(field.contains(".")) {
                parentField = field.substring(0,field.indexOf("."));
                childField = field.substring(field.indexOf(".")+1,field.length());
            } else {
                parentField = field;
                childField = field;
            }
            boolean isDate = childField.equals("date")||childField.endsWith("_date")||childField.endsWith("Date")||childField.contains("_date_");
            String ret = "?";
            if(arrayFields.contains(parentField)) {
                // is array field
                if(isDate) {
                    ret += "::date[]";
                } else if(booleanFields.contains(field)) {
                    ret += "::boolean[]";
                }
            } else {
                // not an array field
                if(isDate) {
                    ret+= "::date";
                } else if(booleanFields.contains(field)) {
                    ret += "::boolean";
                }
            }
            return ret;
        }).collect(Collectors.toList()))+")";
    }
}
