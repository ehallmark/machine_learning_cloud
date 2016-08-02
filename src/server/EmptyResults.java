package server;

/**
 * Created by ehallmark on 7/27/16.
 */
public class EmptyResults extends ServerResponse<String> {
    EmptyResults(String query) {
        super("No similar patents found.",query);
    }
}
