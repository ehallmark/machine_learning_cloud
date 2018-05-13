package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import com.googlecode.wickedcharts.highcharts.options.drilldown.DrilldownPoint;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import lombok.Getter;
import lombok.Setter;
import org.nd4j.linalg.primitives.Pair;

import java.util.Collections;
import java.util.List;

public class DrilldownPointSeries extends PointSeries {
    private static final long serialVersionUID = 1L;

    @Getter @Setter
    private String id;
}