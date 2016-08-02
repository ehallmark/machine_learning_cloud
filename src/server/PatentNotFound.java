package server;


/**
 * Created by ehallmark on 7/27/16.
 */
public class PatentNotFound extends ServerResponse<String> {
    PatentNotFound(String query) {
        super("Patent not found.",query);
    }

}
