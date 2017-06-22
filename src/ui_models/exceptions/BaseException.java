package ui_models.exceptions;

/**
 * Created by Evan on 6/22/2017.
 */
public class BaseException extends Exception {
    protected String name;
    protected String preText;
    public BaseException(String name,String preText) {
        this.preText=preText;
        this.name=name;
    }

    @Override
    public String getMessage() {
           return preText+name;
    }
}
