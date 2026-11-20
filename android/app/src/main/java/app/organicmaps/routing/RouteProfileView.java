package app.organicmaps.routing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.routing.RouteDetails;
import app.organicmaps.sdk.util.StringUtils;
import com.google.android.material.color.MaterialColors;

/// Elevation profile of a route, filled by steepness: flat, moderate and steep (from RouteDetails.STEEP_GRADE).
/// Dragging along it reports the distance under the finger.
public class RouteProfileView extends View
{
  /// Grades below this are flat.
  public static final double MODERATE_GRADE = 0.04;
  /// Steepness is measured over this distance, as in the core (map/route_details.cpp).
  private static final double GRADE_WINDOW_M = 100;

  public interface OnScrubListener
  {
    void onScrub(double distM);
  }

  private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mLine = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mGrid = new Paint();
  private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint mCursor = new Paint();
  private final Path mPath = new Path();
  private final float mDensity;

  @Nullable
  private double[] mDist;
  @Nullable
  private int[] mAlt;
  /// Grade of each piece between two points, measured over GRADE_WINDOW_M.
  @Nullable
  private double[] mGrade;
  private double mTotalM;
  private int mMinAlt;
  private int mMaxAlt;
  private float mCursorX = -1;
  /// A small profile without labels, next to the Start button.
  private boolean mCompact;
  @Nullable
  private OnScrubListener mListener;

  public RouteProfileView(Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    mDensity = getResources().getDisplayMetrics().density;
    mFill.setStyle(Paint.Style.FILL);
    mLine.setStyle(Paint.Style.STROKE);
    mLine.setStrokeWidth(1.2f * mDensity);
    final int primary = MaterialColors.getColor(context, android.R.attr.textColorPrimary, Color.BLACK);
    final int secondary = MaterialColors.getColor(context, android.R.attr.textColorSecondary, Color.GRAY);
    mLine.setColor(primary);
    mLine.setAlpha(140);
    mGrid.setColor(secondary);
    mGrid.setAlpha(60);
    mGrid.setStrokeWidth(mDensity);
    mText.setColor(secondary);
    mText.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
    mCursor.setColor(primary);
    mCursor.setStrokeWidth(mDensity);
    mBandColors = new int[] {gradeColor(context, 0), gradeColor(context, MODERATE_GRADE),
                             gradeColor(context, RouteDetails.STEEP_GRADE)};
  }

  public void setProfile(@NonNull RouteDetails details)
  {
    mDist = details.profileDistM;
    mAlt = details.profileAltM;
    mTotalM = mDist.length > 0 ? mDist[mDist.length - 1] : 0;
    mMinAlt = Integer.MAX_VALUE;
    mMaxAlt = Integer.MIN_VALUE;
    for (int a : mAlt)
    {
      mMinAlt = Math.min(mMinAlt, a);
      mMaxAlt = Math.max(mMaxAlt, a);
    }
    mGrade = new double[Math.max(0, mDist.length - 1)];
    for (int i = 0; i < mGrade.length; ++i)
    {
      // A window around the piece, at least GRADE_WINDOW_M long where the route allows.
      int from = i, to = i + 1;
      while (mDist[to] - mDist[from] < GRADE_WINDOW_M && (from > 0 || to < mDist.length - 1))
      {
        if (to < mDist.length - 1)
          ++to;
        if (mDist[to] - mDist[from] < GRADE_WINDOW_M && from > 0)
          --from;
      }
      final double run = mDist[to] - mDist[from];
      mGrade[i] = run > 0 ? (mAlt[to] - mAlt[from]) / run : 0;
    }
    mCursorX = -1;
    invalidate();
  }

  public void setCompact(boolean compact)
  {
    mCompact = compact;
    requestLayout();
    invalidate();
  }

  public boolean hasProfile()
  {
    return mDist != null && mDist.length >= 2 && mTotalM > 0;
  }

  public void setOnScrubListener(@Nullable OnScrubListener listener)
  {
    mListener = listener;
  }

  @ColorInt
  private final int[] mBandColors;

  @ColorInt
  public static int gradeColor(@NonNull Context context, double grade)
  {
    final double g = Math.abs(grade);
    if (g >= RouteDetails.STEEP_GRADE)
      return ContextCompat.getColor(context, R.color.route_grade_steep);
    if (g >= MODERATE_GRADE)
      return ContextCompat.getColor(context, R.color.route_grade_moderate);
    return ContextCompat.getColor(context, R.color.route_grade_flat);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    final int width = MeasureSpec.getSize(widthMeasureSpec);
    setMeasuredDimension(width, Math.round((mCompact ? 36 : 120) * mDensity));
  }

  private float left()
  {
    return mCompact ? 0 : 34 * mDensity;
  }

  private float bottom()
  {
    return mCompact ? getHeight() : getHeight() - 16 * mDensity;
  }

  private float x(double distM)
  {
    return (float) (left() + distM / mTotalM * (getWidth() - left() - 4 * mDensity));
  }

  private float y(double alt, double lo, double hi)
  {
    final float top = (mCompact ? 2 : 6) * mDensity;
    return (float) (top + (hi - alt) / (hi - lo) * (bottom() - top));
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas)
  {
    if (!hasProfile())
      return;
    final double[] d = mDist;
    final int[] a = mAlt;
    // Round the altitude range out to tens, so the two grid labels are tidy.
    final double lo = Math.floor((mMinAlt - 5) / 10.0) * 10;
    final double hi = Math.max(lo + 20, Math.ceil((mMaxAlt + 5) / 10.0) * 10);

    for (double alt : mCompact ? new double[0] : new double[] {lo, hi})
    {
      final float gy = y(alt, lo, hi);
      canvas.drawLine(left(), gy, getWidth(), gy, mGrid);
      final String label = Framework.nativeFormatAltitude(alt);
      canvas.drawText(label, left() - mText.measureText(label) - 4 * mDensity, gy + 4 * mDensity, mText);
    }
    if (!mCompact)
    {
      final String start = StringUtils.nativeFormatDistance(0).toString(getContext());
      final String end = StringUtils.nativeFormatDistance(mTotalM).toString(getContext());
      canvas.drawText(start, left(), getHeight() - 3 * mDensity, mText);
      canvas.drawText(end, getWidth() - mText.measureText(end), getHeight() - 3 * mDensity, mText);
    }

    // One filled piece per pair of points, coloured by its steepness.
    for (int i = 0; i + 1 < d.length; ++i)
    {
      final double g = Math.abs(mGrade[i]);
      mFill.setColor(mBandColors[g >= RouteDetails.STEEP_GRADE ? 2 : g >= MODERATE_GRADE ? 1 : 0]);
      mPath.reset();
      mPath.moveTo(x(d[i]), bottom());
      mPath.lineTo(x(d[i]), y(a[i], lo, hi));
      mPath.lineTo(x(d[i + 1]), y(a[i + 1], lo, hi));
      mPath.lineTo(x(d[i + 1]), bottom());
      mPath.close();
      canvas.drawPath(mPath, mFill);
    }
    mPath.reset();
    mPath.moveTo(x(d[0]), y(a[0], lo, hi));
    for (int i = 1; i < d.length; ++i)
      mPath.lineTo(x(d[i]), y(a[i], lo, hi));
    canvas.drawPath(mPath, mLine);

    if (mCursorX >= 0)
      canvas.drawLine(mCursorX, 6 * mDensity, mCursorX, bottom(), mCursor);
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent(@NonNull MotionEvent event)
  {
    if (!hasProfile() || mCompact)
      return false;
    final int action = event.getActionMasked();
    if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE)
      return action == MotionEvent.ACTION_UP;
    // Keep the scroll view around from taking the drag.
    getParent().requestDisallowInterceptTouchEvent(true);
    mCursorX = Math.max(left(), Math.min(getWidth() - 4 * mDensity, event.getX()));
    invalidate();
    if (mListener != null)
      mListener.onScrub((mCursorX - left()) / (getWidth() - left() - 4 * mDensity) * mTotalM);
    return true;
  }

  /// Altitude at a distance along the route, linearly between points.
  public int altitudeAt(double distM)
  {
    if (!hasProfile())
      return 0;
    int i = 1;
    while (i < mDist.length - 1 && mDist[i] < distM)
      ++i;
    final double run = mDist[i] - mDist[i - 1];
    final double f = run > 0 ? (distM - mDist[i - 1]) / run : 0;
    return (int) Math.round(mAlt[i - 1] + (mAlt[i] - mAlt[i - 1]) * Math.max(0, Math.min(1, f)));
  }

  /// Grade at a distance along the route.
  public double gradeAt(double distM)
  {
    if (!hasProfile())
      return 0;
    int i = 1;
    while (i < mDist.length - 1 && mDist[i] < distM)
      ++i;
    return mGrade[i - 1];
  }

  /// Steepest climb along the route, as a grade.
  public double steepestClimb()
  {
    double max = 0;
    if (mGrade != null)
      for (double g : mGrade)
        max = Math.max(max, g);
    return max;
  }

  /// Meters of the route in each steepness band: flat, moderate, steep.
  @NonNull
  public double[] metersByBand()
  {
    final double[] out = new double[3];
    if (!hasProfile())
      return out;
    for (int i = 0; i < mGrade.length; ++i)
    {
      final double g = Math.abs(mGrade[i]);
      out[g >= RouteDetails.STEEP_GRADE ? 2 : g >= MODERATE_GRADE ? 1 : 0] += mDist[i + 1] - mDist[i];
    }
    return out;
  }
}
