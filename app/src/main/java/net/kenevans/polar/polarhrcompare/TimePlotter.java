package net.kenevans.polar.polarhrcompare;

import android.content.Context;
import android.util.Log;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYRegionFormatter;
import com.androidplot.xy.XYSeriesFormatter;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import polar.com.sdk.api.model.PolarHrData;
import polar.com.sdk.api.model.PolarOhrPPIData;

/**
 * Implements two series for HR and RR using time for the x values.
 */
@SuppressWarnings("WeakerAccess")
public class TimePlotter implements IConstants {
    /**
     * Scale the RR values by RR_SCALE to use the same axis. (Could implement
     * NormedXYSeries and use two axes)
     */
    private final double RR_SCALE = .1;
    private PlotterListener mListener;

    /**
     * The duration of the data to be retained.
     */
    private final int mDuration;
    private final XYSeriesFormatter<XYRegionFormatter> mHrFormatter;
    private final XYSeriesFormatter<XYRegionFormatter> mRrFormatter;
    private final SimpleXYSeries mHrSeries;
    private final SimpleXYSeries mRrSeries;

    private double mStartRrTime = Double.NEGATIVE_INFINITY;
    private double mLastRrTime;
    private double mLastUpdateTime;
    private double mTotalRrTime;

    public TimePlotter(Context context, int duration, String title,
                       Integer hrColor, Integer rrColor, boolean showVertices) {
        this.mDuration = duration;
        mHrFormatter = new LineAndPointFormatter(hrColor,
                showVertices ? hrColor : null, null, null);
        mHrFormatter.setLegendIconEnabled(false);
        mHrSeries = new SimpleXYSeries("HR");

        mRrFormatter = new LineAndPointFormatter(rrColor,
                showVertices ? rrColor : null, null, null);
        mRrFormatter.setLegendIconEnabled(false);
        mRrSeries = new SimpleXYSeries("RR");
    }

    public SimpleXYSeries getmHrSeries() {
        return mHrSeries;
    }

    public SimpleXYSeries getmRrSeries() {
        return mRrSeries;
    }

    public XYSeriesFormatter<XYRegionFormatter> getmHrFormatter() {
        return mHrFormatter;
    }

    public XYSeriesFormatter<XYRegionFormatter> getmRrFormatter() {
        return mRrFormatter;
    }

    /**
     * Implements a strip chart adding new data at the end.
     *
     * @param plot        The associated XYPlot.
     * @param polarHrData The HR data that came in.
     */
    public void addValues(XYPlot plot, PolarHrData polarHrData) {
        long now = new Date().getTime();
        // Make the plot move forward
        long start = now - mDuration;
        plot.setDomainBoundaries(start, now, BoundaryMode.FIXED);
        // Clear out expired HR values
        if (mHrSeries.size() > 0) {
            while (mHrSeries.getX(0).longValue() < start) {
                mHrSeries.removeFirst();
            }
        }
        mHrSeries.addLast(now, polarHrData.hr);

        // Return if RR is not available (e.g. OH1)
        if (!polarHrData.rrAvailable) return;

        // Do RR
        // We don't know at what time the RR intervals start.  All we know is
        // the time the data arrived (now) and that the intervals ended
        // between the previous update time and now.

        // Clear out expired RR values
        if (mRrSeries.size() > 0) {
            while (mRrSeries.getX(0).longValue() < start) {
                mRrSeries.removeFirst();
            }
        }
        List<Integer> rrsMs = polarHrData.rrsMs;
        int nRrVals = rrsMs.size();
        double[] tVals = new double[nRrVals];
        Integer[] rrVals = new Integer[nRrVals];
        rrVals = rrsMs.toArray(rrVals);
        // Find the sum of the RR intervals
        double totalRR = 0;
        for (int i = 0; i < nRrVals; i++) {
            totalRR += rrVals[i];
        }
        // First time
        if (Double.isInfinite(mStartRrTime)) {
            mStartRrTime = mLastRrTime = mLastUpdateTime = now - totalRR;
            mTotalRrTime = 0;
        }
        mTotalRrTime += totalRR;
        Log.d(TAG, "lastRrTime=" + mLastRrTime
                + " totalRR=" + totalRR
                + " elapsed=" + (mLastRrTime - mStartRrTime)
                + " totalRrTime=" + mTotalRrTime);

        double rr;
        double t = mLastRrTime;
        for (int i = 0; i < nRrVals; i++) {
            rr = rrVals[i];
            t += rr;
            tVals[i] = t;
        }
        // Keep them in this interval
        if (nRrVals > 0 && tVals[0] < mLastUpdateTime) {
            double deltaT = mLastUpdateTime = tVals[0];
            t += deltaT;
            for (int i = 0; i < nRrVals; i++) {
                tVals[i] += deltaT;
            }
        }
        // Keep them from being in the future
        if (t > now) {
            double deltaT = t - now;
            for (int i = 0; i < nRrVals; i++) {
                tVals[i] -= deltaT;
            }
        }
        // Add to the series
        for (int i = 0; i < nRrVals; i++) {
            rr = rrVals[i];
            mRrSeries.addLast(tVals[i], RR_SCALE * rr);
            mLastRrTime = tVals[i];
        }
        mLastUpdateTime = now;
        mListener.update();
    }

    /**
     * Implements a strip chart adding new data at the end using PPI data.
     * The data consists of a timestamp, which seems to be 0, and a list of
     * samples with hr, ppi, and some other items.  Even
     * though the PPI samples have a hr, it appears to be 0.  This just does the
     * RR values (using ppi).  The logic is the same as for handling RR for
     * HR data, except the array of RR values is the ppi values from the
     * samples.  It does not do HR since it is zero.
     *
     * @param plot    The associated XYPlot.
     * @param ppiData The PPI data that came in.
     */
    public void addValues(XYPlot plot, PolarOhrPPIData ppiData) {
        Log.d(TAG, "addValues PPG: nSamples=" + ppiData.samples.size());
        long now = new Date().getTime();
        // Make the plot move forward
        long start = now - mDuration;
        plot.setDomainBoundaries(start, now, BoundaryMode.FIXED);

        // Don't do HR

        // Do RR
        // We don't know at what time the RR intervals start.  All we know is
        // the time the data arrived (now) and that the intervals ended
        // between the previous update time and now.

        // Clear out expired RR values
        if (mRrSeries.size() > 0) {
            while (mRrSeries.getX(0).longValue() < start) {
                mRrSeries.removeFirst();
            }
        }

        // Make an array
        // Assume the oldest is first as for RR values in the Heart
        // characteristic
        List<PolarOhrPPIData.PolarOhrPPISample> samples = ppiData.samples;
        int nRrVals = samples.size();
        double[] tVals = new double[nRrVals];
        Integer[] rrVals = new Integer[nRrVals];
        for (int i = 0; i < nRrVals; i++) {
            rrVals[i] = samples.get(i).ppi;
        }

        // Logic from here on is the same as for HR data
        // Find the sum of the RR intervals
        double totalRR = 0;
        for (int i = 0; i < nRrVals; i++) {
            totalRR += rrVals[i];
        }
        // First time
        if (Double.isInfinite(mStartRrTime)) {
            mStartRrTime = mLastRrTime = mLastUpdateTime = now - totalRR;
            mTotalRrTime = 0;
        }
        mTotalRrTime += totalRR;
        Log.d(TAG, "lastRrTime=" + mLastRrTime
                + " totalRR=" + totalRR
                + " elapsed=" + (mLastRrTime - mStartRrTime)
                + " totalRrTime=" + mTotalRrTime);

        double rr;
        double t = mLastRrTime;
        for (int i = 0; i < nRrVals; i++) {
            rr = rrVals[i];
            t += rr;
            tVals[i] = t;
        }
        // Keep them in this interval
        if (nRrVals > 0 && tVals[0] < mLastUpdateTime) {
            double deltaT = mLastUpdateTime = tVals[0];
            t += deltaT;
            for (int i = 0; i < nRrVals; i++) {
                tVals[i] += deltaT;
            }
        }
        // Keep them from being in the future
        if (t > now) {
            double deltaT = t - now;
            for (int i = 0; i < nRrVals; i++) {
                tVals[i] -= deltaT;
            }
        }
        // Add to the series
        for (int i = 0; i < nRrVals; i++) {
            rr = rrVals[i];
            mRrSeries.addLast(tVals[i], RR_SCALE * rr);
            mLastRrTime = tVals[i];
        }
        mLastUpdateTime = now;
        mListener.update();
    }

    public String getRrInfo() {
        double elapsed = .001 * (mLastRrTime - mStartRrTime);
        double total = .001 * mTotalRrTime;
        double ratio = total / elapsed;
        return "Tot=" + String.format(Locale.US, "%.2f s", elapsed)
                + " RR=" + String.format(Locale.US, "%.2f s", total)
                + " (" + String.format(Locale.US, "%.2f", ratio) + ")";
    }

    public void setmListener(PlotterListener mListener) {
        this.mListener = mListener;
    }
}
