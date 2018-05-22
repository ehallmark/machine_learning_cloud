package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import lombok.Getter;
import lombok.Setter;

public class ExtendedPointSeries extends PointSeries {
    private static final long serialVersionUID = 1L;

    @Getter @Setter
    private boolean colorByPoint;
}
