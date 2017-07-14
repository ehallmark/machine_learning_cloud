package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.Function;

/**
 * Created by Evan on 7/14/2017.
 */
public class CustomFunction extends Function {
    private String name;
    public CustomFunction(String name) {
        this.name=name;
    }
    @Override
    public String getFunction() {
        return name;
    }
}
