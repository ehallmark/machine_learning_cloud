package public_pair;

import java.net.URL;

import static public_pair.DownloadPDFsFromReedTech.PAIR_URL;
import static spark.Spark.*;

public class GoogleCloudProxy {
    public static void main(String[] args) {
        port(8080);
        threadPool(Runtime.getRuntime().availableProcessors()+2);
        get("/public_pair", (req,res)->{
            String appNum = req.queryParams("app_num");
            if(appNum==null) throw new RuntimeException("Must provide application number...");
            final String urlStr = PAIR_URL + appNum + ".zip";
            //HttpServletResponse raw = res.raw();
            res.header("Content-Disposition", "attachment; filename="+appNum+".zip");
            res.type("application/force-download");
            return new URL(urlStr).openStream();
        });
    }
}
