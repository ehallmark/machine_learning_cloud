package detect_acquisitions;

import com.google.gson.Gson;
import spark.Route;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.*;

public class DetermineAcquisitionsServer {
    public static void startServer(int port) {
        if(port>0)port(port);

        final String path = "/alsdkgjaiweurta923859283udksljv/predict_acquisitions";
        final Route route = (req,res)-> {
            String buyer = req.queryParams("buyer");
            String seller = req.queryParams("seller");
            Map<String,Object> responseMap = new HashMap<>();
            if(buyer!=null&&seller!=null&&buyer.length()>0&&seller.length()>0) {
                buyer = buyer.trim();
                seller = seller.trim();
                if(buyer.length()>0&&seller.length()>0) {
                    try {
                        boolean r = PhantomJS.test(buyer, seller);
                        System.out.println("res: "+r);
                        responseMap.put("result", r);
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return new Gson().toJson(responseMap);
        };

        get(path,route);
        post(path,route);
    }
}
