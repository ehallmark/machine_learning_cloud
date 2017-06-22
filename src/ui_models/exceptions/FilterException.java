package ui_models.exceptions;

/**
 * Created by Evan on 6/22/2017.
 */
public class FilterException extends BaseException {
    private static final String preText = "Exception while Filtering: ";
    public FilterException(String name) {
        super(name,preText);
    }

    @Override
    public String getMessage() {
           return preText+name;
    }
}
