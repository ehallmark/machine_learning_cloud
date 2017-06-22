package ui_models.exceptions;

/**
 * Created by Evan on 6/22/2017.
 */
public class AttributeException extends BaseException {
    private static final String preText = "Exception in attribute: ";
    public AttributeException(String name) {
        super(name,preText);
    }

    @Override
    public String getMessage() {
           return preText+name;
    }
}
