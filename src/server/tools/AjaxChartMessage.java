package server.tools;

import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.Options;

/**
 * Created by ehallmark on 2/13/17.
 */
public class AjaxChartMessage extends ServerResponse {
    public AjaxChartMessage(String message,Options... chartOptions) {
        super(new JsonRenderer().toJson(chartOptions).toString(),message);
    }
}
