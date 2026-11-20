package app.organicmaps.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.maplayer.isolines.IsolinesManager;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.util.Config;

public enum MapMode
{
  Walking(0, Router.Pedestrian),
  Cycling(1, Router.Bicycle),
  Driving(2, Router.Vehicle),
  PublicTransport(3, Router.Transit),
  Default(4, null);

  /// Layers that each map mode remembers on its own.
  private static final String LAYER_OUTDOOR = "Outdoor";
  private static final String LAYER_CONTOUR_LINES = "ContourLines";
  private static final String LAYER_BUILDINGS_3D = "Buildings3d";

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
    {
      switchTo(Walking);
      return;
    }
    final RoutingController routing = RoutingController.get();
    final Router router = get().router;
    if (router != null && !routing.isPlanning() && !routing.isNavigating())
      routing.setRouterType(router);
  }

  /// Switches the map style, the layers remembered for the mode and, outside route planning, the router.
  public static void switchTo(@NonNull MapMode mapMode)
  {
    final MapMode current = get();
    if (mapMode == current)
      return;
    if (current != Default)
      rememberLayers(current);
    applyLayers(mapMode);
    set(mapMode);

    final RoutingController routing = RoutingController.get();
    if (mapMode.router != null && !routing.isPlanning() && !routing.isNavigating())
      routing.setRouterType(mapMode.router);
  }

  /// Picks the mode that matches a router chosen while planning a route. The ruler keeps the current mode.
  public static void followRouter(@NonNull Router router)
  {
    for (MapMode mode : values())
    {
      if (mode.router == router)
      {
        switchTo(mode);
        return;
      }
    }
  }

  /// Call after the user changes a layer, so the current mode keeps it.
  public static void rememberLayers()
  {
    final MapMode current = get();
    if (current != Default)
      rememberLayers(current);
  }

  private static void rememberLayers(@NonNull MapMode mode)
  {
    Config.setMapModeLayer(mode.name(), LAYER_OUTDOOR, Framework.nativeIsOutdoorsLayerEnabled());
    Config.setMapModeLayer(mode.name(), LAYER_CONTOUR_LINES, IsolinesManager.isEnabled());
    Config.setMapModeLayer(mode.name(), LAYER_BUILDINGS_3D, isBuildings3dEnabled());
  }

  /// A mode that was never used starts with the layers the map has now.
  private static void applyLayers(@NonNull MapMode mode)
  {
    if (mode == Default)
      return;
    final Framework.Params3dMode params3d = new Framework.Params3dMode();
    Framework.nativeGet3dMode(params3d);

    Framework.nativeSetOutdoorsLayerEnabled(
        Config.getMapModeLayer(mode.name(), LAYER_OUTDOOR, Framework.nativeIsOutdoorsLayerEnabled()));
    IsolinesManager.setEnabled(
        Config.getMapModeLayer(mode.name(), LAYER_CONTOUR_LINES, IsolinesManager.isEnabled()));
    final boolean buildings3d = Config.getMapModeLayer(mode.name(), LAYER_BUILDINGS_3D, params3d.buildings);
    if (buildings3d != params3d.buildings)
      Framework.nativeSet3dMode(params3d.enabled, buildings3d);
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

  /// The router used to plan routes in this mode, null for Default.
  @Nullable
  public Router getRouter()
  {
    return router;
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
