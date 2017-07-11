package user_interface.ui_models.exceptions;

/**
 * Created by Evan on 6/22/2017.
 */
public class SortingException extends BaseException {
    private static final String preText = "Exception while Sorting: ";
    public SortingException(String name) {
        super(name,preText);
    }

    @Override
    public String getMessage() {
           return preText+name;
    }
}
