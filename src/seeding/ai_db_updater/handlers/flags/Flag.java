package seeding.ai_db_updater.handlers.flags;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import seeding.Constants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Created by Evan on 8/6/2017.
 */
public class Flag {
    // functions
    protected static final Function<Flag,Function<String,Boolean>> defaultCompareFunction = (flag) -> (str) ->{
        return flag.localName.equals(str);
    };

    public static final Function<Flag,Function<String,Boolean>> endsWithCompareFunction = (flag) -> (str) ->{
        return flag.localName.endsWith(str);
    };

    private static Function<String,Boolean> validDateFunction(DateTimeFormatter formatter) {
        return (str) -> {
            try {
                LocalDate.parse(str, formatter);
                return true;
            } catch (Exception e) {
                return false;
            }
        };
    }

    private static Function<String,Boolean> validIntegerFunction = (str) -> {
        try {
            Integer.valueOf(str);
            return true;
        } catch(Exception e) {
            return false;
        }
    };

    public static final Function<Flag,Function<String,?>> defaultTransformationFunction = (flag)->(str) -> str;

    public static final Function<Flag,Function<String,?>> defaultISODateTransformationFunction = flag->(str)->str!=null&&str.endsWith("00")?str.substring(0,str.length()-1)+"1":str;

    public static final Function<Flag,Function<String,?>> unknownDocumentHandler = (flag) -> (str) -> {
        str = str.replace(" ","").replace("/","");
        String kind = flag.getEndFlag().getDataMap().getOrDefault(Constants.DOC_KIND,"").toString();
        if(kind.startsWith("S")) {
            // design
            if(str.startsWith("D0")) str = "D"+str.substring(2);
        } else if (kind.equals("A") && str.length()==8) {
            // filing?
            str = str.substring(0,2)+"/"+str.substring(2);
        } else if(kind.startsWith("B")) {
            if(str.startsWith("0")) str = str.substring(1);
        } else if(kind.startsWith("E")) {
            //reissue
            if(str.startsWith("RE0")) str = "RE" + str.substring(3);
        }

        return str;
    };

    public final String localName;
    public final String dbName;
    public final AtomicBoolean flag;
    public final List<Flag> children;
    public Function<Flag,Function<String,?>> transformationFunction;
    public final String type;
    @Setter
    public Function<Flag,Function<String,Boolean>> compareFunction;
    public final Function<String,Boolean> validValueFunction;
    @Getter @Setter
    public EndFlag endFlag;
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

    public static Flag dateFlag(@NonNull String localName,@NonNull String dbName, EndFlag endFlag) {
        return dateFlag(localName,dbName,endFlag,DateTimeFormatter.ISO_DATE);
    }

    public static Flag dateFlag(@NonNull String localName,@NonNull String dbName, EndFlag endFlag, @NonNull DateTimeFormatter dateFormat) {
        return new Flag(localName,dbName,"date",validDateFunction(dateFormat),defaultCompareFunction,defaultTransformationFunction,endFlag);
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
        if(localName.equals(otherName)) {
            flag.set(true);
        }
    }

    public void reset() {
        flag.set(false);
    }

    public Object apply(String text) {
        return transformationFunction.apply(this).apply(text);
    }

}