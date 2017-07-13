package user_interface.server.tools;

import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import user_interface.ui_models.charts.highcharts.AbstractChart;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 2/13/17.
 */
public class AjaxChartMessage extends ServerResponse {
    public AjaxChartMessage(String message, List<AbstractChart> charts) {
        super(new JsonRenderer().toJson(charts.stream().map(chart->chart.getOptions()).collect(Collectors.toList())),message);
    }
}
