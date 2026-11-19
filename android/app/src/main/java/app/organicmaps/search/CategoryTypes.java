package app.organicmaps.search;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.os.ConfigurationCompat;
import app.organicmaps.R;
import app.organicmaps.sdk.util.Utils;
import app.organicmaps.sdk.util.log.Logger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/// Place types listed in "All categories" (assets/search_category_types.json, made by
/// tools/python/categories/search_category_types.py), grouped into sections and filterable.
final class CategoryTypes
{
  private static final String TAG = CategoryTypes.class.getSimpleName();
  private static final String ASSET = "search_category_types.json";

  /// Section keys in display order, with their names.
  static final String[] GROUPS = {"food", "shops", "health", "services", "transport", "stay",
                                  "sport", "culture", "nature", "public", "everyday"};
  @StringRes
  static final int[] GROUP_NAMES = {R.string.search_group_food,      R.string.search_group_shops,
                                    R.string.search_group_health,    R.string.search_group_services,
                                    R.string.search_group_transport, R.string.search_group_stay,
                                    R.string.search_group_sport,     R.string.search_group_culture,
                                    R.string.search_group_nature,    R.string.search_group_public,
                                    R.string.search_group_everyday};

  static final class Type
  {
    @NonNull
    final String name; // translated, e.g. "Dentist"
    @NonNull
    final String icon; // map icon, may be empty
    final int group; // index into GROUPS
    @NonNull
    final String query; // English term to search for it
    @NonNull
    private final String mMatchText; // normalized name and English terms, for filtering

    Type(@NonNull String name, @NonNull String icon, int group, @NonNull String query, @NonNull String matchText)
    {
      this.name = name;
      this.icon = icon;
      this.group = group;
      this.query = query;
      mMatchText = matchText;
    }

    boolean matches(@NonNull String normalizedFilter)
    {
      return normalizedFilter.isEmpty() || mMatchText.contains(normalizedFilter);
    }
  }

  @Nullable
  private static List<Type> sTypes;
  @Nullable
  private static Locale sLocale;

  private CategoryTypes() {}

  /// All types, sorted by section then by name. Loaded once per UI language.
  @NonNull
  static List<Type> get(@NonNull Context context)
  {
    final Locale locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
    if (sTypes == null || !locale.equals(sLocale))
    {
      sTypes = load(context, locale);
      sLocale = locale;
    }
    return sTypes;
  }

  /// Lowercase without accents, so "cafe" finds "Café".
  @NonNull
  static String normalize(@NonNull String s)
  {
    return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{Mn}", "").trim();
  }

  @NonNull
  private static List<Type> load(@NonNull Context context, @NonNull Locale locale)
  {
    final List<Type> types = new ArrayList<>();
    try (InputStream in = context.getAssets().open(ASSET);
         BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
    {
      final StringBuilder json = new StringBuilder();
      for (String line; (line = reader.readLine()) != null;)
        json.append(line);
      final JSONArray array = new JSONArray(json.toString());
      for (int i = 0; i < array.length(); i++)
      {
        final JSONObject o = array.getJSONObject(i);
        final String type = o.getString("t");
        final String name = Utils.getLocalizedFeatureType(context, type);
        final int group = indexOf(o.getString("g"));
        if (group < 0)
          continue;
        final StringBuilder match = new StringBuilder(normalize(name));
        final String query = o.getString("q");
        match.append('|').append(normalize(query));
        final JSONArray more = o.optJSONArray("s");
        if (more != null)
          for (int j = 0; j < more.length(); j++)
            match.append('|').append(normalize(more.getString(j)));
        types.add(new Type(name, o.optString("i"), group, query, match.toString()));
      }
    }
    catch (IOException | JSONException e)
    {
      Logger.e(TAG, "Can't load " + ASSET, e);
    }
    final Collator collator = Collator.getInstance(locale);
    Collections.sort(types, (a, b) -> a.group != b.group ? Integer.compare(a.group, b.group)
                                                         : collator.compare(a.name, b.name));
    return types;
  }

  private static int indexOf(@NonNull String group)
  {
    for (int i = 0; i < GROUPS.length; i++)
      if (GROUPS[i].equals(group))
        return i;
    return -1;
  }
}
