package seeding.ai_db_updater.handlers.flags;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import seeding.Constants;
import tools.AssigneeTrimmer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Created by Evan on 8/6/2017.
 */
public class Flag {
    // functions
    protected static final Function<Flag,Function<String,Boolean>> defaultCompareFunction = (flag) -> (str) ->{
        return flag.localName.equals(str);
    };

    protected static Function<Flag,Function<String,Boolean>> multiCompareFunction(Collection<String> list) {
        return f -> str -> {
            boolean result = list.contains(str);
            if(result) f.currentTag = str;
            else f.currentTag = null;
            return list.contains(str);
        };
    }

    public static final Function<Flag,Function<String,Boolean>> endsWithCompareFunction = (flag) -> (str) ->{
        return flag.localName.endsWith(str);
    };

    private static Function<String,Boolean> validDateFunction = (str) -> {
        try {
            LocalDate.parse(str, DateTimeFormatter.ISO_DATE);
            return true;
        } catch (Exception e) {
            return false;
        }
    };

    private static Function<String,Boolean> validIntegerFunction = (str) -> {
        try {
            Integer.valueOf(str);
            return true;
        } catch(Exception e) {
            return false;
        }
    };

    public static  Function<Flag,Function<String,?>> dateTransformationFunction(DateTimeFormatter format) {
         return flag->(str)->{
             String date = str!=null&&str.endsWith("00")?str.substring(0,str.length()-1)+"1":str;
             try {
                 return LocalDate.parse(date,format).format(DateTimeFormatter.ISO_DATE);
             } catch(Exception e) {
                 return null;
             }
         };

    }

    public static final Function<Flag,Function<String,?>> assigneeTransformationFunction = (flag) -> (str) -> AssigneeTrimmer.standardizedAssignee(str);

    public static final Function<Flag,Function<String,?>> defaultTransformationFunction = (flag)->(str) -> str;


    public static final Function<Flag,Function<String,?>> filingDocumentHandler = (flag) -> (str) -> {
        str=str.replace(" ","").replace("/","");
        if(str.length()==8) {
            return str.substring(0,2)+"/"+str.substring(2);
        } else if(str.length()==7) {
            return "0"+str.substring(0,1)+"/"+str.substring(1);
        }
        return str;
    };

    public static final Function<Flag,Function<String,?>> unknownDocumentHandler = (flag) -> (str) -> {
        str = str.replace(" ","").replace("/","");
        String kind = flag.getDataForField(Constants.DOC_KIND);
        if(kind.startsWith("S")) {
            // design
            if(str.startsWith("D0")) str = "D"+str.substring(2);
        } else if (kind.trim().equals("A")||kind.equals("00")||kind.equals("X0")) {
            // filing?
            return filingDocumentHandler.apply(flag).apply(str);
        } else if(kind.startsWith("B")) {
            if(str.startsWith("0")) str = str.substring(1);
        } else if(kind.startsWith("E")) {
            //reissue
            if(str.startsWith("RE0")) str = "RE" + str.substring(3);
        }
        return str;
    };

    public final String localName;
    public String currentTag;
    public String dbName;
    public final AtomicBoolean flag;
    public final List<Flag> children;
    boolean isAttributeFlag = false;
    public Function<Flag,Function<String,?>> transformationFunction;
    public String type;
    @Setter
    public Function<Flag,Function<String,Boolean>> compareFunction;
    public final Function<String,Boolean> validValueFunction;
    @Getter
    public EndFlag endFlag;
    private final int id;
    protected boolean isForeign = false;
    private static final AtomicInteger idCounter = new AtomicInteger(0);
    protected Flag(String localName, String dbName, String type, Function<String,Boolean> validValueFunction, Function<Flag,Function<String,Boolean>> compareFunction, Function<Flag,Function<String,?>> transformationFunction, EndFlag endFlag) {
        this.dbName=dbName;
        this.validValueFunction=validValueFunction;
        this.type=type;
        this.localName=localName;
        this.flag = new AtomicBoolean(false);
        this.children = new ArrayList<>();
        this.endFlag=endFlag;
        this.compareFunction=compareFunction;
        this.transformationFunction = transformationFunction;
        this.setEndFlag(endFlag);
        this.id=idCounter.getAndIncrement();
    }

    public void setEndFlag(EndFlag endFlag) {
        if(endFlag!=null&&dbName!=null)endFlag.flagMap.put(dbName,this);
        this.endFlag=endFlag;
    }

    public Flag isAttributesFlag(boolean isAttributeFlag) {
        this.isAttributeFlag=isAttributeFlag;
        return this;
    }

    public static Flag fakeFlag(@NonNull String dbName) {
        return new Flag(null,dbName,null,null,null,null,null);
    }

    public static Flag parentFlag(@NonNull String localName) {
        return new Flag(localName,null,null,null,defaultCompareFunction,null,null);
    }

    public static Flag simpleFlag(@NonNull String localName,@NonNull String dbName, EndFlag endFlag) {
        return new Flag(localName,dbName,"text",(str)->true,defaultCompareFunction,defaultTransformationFunction,endFlag);
    }

    public static Flag booleanFlag(@NonNull String localName,@NonNull String dbName, EndFlag endFlag) {
        return new Flag(localName,dbName,"boolean",(str)->str.equals("true")||str.equals("false"),defaultCompareFunction,defaultTransformationFunction,endFlag);
    }

    public static Flag dateFlag(@NonNull String localName,@NonNull String dbName, EndFlag endFlag) {
        return new Flag(localName,dbName,"date",validDateFunction,defaultCompareFunction,defaultTransformationFunction,endFlag);
    }

    public static Flag integerFlag(@NonNull String localName,@NonNull String dbName, EndFlag endFlag) {
        return new Flag(localName,dbName,"int",validIntegerFunction,defaultCompareFunction,defaultTransformationFunction,endFlag);
    }

    public static Flag customFlag(@NonNull String localName,@NonNull String dbName,@NonNull String type, @NonNull Function<String,Boolean> validationFunction, EndFlag endFlag) {
        return new Flag(localName,dbName,type,validationFunction,defaultCompareFunction,defaultTransformationFunction,endFlag);
    }

    public Flag withTransformationFunction(Function<Flag,Function<String,?>> transformationFunction) {
        this.transformationFunction=transformationFunction;
        return this;
    }

    public boolean compareTag(String tag) {
        return compareFunction.apply(this).apply(tag);
    }

    public boolean validValue(String text) {
        return validValueFunction.apply(text);
    }

    public boolean get() {
        return flag.get();
    }

    public void addChild(Flag child) {
        children.add(child);
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public void setTrueIfEqual(String otherName) {
        if(compareTag(otherName)) {
            flag.set(true);
        }
    }

    public String getDataForField(String field) {
        Flag flag = getEndFlag().flagMap.get(field);
        if(flag==null) return "";
        return getEndFlag().getDataMap().getOrDefault(flag,"");
    }

    public void reset() {
        flag.set(false);
    }

    public Object apply(String text) {
        return transformationFunction.apply(this).apply(text);
    }

    public boolean isForeign() {
        return isForeign;
    }

    public Flag setIsForeign(boolean isForeign) {
        this.isForeign=isForeign;
        return this;
    }

    public boolean isAttributeFlag() {
        return isAttributeFlag;
    }

}