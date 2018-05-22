package user_interface.ui_models.charts.highcharts;

import lombok.Getter;
import lombok.Setter;
public class DrilldownPointSeries extends ArraySeries {
    private static final long serialVersionUID = 1L;

    @Getter @Setter
    private String id;
    @Getter @Setter
    private boolean colorByPoint;
}