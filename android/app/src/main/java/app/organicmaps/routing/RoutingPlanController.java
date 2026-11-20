package app.organicmaps.routing;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.maplayer.MapModeUi;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.MapMode;
import app.organicmaps.sdk.Router;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.routing.RouteDetails;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.routing.RoutingOptions;
import app.organicmaps.sdk.routing.TransitRouteInfo;
import app.organicmaps.sdk.settings.RoadType;
import app.organicmaps.sdk.util.log.Logger;
import app.organicmaps.util.ThemeUtils;
import app.organicmaps.util.UiUtils;
import app.organicmaps.util.WindowInsetUtils.PaddingInsetsListener;
import app.organicmaps.widget.RoutingToolbarButton;
import app.organicmaps.widget.ToolbarController;
import app.organicmaps.widget.WheelProgressView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RoutingPlanController extends ToolbarController
{
  private static final String TAG = RoutingPlanController.class.getSimpleName();
  private static final String BUNDLE_HAS_DRIVING_OPTIONS_VIEW = "has_driving_options_view";

  private final View mFrame;
  @NonNull
  private final RoutingPlanInplaceController.RoutingPlanListener mRoutingPlanListener;
  private final RadioGroup mRouterTypes;
  @NonNull
  private final WheelProgressView mProgressVehicle;
  @NonNull
  private final WheelProgressView mProgressPedestrian;
  @NonNull
  private final WheelProgressView mProgressTransit;
  @NonNull
  private final WheelProgressView mProgressBicycle;
  @NonNull
  private final WheelProgressView mProgressRuler;

  @NonNull
  private final RoutingBottomMenuController mRoutingBottomMenuController;

  int mFrameHeight;
  final int mAnimToggle;
  private boolean mIsPreviewingTurn;

  @NonNull
  private final FrameLayout mRoutingOptionsBanner;

  @NonNull
  private final View.OnLayoutChangeListener mRoutingOptionsLayoutListener;
  @NonNull
  private final TextView mRouteFrom;
  @NonNull
  private final TextView mRouteTo;
  @NonNull
  private final View mAvoidRow;
  @NonNull
  private final TextView mAvoidValue;
  @NonNull
  private final ColorStateList mAvoidValueColors;
  @NonNull
  private final TextView mAvoidLabel;
  private final int mAvoidRowMinHeight;
  private final int mAvoidRowPadding;
  @NonNull
  private final View mAvoidChevron;
  @NonNull
  private final View mOptionsDropdown;
  @Nullable
  private Router mOptionsDropdownRouter;
  @NonNull
  private Set<RoadType> mOptionsAtOpen = Collections.emptySet();

  /// The selected mode gets its map mode colour, like the pill on the map.
  private void setupRouterButton(@IdRes int buttonId, final @DrawableRes int iconRes, @ColorInt int selectedColor,
                                 View.OnClickListener clickListener)
  {
    final int iconTint = ThemeUtils.getColor(mFrame.getContext(), R.attr.iconTint);
    CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) ->
    {
      RoutingToolbarButton button = (RoutingToolbarButton) buttonView;
      button.setIcon(iconRes);
      if (isChecked)
        button.activate();
      else
        button.deactivate();
      button.setBackgroundResource(isChecked ? R.drawable.bg_map_mode_selected : 0);
      button.setBackgroundTintList(isChecked ? ColorStateList.valueOf(selectedColor) : null);
      button.setButtonTintList(ColorStateList.valueOf(isChecked ? Color.WHITE : iconTint));
    };

    RoutingToolbarButton rb = mRouterTypes.findViewById(buttonId);
    listener.onCheckedChanged(rb, false);
    rb.setOnCheckedChangeListener(listener);
    rb.setOnClickListener(clickListener);
  }

  RoutingPlanController(View root, Activity activity, ActivityResultLauncher<Intent> startRoutingOptionsForResult,
                        @NonNull RoutingPlanInplaceController.RoutingPlanListener routingPlanListener,
                        @NonNull RoutingBottomMenuListener listener)
  {
    super(root, activity);
    mFrame = root;
    mRoutingPlanListener = routingPlanListener;

    mRouterTypes = getToolbar().findViewById(R.id.route_type);

    setupRouterButtons();

    View progressFrame = getToolbar().findViewById(R.id.progress_frame);
    mProgressVehicle = progressFrame.findViewById(R.id.progress_vehicle);
    mProgressPedestrian = progressFrame.findViewById(R.id.progress_pedestrian);
    mProgressTransit = progressFrame.findViewById(R.id.progress_transit);
    mProgressBicycle = progressFrame.findViewById(R.id.progress_bicycle);
    mProgressRuler = progressFrame.findViewById(R.id.progress_ruler);
    //    mProgressTaxi = (WheelProgressView) progressFrame.findViewById(R.id.progress_taxi);

    mRoutingBottomMenuController = RoutingBottomMenuController.newInstance(requireActivity(), mFrame, listener);

    mRoutingOptionsBanner = mFrame.findViewById(R.id.routing_options_banner);
    mRouteFrom = mFrame.findViewById(R.id.route_from);
    mRouteTo = mFrame.findViewById(R.id.route_to);
    mAvoidRow = mFrame.findViewById(R.id.route_avoid_row);
    mAvoidValue = mFrame.findViewById(R.id.route_avoid_value);
    mAvoidLabel = mFrame.findViewById(R.id.route_avoid_label);
    mAvoidRowMinHeight = mAvoidRow.getMinimumHeight();
    mAvoidRowPadding = mFrame.getResources().getDimensionPixelSize(R.dimen.margin_half);
    mAvoidValueColors = mAvoidValue.getTextColors();
    mAvoidChevron = mFrame.findViewById(R.id.route_avoid_chevron);
    mOptionsDropdown = mFrame.findViewById(R.id.route_options_dropdown);
    mAvoidRow.setOnClickListener(v -> {
      if (!closeRouteOptions())
        openRouteOptions();
    });

    mRoutingOptionsLayoutListener = new SelfTerminatedRoutingOptionsLayoutListener();
    mAnimToggle =
        MwmApplication.from(activity.getApplicationContext()).getResources().getInteger(R.integer.anim_default);

    final View menuFrame = activity.findViewById(R.id.menu_frame);
    final PaddingInsetsListener insetsListener =
        new PaddingInsetsListener.Builder()
            .setInsetsTypeMask(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout())
            .setExcludeTop()
            .build();
    ViewCompat.setOnApplyWindowInsetsListener(menuFrame, insetsListener);

    onTurnPreviewChanged(RoutingController.get().isPreviewingTurn());
  }

  @NonNull
  protected View getFrame()
  {
    return mFrame;
  }

  @NonNull
  private View getRoutingOptionsBtnContainer()
  {
    return mRoutingOptionsBanner;
  }

  private void setupRouterButtons()
  {
    final Context context = mFrame.getContext();
    setupRouterButton(R.id.vehicle, R.drawable.ic_car, MapModeUi.color(context, MapMode.Driving),
                      this::onVehicleModeSelected);
    setupRouterButton(R.id.pedestrian, R.drawable.ic_pedestrian, MapModeUi.color(context, MapMode.Walking),
                      this::onPedestrianModeSelected);
    setupRouterButton(R.id.transit, app.organicmaps.sdk.R.drawable.ic_route_planning_metro_40px,
                      MapModeUi.color(context, MapMode.PublicTransport), this::onTransitModeSelected);
    setupRouterButton(R.id.bicycle, R.drawable.ic_bike, MapModeUi.color(context, MapMode.Cycling),
                      this::onBicycleModeSelected);
    setupRouterButton(R.id.ruler, app.organicmaps.sdk.R.drawable.ic_ruler_route,
                      ThemeUtils.getColor(context, R.attr.iconTint), this::onRulerModeSelected);
  }

  private void onTransitModeSelected(@NonNull View v)
  {
    RoutingController.get().setRouterType(Router.Transit);
  }

  private void onBicycleModeSelected(@NonNull View v)
  {
    RoutingController.get().setRouterType(Router.Bicycle);
  }

  private void onRulerModeSelected(@NonNull View v)
  {
    RoutingController.get().setRouterType(Router.Ruler);
  }

  private void onPedestrianModeSelected(@NonNull View v)
  {
    RoutingController.get().setRouterType(Router.Pedestrian);
  }

  private void onVehicleModeSelected(@NonNull View v)
  {
    RoutingController.get().setRouterType(Router.Vehicle);
  }

  @Override
  public void onUpClick()
  {
    // Ignore the event if the back and start buttons are pressed at the same time.
    // See {@link #RoutingBottomMenuController.setStartButton()}.
    if (RoutingController.get().isNavigating())
      return;
    RoutingController.get().cancel();
  }

  boolean checkFrameHeight()
  {
    if (mFrameHeight > 0)
      return true;

    mFrameHeight = mFrame.getHeight();
    return (mFrameHeight > 0);
  }

  private void updateProgressLabels()
  {
    if (mIsPreviewingTurn)
    {
      mRoutingBottomMenuController.hideAltitudeChartAndRoutingDetails();
      return;
    }

    RoutingController.BuildState buildState = RoutingController.get().getBuildState();

    final boolean ready = (buildState == RoutingController.BuildState.BUILT);

    if (!ready)
    {
      mRoutingBottomMenuController.hideAltitudeChartAndRoutingDetails();
      return;
    }

    if (isTransitType())
    {
      TransitRouteInfo info = RoutingController.get().getCachedTransitInfo();
      if (info != null)
        mRoutingBottomMenuController.showTransitInfo(info);
      return;
    }

    if (isRulerType())
    {
      RoutingInfo routingInfo = RoutingController.get().getCachedRoutingInfo();
      if (routingInfo != null)
        mRoutingBottomMenuController.showRulerInfo(Framework.nativeGetRoutePoints(), routingInfo.distToTarget);
      return;
    }

    final boolean showStartButton = !RoutingController.get().isRulerRouterType();
    mRoutingBottomMenuController.setStartButton(showStartButton);
    mRoutingBottomMenuController.showAltitudeChartAndRoutingDetails();
    requestRouteDetails();
  }

  /// What the route is made of, for the route details panel. For now only logged.
  private void requestRouteDetails()
  {
    RouteDetails.request(details -> {
      if (details.isEmpty())
        return;
      final StringBuilder sb = new StringBuilder("Route details: ").append(Math.round(details.totalM)).append(" m;");
      for (RouteDetails.WayType type : RouteDetails.WayType.values())
      {
        final double m = details.getWayTypeMeters(type);
        if (m > 0)
          sb.append(' ').append(type).append('=').append(Math.round(m));
      }
      sb.append(';');
      for (RouteDetails.Surface surface : RouteDetails.Surface.values())
      {
        final double m = details.getSurfaceMeters(surface);
        if (m > 0)
          sb.append(' ').append(surface).append('=').append(Math.round(m));
      }
      sb.append("; steep stretches: ").append(details.steepGrade.length).append("; stops:");
      for (RouteDetails.Stop stop : details.stops)
        sb.append(' ').append(stop.type).append('@').append(Math.round(stop.alongM)).append("m(")
            .append(Math.round(stop.offsetM)).append("m off)");
      Logger.i(TAG, sb.toString());
    });
  }

  public void updateBuildProgress(int progress, @NonNull Router router)
  {
    UiUtils.invisible(mProgressVehicle, mProgressPedestrian, mProgressTransit, mProgressBicycle, mProgressRuler);
    WheelProgressView progressView = switch (router)
    {
      case Vehicle ->
      {
        mRouterTypes.check(R.id.vehicle);
        yield mProgressVehicle;
      }
      case Pedestrian ->
      {
        mRouterTypes.check(R.id.pedestrian);
        yield mProgressPedestrian;
      }
      case Transit ->
      {
        mRouterTypes.check(R.id.transit);
        yield mProgressTransit;
      }
      case Bicycle ->
      {
        mRouterTypes.check(R.id.bicycle);
        yield mProgressBicycle;
      }
      case Ruler ->
      {
        mRouterTypes.check(R.id.ruler);
        yield mProgressRuler;
      }
      default -> throw new IllegalArgumentException("unknown router: " + router);
    };

    RoutingToolbarButton button = mRouterTypes.findViewById(mRouterTypes.getCheckedRadioButtonId());
    button.progress();

    updateProgressLabels();
    updatePoints();
    updateRouteOptionsSummary();
    if (router != mOptionsDropdownRouter)
      closeRouteOptions();

    if (!RoutingController.get().isBuilding())
    {
      button.complete();
      return;
    }

    UiUtils.show(progressView);
    progressView.setPending(progress == 0);
    if (progress != 0)
      progressView.setProgress(progress);
  }

  public void onTurnPreviewChanged(boolean isPreviewing)
  {
    boolean wasPreviewing = mIsPreviewingTurn;
    mIsPreviewingTurn = isPreviewing;

    if (isPreviewing)
      mRoutingBottomMenuController.hideAltitudeChartAndRoutingDetails();
    else if (wasPreviewing)
      updateProgressLabels();
  }

  private boolean isTransitType()
  {
    return RoutingController.get().isTransitType();
  }

  private boolean isRulerType()
  {
    return RoutingController.get().isRulerRouterType();
  }

  void saveRoutingPanelState(@NonNull Bundle outState)
  {
    mRoutingBottomMenuController.saveRoutingPanelState(outState);
    outState.putBoolean(BUNDLE_HAS_DRIVING_OPTIONS_VIEW, UiUtils.isVisible(mRoutingOptionsBanner));
  }

  void restoreRoutingPanelState(@NonNull Bundle state)
  {
    if (mIsPreviewingTurn)
      mRoutingBottomMenuController.hideAltitudeChartAndRoutingDetails();
    else
      mRoutingBottomMenuController.restoreRoutingPanelState(state);

    boolean hasView = state.getBoolean(BUNDLE_HAS_DRIVING_OPTIONS_VIEW);
    if (hasView)
      showRoutingOptionsView();
  }

  public void showAddStartFrame()
  {
    mRoutingBottomMenuController.showAddStartFrame();
  }

  public void showAddFinishFrame()
  {
    mRoutingBottomMenuController.showAddFinishFrame();
  }

  private void updatePoints()
  {
    mRouteFrom.setText(pointName(RoutingController.get().getStartPoint()));
    mRouteTo.setText(pointName(RoutingController.get().getEndPoint()));
  }

  @NonNull
  private String pointName(@Nullable MapObject point)
  {
    final Context context = mFrame.getContext();
    if (point == null)
      return "…";
    if (point.isMyPosition())
      return context.getString(app.organicmaps.sdk.R.string.core_my_position);
    final String title = point.getTitle();
    return title.isEmpty() ? context.getString(app.organicmaps.sdk.R.string.core_my_position) : title;
  }

  /// "Avoiding: Unpaved, Stairs +1" on one line, whatever the number of options.
  private void updateRouteOptionsSummary()
  {
    final Router router = RoutingController.get().getLastRouterType();
    final boolean hasOptions = MapModeUi.newRouteOptionsFragment(modeOf(router)) != null;
    UiUtils.showIf(hasOptions, mAvoidRow);
    if (!hasOptions)
      return;
    final Context context = mFrame.getContext();
    final List<String> names = new ArrayList<>();
    final RoadType[] order = {RoadType.Dirty, RoadType.Paved, RoadType.Steps, RoadType.Ferry, RoadType.Toll,
                              RoadType.Motorway};
    for (RoadType type : order)
    {
      if (RoutingOptions.hasOption(type, router))
        names.add(context.getString(shortName(type)));
    }
    // Nothing avoided: a quiet link to the options instead of "Avoiding: nothing".
    final boolean quiet = names.isEmpty();
    UiUtils.showIf(!quiet, mAvoidLabel);
    mAvoidRow.setMinimumHeight(quiet ? 0 : mAvoidRowMinHeight);
    mAvoidRow.setPadding(mAvoidRow.getPaddingStart(), quiet ? 0 : mAvoidRowPadding, mAvoidRow.getPaddingEnd(),
                         mAvoidRowPadding);
    final String summary;
    if (names.isEmpty())
      summary = context.getString(R.string.route_options_link);
    else if (names.size() <= 2)
      summary = String.join(", ", names);
    else
      summary = String.join(", ", names.subList(0, 2)) + " +" + (names.size() - 2);
    mAvoidValue.setText(summary);
    mAvoidValue.setTextColor(quiet ? mAvoidLabel.getTextColors() : mAvoidValueColors);
  }

  @StringRes
  private static int shortName(@NonNull RoadType type)
  {
    return switch (type)
    {
      case Paved -> R.string.route_avoid_paved;
      case Steps -> R.string.route_avoid_stairs;
      case Ferry -> R.string.route_avoid_ferries;
      case Toll -> R.string.route_avoid_tolls;
      case Motorway -> R.string.route_avoid_freeways;
      default -> R.string.route_avoid_unpaved;
    };
  }

  @NonNull
  private static MapMode modeOf(@NonNull Router router)
  {
    for (MapMode mode : MapMode.values())
    {
      if (mode.getRouter() == router)
        return mode;
    }
    return MapMode.Default;
  }

  /// Shows the route options of the current mode under the card: the same screens as the route settings.
  public void openRouteOptions()
  {
    final Router router = RoutingController.get().getLastRouterType();
    final Fragment options = MapModeUi.newRouteOptionsFragment(modeOf(router));
    if (options == null || !(requireActivity() instanceof FragmentActivity activity))
      return;
    mOptionsDropdownRouter = router;
    mOptionsAtOpen = RoutingOptions.getActiveRoadTypes(router);
    activity.getSupportFragmentManager().beginTransaction()
        .replace(R.id.route_options_dropdown_content, options)
        .commitNow();
    UiUtils.show(mOptionsDropdown);
    mAvoidChevron.setRotation(180);
  }

  /// Closes the route options and recalculates the route if they changed.
  /// @return true if they were open.
  public boolean closeRouteOptions()
  {
    if (!UiUtils.isVisible(mOptionsDropdown))
      return false;
    UiUtils.hide(mOptionsDropdown);
    mAvoidChevron.setRotation(0);
    if (requireActivity() instanceof FragmentActivity activity)
    {
      final Fragment current = activity.getSupportFragmentManager().findFragmentById(R.id.route_options_dropdown_content);
      if (current != null)
        activity.getSupportFragmentManager().beginTransaction().remove(current).commitNow();
    }
    final Router router = mOptionsDropdownRouter;
    mOptionsDropdownRouter = null;
    if (router != null && router == RoutingController.get().getLastRouterType()
        && !RoutingOptions.getActiveRoadTypes(router).equals(mOptionsAtOpen))
      RoutingController.get().rebuildLastRoute();
    else
      updateRouteOptionsSummary();
    return true;
  }

  /// Called when route options are on: the summary already shows them.
  public void showRoutingOptionsView()
  {
    updateRouteOptionsSummary();
  }

  /// Called when a new route starts building.
  public void hideRoutingOptionsView()
  {
    updateRouteOptionsSummary();
    mRoutingPlanListener.onRoutingPlanStartAnimate(UiUtils.isVisible(getFrame()));
  }

  public int calcHeight()
  {
    int frameHeight = getFrame().getHeight();
    if (frameHeight == 0)
      return 0;

    View driverOptionsView = getRoutingOptionsBtnContainer();
    int extraOppositeOffset = UiUtils.isVisible(driverOptionsView) ? 0 : driverOptionsView.getHeight();

    // The route options drop over the map; they don't move the visible part of it.
    final int dropdownHeight = UiUtils.isVisible(mOptionsDropdown) ? mOptionsDropdown.getHeight() : 0;
    return frameHeight - extraOppositeOffset - dropdownHeight;
  }

  private class SelfTerminatedRoutingOptionsLayoutListener implements View.OnLayoutChangeListener
  {
    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight,
                               int oldBottom)
    {
      mRoutingPlanListener.onRoutingPlanStartAnimate(UiUtils.isVisible(getFrame()));
      mRoutingOptionsBanner.removeOnLayoutChangeListener(this);
    }
  }
}
