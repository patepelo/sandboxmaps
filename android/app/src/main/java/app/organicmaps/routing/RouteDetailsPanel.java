package app.organicmaps.routing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.routing.RouteDetails;
import app.organicmaps.sdk.routing.RouteDetails.Stop;
import app.organicmaps.sdk.routing.RouteDetails.StopType;
import app.organicmaps.sdk.routing.RouteDetails.Surface;
import app.organicmaps.sdk.routing.RouteDetails.WayType;
import app.organicmaps.sdk.util.StringUtils;
import app.organicmaps.util.UiUtils;
import app.organicmaps.util.Utils;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The route details under the route summary, for walking and cycling routes: a summary, the steepness profile,
/// way types, surface, things to watch out for and stops along the route. Built from RouteDetails.
final class RouteDetailsPanel
{
  private static final int MAX_RANGES = 3;

  @NonNull
  private final Context mContext;
  @NonNull
  private final View mRoot;
  @NonNull
  private final View mScroll;
  @NonNull
  private final LinearLayout mContent;
  /// In the summary: the drag handle, the grey chart and the small coloured profile that replaces it.
  @NonNull
  private final View mHandle;
  @NonNull
  private final View mGreyChart;
  @NonNull
  private final RouteProfileView mCompactProfile;
  @Nullable
  private RouteDetails mDetails;
  private int mTotalTimeSec;
  private boolean mBicycle;
  private boolean mExpanded;
  @Nullable
  private OnExpandedListener mExpandedListener;

  interface OnExpandedListener
  {
    void onExpanded(boolean expanded);
  }
  private boolean mShowAllStops;

  /// @return null if this layout has no route details (e.g. landscape).
  @Nullable
  static RouteDetailsPanel create(@NonNull View frame)
  {
    return frame.findViewById(R.id.route_details) == null ? null : new RouteDetailsPanel(frame);
  }

  @SuppressLint("ClickableViewAccessibility")
  private RouteDetailsPanel(@NonNull View frame)
  {
    mRoot = frame.findViewById(R.id.route_details);
    mContext = frame.getContext();
    mScroll = mRoot.findViewById(R.id.route_details_scroll);
    mContent = mRoot.findViewById(R.id.route_details_content);
    mHandle = frame.findViewById(R.id.route_details_handle);
    mGreyChart = frame.findViewById(R.id.altitude_chart);
    mCompactProfile = frame.findViewById(R.id.route_profile_compact);
    mCompactProfile.setCompact(true);

    // Drag the summary up to open the details and down to close them; a tap on the handle toggles them.
    final View.OnTouchListener drag = new View.OnTouchListener() {
      private float mDownY;

      @Override
      public boolean onTouch(View v, MotionEvent event)
      {
        if (mDetails == null)
          return false;
        switch (event.getActionMasked())
        {
        case MotionEvent.ACTION_DOWN -> mDownY = event.getRawY();
        case MotionEvent.ACTION_UP ->
        {
          final float dy = event.getRawY() - mDownY;
          if (dy < -dp(24))
            setExpanded(true);
          else if (dy > dp(24))
            setExpanded(false);
          else if (v == mHandle)
            setExpanded(!mExpanded);
        }
        default -> {}
        }
        return true;
      }
    };
    mHandle.setOnTouchListener(drag);
    mCompactProfile.setOnTouchListener(drag);
    frame.findViewById(R.id.time_elevation_line).setOnTouchListener(drag);
  }

  /// The grey chart stays hidden while the coloured profile is known.
  boolean isShown()
  {
    return mDetails != null;
  }

  void show(@NonNull RouteDetails details, int totalTimeSec, boolean bicycle)
  {
    mDetails = details;
    mTotalTimeSec = totalTimeSec;
    mBicycle = bicycle;
    mShowAllStops = false;
    mCompactProfile.setProfile(details);
    UiUtils.show(mRoot, mHandle);
    if (mCompactProfile.hasProfile())
      UiUtils.hide(mGreyChart);
    render();
  }

  /// @return true if the details were open.
  boolean collapse()
  {
    if (!mExpanded)
      return false;
    setExpanded(false);
    return true;
  }

  void setOnExpandedListener(@Nullable OnExpandedListener listener)
  {
    mExpandedListener = listener;
  }

  void hide()
  {
    if (mExpanded)
      setExpanded(false);
    mDetails = null;
    UiUtils.hide(mRoot, mHandle, mCompactProfile);
    mContent.removeAllViews();
  }

  /// Open: the full profile shows in the details, so the small one hides. One chart at a time.
  private void setExpanded(boolean expanded)
  {
    final boolean changed = mExpanded != expanded;
    mExpanded = expanded;
    if (changed && mExpandedListener != null)
      mExpandedListener.onExpanded(expanded);
    UiUtils.showIf(expanded, mScroll);
    UiUtils.showIf(!expanded && mCompactProfile.hasProfile(), mCompactProfile);
  }

  private void render()
  {
    mContent.removeAllViews();
    setExpanded(mExpanded);
    final RouteDetails d = mDetails;
    if (d == null)
      return;
    final RouteProfileView profile = new RouteProfileView(mContext, null);
    profile.setProfile(d);
    addSummary(d, profile);
    if (profile.hasProfile())
      addSteepness(d, profile);
    addWayTypes(d);
    addSurfaces(d);
    addHeadsUp(d, profile);
    addStops(d);
  }

  // ---- Sections ----

  private void addSummary(@NonNull RouteDetails d, @NonNull RouteProfileView profile)
  {
    final LinearLayout row = new LinearLayout(mContext);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, dp(4), 0, dp(8));
    addCell(row, Utils.formatArrivalTime(mTotalTimeSec), R.string.route_details_arrival);
    if (mTotalTimeSec > 0)
    {
      final Pair<String, String> speed = StringUtils.nativeFormatSpeedAndUnits(d.totalM / mTotalTimeSec);
      addCell(row, speed.first + " " + speed.second, R.string.route_details_average_speed);
    }
    if (profile.hasProfile())
      addCell(row, Math.round(profile.steepestClimb() * 100) + "%", R.string.route_details_steepest_climb);
    mContent.addView(row);
  }

  private void addCell(@NonNull LinearLayout row, @NonNull String value, @StringRes int label)
  {
    final LinearLayout cell = new LinearLayout(mContext);
    cell.setOrientation(LinearLayout.VERTICAL);
    final TextView v = text(value, R.style.MwmTextAppearance_Body1, android.R.attr.textColorPrimary);
    v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
    cell.addView(v);
    cell.addView(text(mContext.getString(label), R.style.MwmTextAppearance_Body4, android.R.attr.textColorSecondary));
    row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
  }

  private void addSteepness(@NonNull RouteDetails d, @NonNull RouteProfileView profile)
  {
    addHeader(R.string.route_details_steepness, null);
    mContent.addView(profile);
    final TextView tip = text(mContext.getString(R.string.route_profile_hint), R.style.MwmTextAppearance_Body4,
                              android.R.attr.textColorSecondary);
    tip.setPadding(0, dp(2), 0, dp(4));
    mContent.addView(tip);
    profile.setOnScrubListener(distM -> tip.setText(describeSpot(d, profile, distM)));

    final double[] bands = profile.metersByBand();
    final int[] names = {R.string.route_grade_flat, R.string.route_grade_moderate, R.string.route_grade_steep};
    final int[] colors = {R.color.route_grade_flat, R.color.route_grade_moderate, R.color.route_grade_steep};
    for (int i = 0; i < bands.length; ++i)
      if (bands[i] > 0)
        addRow(colors[i], mContext.getString(names[i]), bands[i], d.totalM);
  }

  @NonNull
  private String describeSpot(@NonNull RouteDetails d, @NonNull RouteProfileView profile, double distM)
  {
    final long grade = Math.round(profile.gradeAt(distM) * 100);
    final StringBuilder sb = new StringBuilder()
                                 .append(distance(distM))
                                 .append(" · ")
                                 .append(Framework.nativeFormatAltitude(profile.altitudeAt(distM)))
                                 .append(" · ")
                                 .append(grade > 0 ? "+" : "")
                                 .append(grade)
                                 .append('%');
    for (int i = 0; i < d.getStretchCount(); ++i)
    {
      if (distM >= d.stretchStartM[i] && distM <= d.stretchEndM[i])
      {
        sb.append(" · ").append(mContext.getString(wayName(d.getWayType(i))));
        sb.append(", ").append(mContext.getString(surfaceName(d.getSurface(i))));
        break;
      }
    }
    return sb.toString();
  }

  private void addWayTypes(@NonNull RouteDetails d)
  {
    addHeader(R.string.route_details_way_types, null);
    final List<Object[]> parts = new ArrayList<>();
    for (WayType type : WayType.values())
    {
      final double m = d.getWayTypeMeters(type);
      if (m > 0)
        parts.add(new Object[] {wayColor(type), mContext.getString(wayName(type)), m});
    }
    addBarAndRows(parts, d.totalM);
  }

  private void addSurfaces(@NonNull RouteDetails d)
  {
    addHeader(R.string.route_details_surface, null);
    final List<Object[]> parts = new ArrayList<>();
    for (Surface surface : Surface.values())
    {
      final double m = d.getSurfaceMeters(surface);
      if (m > 0)
        parts.add(new Object[] {surfaceColor(surface), mContext.getString(surfaceName(surface)), m});
    }
    addBarAndRows(parts, d.totalM);
  }

  private void addHeadsUp(@NonNull RouteDetails d, @NonNull RouteProfileView profile)
  {
    addHeader(R.string.route_details_heads_up, null);
    int count = 0;

    final List<double[]> stairs = ranges(d, i -> d.getWayType(i) == WayType.STEPS);
    if (!stairs.isEmpty())
    {
      final int shown = Math.min(MAX_RANGES, stairs.size());
      final List<String> at = new ArrayList<>();
      for (int i = 0; i < shown; ++i)
        at.add(distance(stairs.get(i)[0]));
      String list = String.join(", ", at);
      if (stairs.size() > shown)
        list = mContext.getString(R.string.route_heads_and_more, list, stairs.size() - shown);
      count += addHeadsUpItem(R.color.route_way_steps, "S",
                              mContext.getString(mBicycle ? R.string.route_heads_stairs_bike : R.string.route_heads_stairs),
                              mContext.getString(R.string.route_heads_at, list));
    }

    if (d.steepGrade.length > 0)
    {
      double max = 0;
      final List<double[]> where = new ArrayList<>();
      for (int i = 0; i < d.steepGrade.length; ++i)
      {
        max = Math.max(max, Math.abs(d.steepGrade[i]));
        where.add(new double[] {d.steepStartM[i], d.steepEndM[i]});
      }
      count += addHeadsUpItem(R.color.route_grade_steep, "%",
                              mContext.getString(R.string.route_heads_steep, d.steepGrade.length,
                                                 (int) Math.round(max * 100)),
                              describeRanges(where));
    }

    if (mBicycle)
    {
      final List<double[]> busy = ranges(d, i -> d.getWayType(i) == WayType.BUSY);
      if (!busy.isEmpty())
        count += addHeadsUpItem(R.color.route_way_busy, "!",
                                mContext.getString(R.string.route_heads_busy, distance(d.getWayTypeMeters(WayType.BUSY))),
                                mContext.getString(R.string.route_heads_busy_detail, describeRanges(busy)));
      double walkM = 0;
      for (int i = 0; i < d.getStretchCount(); ++i)
        if (d.isWalkBike(i))
          walkM += d.stretchEndM[i] - d.stretchStartM[i];
      if (walkM > 0)
        count += addHeadsUpItem(R.color.route_way_footway, "⇢", mContext.getString(R.string.route_heads_walk_bike, distance(walkM)),
                                mContext.getString(R.string.route_heads_walk_bike_detail));
    }

    final double unpaved = d.getSurfaceMeters(Surface.UNPAVED) + d.getSurfaceMeters(Surface.UNPAVED_BAD);
    if (unpaved > 0)
    {
      final double rough = d.getSurfaceMeters(Surface.UNPAVED_BAD);
      count += addHeadsUpItem(R.color.route_surface_unpaved, "≈",
                              mContext.getString(R.string.route_heads_unpaved, distance(unpaved)),
                              rough > 0 ? mContext.getString(R.string.route_heads_unpaved_rough, distance(rough))
                                        : mContext.getString(R.string.route_heads_unpaved_good));
    }

    final double unknown = d.getSurfaceMeters(Surface.UNKNOWN);
    if (unknown > 0)
      count += addHeadsUpItem(R.color.route_surface_unknown, "?",
                              mContext.getString(R.string.route_heads_unknown, distance(unknown)),
                              mContext.getString(R.string.route_heads_unknown_detail));

    if (count == 0)
      mContent.addView(text(mContext.getString(R.string.route_heads_none), R.style.MwmTextAppearance_Body3,
                            android.R.attr.textColorSecondary));
  }

  private int addHeadsUpItem(@ColorRes int color, @NonNull String icon, @NonNull String title, @NonNull String detail)
  {
    final LinearLayout row = new LinearLayout(mContext);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, dp(5), 0, dp(5));
    final TextView ic = text(icon, R.style.MwmTextAppearance_Body3, 0);
    ic.setTextColor(Color.WHITE);
    ic.setGravity(Gravity.CENTER);
    ic.setBackground(circle(ContextCompat.getColor(mContext, color)));
    final LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(dp(26), dp(26));
    icLp.setMarginEnd(dp(12));
    row.addView(ic, icLp);
    final LinearLayout texts = new LinearLayout(mContext);
    texts.setOrientation(LinearLayout.VERTICAL);
    texts.addView(text(title, R.style.MwmTextAppearance_Body3, android.R.attr.textColorPrimary));
    texts.addView(text(detail, R.style.MwmTextAppearance_Body4, android.R.attr.textColorSecondary));
    row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    mContent.addView(row);
    return 1;
  }

  private void addStops(@NonNull RouteDetails d)
  {
    if (d.stops.length == 0)
      return;
    addHeader(R.string.route_details_along_route,
              mContext.getString(R.string.route_details_within, distance(150)));
    if (!mShowAllStops)
    {
      // One line per kind: how many, and where the first one is.
      final Map<StopType, List<Stop>> byKind = new LinkedHashMap<>();
      for (Stop stop : d.stops)
        byKind.computeIfAbsent(stop.type, k -> new ArrayList<>()).add(stop);
      for (Map.Entry<StopType, List<Stop>> kind : byKind.entrySet())
      {
        final List<Stop> stops = kind.getValue();
        final String name = mContext.getString(stopName(kind.getKey()));
        addStopRow(kind.getKey(), stops.size() > 1 ? mContext.getString(R.string.route_stops_kind, name, stops.size()) : name,
                   mContext.getString(stops.size() > 1 ? R.string.route_stops_first_at : R.string.route_heads_at,
                                      distance(stops.get(0).alongM)),
                   null);
      }
    }
    else
    {
      for (Stop stop : d.stops)
      {
        final String name = stop.name.isEmpty() ? mContext.getString(stopName(stop.type)) : stop.name;
        final String off = stop.offsetM < 5 ? mContext.getString(R.string.route_stops_on)
                                            : mContext.getString(R.string.route_stops_off, distance(stop.offsetM));
        addStopRow(stop.type, name, off, distance(stop.alongM));
      }
    }
    final TextView more = text(mShowAllStops ? mContext.getString(R.string.route_stops_show_fewer)
                                             : mContext.getString(R.string.route_stops_show_all, d.stops.length),
                               R.style.MwmTextAppearance_Body3, 0);
    more.setTextColor(accent());
    more.setPadding(0, dp(8), 0, dp(4));
    more.setOnClickListener(v -> {
      mShowAllStops = !mShowAllStops;
      render();
    });
    mContent.addView(more);
  }

  private void addStopRow(@NonNull StopType type, @NonNull String title, @NonNull String detail, @Nullable String right)
  {
    final LinearLayout row = new LinearLayout(mContext);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(4), 0, dp(4));
    final View dot = new View(mContext);
    dot.setBackground(circle(ContextCompat.getColor(mContext, stopColor(type))));
    final LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(12), dp(12));
    dotLp.setMarginEnd(dp(12));
    row.addView(dot, dotLp);
    final LinearLayout texts = new LinearLayout(mContext);
    texts.setOrientation(LinearLayout.VERTICAL);
    texts.addView(text(title, R.style.MwmTextAppearance_Body3, android.R.attr.textColorPrimary));
    texts.addView(text(detail, R.style.MwmTextAppearance_Body4, android.R.attr.textColorSecondary));
    row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    if (right != null)
      row.addView(text(right, R.style.MwmTextAppearance_Body4, android.R.attr.textColorSecondary));
    mContent.addView(row);
  }

  // ---- Building blocks ----

  private void addHeader(@StringRes int title, @Nullable String right)
  {
    final LinearLayout row = new LinearLayout(mContext);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(0, dp(12), 0, dp(4));
    final TextView t = text(mContext.getString(title), R.style.MwmTextAppearance_Body4, 0);
    t.setAllCaps(true);
    t.setTextColor(accent());
    row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    if (right != null)
      row.addView(text(right, R.style.MwmTextAppearance_Body4, android.R.attr.textColorSecondary));
    mContent.addView(row);
  }

  /// A bar split by share, and one row per part. Each part is {@ColorRes color, String name, Double meters}.
  private void addBarAndRows(@NonNull List<Object[]> parts, double totalM)
  {
    final LinearLayout bar = new LinearLayout(mContext);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    final GradientDrawable rounded = new GradientDrawable();
    rounded.setCornerRadius(dp(6));
    bar.setBackground(rounded);
    bar.setClipToOutline(true);
    for (Object[] part : parts)
    {
      final View piece = new View(mContext);
      piece.setBackgroundColor(ContextCompat.getColor(mContext, (int) part[0]));
      bar.addView(piece, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (float) (double) part[2]));
    }
    final LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12));
    barLp.bottomMargin = dp(4);
    mContent.addView(bar, barLp);
    for (Object[] part : parts)
      addRow((int) part[0], (String) part[1], (double) part[2], totalM);
  }

  private void addRow(@ColorRes int color, @NonNull String name, double meters, double totalM)
  {
    final LinearLayout row = new LinearLayout(mContext);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(3), 0, dp(3));
    final View swatch = new View(mContext);
    final GradientDrawable sw = new GradientDrawable();
    sw.setCornerRadius(dp(3));
    sw.setColor(ContextCompat.getColor(mContext, color));
    swatch.setBackground(sw);
    final LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(12), dp(12));
    swLp.setMarginEnd(dp(12));
    row.addView(swatch, swLp);
    row.addView(text(name, R.style.MwmTextAppearance_Body3, android.R.attr.textColorPrimary),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    row.addView(text(distance(meters), R.style.MwmTextAppearance_Body4, android.R.attr.textColorSecondary));
    final TextView pc = text(Math.round(meters / totalM * 100) + "%", R.style.MwmTextAppearance_Body4,
                             android.R.attr.textColorSecondary);
    pc.setGravity(Gravity.END);
    row.addView(pc, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
    mContent.addView(row);
  }

  @NonNull
  private TextView text(@NonNull String value, int style, int colorAttr)
  {
    final MaterialTextView t = new MaterialTextView(mContext);
    TextViewCompat.setTextAppearance(t, style);
    if (colorAttr != 0)
      t.setTextColor(MaterialColors.getColor(mContext, colorAttr, Color.GRAY));
    t.setText(value);
    return t;
  }

  private int accent()
  {
    return MaterialColors.getColor(mContext, androidx.appcompat.R.attr.colorAccent, Color.GRAY);
  }

  @NonNull
  private static GradientDrawable circle(int color)
  {
    final GradientDrawable d = new GradientDrawable();
    d.setShape(GradientDrawable.OVAL);
    d.setColor(color);
    return d;
  }

  private int dp(float value)
  {
    return Math.round(value * mContext.getResources().getDisplayMetrics().density);
  }

  @NonNull
  private String distance(double meters)
  {
    return StringUtils.nativeFormatDistance(meters).toString(mContext);
  }

  private interface StretchTest
  {
    boolean test(int stretch);
  }

  /// Consecutive stretches that pass the test, as {startM, endM}.
  @NonNull
  private static List<double[]> ranges(@NonNull RouteDetails d, @NonNull StretchTest test)
  {
    final List<double[]> out = new ArrayList<>();
    for (int i = 0; i < d.getStretchCount(); ++i)
    {
      if (!test.test(i))
        continue;
      if (!out.isEmpty() && out.get(out.size() - 1)[1] == d.stretchStartM[i])
        out.get(out.size() - 1)[1] = d.stretchEndM[i];
      else
        out.add(new double[] {d.stretchStartM[i], d.stretchEndM[i]});
    }
    return out;
  }

  /// The first few ranges, then how many more: a long list doesn't help on a phone.
  @NonNull
  private String describeRanges(@NonNull List<double[]> ranges)
  {
    final int shown = Math.min(MAX_RANGES, ranges.size());
    final List<String> parts = new ArrayList<>();
    for (int i = 0; i < shown; ++i)
      parts.add(distance(ranges.get(i)[0]) + "–" + distance(ranges.get(i)[1]));
    final String list = String.join(", ", parts);
    return ranges.size() > shown ? mContext.getString(R.string.route_heads_and_more, list, ranges.size() - shown) : list;
  }

  // ---- Names and colours ----

  @StringRes
  private static int wayName(@NonNull WayType type)
  {
    return switch (type)
    {
      case QUIET -> R.string.route_way_quiet;
      case MINOR -> R.string.route_way_minor;
      case MAIN -> R.string.route_way_main;
      case BUSY -> R.string.route_way_busy;
      case CYCLEWAY -> R.string.route_way_cycleway;
      case TRACK -> R.string.route_way_track;
      case PATH -> R.string.route_way_path;
      case FOOTWAY -> R.string.route_way_footway;
      case STEPS -> R.string.route_way_steps;
      case FERRY -> R.string.route_way_ferry;
      case OTHER -> R.string.route_way_other;
    };
  }

  @ColorRes
  private static int wayColor(@NonNull WayType type)
  {
    return switch (type)
    {
      case QUIET -> R.color.route_way_quiet;
      case MINOR -> R.color.route_way_minor;
      case MAIN -> R.color.route_way_main;
      case BUSY -> R.color.route_way_busy;
      case CYCLEWAY -> R.color.route_way_cycleway;
      case TRACK -> R.color.route_way_track;
      case PATH -> R.color.route_way_path;
      case FOOTWAY -> R.color.route_way_footway;
      case STEPS -> R.color.route_way_steps;
      case FERRY -> R.color.route_way_ferry;
      case OTHER -> R.color.route_way_other;
    };
  }

  @StringRes
  private static int surfaceName(@NonNull Surface surface)
  {
    return switch (surface)
    {
      case PAVED -> R.string.route_surface_paved;
      case PAVED_BAD -> R.string.route_surface_paved_bad;
      case UNPAVED -> R.string.route_surface_unpaved;
      case UNPAVED_BAD -> R.string.route_surface_unpaved_bad;
      case UNKNOWN -> R.string.route_surface_unknown;
    };
  }

  @ColorRes
  private static int surfaceColor(@NonNull Surface surface)
  {
    return switch (surface)
    {
      case PAVED -> R.color.route_surface_paved;
      case PAVED_BAD -> R.color.route_surface_paved_bad;
      case UNPAVED -> R.color.route_surface_unpaved;
      case UNPAVED_BAD -> R.color.route_surface_unpaved_bad;
      case UNKNOWN -> R.color.route_surface_unknown;
    };
  }

  /// Map type names, already translated.
  @StringRes
  private static int stopName(@NonNull StopType type)
  {
    return switch (type)
    {
      case DRINKING_WATER -> app.organicmaps.sdk.R.string.type_amenity_drinking_water;
      case TOILETS -> app.organicmaps.sdk.R.string.type_amenity_toilets;
      case BICYCLE_REPAIR -> app.organicmaps.sdk.R.string.type_amenity_bicycle_repair_station;
      case SHELTER -> app.organicmaps.sdk.R.string.type_amenity_shelter;
      case VIEWPOINT -> app.organicmaps.sdk.R.string.type_tourism_viewpoint;
      case TRAIN_STATION -> app.organicmaps.sdk.R.string.type_railway_station;
    };
  }

  @ColorRes
  private static int stopColor(@NonNull StopType type)
  {
    return switch (type)
    {
      case DRINKING_WATER -> R.color.route_surface_paved;
      case TOILETS -> R.color.route_way_footway;
      case BICYCLE_REPAIR -> R.color.route_way_cycleway;
      case SHELTER -> R.color.route_way_track;
      case VIEWPOINT -> R.color.route_way_quiet;
      case TRAIN_STATION -> R.color.route_way_busy;
    };
  }
}
