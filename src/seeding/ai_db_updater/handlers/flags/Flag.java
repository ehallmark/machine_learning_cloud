package seeding.ai_db_updater.handlers.flags;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

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

    private static Function<String,Boolean> validDateFunction = (str) -> {
        try {
            LocalDate.parse(str, DateTimeFormatter.ISO_DATE);
            return true;
        } catch(Exception e) {
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


    public final String localName;
    public final String dbName;
    public final AtomicBoolean flag;
    public final List<Flag> children;
    public final String type;
    @Setter
    public Function<Flag,Function<String,Boolean>> compareFunction;
    public final Function<String,Boolean> validValueFunction;
    @Getter @Setter
    public EndFlag endFlag;
    protected Flag(String localName, String dbName, String type, Function<String,Boolean> validValueFunction, Function<Flag,Function<String,Boolean>> compareFunction, EndFlag endFlag) {
        this.dbName=dbName;
        this.validValueFunction=validValueFunction;
        this.type=type;
        this.localName=localName;
        this.flag = new AtomicBoolean(false);
        this.children = new ArrayList<>();
        this.endFlag=endFlag;
        this.compareFunction=compareFunction;
    }

    public static Flag fakeFlag(@NonNull String dbName) {
        return new Flag(null,dbName,null,null,null,null);
    }

    public static Flag parentFlag(@NonNull String localName, EndFlag endFlag) {
        return new Flag(localName,null,null,null,defaultCompareFunction,endFlag);
    }

    public static Flag simpleFlag(@NonNull String localName,@NonNull String dbName, EndFlag endFlag) {
        return new Flag(localName,dbName,"text",(str)->true,defaultCompareFunction,endFlag);
    }

    public static Flag dateFlag(@NonNull String localName,@NonNull String dbName, EndFlag endFlag) {
        return new Flag(localName,dbName,"date",validDateFunction,defaultCompareFunction,endFlag);
    }

    public static Flag integerFlag(@NonNull String localName,@NonNull String dbName, EndFlag endFlag) {
        return new Flag(localName,dbName,"int",validIntegerFunction,defaultCompareFunction,endFlag);
    }

    public static Flag customFlag(@NonNull String localName,@NonNull String dbName,@NonNull String type, @NonNull Function<String,Boolean> validationFunction, EndFlag endFlag) {
        return new Flag(localName,dbName,type,validationFunction,defaultCompareFunction,endFlag);
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

}