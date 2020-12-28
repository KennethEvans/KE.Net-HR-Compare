package net.kenevans.polar.polarhrcompare;

import android.content.Context;
import android.graphics.Paint;

import com.androidplot.xy.AdvancedLineAndPointRenderer;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYSeries;

import java.util.Arrays;

@SuppressWarnings("WeakerAccess")
public class Plotter implements IConstants {

    private PlotterListener mListener;
    private final Number[] mPlotNumbers = new Number[500];
    private final FadeFormatter mFormatter;
    private final XYSeries mSeries;
    private int mDataIndex;


    public Plotter(Context context, String title) {

        for (int i = 0; i < mPlotNumbers.length - 1; i++) {
            mPlotNumbers[i] = 60;
        }

        mFormatter = new FadeFormatter(800);
        mFormatter.setLegendIconEnabled(false);

        mSeries = new SimpleXYSeries(Arrays.asList(mPlotNumbers),
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, title);
    }

    public SimpleXYSeries getmSeries() {
        return (SimpleXYSeries) mSeries;
    }

    public FadeFormatter getmFormatter() {
        return mFormatter;
    }

    public void sendSingleSample(float mV) {
        mPlotNumbers[mDataIndex] = mV;
        if (mDataIndex >= mPlotNumbers.length - 1) {
            mDataIndex = 0;
        }
        if (mDataIndex < mPlotNumbers.length - 1) {
            mPlotNumbers[mDataIndex + 1] = null;
        }

        ((SimpleXYSeries) mSeries).setModel(Arrays.asList(mPlotNumbers),
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);
        mDataIndex++;
        mListener.update();
    }

    public void setmListener(PlotterListener mListener) {
        this.mListener = mListener;
    }

    //Custom paint stroke to generate a "fade" effect
    public static class FadeFormatter extends AdvancedLineAndPointRenderer.Formatter {
        private final int mTrailSize;

        public FadeFormatter(int trailSize) {
            this.mTrailSize = trailSize;
        }

        @Override
        public Paint getLinePaint(int thisIndex, int latestIndex,
                                  int seriesSize) {
            // offset from the latest index:
            int offset;
            if (thisIndex > latestIndex) {
                offset = latestIndex + (seriesSize - thisIndex);
            } else {
                offset = latestIndex - thisIndex;
            }
            float scale = 255f / mTrailSize;
            int alpha = (int) (255 - (offset * scale));
            getLinePaint().setAlpha(Math.max(alpha, 0));
            return getLinePaint();
        }
    }
}
