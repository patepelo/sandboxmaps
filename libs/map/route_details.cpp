#include "map/route_details.hpp"

#include "indexer/classificator.hpp"
#include "indexer/data_source.hpp"
#include "indexer/feature.hpp"
#include "indexer/feature_algo.hpp"
#include "indexer/feature_data.hpp"
#include "indexer/scales.hpp"

#include "geometry/mercator.hpp"
#include "geometry/parametrized_segment.hpp"

#include "i18n/localisation.hpp"

#include <algorithm>
#include <cmath>
#include <map>
#include <memory>
#include <unordered_map>

namespace route_details
{
namespace
{
/// Steepness is measured over this distance, so single noisy altitudes don't count.
double constexpr kGradeWindowM = 50.0;
/// The route is searched for stops in pieces of about this length.
double constexpr kStopChunkM = 1500.0;
size_t constexpr kMaxStops = 1000;

/// Maps classificator types to what the panel shows.
class Types
{
public:
  static Types const & Instance()
  {
    static Types const types;
    return types;
  }

  /// Way type, surface and "no bicycles" of a road feature.
  void ClassifyRoad(FeatureType & ft, bool bicycle, Stretch & out) const
  {
    out.m_way = WayType::Other;
    out.m_surface = Surface::Unknown;
    bool noBicycle = false;
    bool yesBicycle = false;
    for (uint32_t t : feature::TypesHolder(ft))
    {
      ftype::TruncValue(t, 2);
      if (auto const it = m_ways.find(t); it != m_ways.end() && out.m_way == WayType::Other)
        out.m_way = it->second;
      else if (auto const it = m_surfaces.find(t); it != m_surfaces.end())
        out.m_surface = it->second;
      else if (t == m_noBicycle)
        noBicycle = true;
      else if (t == m_yesBicycle)
        yesBicycle = true;
    }
    bool const forPedestrians =
        out.m_way == WayType::Footway || out.m_way == WayType::Steps;
    out.m_walkBike = bicycle && (noBicycle || (forPedestrians && !yesBicycle));
  }

  bool GetStopType(FeatureType & ft, StopType & out) const
  {
    feature::TypesHolder const types(ft);
    // Bus stop shelters aren't a place to stop on a walk or ride.
    if (types.Has(m_busShelter))
      return false;
    for (uint32_t t : types)
    {
      ftype::TruncValue(t, 2);
      if (auto const it = m_stops.find(t); it != m_stops.end())
      {
        out = it->second;
        return true;
      }
    }
    return false;
  }

private:
  Types()
  {
    auto const & c = classif();
    auto const type = [&c](char const * a, char const * b) { return c.GetTypeByPath({a, b}); };

    for (auto const * w : {"residential", "living_street", "service"})
      m_ways[type("highway", w)] = WayType::Quiet;
    for (auto const * w : {"tertiary", "tertiary_link", "unclassified", "road"})
      m_ways[type("highway", w)] = WayType::Minor;
    for (auto const * w : {"secondary", "secondary_link"})
      m_ways[type("highway", w)] = WayType::Main;
    for (auto const * w : {"primary", "primary_link", "trunk", "trunk_link", "motorway", "motorway_link"})
      m_ways[type("highway", w)] = WayType::Busy;
    m_ways[type("highway", "cycleway")] = WayType::Cycleway;
    m_ways[type("highway", "track")] = WayType::Track;
    m_ways[type("highway", "path")] = WayType::Path;
    m_ways[type("highway", "bridleway")] = WayType::Path;
    m_ways[type("highway", "footway")] = WayType::Footway;
    m_ways[type("highway", "pedestrian")] = WayType::Footway;
    m_ways[type("highway", "steps")] = WayType::Steps;
    m_ways[type("route", "ferry")] = WayType::Ferry;

    m_surfaces[type("psurface", "paved_good")] = Surface::Paved;
    m_surfaces[type("psurface", "paved_bad")] = Surface::PavedBad;
    m_surfaces[type("psurface", "unpaved_good")] = Surface::Unpaved;
    m_surfaces[type("psurface", "unpaved_bad")] = Surface::UnpavedBad;

    m_noBicycle = type("hwtag", "nobicycle");
    m_yesBicycle = type("hwtag", "yesbicycle");

    m_stops[type("amenity", "drinking_water")] = StopType::DrinkingWater;
    m_stops[type("amenity", "toilets")] = StopType::Toilets;
    m_stops[type("amenity", "bicycle_repair_station")] = StopType::BicycleRepair;
    m_stops[type("amenity", "shelter")] = StopType::Shelter;
    m_busShelter = c.GetTypeByPath({"amenity", "shelter", "public_transport"});
    m_stops[type("tourism", "viewpoint")] = StopType::Viewpoint;
    m_stops[type("railway", "station")] = StopType::TrainStation;
    m_stops[type("railway", "halt")] = StopType::TrainStation;
  }

  std::unordered_map<uint32_t, WayType> m_ways;
  std::unordered_map<uint32_t, Surface> m_surfaces;
  std::unordered_map<uint32_t, StopType> m_stops;
  uint32_t m_busShelter = 0;
  uint32_t m_noBicycle = 0;
  uint32_t m_yesBicycle = 0;
};

/// Reads features by id, keeping one loader per map file.
class FeatureReader
{
public:
  explicit FeatureReader(DataSource const & dataSource) : m_dataSource(dataSource) {}

  std::unique_ptr<FeatureType> Read(FeatureID const & id)
  {
    if (!id.IsValid() || !id.m_mwmId.IsAlive())
      return nullptr;
    auto it = m_guards.find(id.m_mwmId);
    if (it == m_guards.end())
      it = m_guards.emplace(id.m_mwmId, std::make_unique<FeaturesLoaderGuard>(m_dataSource, id.m_mwmId)).first;
    return it->second->GetFeatureByIndex(id.m_index);
  }

private:
  DataSource const & m_dataSource;
  std::map<MwmSet::MwmId, std::unique_ptr<FeaturesLoaderGuard>> m_guards;
};

bool SameKind(Stretch const & a, Stretch const & b)
{
  return a.m_way == b.m_way && a.m_surface == b.m_surface && a.m_walkBike == b.m_walkBike;
}

void CollectStretches(FeatureReader & reader, RouteInput const & input, RouteDetails & out)
{
  auto const & types = Types::Instance();
  FeatureID lastId;
  Stretch lastKind;
  bool hasKind = false;
  double startM = 0;
  for (auto const & s : input.m_segments)
  {
    double const endM = s.GetDistFromBeginningMeters();
    FeatureID const & id = s.GetRoadNameInfo().m_mwmId;
    Stretch kind;
    if (hasKind && id == lastId)
      kind = lastKind;
    else if (auto ft = reader.Read(id))
      types.ClassifyRoad(*ft, input.m_bicycle, kind);
    else if (hasKind)
      kind = lastKind;  // Short fake segments at the start and end continue the road next to them.
    lastId = id;
    lastKind = kind;
    hasKind = true;

    if (!out.m_stretches.empty() && SameKind(out.m_stretches.back(), kind))
      out.m_stretches.back().m_endM = endM;
    else if (endM > startM)
    {
      kind.m_startM = startM;
      kind.m_endM = endM;
      out.m_stretches.push_back(kind);
    }
    startM = endM;
  }
  // A fake first segment had nothing before it to continue: give it the next road's kind.
  if (out.m_stretches.size() > 1 && out.m_stretches.front().m_way == WayType::Other)
  {
    auto & second = out.m_stretches[1];
    second.m_startM = out.m_stretches.front().m_startM;
    out.m_stretches.erase(out.m_stretches.begin());
  }
}

void CollectSteep(RouteInput const & input, RouteDetails & out)
{
  auto const & d = input.m_distancesM;
  auto const & a = input.m_altitudesM;
  if (d.size() < 2 || d.size() != a.size())
    return;

  // The steepest grade measured over any window covering each piece between two points.
  std::vector<double> grade(d.size() - 1, 0.0);
  for (size_t i = 0; i + 1 < d.size(); ++i)
  {
    if (a[i] == geometry::kInvalidAltitude)
      continue;
    size_t j = i + 1;
    while (j + 1 < d.size() && d[j] - d[i] < kGradeWindowM)
      ++j;
    double const run = d[j] - d[i];
    if (run < kGradeWindowM / 2 || a[j] == geometry::kInvalidAltitude)
      continue;
    double const g = (a[j] - a[i]) / run;
    if (std::abs(g) < kSteepGrade)
      continue;
    for (size_t k = i; k < j; ++k)
      if (std::abs(g) > std::abs(grade[k]))
        grade[k] = g;
  }

  for (size_t k = 0; k < grade.size(); ++k)
  {
    if (grade[k] == 0)
      continue;
    bool const up = grade[k] > 0;
    if (!out.m_steep.empty() && out.m_steep.back().m_endM == d[k] && (out.m_steep.back().m_maxGrade > 0) == up)
    {
      auto & last = out.m_steep.back();
      last.m_endM = d[k + 1];
      if (std::abs(grade[k]) > std::abs(last.m_maxGrade))
        last.m_maxGrade = grade[k];
    }
    else
    {
      out.m_steep.push_back({d[k], d[k + 1], grade[k]});
    }
  }
}

void CollectStops(DataSource const & dataSource, RouteInput const & input, RouteDetails & out)
{
  // The route as points with their distance from the start.
  std::vector<m2::PointD> points{input.m_start};
  std::vector<double> dist{0.0};
  for (auto const & s : input.m_segments)
  {
    points.push_back(s.GetJunction().GetPoint());
    dist.push_back(s.GetDistFromBeginningMeters());
  }
  if (points.size() < 2)
    return;

  auto const & types = Types::Instance();
  std::map<FeatureID, Stop> best;
  size_t chunkBegin = 0;
  while (chunkBegin + 1 < points.size())
  {
    size_t chunkEnd = chunkBegin + 1;
    while (chunkEnd + 1 < points.size() && dist[chunkEnd] - dist[chunkBegin] < kStopChunkM)
      ++chunkEnd;

    m2::RectD rect;
    for (size_t i = chunkBegin; i <= chunkEnd; ++i)
      rect.Add(points[i]);
    // Inflate by the offset, measured where the chunk is (mercator grows with latitude).
    // The helper's size is a half-size: its rect is 2 * kStopMaxOffsetM wide.
    m2::RectD const r = mercator::RectByCenterXYAndSizeInMeters(rect.Center(), kStopMaxOffsetM);
    rect.Inflate(r.SizeX() / 2, r.SizeY() / 2);

    dataSource.ForEachInRect([&](FeatureType & ft)
    {
      if (ft.GetGeomType() == feature::GeomType::Line)
        return;
      StopType type;
      if (!types.GetStopType(ft, type))
        return;
      m2::PointD const center = feature::GetCenter(ft);

      double bestOffset = kStopMaxOffsetM;
      double along = -1;
      for (size_t i = chunkBegin; i < chunkEnd; ++i)
      {
        m2::ParametrizedSegment<m2::PointD> const seg(points[i], points[i + 1]);
        m2::PointD const p = seg.ClosestPointTo(center);
        double const offset = mercator::DistanceOnEarth(p, center);
        if (offset <= bestOffset)
        {
          bestOffset = offset;
          along = dist[i] + mercator::DistanceOnEarth(points[i], p);
        }
      }
      if (along < 0)
        return;
      auto const it = best.find(ft.GetID());
      if (it != best.end() && it->second.m_offsetM <= bestOffset)
        return;
      Stop stop;
      stop.m_type = type;
      stop.m_name = std::string(ft.GetName(localisation::kDefaultNameIndex));
      stop.m_point = center;
      stop.m_alongM = along;
      stop.m_offsetM = bestOffset;
      best[ft.GetID()] = std::move(stop);
    }, rect, scales::GetUpperScale());

    chunkBegin = chunkEnd;
  }

  for (auto & [id, stop] : best)
    out.m_stops.push_back(std::move(stop));
  std::sort(out.m_stops.begin(), out.m_stops.end(),
            [](Stop const & a, Stop const & b) { return a.m_alongM < b.m_alongM; });
  if (out.m_stops.size() > kMaxStops)
    out.m_stops.resize(kMaxStops);
}
}  // namespace

RouteDetails Compute(DataSource const & dataSource, RouteInput const & input)
{
  RouteDetails out;
  if (input.m_segments.empty())
    return out;
  out.m_totalM = input.m_segments.back().GetDistFromBeginningMeters();

  FeatureReader reader(dataSource);
  CollectStretches(reader, input, out);
  CollectSteep(input, out);
  CollectStops(dataSource, input, out);
  return out;
}
}  // namespace route_details
