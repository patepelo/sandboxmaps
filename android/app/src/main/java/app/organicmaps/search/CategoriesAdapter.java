package app.organicmaps.search;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.organicmaps.sdk.search.DisplayedCategories;
import app.organicmaps.sdk.util.Language;
import app.organicmaps.util.ThemeUtils;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textview.MaterialTextView;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class CategoriesAdapter extends RecyclerView.Adapter<CategoriesAdapter.ViewHolder>
{
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({ViewType.CATEGORY})
  @interface ViewType
  {
    int CATEGORY = 0;
  }

  // One tile in the grid. Order: built-in categories, [extra categories when expanded], More/Less, Random.
  private static final class Item
  {
    static final int BUILT_IN = 0; // native category: searches its translated name
    static final int EXTRA = 1; // ExtraCategories entry: searches its English term, shows its map icon
    static final int TOGGLE = 2; // shows or hides the extra categories
    static final int RANDOM = 3; // fills in example queries (SearchExamples)

    final int kind;
    @StringRes
    final int label;
    @DrawableRes
    final int icon;
    @Nullable
    final ExtraCategories.Entry extra;

    Item(int kind, @StringRes int label, @DrawableRes int icon, @Nullable ExtraCategories.Entry extra)
    {
      this.kind = kind;
      this.label = label;
      this.icon = icon;
      this.extra = extra;
    }
  }

  private final List<Item> mBuiltIn = new ArrayList<>();
  private final List<Item> mItems = new ArrayList<>();
  private boolean mExpanded;

  private final LayoutInflater mInflater;
  private final Resources mResources;

  interface CategoriesUiListener
  {
    void onSearchCategorySelected(String category);
  }

  private final CategoriesUiListener mListener;

  CategoriesAdapter(@NonNull Fragment fragment)
  {
    mListener = (CategoriesUiListener) fragment;
    mResources = fragment.getResources();
    mInflater = LayoutInflater.from(fragment.requireActivity());
  }

  void updateCategories(@NonNull Fragment fragment)
  {
    final Activity activity = fragment.requireActivity();
    final String packageName = activity.getPackageName();

    final String[] keys = DisplayedCategories.nativeGetKeys();
    final int numKeys = keys.length;

    mBuiltIn.clear();
    for (int i = 0; i < numKeys; i++)
    {
      String key = keys[i];
      @StringRes
      final int label = getStringResIdByKey(activity.getResources(), packageName, key);

      if (label == 0)
        throw new IllegalStateException("Can't get string resource id for category:" + key);

      @DrawableRes
      final int icon = getDrawableResIdByKey(activity.getApplicationContext(), packageName, key);
      if (icon == 0)
        throw new IllegalStateException("Can't get icon resource id for category:" + key);
      mBuiltIn.add(new Item(Item.BUILT_IN, label, icon, null));
    }
    rebuildItems();
  }

  private void rebuildItems()
  {
    mItems.clear();
    mItems.addAll(mBuiltIn);
    if (mExpanded)
    {
      for (ExtraCategories.Entry entry : ExtraCategories.ALL)
        mItems.add(new Item(Item.EXTRA, entry.label, 0, entry));
    }
    mItems.add(mExpanded ? new Item(Item.TOGGLE, R.string.search_categories_less, R.drawable.ic_category_less, null)
                         : new Item(Item.TOGGLE, R.string.search_categories_more, R.drawable.ic_category_more, null));
    mItems.add(new Item(Item.RANDOM, R.string.search_random, R.drawable.ic_category_random, null));
  }

  @SuppressLint("NotifyDataSetChanged")
  private void toggleExpanded()
  {
    mExpanded = !mExpanded;
    rebuildItems();
    notifyDataSetChanged();
  }

  @SuppressLint("DiscouragedApi")
  @StringRes
  private static int getStringResIdByKey(@NonNull Resources resources, @NonNull String packageName, @NonNull String key)
  {
    return resources.getIdentifier(key, "string", packageName);
  }

  @SuppressLint("DiscouragedApi")
  @DrawableRes
  private static int getDrawableResIdByKey(@NonNull Context context, @NonNull String packageName, @NonNull String key)
  {
    return context.getResources().getIdentifier("ic_" + key, "drawable", packageName);
  }

  @Override
  @ViewType
  public int getItemViewType(int position)
  {
    return ViewType.CATEGORY;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, @ViewType int viewType)
  {
    View view;
    ViewHolder viewHolder;
    if (viewType == ViewType.CATEGORY)
    {
      view = mInflater.inflate(R.layout.item_search_category, parent, false);
      viewHolder = new ViewHolder(view, view.findViewById(R.id.title), view.findViewById(R.id.icon));
    }
    else
    {
      throw new AssertionError("Unsupported type detected: " + viewType);
    }

    viewHolder.setupClickListeners();
    return viewHolder;
  }

  @Override
  public void onBindViewHolder(ViewHolder holder, int position)
  {
    holder.bind(mItems.get(position));
  }

  @Override
  public int getItemCount()
  {
    return mItems.size();
  }

  class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener
  {
    @NonNull
    private final MaterialTextView mTitle;
    @NonNull
    private final ShapeableImageView mIcon;
    @NonNull
    private final View mView;

    private final boolean mIsLangSupported;
    private Resources mEnglishResources; // Lazy-initialized

    // Get locale/language of the translations that are used in the app UI and are visible to the user.
    // Handles cases when the primary system language translation is not supported by OM yet.
    // private @NonNull String getResourcesLanguage()
    // {
    //   final Configuration c = mResources.getConfiguration();
    //   return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? c.getLocales().get(0).toString() : c.locale.toString();
    // }

    private @NonNull String getEnglishString(@StringRes int categoryId)
    {
      // Not thread safe, but we don't care, as it should always run on the same thread.
      if (mEnglishResources == null)
      {
        final Configuration newConfig = new Configuration(mResources.getConfiguration());
        newConfig.setLocale(new Locale("en"));
        final Context localizedContext = mInflater.getContext().createConfigurationContext(newConfig);
        mEnglishResources = localizedContext.getResources();
      }
      return mEnglishResources.getString(categoryId);
    }

    ViewHolder(@NonNull View v, @NonNull MaterialTextView tv, @NonNull ShapeableImageView ic)
    {
      super(v);
      mView = v;
      mTitle = tv;
      mIcon = ic;

      // TODO(AB): Change Language.getDefaultLocale() to getResourcesLanguage() and pass proper language to the search.
      mIsLangSupported = DisplayedCategories.nativeIsLangSupported(Language.getDefaultLocale());
    }

    void setupClickListeners()
    {
      mView.setOnClickListener(this);
    }

    @Override
    public final void onClick(View v)
    {
      final int position = getBindingAdapterPosition();
      if (position == RecyclerView.NO_POSITION)
        return;

      final Item item = mItems.get(position);
      if (item.kind == Item.TOGGLE)
      {
        toggleExpanded();
        return;
      }
      if (item.kind == Item.RANDOM)
      {
        final Context context = v.getContext();
        final SearchExamples.Example example = SearchExamples.next(context);
        mListener.onSearchCategorySelected(example.query + " ");
        Toast.makeText(context, example.hint, Toast.LENGTH_LONG).show();
        return;
      }
      if (item.kind == Item.EXTRA && item.extra != null)
      {
        mListener.onSearchCategorySelected(item.extra.query + " ");
        return;
      }

      @StringRes
      final int categoryId = item.label;

      /// @todo Pass the correct input language. Now the Core always matches "en" together with "m_inputLocale".
      /// We expect that Language.getDefaultLocale() will be called further inside.
      if (mIsLangSupported)
        mListener.onSearchCategorySelected(mResources.getString(categoryId) + " " /*, getResourcesLanguage()*/);
      else
        mListener.onSearchCategorySelected(getEnglishString(categoryId) + " " /*, "en"*/);
    }

    void bind(@NonNull Item item)
    {
      final Context context = mInflater.getContext();
      mTitle.setText(item.label);
      if (item.kind == Item.EXTRA && item.extra != null)
      {
        // Map icon on a soft circle, same size as the built-in icons.
        mIcon.setBackgroundResource(R.drawable.bg_category_map_icon);
        mIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mIcon.setImageBitmap(MapIconCache.get(context).getIcon(item.extra.icon, ThemeUtils.isNightTheme()));
      }
      else
      {
        mIcon.setBackground(null);
        mIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mIcon.setImageDrawable(ContextCompat.getDrawable(context, item.icon));
      }
    }
  }
}
