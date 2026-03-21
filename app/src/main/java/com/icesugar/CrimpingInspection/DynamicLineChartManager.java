package com.icesugar.CrimpingInspection;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class DynamicLineChartManager {

    private LineChart lineChart;
    private YAxis leftAxis;
    private YAxis rightAxis;
    private XAxis xAxis;
    private LineData lineData;
    private LineDataSet lineDataSet;
    private List<ILineDataSet> lineDataSets = new ArrayList<>();
    private SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");//设置日期格式
    private int max_x = 0;
    private Float max_y = 0f;
    private int min_x = 0;
    private Float min_y = 0f;
    private static final int MAX_DATA_POINTS = 4000; // 限制最大数据点数

    //一条曲线
    public DynamicLineChartManager(LineChart mLineChart, String name, int color) {
        this.lineChart = mLineChart;
        leftAxis = lineChart.getAxisLeft();
        rightAxis = lineChart.getAxisRight();
        xAxis = lineChart.getXAxis();
        initLineChart();
        initLineDataSet(name, color);
    }

    //多条曲线
    public DynamicLineChartManager(LineChart mLineChart, List<String> names, List<Integer> colors) {
        this.lineChart = mLineChart;
        leftAxis = lineChart.getAxisLeft();
        rightAxis = lineChart.getAxisRight();
        xAxis = lineChart.getXAxis();
        initLineChart();
        initLineDataSet(names, colors);
    }

    /**
     * 初始化LineChar
     */
    public void initLineChart() {

        lineChart.setDrawGridBackground(false);
        //显示边界
        lineChart.setDrawBorders(true);
        
        // 优化内存使用
        lineChart.setHardwareAccelerationEnabled(true);
        lineChart.setMaxVisibleValueCount(100);
        
        //折线图例 标签 设置
        Legend legend = lineChart.getLegend();
        legend.setForm(Legend.LegendForm.LINE);
        legend.setTextSize(15f);
        //显示位置
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
//        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        
        //X轴设置显示位置在底部
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(5); // 减少标签数量
        
        //Y轴设置
        leftAxis.setAxisMinimum(0f);
        leftAxis.setLabelCount(5); // 减少标签数量
        leftAxis.setGranularity(1f);
        
        //禁用右坐标
        rightAxis.setEnabled(false);
    }

    /**
     * 初始化折线(一条线)
     */
    public void initLineDataSet(String name, int color) {

        lineDataSet = new LineDataSet(null, name);
        lineDataSet.setLineWidth(1.5f);
        lineDataSet.setCircleRadius(1.5f);
        lineDataSet.setColor(color);
        lineDataSet.setCircleColor(color);
        lineDataSet.setHighLightColor(color);
        //设置曲线填充
        lineDataSet.setDrawFilled(false);
        lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        lineDataSet.setValueTextSize(10f);
        lineDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); //圆滑曲线（默认为折线）
        //添加一个空的 LineData
        lineData = new LineData();
        lineChart.setData(lineData);
        lineChart.invalidate();

    }

    /**
     * 初始化折线（多条线）
     */
    private void initLineDataSet(List<String> names, List<Integer> colors) {

        for (int i = 0; i < names.size(); i++) {
            lineDataSet = new LineDataSet(null, names.get(i));
            lineDataSet.setColor(colors.get(i));
            lineDataSet.setLineWidth(1.5f);
            lineDataSet.setCircleRadius(1.5f);
            lineDataSet.setColor(colors.get(i));

            lineDataSet.setDrawFilled(false);
            lineDataSet.setCircleColor(colors.get(i));
            lineDataSet.setHighLightColor(colors.get(i));
            lineDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);



            lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
//            if(i==0)
//                lineDataSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
//            else
//                lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);

            lineDataSet.setValueTextSize(10f);
            lineDataSets.add(lineDataSet);

        }
        //添加一个空的 LineData
        lineData = new LineData();
        lineChart.setData(lineData);
        lineChart.invalidate();
    }

    /**
     * 动态添加数据（一条折线图）
     */
    
    public void addEntry(float number) {
        if (lineDataSet.getEntryCount() == 0) {
            lineData.addDataSet(lineDataSet);
        }

        Entry entry = new Entry(lineDataSet.getEntryCount(), number);
        lineData.addEntry(entry, 0);
        
        // 合并通知操作，减少UI更新频率
        if (lineDataSet.getEntryCount() % 50 == 0) { // 每50个点更新一次UI
            lineChart.notifyDataSetChanged();
            lineChart.setVisibleXRangeMaximum(50);
            lineChart.moveViewToX(lineData.getEntryCount() - 25);
        }
    }

    /**
     * 动态添加数据（多条折线图）
     */
    public void addEntry(List<Float> numbers) {
        if (numbers == null || numbers.isEmpty()) return;

        // 批量添加前检查容量
        int overflow = lineDataSets.get(0).getEntryCount() + numbers.size() - MAX_DATA_POINTS;
        if (overflow > 0) {
            for (int i = 0; i < overflow; i++) {
                lineData.removeEntry(0, 0);
            }
        }

        if (lineDataSets.get(0).getEntryCount() == 0) {
            lineData = new LineData(lineDataSets);
            lineChart.setData(lineData);
        }

        // 批量添加所有点
        for (int i = 0; i < numbers.size(); i++) {
            Entry entry = new Entry(lineDataSets.get(i).getEntryCount(), numbers.get(i));
            lineData.addEntry(entry, i);
        }
        
        // 单次UI更新
        lineChart.notifyDataSetChanged();
        lineChart.setVisibleXRangeMaximum(10);
        lineChart.moveViewToX(lineData.getEntryCount() - 5);
    }

    /**
     * 设置Y轴值
     */
    public void setYAxis(float max, float min) {
        if (max < min) {
            return;
        }
        leftAxis.setAxisMaximum(max);
        leftAxis.setAxisMinimum(min);
        lineChart.invalidate();
    }

    public void setRightYAxis(float max, float min, int labelCount) {
        if (max < min) {
            return;
        }
        rightAxis.setEnabled(true);
        rightAxis.setAxisMaximum(max);
        rightAxis.setAxisMinimum(min);
        lineChart.invalidate();
    }

    public void fitScreen(){
        lineChart.fitScreen();
    }

}
