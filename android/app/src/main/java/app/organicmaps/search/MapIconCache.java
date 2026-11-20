package app.organicmaps.search;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Xml;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.util.log.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/// Cuts single POI icons out of the map's symbol atlas (assets/symbols/<skin>/<light|dark>/symbols.png),
/// so search results show the same icon as the map. Only the icon's region is decoded, never the whole atlas.
///
/// Decoding is slow (a PNG region is decoded row by row from the top of the atlas), so it runs on a
/// background thread: use peek() for an icon that is already cached, and request() to load one.
final class MapIconCache
{
  interface Callback
  {
    /// Called on the main thread. bitmap is null if the icon doesn't exist.
    void onIcon(@NonNull String name, @Nullable Bitmap bitmap);
  }

  private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
  private final Handler mMainThread = new Handler(Looper.getMainLooper());
  private static final String TAG = MapIconCache.class.getSimpleName();

  // Same skins and scales as drape's VisualParams::GetResourcePostfix(), so icons match their size on the map.
  private static final String[] SKINS = {"mdpi", "hdpi", "xhdpi", "6plus", "xxhdpi", "xxxhdpi"};
  private static final float[] SCALES = {1.0f, 1.5f, 2.0f, 2.4f, 3.0f, 3.5f};

  @Nullable
  private static MapIconCache sInstance;

  @NonNull
  static MapIconCache get(@NonNull Context context)
  {
    if (sInstance == null)
      sInstance = new MapIconCache(context.getApplicationContext());
    return sInstance;
  }

  @NonNull
  private final AssetManager mAssets;
  @NonNull
  private final String mSkin;
  // Bitmap density that maps one atlas pixel to its size on the map.
  private final int mBitmapDensity;
  // Keyed by "<style>/<name>", so light and dark icons don't mix. Thread-safe.
  private final LruCache<String, Bitmap> mBitmaps = new LruCache<>(256);

  @Nullable
  private String mStyle; // "light" or "dark", whichever atlas is loaded
  @Nullable
  private Map<String, Rect> mRects;
  @Nullable
  private BitmapRegionDecoder mDecoder;

  private MapIconCache(@NonNull Context context)
  {
    mAssets = context.getAssets();
    final float density = context.getResources().getDisplayMetrics().density;
    int best = 0;
    for (int i = 1; i < SCALES.length; i++)
    {
      if (Math.abs(SCALES[i] - density) < Math.abs(SCALES[best] - density))
        best = i;
    }
    mSkin = SKINS[best];
    mBitmapDensity = Math.round(160 * SCALES[best]);
  }

  /// @return the map icon called `name` (e.g. "cafe-m") if it's already loaded, otherwise null.
  @Nullable
  Bitmap peek(@NonNull String name, boolean night)
  {
    return name.isEmpty() ? null : mBitmaps.get(key(name, night));
  }

  /// Loads the icon on a background thread and passes it to `callback` on the main thread.
  void request(@NonNull String name, boolean night, @NonNull Callback callback)
  {
    if (name.isEmpty())
      return;
    mExecutor.execute(() -> {
      final Bitmap bitmap = decode(name, night);
      mMainThread.post(() -> callback.onIcon(name, bitmap));
    });
  }

  @NonNull
  private static String key(@NonNull String name, boolean night)
  {
    return (night ? "dark/" : "light/") + name;
  }

  // Runs on the background thread only.
  @Nullable
  private Bitmap decode(@NonNull String name, boolean night)
  {
    final String key = key(name, night);
    Bitmap bitmap = mBitmaps.get(key);
    if (bitmap != null)
      return bitmap;

    final String style = night ? "dark" : "light";
    if (!style.equals(mStyle))
      load(style);
    if (mDecoder == null || mRects == null)
      return null;

    final Rect rect = mRects.get(name);
    if (rect == null)
      return null;
    bitmap = mDecoder.decodeRegion(rect, null);
    if (bitmap == null)
      return null;
    bitmap.setDensity(mBitmapDensity);
    mBitmaps.put(key, bitmap);
    return bitmap;
  }

  private void load(@NonNull String style)
  {
    mStyle = style;
    if (mDecoder != null)
      mDecoder.recycle();
    mDecoder = null;
    mRects = null;

    final String dir = "symbols/" + mSkin + "/" + style + "/";
    try (InputStream sdf = mAssets.open(dir + "symbols.sdf"); InputStream png = mAssets.open(dir + "symbols.png"))
    {
      mRects = parseRects(sdf);
      //noinspection deprecation: the non-deprecated overload needs API 31.
      mDecoder = BitmapRegionDecoder.newInstance(png, false);
    }
    catch (IOException | XmlPullParserException e)
    {
      Logger.e(TAG, "Can't load map icons from " + dir, e);
      mRects = null;
      mDecoder = null;
    }
  }

  // symbols.sdf lists each icon as <symbol name="cafe-m" minX=".." minY=".." maxX=".." maxY=".."/>.
  @NonNull
  private static Map<String, Rect> parseRects(@NonNull InputStream in) throws IOException, XmlPullParserException
  {
    final Map<String, Rect> rects = new HashMap<>();
    final XmlPullParser parser = Xml.newPullParser();
    parser.setInput(in, "UTF-8");
    for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next())
    {
      if (event != XmlPullParser.START_TAG || !"symbol".equals(parser.getName()))
        continue;
      final String name = parser.getAttributeValue(null, "name");
      if (name == null)
        continue;
      rects.put(name, new Rect(Integer.parseInt(parser.getAttributeValue(null, "minX")),
                               Integer.parseInt(parser.getAttributeValue(null, "minY")),
                               Integer.parseInt(parser.getAttributeValue(null, "maxX")),
                               Integer.parseInt(parser.getAttributeValue(null, "maxY"))));
    }
    return rects;
  }
}
