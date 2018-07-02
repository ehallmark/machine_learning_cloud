package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DrilldownOptions extends Options {
    private static final long serialVersionUID = 1L;

    @Getter @Setter
    private Map<String,Object> drilldown;
    public DrilldownOptions() {
        super();
        this.drilldown = new HashMap<>();
    }

    @Override
    public void copyFrom(Options template) {
        super.copyFrom(template);
        if(template instanceof DrilldownOptions) {
            this.drilldown = ((DrilldownOptions) template).drilldown;
        }
    }


    public void setDrilldownData(List<? extends Series> series) {
        this.drilldown.put("series", series);
    }

    public List<? extends Series> getDrilldownData() {
        return (List<? extends Series>) drilldown.getOrDefault("series", Collections.emptyList());
    }
}