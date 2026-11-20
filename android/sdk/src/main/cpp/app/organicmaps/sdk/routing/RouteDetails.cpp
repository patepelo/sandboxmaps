#include "app/organicmaps/sdk/Framework.hpp"
#include "app/organicmaps/sdk/core/jni_helper.hpp"

#include "map/route_details.hpp"

#include "geometry/mercator.hpp"

#include <vector>

namespace
{
template <typename T, typename Fn>
jdoubleArray ToDoubles(JNIEnv * env, std::vector<T> const & items, Fn && get)
{
  std::vector<jdouble> values;
  values.reserve(items.size());
  for (auto const & item : items)
    values.push_back(get(item));
  jdoubleArray array = env->NewDoubleArray(static_cast<jsize>(values.size()));
  env->SetDoubleArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
  return array;
}

template <typename T, typename Fn>
jintArray ToInts(JNIEnv * env, std::vector<T> const & items, Fn && get)
{
  std::vector<jint> values;
  values.reserve(items.size());
  for (auto const & item : items)
    values.push_back(get(item));
  jintArray array = env->NewIntArray(static_cast<jsize>(values.size()));
  env->SetIntArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
  return array;
}

jobject ToJava(JNIEnv * env, route_details::RouteDetails const & d)
{
  using namespace route_details;
  static jclass const stopClass = jni::GetGlobalClassRef(env, "app/organicmaps/sdk/routing/RouteDetails$Stop");
  // Stop(int type, String name, double lat, double lon, double alongM, double offsetM)
  static jmethodID const stopCtor = jni::GetConstructorID(env, stopClass, "(ILjava/lang/String;DDDD)V");
  jobjectArray stops = jni::ToJavaArray(env, stopClass, d.m_stops, [](JNIEnv * env, Stop const & s)
  {
    auto const ll = mercator::ToLatLon(s.m_point);
    jni::TScopedLocalRef name(env, jni::ToJavaString(env, s.m_name));
    return env->NewObject(stopClass, stopCtor, static_cast<jint>(s.m_type), name.get(), ll.m_lat, ll.m_lon,
                          s.m_alongM, s.m_offsetM);
  });

  static jclass const detailsClass = jni::GetGlobalClassRef(env, "app/organicmaps/sdk/routing/RouteDetails");
  // RouteDetails(double totalM, double[] profileDistM, int[] profileAltM, double[] stretchStartM, double[] stretchEndM,
  //              int[] stretchWay, int[] stretchSurface, int[] stretchWalkBike, double[] steepStartM, double[] steepEndM,
  //              double[] steepGrade, Stop[] stops)
  static jmethodID const detailsCtor = jni::GetConstructorID(
      env, detailsClass, "(D[D[I[D[D[I[I[I[D[D[D[Lapp/organicmaps/sdk/routing/RouteDetails$Stop;)V");

  jni::TScopedLocalRef profileDist(env, ToDoubles(env, d.m_profileDistM, [](double m) { return m; }));
  jni::TScopedLocalRef profileAlt(env, ToInts(env, d.m_profileAltM, [](geometry::Altitude a) { return static_cast<jint>(a); }));
  auto const & st = d.m_stretches;
  jni::TScopedLocalRef stretchStart(env, ToDoubles(env, st, [](Stretch const & s) { return s.m_startM; }));
  jni::TScopedLocalRef stretchEnd(env, ToDoubles(env, st, [](Stretch const & s) { return s.m_endM; }));
  jni::TScopedLocalRef stretchWay(env, ToInts(env, st, [](Stretch const & s) { return static_cast<jint>(s.m_way); }));
  jni::TScopedLocalRef stretchSurface(
      env, ToInts(env, st, [](Stretch const & s) { return static_cast<jint>(s.m_surface); }));
  jni::TScopedLocalRef stretchWalkBike(env, ToInts(env, st, [](Stretch const & s) { return s.m_walkBike ? 1 : 0; }));
  jni::TScopedLocalRef steepStart(env, ToDoubles(env, d.m_steep, [](SteepStretch const & s) { return s.m_startM; }));
  jni::TScopedLocalRef steepEnd(env, ToDoubles(env, d.m_steep, [](SteepStretch const & s) { return s.m_endM; }));
  jni::TScopedLocalRef steepGrade(env, ToDoubles(env, d.m_steep, [](SteepStretch const & s) { return s.m_maxGrade; }));
  jni::TScopedLocalRef stopsRef(env, stops);

  return env->NewObject(detailsClass, detailsCtor, d.m_totalM, profileDist.get(), profileAlt.get(), stretchStart.get(), stretchEnd.get(), stretchWay.get(),
                        stretchSurface.get(), stretchWalkBike.get(), steepStart.get(), steepEnd.get(), steepGrade.get(),
                        stopsRef.get());
}
}  // namespace

extern "C"
{
JNIEXPORT void JNICALL Java_app_organicmaps_sdk_routing_RouteDetails_nativeRequest(JNIEnv *, jclass, jobject listener)
{
  auto const listenerRef = jni::make_global_ref(listener);
  frm()->GetRoutingManager().RequestRouteDetails([listenerRef](route_details::RouteDetails const & details)
  {
    JNIEnv * env = jni::GetEnv();
    jni::TScopedLocalRef jDetails(env, ToJava(env, details));
    jmethodID const onDetails =
        jni::GetMethodID(env, *listenerRef, "onRouteDetails", "(Lapp/organicmaps/sdk/routing/RouteDetails;)V");
    env->CallVoidMethod(*listenerRef, onDetails, jDetails.get());
  });
}
}  // extern "C"
