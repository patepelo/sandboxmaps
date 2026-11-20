package app.organicmaps.maplayer;

import android.content.Context;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.MapMode;
import app.organicmaps.sdk.maplayer.isolines.IsolinesManager;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;

/// How each map mode looks in the picker and its options sheet.
final class MapModeUi
{
  static final MapMode[] MODES = {MapMode.Walking, MapMode.Cycling, MapMode.Driving, MapMode.PublicTransport};

  private MapModeUi() {}

  /// Switches the mode and, when that changes the layers the mode remembers, says which ones.
  static void switchTo(@NonNull MapMode mode, @NonNull View view, @Nullable View anchor)
  {
    final boolean outdoor = Framework.nativeIsOutdoorsLayerEnabled();
    final boolean contourLines = IsolinesManager.isEnabled();
    final boolean buildings3d = MapMode.isBuildings3dEnabled();
    MapMode.switchTo(mode);

    final Context context = view.getContext();
    final List<String> changes = new ArrayList<>();
    addChange(context, changes, R.string.button_layer_outdoor, outdoor, Framework.nativeIsOutdoorsLayerEnabled());
    addChange(context, changes, R.string.button_layer_isolines, contourLines, IsolinesManager.isEnabled());
    addChange(context, changes, R.string.pref_map_3d_buildings_title, buildings3d, MapMode.isBuildings3dEnabled());
    if (changes.isEmpty())
      return;
    final String message = context.getString(name(mode)) + " · " + String.join(", ", changes);
    final Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
    if (anchor != null)
      snackbar.setAnchorView(anchor);
    snackbar.show();
  }

  private static void addChange(@NonNull Context context, @NonNull List<String> changes, @StringRes int layer,
                                boolean before, boolean after)
  {
    if (before != after)
      changes.add(context.getString(after ? R.string.map_mode_layer_on : R.string.map_mode_layer_off,
                                    context.getString(layer)));
  }

  @StringRes
  static int name(@NonNull MapMode mode)
  {
    return switch (mode)
    {
      case Cycling -> R.string.mode_cycling;
      case Driving -> R.string.mode_driving;
      case PublicTransport -> R.string.mode_public_transport;
      default -> R.string.mode_walking;
    };
  }

  @DrawableRes
  static int icon(@NonNull MapMode mode)
  {
    return switch (mode)
    {
      case Cycling -> R.drawable.ic_bike;
      case Driving -> R.drawable.ic_car;
      case PublicTransport -> app.organicmaps.sdk.R.drawable.ic_route_planning_metro_40px;
      default -> R.drawable.ic_pedestrian;
    };
  }

  @ColorInt
  static int color(@NonNull Context context, @NonNull MapMode mode)
  {
    return ContextCompat.getColor(context, switch (mode)
    {
      case Cycling -> R.color.mode_cycling;
      case Driving -> R.color.mode_driving;
      case PublicTransport -> R.color.mode_public_transport;
      default -> R.color.mode_walking;
    });
  }
}
