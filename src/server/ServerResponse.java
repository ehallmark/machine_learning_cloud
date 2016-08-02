package server;

/**
 * Created by ehallmark on 8/2/16.
 */
public abstract class ServerResponse<T> {
    public T results;
    public String query;
    ServerResponse(T results, String query) {
        this.results=results;
        this.query=query;
    }
}
