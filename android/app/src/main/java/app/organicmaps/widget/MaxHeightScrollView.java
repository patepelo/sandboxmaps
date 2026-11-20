package app.organicmaps.widget;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/// A scroll view that wraps its content up to a share of the screen height, then scrolls.
public class MaxHeightScrollView extends NestedScrollView
{
  private static final float MAX_SCREEN_SHARE = 0.45f;

  public MaxHeightScrollView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    final int max = Math.round(getResources().getDisplayMetrics().heightPixels * MAX_SCREEN_SHARE);
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
  }
}
