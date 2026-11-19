package app.organicmaps.search;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import app.organicmaps.R;

/// Example queries for the "Random" search category (res/values/search_examples.xml).
/// Each call returns the next example, from basic to advanced, and remembers where it got to.
final class SearchExamples
{
  private static final String PREFS = "search_examples";
  private static final String KEY_NEXT = "next";

  static final class Example
  {
    @NonNull
    final String query;
    @NonNull
    final String hint;

    Example(@NonNull String query, @NonNull String hint)
    {
      this.query = query;
      this.hint = hint;
    }
  }

  private SearchExamples() {}

  @NonNull
  static Example next(@NonNull Context context)
  {
    final Resources res = context.getResources();
    final String[] queries = res.getStringArray(R.array.search_random_queries);
    final String[] hints = res.getStringArray(R.array.search_random_hints);
    final int count = Math.min(queries.length, hints.length);

    final SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    final int index = ((prefs.getInt(KEY_NEXT, 0) % count) + count) % count;
    prefs.edit().putInt(KEY_NEXT, (index + 1) % count).apply();

    return new Example(queries[index], hints[index]);
  }
}
