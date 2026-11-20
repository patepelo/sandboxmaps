package app.organicmaps.maplayer;

import android.content.Context;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import app.organicmaps.R;
import app.organicmaps.sdk.MapMode;
import app.organicmaps.settings.CyclingOptionsFragment;
import app.organicmaps.settings.DrivingOptionsFragment;
import app.organicmaps.settings.WalkingOptionsFragment;

/// How each map mode looks in the picker, and its route options.
public final class MapModeUi
{
  public static final MapMode[] MODES = {MapMode.Walking, MapMode.Cycling, MapMode.Driving, MapMode.PublicTransport};

  private MapModeUi() {}

  public static boolean hasRouteOptions(@Nullable MapMode mode)
  {
    return mode == MapMode.Walking || mode == MapMode.Cycling || mode == MapMode.Driving;
  }

  /// Shows the route option screen of the route settings for this mode in a container.
  /// @return false if the mode has no route options (and the container is left empty).
  public static boolean showRouteOptions(@NonNull FragmentManager fm, @IdRes int container, @NonNull MapMode mode)
  {
    final Fragment options = switch (mode)
    {
      case Walking -> new WalkingOptionsFragment();
      case Cycling -> new CyclingOptionsFragment();
      case Driving -> new DrivingOptionsFragment();
      default -> null;
    };
    if (options == null)
    {
      removeRouteOptions(fm, container);
      return false;
    }
    fm.beginTransaction().replace(container, options).commitNow();
    return true;
  }

  public static void removeRouteOptions(@NonNull FragmentManager fm, @IdRes int container)
  {
    final Fragment current = fm.findFragmentById(container);
    if (current != null)
      fm.beginTransaction().remove(current).commitNow();
  }

  @StringRes
  public static int name(@NonNull MapMode mode)
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
  public static int icon(@NonNull MapMode mode)
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
  public static int color(@NonNull Context context, @NonNull MapMode mode)
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
