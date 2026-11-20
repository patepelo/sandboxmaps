package app.organicmaps.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.routing.RoutingController;

public enum MapMode
{
  Walking(0, Router.Pedestrian),
  Cycling(1, Router.Bicycle),
  Driving(2, Router.Vehicle),
  PublicTransport(3, Router.Transit),
  Default(4, null);

  MapMode(int value, @Nullable Router router)
  {
    this.value = value;
    this.router = router;
  }

  @NonNull
  public static MapMode get()
  {
    return valueOf(nativeGet());
  }

  public static void set(@NonNull MapMode mapMode)
  {
    nativeSet(mapMode.value);
  }

  /// The mode the user picked; Walking until they pick one.
  @NonNull
  public static MapMode getChosen()
  {
    final MapMode mode = get();
    return mode == Default ? Walking : mode;
  }

  /// On start: picks Walking the first time, and makes new routes use the mode's router.
  public static void initialize()
  {
    if (get() == Default)
      switchTo(Walking);
    else
      applyRouter(get());
  }

  /// Switches the map style and, outside route planning, the router. Layers are the same for all modes.
  public static void switchTo(@NonNull MapMode mapMode)
  {
    final MapMode current = get();
    if (mapMode == current)
      return;
    set(mapMode);
    applyRouter(mapMode);
  }

  /// New routes are planned with the mode's router; a route being planned or followed keeps its own.
  private static void applyRouter(@NonNull MapMode mapMode)
  {
    final RoutingController routing = RoutingController.get();
    if (mapMode.router != null && !routing.isPlanning() && !routing.isNavigating())
      routing.setRouterType(mapMode.router);
  }

  /// Picks the mode that matches a router chosen while planning a route. The ruler keeps the current mode.
  public static void followRouter(@NonNull Router router)
  {
    final MapMode mode = forRouter(router);
    if (mode != null)
      switchTo(mode);
  }

  /// The mode that plans routes with this router, or null for the ruler.
  @Nullable
  public static MapMode forRouter(@NonNull Router router)
  {
    for (MapMode mode : values())
    {
      if (mode.router == router)
        return mode;
    }
    return null;
  }

  public static boolean isBuildings3dEnabled()
  {
    final Framework.Params3dMode params3d = new Framework.Params3dMode();
    Framework.nativeGet3dMode(params3d);
    return params3d.buildings;
  }

  public static void setBuildings3dEnabled(boolean enabled)
  {
    final Framework.Params3dMode params3d = new Framework.Params3dMode();
    Framework.nativeGet3dMode(params3d);
    Framework.nativeSet3dMode(params3d.enabled, enabled);
  }

  @NonNull
  public static MapMode valueOf(int value)
  {
    for (MapMode mapMode : MapMode.values())
    {
      if (mapMode.value == value)
        return mapMode;
    }
    throw new IllegalArgumentException("Unknown map mode value: " + value);
  }

  private final int value;
  @Nullable
  private final Router router;

  private static native void nativeSet(int mapMode);

  private static native int nativeGet();
}
