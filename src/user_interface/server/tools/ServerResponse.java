package user_interface.server.tools;

/**
 * Created by ehallmark on 8/2/16.
 */
public class ServerResponse {
    public int chartCnt;
    public String message;
    public ServerResponse(int chartCnt, String message) {
        //this.results=results;
        this.chartCnt=chartCnt;
        this.message=message;
    }
}
