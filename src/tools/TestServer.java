package tools;
import static spark.Spark.*;

/**
 * Created by ehallmark on 3/16/17.
 */
public class TestServer {
    public static void main(String[] args) {
        port(4567);
        get("/hello",(req,res)->{
            return "Hello World!";
        });
    }
}
