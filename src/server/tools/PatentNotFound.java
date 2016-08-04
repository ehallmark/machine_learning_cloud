package server.tools;


/**
 * Created by ehallmark on 7/27/16.
 */
public class PatentNotFound extends ServerResponse{
    public PatentNotFound(String query) {
        super(query,"Patent not found.",null);
    }

}
