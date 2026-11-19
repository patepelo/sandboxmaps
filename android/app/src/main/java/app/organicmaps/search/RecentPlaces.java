package app.organicmaps.search;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.bookmarks.data.FeatureId;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.search.SearchResult;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.log.Logger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/// Places opened from search, shown in Recent on the search start screen.
///
/// Tapping a search result only makes it a candidate. It is saved when the user acts on that same place:
/// expands the place page, routes to or from it, saves it as a bookmark or shares it (onPlacePageAction()).
/// Picking a result as a route point saves it straight away. Follows the "Search history" privacy setting.
public final class RecentPlaces
{
  private static final String TAG = RecentPlaces.class.getSimpleName();
  private static final String PREFS = "search_recent_places";
  private static final String KEY_ENTRIES = "entries";
  static final int MAX_ENTRIES = 20;
  // Same place if no feature id matches but the position is this close (in degrees, about 30 m).
  private static final double SAME_PLACE_DEGREES = 0.0003;

  public static final class Entry
  {
    @NonNull
    final String title;
    @NonNull
    final String subtitle; // translated place type, e.g. "Dry Cleaner"
    @NonNull
    final String icon; // map icon name, see MapIconCache
    final double lat;
    final double lon;
    @NonNull
    final FeatureId featureId;
    // Maps data date (yyMMdd) when saved. The feature id is only reused while the maps are unchanged.
    final long dataVersion;

    Entry(@NonNull String title, @NonNull String subtitle, @NonNull String icon, double lat, double lon,
          @NonNull FeatureId featureId, long dataVersion)
    {
      this.title = title;
      this.subtitle = subtitle;
      this.icon = icon;
      this.lat = lat;
      this.lon = lon;
      this.featureId = featureId;
      this.dataVersion = dataVersion;
    }

    /// The feature id still points at this place: it's real and the maps haven't been updated since.
    boolean canShowFeature()
    {
      return featureId.isRealId() && dataVersion == currentDataVersion();
    }
  }

  @Nullable
  private static Entry sCandidate;

  private RecentPlaces() {}

  /// The user tapped this search result. It is saved only if they then act on it.
  static void setCandidate(@NonNull SearchResult result)
  {
    sCandidate = fromResult(result);
  }

  /// The user picked this result directly, e.g. as a route point: save it now.
  static void saveNow(@NonNull Context context, @NonNull SearchResult result)
  {
    final Entry entry = fromResult(result);
    if (entry != null)
      save(context, entry);
  }

  /// Called when the user acts on the open place page. Saves the candidate if it's the same place.
  public static void onPlacePageAction(@NonNull Context context, @Nullable MapObject mapObject)
  {
    final Entry candidate = sCandidate;
    if (candidate == null || mapObject == null || !isSamePlace(candidate, mapObject))
      return;
    sCandidate = null;
    save(context, candidate);
  }

  @NonNull
  static List<Entry> load(@NonNull Context context)
  {
    final List<Entry> entries = new ArrayList<>();
    final String json = prefs(context).getString(KEY_ENTRIES, null);
    if (TextUtils.isEmpty(json))
      return entries;
    try
    {
      final JSONArray array = new JSONArray(json);
      for (int i = 0; i < array.length(); i++)
      {
        final JSONObject o = array.getJSONObject(i);
        entries.add(new Entry(o.getString("title"), o.optString("subtitle"), o.optString("icon"), o.getDouble("lat"),
                              o.getDouble("lon"),
                              new FeatureId(o.optString("mwm"), o.optLong("mwmVersion", -1), o.optInt("index")),
                              o.optLong("dataVersion")));
      }
    }
    catch (JSONException e)
    {
      Logger.e(TAG, "Can't read recent places", e);
    }
    return entries;
  }

  public static void clear(@NonNull Context context)
  {
    sCandidate = null;
    prefs(context).edit().remove(KEY_ENTRIES).apply();
  }

  private static void save(@NonNull Context context, @NonNull Entry entry)
  {
    if (!Config.isSearchHistoryEnabled())
      return;
    final List<Entry> entries = load(context);
    // Newest first; opening the same place again moves it back to the top.
    for (int i = entries.size() - 1; i >= 0; i--)
    {
      if (isSamePlace(entries.get(i), entry.featureId, entry.lat, entry.lon))
        entries.remove(i);
    }
    entries.add(0, entry);
    while (entries.size() > MAX_ENTRIES)
      entries.remove(entries.size() - 1);

    final JSONArray array = new JSONArray();
    try
    {
      for (Entry e : entries)
      {
        array.put(new JSONObject()
                      .put("title", e.title)
                      .put("subtitle", e.subtitle)
                      .put("icon", e.icon)
                      .put("lat", e.lat)
                      .put("lon", e.lon)
                      .put("mwm", e.featureId.getMwmName())
                      .put("mwmVersion", e.featureId.getMwmVersion())
                      .put("index", e.featureId.getFeatureIndex())
                      .put("dataVersion", e.dataVersion));
      }
    }
    catch (JSONException ex)
    {
      Logger.e(TAG, "Can't save recent places", ex);
      return;
    }
    prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply();
  }

  @Nullable
  private static Entry fromResult(@NonNull SearchResult result)
  {
    if (result.type != SearchResult.TYPE_RESULT || result.description == null)
      return null;
    final SearchResult.Description d = result.description;
    final String title = TextUtils.isEmpty(result.name) ? d.localizedFeatureType : result.name;
    if (TextUtils.isEmpty(title))
      return null;
    return new Entry(title, d.localizedFeatureType, d.iconName, result.lat, result.lon, d.featureId,
                     currentDataVersion());
  }

  private static boolean isSamePlace(@NonNull Entry entry, @NonNull MapObject mapObject)
  {
    return isSamePlace(entry, mapObject.getFeatureId(), mapObject.getLat(), mapObject.getLon());
  }

  private static boolean isSamePlace(@NonNull Entry entry, @NonNull FeatureId featureId, double lat, double lon)
  {
    if (entry.featureId.isRealId() && featureId.isRealId())
      return entry.featureId.equals(featureId);
    return Math.abs(entry.lat - lat) < SAME_PLACE_DEGREES && Math.abs(entry.lon - lon) < SAME_PLACE_DEGREES;
  }

  private static long currentDataVersion()
  {
    return Long.parseLong(new SimpleDateFormat("yyMMdd", Locale.ENGLISH).format(Framework.getDataVersion()));
  }

  @NonNull
  private static SharedPreferences prefs(@NonNull Context context)
  {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }
}
