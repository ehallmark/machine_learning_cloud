package user_interface.server.tools;

/**
 * Created by ehallmark on 8/2/16.
 */
public class ServerResponse {
    public int chartCnt;
    public int tableCnt;
    public String message;
    public ServerResponse(int chartCnt, int tableCnt, String message) {
        //this.results=results;
        this.tableCnt=tableCnt;
        this.chartCnt=chartCnt;
        this.message=message;
    }
}
