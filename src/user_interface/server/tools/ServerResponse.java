package user_interface.server.tools;

/**
 * Created by ehallmark on 8/2/16.
 */
public class ServerResponse {
    public String charts;
    public String message;
    public ServerResponse(String charts, String message) {
        //this.results=results;
        this.charts=charts;
        this.message=message;
    }
}
