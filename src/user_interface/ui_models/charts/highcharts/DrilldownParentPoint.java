package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DrilldownParentPoint extends Point {
    private static final long serialVersionUID = 1L;

    @Getter @Setter
    private String drilldown;
    public DrilldownParentPoint(String name, Number val, String drilldownID) {
        super(name,val);
        this.drilldown = drilldownID;
    }
}