package stocks.util;

import com.google.gson.Gson;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Created by Evan on 11/16/2017.
 */
public class StockResponse {
    private String json;
    @Getter
    private List<Number> dates;
    @Getter
    private List<Double> prices;
    public StockResponse(String json) {
        this.json=json;
    }

    public void parse() {
        // convert JSON string to Map
        Map<String,Object> data = new Gson().fromJson(json, HashMap.class);

        Map<String,Object> chart = (Map<String,Object>)data.get("chart");
        if(chart!=null) {
            List<Map<String,Object>> resultList = (List<Map<String,Object>>)chart.get("result");
            if(resultList!=null&&resultList.size()>0) {
                Map<String,Object> result = resultList.get(0);
                if(result!=null) {
                    List<Number> timestamp = (List<Number>) result.get("timestamp");
                    if(timestamp!=null) {
                        this.dates = timestamp;
                    }
                    Map<String,Object> indicators = (Map<String,Object>) result.get("indicators");
                    if(indicators!=null) {
                        List<Map<String, Object>> adjCloseList = (List<Map<String, Object>>) indicators.get("adjclose");
                        if (adjCloseList != null && adjCloseList.size() > 0) {
                            Map<String, Object> adjCloseMap = adjCloseList.get(0);
                            if (adjCloseMap != null) {
                                List<Double> adjClose = (List<Double>) adjCloseMap.get("adjclose");
                                if (adjClose != null) {
                                    this.prices = adjClose;
                                }
                            }
                        }
                    }
                }
            }
        }
    }


}
