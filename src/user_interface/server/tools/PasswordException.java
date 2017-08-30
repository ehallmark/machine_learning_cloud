package user_interface.server.tools;

/**
 * Created by ehallmark on 8/29/17.
 */
public class PasswordException extends Exception {
    private String message;
    public PasswordException(String message) {
        this.message = message;
    }
    @Override
    public String getMessage() {
        return message;
    }
}