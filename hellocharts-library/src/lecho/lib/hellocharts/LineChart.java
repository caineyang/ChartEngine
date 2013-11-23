package lecho.lib.hellocharts;

import java.util.ArrayList;
import java.util.List;

import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.LineSeries;
import lecho.lib.hellocharts.utils.SplineInterpolator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * TODO nullcheck for mData
 * 
 * @author lecho
 * 
 */
public class LineChart extends View {
	private LineChartData mData;
	private List<Float> mGeneratedX;
	private List<SplineInterpolator> mSplineInterpolators;
	private Bitmap mBitmap;
	private Canvas mCanvas;
	private Path mLinePath = new Path();
	private Paint mLinePaint = new Paint();
	private Paint mPointPaint = new Paint();
	private Paint mRulersPaint = new Paint();
	private float mLineWidth = 4.0f;
	private float mPointRadius = 12.0f;
	private float minXValue = Float.MAX_VALUE;
	private float maxXValue = Float.MIN_VALUE;
	private float minYValue = Float.MAX_VALUE;
	private float maxYValue = Float.MIN_VALUE;
	private float mXMultiplier;
	private float mYMultiplier;
	private float mAvailableWidth;
	private float mAvailableHeight;
	private int mhorizontalRulersDivider;

	public LineChart(Context context) {
		super(context);
		initPaint();
	}

	public LineChart(Context context, AttributeSet attrs) {
		super(context, attrs);
		initPaint();
	}

	public LineChart(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initPaint();
	}

	private void initPaint() {
		mLinePaint.setAntiAlias(true);
		mLinePaint.setStyle(Paint.Style.STROKE);
		mLinePaint.setStrokeWidth(mLineWidth);

		mPointPaint.setAntiAlias(true);
		mPointPaint.setStyle(Paint.Style.FILL);

		mRulersPaint.setStyle(Paint.Style.STROKE);
		mRulersPaint.setColor(Color.LTGRAY);
		mRulersPaint.setStrokeWidth(1);
	}

	@Override
	protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
		super.onSizeChanged(width, height, oldWidth, oldHeight);
		if (null == mBitmap) {
			mBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Config.ARGB_8888);
		} else {
			mBitmap.eraseColor(Color.TRANSPARENT);
		}
		mCanvas = new Canvas(mBitmap);

		mAvailableWidth = getWidth() - getPaddingLeft() - getPaddingRight() - 2 * mPointRadius;
		mAvailableHeight = getHeight() - getPaddingTop() - getPaddingBottom() - 2 * mPointRadius;
		// TODO max-min can chaged(setters) move it to set data or ondraw
		mXMultiplier = mAvailableWidth / (maxXValue - minXValue);
		mYMultiplier = mAvailableHeight / (maxYValue - minYValue);
		generateXForInterpolation();
		calculateHorizontalRulersDivider();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		long time = System.nanoTime();
		drawHorizontalRulers();

		// lines
		int seriesIndex = 0;
		for (LineSeries lineSeries : mData.series) {
			mLinePaint.setColor(lineSeries.color);
			int valueIndex = 0;
			for (float valueX : mGeneratedX) {
				final float rawValueX = calculateX(valueX);
				final float rawValueY = calculateY(mSplineInterpolators.get(seriesIndex).interpolate(valueX));
				if (valueIndex == 0) {
					mLinePath.moveTo(rawValueX, rawValueY);
				} else {
					mLinePath.lineTo(rawValueX, rawValueY);
				}
				++valueIndex;
			}
			mCanvas.drawPath(mLinePath, mLinePaint);
			mLinePath.reset();
			++seriesIndex;
		}
		// TODO check if point drawing on
		// pints
		for (LineSeries lineSeries : mData.series) {
			mPointPaint.setColor(lineSeries.color);
			int valueIndex = 0;
			for (float valueX : mData.domain) {
				final float rawValueX = calculateX(valueX);
				final float rawValueY = calculateY(lineSeries.values.get(valueIndex));
				mCanvas.drawCircle(rawValueX, rawValueY, mPointRadius, mPointPaint);
				++valueIndex;
			}
		}

		Log.v("TAG", "Narysowane w [ms]: " + (System.nanoTime() - time) / 1000000);
		canvas.drawBitmap(mBitmap, 0, 0, null);
		Log.v("TAG", "Wyświetlone w [ms]: " + (System.nanoTime() - time) / 1000000);
	}

	private float calculateX(float valueX) {
		return getPaddingLeft() + mPointRadius + (valueX - minXValue) * mXMultiplier;
	}

	private float calculateY(float valueY) {
		return getHeight() - getPaddingBottom() - mPointRadius - (valueY - minYValue) * mYMultiplier;
	}

	/**
	 * Generates additional X values for interpolation. Should be called after any view size changes.
	 */
	private void generateXForInterpolation() {
		// TODO check null mData and domain.size()>2
		final float scale = getResources().getDisplayMetrics().density;
		final float xRange = maxXValue - minXValue;
		final float xStep = 4.0f * xRange * scale / mAvailableWidth;
		mGeneratedX = new ArrayList<Float>();
		int i = 0;
		for (float value : mData.domain) {
			mGeneratedX.add(value);
			if (i < mData.domain.size() - 1) {
				for (float f = value + xStep; f < mData.domain.get(i + 1) - xStep; f += xStep) {
					mGeneratedX.add(f);
				}
			}
			++i;
		}
	}

	/**
	 * Calculates how many horizontal rulers will be visible on chart if user enabled rulers.
	 */
	private void calculateHorizontalRulersDivider() {
		final float scale = getResources().getDisplayMetrics().density;
		// divider should be integer
		int divider = Math.round((mAvailableHeight / scale) / 128.0f);
		if (divider < 2) {
			divider = 2;
		}
		mhorizontalRulersDivider = divider;
	}

	/**
	 * Draw horizontal Rulers. Number or lines is determined by chart height and screen resolution.
	 */
	private void drawHorizontalRulers() {
		float rawMinX = calculateX(minXValue) - mPointRadius;
		float rawMinY = calculateY(minYValue);
		float rawMaxX = calculateX(maxXValue) + mPointRadius;
		float rawMaxY = calculateY(maxYValue);
		mLinePath.moveTo(rawMinX, rawMinY);
		mLinePath.lineTo(rawMaxX, rawMinY);
		mCanvas.drawPath(mLinePath, mRulersPaint);
		mLinePath.reset();
		mLinePath.moveTo(rawMinX, rawMaxY);
		mLinePath.lineTo(rawMaxX, rawMaxY);
		mCanvas.drawPath(mLinePath, mRulersPaint);
		mLinePath.reset();
		final float step = (maxYValue - minYValue) / mhorizontalRulersDivider;
		for (int i = 1; i < mhorizontalRulersDivider; ++i) {
			final float rawValueY = calculateY(minYValue + step * i);
			mLinePath.moveTo(rawMinX, rawValueY);
			mLinePath.lineTo(rawMaxX, rawValueY);
			mCanvas.drawPath(mLinePath, mRulersPaint);
			mLinePath.reset();
		}
	}

	/**
	 * Sets chart data.
	 * 
	 * @param data
	 */
	public void setData(final LineChartData data) {
		mData = data;
		calculateRanges();
		// TODO check if interpolation on and series number
		generateSplineInterpolators(data);
		postInvalidate();
	}

	private void generateSplineInterpolators(final LineChartData data) {
		mSplineInterpolators = new ArrayList<SplineInterpolator>();
		for (LineSeries lineSeries : data.series) {
			mSplineInterpolators.add(SplineInterpolator.createMonotoneCubicSpline(data.domain, lineSeries.values));
		}
	}

	private void calculateRanges() {
		for (Float value : mData.domain) {
			if (value < minXValue) {
				minXValue = value;
			} else if (value > maxXValue) {
				maxXValue = value;
			}
		}
		for (LineSeries lineSeries : mData.series) {
			for (Float value : lineSeries.values) {
				if (value < minYValue) {
					minYValue = value;
				} else if (value > maxYValue) {
					maxYValue = value;
				}
			}
		}
	}

}