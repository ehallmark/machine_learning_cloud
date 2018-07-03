package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.color.ColorReference;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class ArraySeries extends Series<List> {
    private static final long serialVersionUID = 1L;

    @Getter @Setter
    Integer borderWidth;

    @Setter @Getter
    protected List<ColorReference> colors;

}