package info.nightscout.androidaps;

import android.content.Context;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.Viewport;

/**
 * Created by stephenblack on 11/15/14.
 */
public class BgGraphBuilder {
    private ArrayList<BasalWatchData> basalWatchDataList;
    public List<TempWatchData> tempWatchDataList;
    private int timespan;
    public double end_time;
    public double start_time;
    public double fuzzyTimeDenom = (1000 * 60 * 1);
    public Context context;
    public double highMark;
    public double lowMark;
    public List<BgWatchData> bgDataList = new ArrayList<BgWatchData>();

    public int pointSize;
    public int highColor;
    public int lowColor;
    public int midColor;
    public boolean singleLine = false;

    private double endHour;
    private List<PointValue> inRangeValues = new ArrayList<PointValue>();
    private List<PointValue> highValues = new ArrayList<PointValue>();
    private List<PointValue> lowValues = new ArrayList<PointValue>();
    public Viewport viewport;

    public BgGraphBuilder(Context context, List<BgWatchData> aBgList, List<TempWatchData> tempWatchDataList, ArrayList<BasalWatchData> basalWatchDataList, int aPointSize, int aMidColor, int timespan) {
        end_time = new Date().getTime() + (1000 * 60 * 6 * timespan); //Now plus 30 minutes padding (for 5 hours. Less if less.)
        start_time = new Date().getTime()  - (1000 * 60 * 60 * timespan); //timespan hours ago
        this.bgDataList = aBgList;
        this.context = context;
        this.highMark = aBgList.get(aBgList.size() - 1).high;
        this.lowMark = aBgList.get(aBgList.size() - 1).low;
        this.pointSize = aPointSize;
        this.singleLine = true;
        this.midColor = aMidColor;
        this.lowColor = aMidColor;
        this.highColor = aMidColor;
        this.timespan = timespan;
        this.tempWatchDataList = tempWatchDataList;
        this.basalWatchDataList = basalWatchDataList;
    }

    public BgGraphBuilder(Context context, List<BgWatchData> aBgList, List<TempWatchData> tempWatchDataList, ArrayList<BasalWatchData> basalWatchDataList, int aPointSize, int aHighColor, int aLowColor, int aMidColor, int timespan) {
        end_time = new Date().getTime() + (1000 * 60 * 6 * timespan); //Now plus 30 minutes padding (for 5 hours. Less if less.)
        start_time = new Date().getTime()  - (1000 * 60 * 60 * timespan); //timespan hours ago
        this.bgDataList = aBgList;
        this.context = context;
        this.highMark = aBgList.get(aBgList.size() - 1).high;
        this.lowMark = aBgList.get(aBgList.size() - 1).low;
        this.pointSize = aPointSize;
        this.highColor = aHighColor;
        this.lowColor = aLowColor;
        this.midColor = aMidColor;
        this.timespan = timespan;
        this.tempWatchDataList = tempWatchDataList;
        this.basalWatchDataList = basalWatchDataList;
    }

    public LineChartData lineData() {
        LineChartData lineData = new LineChartData(defaultLines());
        lineData.setAxisYLeft(yAxis());
        lineData.setAxisXBottom(xAxis());
        return lineData;
    }

    public List<Line> defaultLines() {
        addBgReadingValues();
        List<Line> lines = new ArrayList<Line>();
        lines.add(highLine());
        lines.add(lowLine());
        lines.add(inRangeValuesLine());
        lines.add(lowValuesLine());
        lines.add(highValuesLine());

        double minChart = lowMark;
        double maxChart = highMark;

        for ( BgWatchData bgd:bgDataList) {
            if(bgd.sgv > maxChart){
                maxChart = bgd.sgv;
            }
            if(bgd.sgv < minChart){
                minChart = bgd.sgv;
            }
        }

        double maxBasal = 0.1;
        for (BasalWatchData bwd: basalWatchDataList) {
            if(bwd.amount > maxBasal){
                maxBasal = bwd.amount;
            }
        }

        double maxTemp = maxBasal;
        for (TempWatchData twd: tempWatchDataList) {
            if(twd.amount > maxTemp){
                maxTemp = twd.amount;
            }
        }

        double factor = (maxChart-minChart)/maxTemp;
        // in case basal is the highest, don't paint it totally at the top.
        factor = Math.min(factor, ((maxChart-minChart)/maxTemp)*(2/3d));
        for (TempWatchData twd: tempWatchDataList) {
            if(twd.endTime > start_time) {
                lines.add(tempValuesLine(twd, (float) minChart, factor));
            }
        }

        lines.add(basalLine((float) minChart, factor));

        return lines;
    }

    private Line basalLine(float offset, double factor) {

        List<PointValue> pointValues = new ArrayList<PointValue>();

        for (BasalWatchData bwd: basalWatchDataList) {
            if(bwd.endTime > start_time) {
                long begin = (long) Math.max(start_time, bwd.startTime);
                pointValues.add(new PointValue(fuzz(begin), offset + (float) (factor * bwd.amount)));
                pointValues.add(new PointValue(fuzz(bwd.endTime), offset + (float) (factor * bwd.amount)));
            }
        }

        Line basalLine = new Line(pointValues);
        basalLine.setHasPoints(false);
        basalLine.setColor(Color.parseColor("#00BFFF"));
        basalLine.setPathEffect(new DashPathEffect(new float[]{4f, 3f}, 4f));
        basalLine.setStrokeWidth(1);
        return basalLine;


    }

    public Line highValuesLine() {
        Line highValuesLine = new Line(highValues);
        highValuesLine.setColor(highColor);
        highValuesLine.setHasLines(false);
        highValuesLine.setPointRadius(pointSize);
        highValuesLine.setHasPoints(true);
        return highValuesLine;
    }

    public Line lowValuesLine() {
        Line lowValuesLine = new Line(lowValues);
        lowValuesLine.setColor(lowColor);
        lowValuesLine.setHasLines(false);
        lowValuesLine.setPointRadius(pointSize);
        lowValuesLine.setHasPoints(true);
        return lowValuesLine;
    }

    public Line inRangeValuesLine() {
        Line inRangeValuesLine = new Line(inRangeValues);
        inRangeValuesLine.setColor(midColor);
        if(singleLine) {
            inRangeValuesLine.setHasLines(true);
            inRangeValuesLine.setHasPoints(false);
            inRangeValuesLine.setStrokeWidth(pointSize);
        } else {
            inRangeValuesLine.setPointRadius(pointSize);
            inRangeValuesLine.setHasPoints(true);
            inRangeValuesLine.setHasLines(false);
        }
        return inRangeValuesLine;
    }


    public Line tempValuesLine(TempWatchData twd, float offset, double factor) {
        List<PointValue> lineValues = new ArrayList<PointValue>();
        long begin = (long) Math.max(start_time, twd.startTime);
        lineValues.add(new PointValue(fuzz(begin), offset + (float) (factor * twd.startBasal)));
        lineValues.add(new PointValue(fuzz(begin), offset + (float) (factor * twd.amount)));
        lineValues.add(new PointValue(fuzz(twd.endTime), offset + (float) (factor * twd.amount)));
        lineValues.add(new PointValue(fuzz(twd.endTime), offset + (float) (factor * twd.endBasal)));
        lineValues.add(new PointValue(fuzz(begin), offset + (float) (factor * twd.startBasal)));
        Line valueLine = new Line(lineValues);
        valueLine.setHasPoints(false);
        valueLine.setColor(Color.BLUE);
        valueLine.setStrokeWidth(1);
        valueLine.setFilled(true);
        return valueLine;
    }




    private void addBgReadingValues() {
        if(singleLine) {
            for (BgWatchData bgReading : bgDataList) {
                if(bgReading.timestamp > start_time) {
                    if (bgReading.sgv >= 400) {
                        inRangeValues.add(new PointValue(fuzz(bgReading.timestamp), (float) 400));
                    } else if (bgReading.sgv >= highMark) {
                        inRangeValues.add(new PointValue(fuzz(bgReading.timestamp), (float) bgReading.sgv));
                    } else if (bgReading.sgv >= lowMark) {
                        inRangeValues.add(new PointValue(fuzz(bgReading.timestamp), (float) bgReading.sgv));
                    } else if (bgReading.sgv >= 40) {
                        inRangeValues.add(new PointValue(fuzz(bgReading.timestamp), (float) bgReading.sgv));
                    } else if (bgReading.sgv >= 11) {
                        inRangeValues.add(new PointValue(fuzz(bgReading.timestamp), (float) 40));
                    }
                }
            }
        } else {
            for (BgWatchData bgReading : bgDataList) {
                if (bgReading.timestamp > start_time) {
                    if (bgReading.sgv >= 400) {
                        highValues.add(new PointValue(fuzz(bgReading.timestamp), (float) 400));
                    } else if (bgReading.sgv >= highMark) {
                        highValues.add(new PointValue(fuzz(bgReading.timestamp), (float) bgReading.sgv));
                    } else if (bgReading.sgv >= lowMark) {
                        inRangeValues.add(new PointValue(fuzz(bgReading.timestamp), (float) bgReading.sgv));
                    } else if (bgReading.sgv >= 40) {
                        lowValues.add(new PointValue(fuzz(bgReading.timestamp), (float) bgReading.sgv));
                    } else if (bgReading.sgv >= 11) {
                        lowValues.add(new PointValue(fuzz(bgReading.timestamp), (float) 40));
                    }
                }
            }
        }
    }

    public Line highLine() {
        List<PointValue> highLineValues = new ArrayList<PointValue>();
        highLineValues.add(new PointValue(fuzz(start_time), (float) highMark));
        highLineValues.add(new PointValue(fuzz(end_time), (float) highMark));
        Line highLine = new Line(highLineValues);
        highLine.setHasPoints(false);
        highLine.setStrokeWidth(1);
        highLine.setColor(highColor);
        return highLine;
    }

    public Line lowLine() {
        List<PointValue> lowLineValues = new ArrayList<PointValue>();
        lowLineValues.add(new PointValue(fuzz(start_time), (float) lowMark));
        lowLineValues.add(new PointValue(fuzz(end_time), (float) lowMark));
        Line lowLine = new Line(lowLineValues);
        lowLine.setHasPoints(false);
        lowLine.setColor(lowColor);
        lowLine.setStrokeWidth(1);
        return lowLine;
    }

    /////////AXIS RELATED//////////////
    public Axis yAxis() {
        Axis yAxis = new Axis();
        yAxis.setAutoGenerated(true);
        List<AxisValue> axisValues = new ArrayList<AxisValue>();
        yAxis.setValues(axisValues);
        yAxis.setHasLines(false);
        return yAxis;
    }

    public Axis xAxis() {
        final boolean is24 = DateFormat.is24HourFormat(context);
        Axis xAxis = new Axis();
        xAxis.setAutoGenerated(false);
        List<AxisValue> xAxisValues = new ArrayList<AxisValue>();
        GregorianCalendar now = new GregorianCalendar();
        GregorianCalendar today = new GregorianCalendar(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        SimpleDateFormat timeFormat = new SimpleDateFormat(is24? "HH" : "h a");
        timeFormat.setTimeZone(TimeZone.getDefault());
        double start_hour = today.getTime().getTime();
        double timeNow = new Date().getTime();
        for (int l = 0; l <= 24; l++) {
            if ((start_hour + (60000 * 60 * (l))) < timeNow) {
                if ((start_hour + (60000 * 60 * (l + 1))) >= timeNow) {
                    endHour = start_hour + (60000 * 60 * (l));
                    l = 25;
                }
            }
        }
        //Display current time on the graph
        SimpleDateFormat longTimeFormat = new SimpleDateFormat(is24? "HH:mm" : "h:mm a");
        xAxisValues.add(new AxisValue(fuzz(timeNow), (longTimeFormat.format(timeNow)).toCharArray()));

        //Add whole hours endTime the axis (as long as they are more than 15 mins away from the current time)
        for (int l = 0; l <= 24; l++) {
            double timestamp = endHour - (60000 * 60 * l);
            if((timestamp - timeNow < 0) && (timestamp > start_time)) {
                if(Math.abs(timestamp - timeNow) > (1000 * 60 * 8 * timespan)){
                    xAxisValues.add(new AxisValue(fuzz(timestamp), (timeFormat.format(timestamp)).toCharArray()));
                }else {
                    xAxisValues.add(new AxisValue(fuzz(timestamp), "".toCharArray()));
                }
            }
        }
        xAxis.setValues(xAxisValues);
        xAxis.setTextSize(10);
        xAxis.setHasLines(true);
        return xAxis;
    }

    public float fuzz(double value) {
        return (float) Math.round(value / fuzzyTimeDenom);
    }
}
