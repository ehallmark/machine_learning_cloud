package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.color.ColorReference;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class ExtendedPointSeries extends PointSeries {
    private static final long serialVersionUID = 1L;


    @Setter @Getter
    protected List<ColorReference> colors;

    @Getter @Setter
    private boolean colorByPoint;
}
