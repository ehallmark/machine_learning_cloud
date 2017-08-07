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
    public Function<String,Boolean> validValueFunction;
    @Getter @Setter
    public EndFlag endFlag;
    protected Flag(String localName, String dbName, String type, Function<String,Boolean> validValueFunction, EndFlag endFlag) {
        this.dbName=dbName;
        this.validValueFunction=validValueFunction;
        this.type=type;
        this.localName=localName;
        this.flag = new AtomicBoolean(false);
        this.children = new ArrayList<>();
        this.endFlag=endFlag;
    }


    public static Flag simpleFlag(String localName, String dbName, EndFlag endFlag) {
        return new Flag(localName,dbName,"text",(str)->true,endFlag);
    }

    public static Flag dateFlag(@NonNull String localName,@NonNull String dbName, EndFlag endFlag) {
        return new Flag(localName,dbName,"date",validDateFunction,endFlag);
    }

    public static Flag integerFlag(@NonNull String localName,@NonNull String dbName, EndFlag endFlag) {
        return new Flag(localName,dbName,"int",validIntegerFunction,endFlag);
    }

    public static Flag customFlag(@NonNull String localName,@NonNull String dbName,@NonNull String type, @NonNull Function<String,Boolean> validationFunction, EndFlag endFlag) {
        return new Flag(localName,dbName,type,validationFunction,endFlag);
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