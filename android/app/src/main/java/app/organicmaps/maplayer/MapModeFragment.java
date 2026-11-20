package app.organicmaps.maplayer;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import app.organicmaps.R;
import app.organicmaps.sdk.MapMode;
import app.organicmaps.sdk.Router;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingOptions;
import app.organicmaps.sdk.settings.RoadType;
import app.organicmaps.settings.CyclingOptionsFragment;
import app.organicmaps.settings.DrivingOptionsFragment;
import app.organicmaps.settings.WalkingOptionsFragment;
import app.organicmaps.util.ThemeUtils;
import app.organicmaps.util.bottomsheet.MenuBottomSheetFragment;
import com.google.android.material.card.MaterialCardView;
import java.util.Collections;
import java.util.Set;

/// Options of the current map mode: switch to another mode and its route options. Layers live in the layers sheet.
public class MapModeFragment extends Fragment
{
  public static final String MENU_ID = "MAP_MODE_BOTTOM_SHEET";

  private View mRoot;
  /// Route options when the sheet opened, to recalculate a planned route only if they changed.
  @Nullable
  private Router mRouterAtOpen;
  @NonNull
  private Set<RoadType> mOptionsAtOpen = Collections.emptySet();

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    mRoot = inflater.inflate(R.layout.fragment_map_mode, container, false);
    mRoot.findViewById(R.id.close_button).setOnClickListener(v -> close());
    mRouterAtOpen = RoutingController.get().getLastRouterType();
    mOptionsAtOpen = RoutingOptions.getActiveRoadTypes(mRouterAtOpen);
    return mRoot;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    render();
  }

  private void render()
  {
    final Context context = requireContext();
    final MapMode mode = MapMode.getChosen();
    final int color = MapModeUi.color(context, mode);

    mRoot.findViewById(R.id.mode_dot).setBackgroundTintList(ColorStateList.valueOf(color));
    ((ImageView) mRoot.findViewById(R.id.mode_dot_icon)).setImageResource(MapModeUi.icon(mode));
    ((TextView) mRoot.findViewById(R.id.mode_title)).setText(MapModeUi.name(mode));

    renderModes(context, mode);
    renderRouteOptions(mode);
  }

  private void renderModes(@NonNull Context context, @NonNull MapMode current)
  {
    final LinearLayout row = mRoot.findViewById(R.id.modes_row);
    row.removeAllViews();
    final LayoutInflater inflater = LayoutInflater.from(context);
    final int iconTint = ThemeUtils.getColor(context, R.attr.iconTint);
    for (MapMode mode : MapModeUi.MODES)
    {
      final MaterialCardView card = (MaterialCardView) inflater.inflate(R.layout.item_map_mode_choice, row, false);
      final ImageView icon = card.findViewById(R.id.icon);
      final TextView label = card.findViewById(R.id.label);
      icon.setImageResource(MapModeUi.icon(mode));
      label.setText(MapModeUi.name(mode));
      final boolean selected = mode == current;
      card.setSelected(selected);
      if (selected)
      {
        card.setCardBackgroundColor(MapModeUi.color(context, mode));
        card.setStrokeWidth(0);
        label.setTextColor(Color.WHITE);
      }
      ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(selected ? Color.WHITE : iconTint));
      card.setOnClickListener(v -> {
        final RoutingController routing = RoutingController.get();
        if (routing.isPlanning() && mode.getRouter() != null)
          routing.setRouterType(mode.getRouter()); // Recalculates the route; the map mode follows.
        else
          MapModeUi.switchTo(mode, mRoot, null);
        refreshMapButtons();
        render();
      });
      row.addView(card);
    }
  }

  /// Shows the same route option screen as the route settings, for the current mode.
  private void renderRouteOptions(@NonNull MapMode mode)
  {
    final Fragment options = switch (mode)
    {
      case Walking -> new WalkingOptionsFragment();
      case Cycling -> new CyclingOptionsFragment();
      case Driving -> new DrivingOptionsFragment();
      default -> null;
    };
    ((TextView) mRoot.findViewById(R.id.route_options_note))
        .setText(options == null ? R.string.map_mode_no_route_options : R.string.map_mode_route_options_note);
    final FragmentManager fm = getChildFragmentManager();
    final Fragment current = fm.findFragmentById(R.id.route_options);
    if (options != null)
      fm.beginTransaction().replace(R.id.route_options, options).commitNow();
    else if (current != null)
      fm.beginTransaction().remove(current).commitNow();
  }

  @Override
  public void onDestroyView()
  {
    super.onDestroyView();
    final RoutingController routing = RoutingController.get();
    final Router router = routing.getLastRouterType();
    if (routing.isPlanning() && router == mRouterAtOpen
        && !RoutingOptions.getActiveRoadTypes(router).equals(mOptionsAtOpen))
      routing.rebuildLastRoute();
  }

  private void refreshMapButtons()
  {
    final Fragment buttons = requireActivity().getSupportFragmentManager().findFragmentById(R.id.map_buttons);
    if (buttons instanceof MapButtonsController controller)
    {
      controller.updateLayerButton();
      controller.updateModePicker();
    }
  }

  private void close()
  {
    final Fragment sheet = requireActivity().getSupportFragmentManager().findFragmentByTag(MENU_ID);
    if (sheet instanceof MenuBottomSheetFragment bottomSheet)
      bottomSheet.dismiss();
  }
}
