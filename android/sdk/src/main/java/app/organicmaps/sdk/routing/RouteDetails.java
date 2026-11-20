package app.organicmaps.sdk.routing;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/// What the current route is made of: kinds of way and surface along it, steep stretches and stops near it.
/// Distances are in meters from the start. Filled by the core (map/route_details.hpp).
// Used by JNI.
@Keep
public final class RouteDetails
{
  /// Same order as route_details::WayType.
  public enum WayType
  {
    QUIET,
    MINOR,
    MAIN,
    BUSY,
    CYCLEWAY,
    TRACK,
    PATH,
    FOOTWAY,
    STEPS,
    FERRY,
    OTHER
  }

  /// Same order as route_details::Surface.
  public enum Surface
  {
    PAVED,
    PAVED_BAD,
    UNPAVED,
    UNPAVED_BAD,
    UNKNOWN
  }

  /// Same order as route_details::StopType.
  public enum StopType
  {
    DRINKING_WATER,
    TOILETS,
    BICYCLE_REPAIR,
    SHELTER,
    VIEWPOINT,
    TRAIN_STATION
  }

  /// Steepness from which a stretch counts as steep (route_details::kSteepGrade).
  public static final double STEEP_GRADE = 0.08;

  public interface Listener
  {
    void onRouteDetails(@NonNull RouteDetails details);
  }

  @Keep
  public static final class Stop
  {
    @NonNull
    public final StopType type;
    @NonNull
    public final String name;
    public final double lat;
    public final double lon;
    public final double alongM;
    public final double offsetM;

    // Used by JNI.
    Stop(int type, @Nullable String name, double lat, double lon, double alongM, double offsetM)
    {
      this.type = StopType.values()[type];
      this.name = name == null ? "" : name;
      this.lat = lat;
      this.lon = lon;
      this.alongM = alongM;
      this.offsetM = offsetM;
    }
  }

  public final double totalM;
  @NonNull
  public final double[] stretchStartM;
  @NonNull
  public final double[] stretchEndM;
  @NonNull
  private final int[] mStretchWay;
  @NonNull
  private final int[] mStretchSurface;
  @NonNull
  private final int[] mStretchWalkBike;
  @NonNull
  public final double[] steepStartM;
  @NonNull
  public final double[] steepEndM;
  /// Positive uphill, negative downhill.
  @NonNull
  public final double[] steepGrade;
  @NonNull
  public final Stop[] stops;

  // Used by JNI.
  RouteDetails(double totalM, @NonNull double[] stretchStartM, @NonNull double[] stretchEndM, @NonNull int[] stretchWay,
               @NonNull int[] stretchSurface, @NonNull int[] stretchWalkBike, @NonNull double[] steepStartM,
               @NonNull double[] steepEndM, @NonNull double[] steepGrade, @NonNull Stop[] stops)
  {
    this.totalM = totalM;
    this.stretchStartM = stretchStartM;
    this.stretchEndM = stretchEndM;
    mStretchWay = stretchWay;
    mStretchSurface = stretchSurface;
    mStretchWalkBike = stretchWalkBike;
    this.steepStartM = steepStartM;
    this.steepEndM = steepEndM;
    this.steepGrade = steepGrade;
    this.stops = stops;
  }

  public boolean isEmpty()
  {
    return stretchStartM.length == 0;
  }

  public int getStretchCount()
  {
    return stretchStartM.length;
  }

  @NonNull
  public WayType getWayType(int stretch)
  {
    return WayType.values()[mStretchWay[stretch]];
  }

  @NonNull
  public Surface getSurface(int stretch)
  {
    return Surface.values()[mStretchSurface[stretch]];
  }

  /// Cycling routes only: bikes aren't allowed on this stretch, so it's walked.
  public boolean isWalkBike(int stretch)
  {
    return mStretchWalkBike[stretch] != 0;
  }

  /// Meters of the route with this way type.
  public double getWayTypeMeters(@NonNull WayType type)
  {
    double meters = 0;
    for (int i = 0; i < getStretchCount(); ++i)
      if (getWayType(i) == type)
        meters += stretchEndM[i] - stretchStartM[i];
    return meters;
  }

  /// Meters of the route with this surface.
  public double getSurfaceMeters(@NonNull Surface surface)
  {
    double meters = 0;
    for (int i = 0; i < getStretchCount(); ++i)
      if (getSurface(i) == surface)
        meters += stretchEndM[i] - stretchStartM[i];
    return meters;
  }

  /// Works out the details of the current route in the background; the listener is called on the UI thread.
  public static void request(@NonNull Listener listener)
  {
    nativeRequest(listener);
  }

  private static native void nativeRequest(@NonNull Listener listener);
}
