#pragma once

#include "indexer/feature_decl.hpp"

#include "geometry/point2d.hpp"
#include "geometry/point_with_altitude.hpp"

#include <cstdint>
#include <string>
#include <vector>

class DataSource;

/// What a route is made of, for the route details panel: the kind of way and surface along it,
/// its steep stretches and useful stops close to it.
namespace route_details
{
/// Steepness (rise over run) from which a stretch counts as steep, in both directions.
double constexpr kSteepGrade = 0.08;
/// How far from the route a stop may be.
double constexpr kStopMaxOffsetM = 150.0;

enum class WayType : uint8_t
{
  Quiet,     // residential, living street, service
  Minor,     // tertiary, unclassified, road
  Main,      // secondary
  Busy,      // primary, trunk, motorway and their links
  Cycleway,
  Track,
  Path,      // path, bridleway
  Footway,   // footway, pedestrian
  Steps,
  Ferry,
  Other
};

enum class Surface : uint8_t
{
  Paved,
  PavedBad,
  Unpaved,
  UnpavedBad,
  Unknown
};

enum class StopType : uint8_t
{
  DrinkingWater,
  Toilets,
  BicycleRepair,
  Shelter,
  Viewpoint,
  TrainStation
};

/// A run of the route with the same way type and surface, in meters from its start.
struct Stretch
{
  double m_startM = 0;
  double m_endM = 0;
  WayType m_way = WayType::Other;
  Surface m_surface = Surface::Unknown;
  /// Cycling routes only: bikes aren't allowed here, so it's walked.
  bool m_walkBike = false;
};

struct SteepStretch
{
  double m_startM = 0;
  double m_endM = 0;
  /// Steepest grade along it: positive uphill, negative downhill.
  double m_maxGrade = 0;
};

struct Stop
{
  StopType m_type = StopType::DrinkingWater;
  std::string m_name;
  m2::PointD m_point;
  /// Where along the route it is, and how far from it.
  double m_alongM = 0;
  double m_offsetM = 0;
};

struct RouteDetails
{
  double m_totalM = 0;
  /// Elevation profile: distance from the start and altitude of each route point. Empty without altitudes.
  std::vector<double> m_profileDistM;
  geometry::Altitudes m_profileAltM;
  std::vector<Stretch> m_stretches;
  std::vector<SteepStretch> m_steep;
  std::vector<Stop> m_stops;
};

/// The end of one route segment: the road it's on, where it is and how far from the start.
struct RoutePoint
{
  FeatureID m_featureId;
  m2::PointD m_point;
  double m_distM = 0;
};

/// What's needed of the route, copied on the routing thread so the details can be computed on another one.
struct RouteInput
{
  std::vector<RoutePoint> m_points;
  m2::PointD m_start;
  /// From RoutingSession::GetRouteAltitudesAndDistancesM(); empty when the route has no altitudes.
  std::vector<double> m_distancesM;
  geometry::Altitudes m_altitudesM;
  bool m_bicycle = false;
};

/// Reads the map features along the route. Safe to call on a background thread.
RouteDetails Compute(DataSource const & dataSource, RouteInput const & input);
}  // namespace route_details
