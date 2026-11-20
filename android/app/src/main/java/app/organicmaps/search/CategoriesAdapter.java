package app.organicmaps.search;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.organicmaps.sdk.search.DisplayedCategories;
import app.organicmaps.sdk.util.Language;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textview.MaterialTextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// The built-in category tiles.
/// - COMPACT, on the search start screen: the first tiles that fit in two rows, then "All categories".
/// - FEATURED, at the top of "All categories": every built-in category, then "Random".
class CategoriesAdapter extends RecyclerView.Adapter<CategoriesAdapter.ViewHolder>
{
  static final int COMPACT = 0;
  static final int FEATURED = 1;

  private static final class Item
  {
    static final int BUILT_IN = 0; // native category: searches its translated name
    static final int ALL = 1; // opens "All categories"
    static final int RANDOM = 2; // fills in example queries (SearchExamples)

    final int kind;
    @StringRes
    final int label;
    @DrawableRes
    final int icon;

    Item(int kind, @StringRes int label, @DrawableRes int icon)
    {
      this.kind = kind;
      this.label = label;
      this.icon = icon;
    }
  }

  interface CategoriesUiListener
  {
    void onSearchCategorySelected(String category);

    default void onAllCategoriesSelected() {}
  }

  private final int mMode;
  private final List<Item> mBuiltIn = new ArrayList<>();
  private final List<Item> mItems = new ArrayList<>();
  private int mCompactCount = 8;
  private boolean mHidden;

  private final LayoutInflater mInflater;
  private final Resources mResources;
  private final CategoriesUiListener mListener;

  CategoriesAdapter(@NonNull Fragment fragment, int mode)
  {
    mListener = (CategoriesUiListener) fragment;
    mResources = fragment.getResources();
    mInflater = LayoutInflater.from(fragment.requireActivity());
    mMode = mode;
  }

  void updateCategories(@NonNull Fragment fragment)
  {
    final Activity activity = fragment.requireActivity();
    final String packageName = activity.getPackageName();

    final String[] keys = DisplayedCategories.nativeGetKeys();
    mBuiltIn.clear();
    for (String key : keys)
    {
      @StringRes
      final int label = getStringResIdByKey(activity.getResources(), packageName, key);
      if (label == 0)
        throw new IllegalStateException("Can't get string resource id for category:" + key);

      @DrawableRes
      final int icon = getDrawableResIdByKey(activity.getApplicationContext(), packageName, key);
      if (icon == 0)
        throw new IllegalStateException("Can't get icon resource id for category:" + key);
      mBuiltIn.add(new Item(Item.BUILT_IN, label, icon));
    }
    rebuildItems();
  }

  private void rebuildItems()
  {
    mItems.clear();
    if (mMode == FEATURED)
    {
      mItems.addAll(mBuiltIn);
      mItems.add(new Item(Item.RANDOM, R.string.search_random, R.drawable.ic_category_random));
    }
    else
    {
      mItems.addAll(mBuiltIn.subList(0, Math.max(0, Math.min(mBuiltIn.size(), mCompactCount - 1))));
      mItems.add(new Item(Item.ALL, R.string.search_all_categories, R.drawable.ic_category_all));
    }
  }

  /// COMPACT: number of tiles, normally two rows of the grid.
  @SuppressLint("NotifyDataSetChanged")
  void setCompactCount(int count)
  {
    if (mCompactCount == count)
      return;
    mCompactCount = count;
    rebuildItems();
    notifyDataSetChanged();
  }

  /// FEATURED is hidden while the category filter has text.
  @SuppressLint("NotifyDataSetChanged")
  void setHidden(boolean hidden)
  {
    if (mHidden == hidden)
      return;
    mHidden = hidden;
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

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
  {
    final View view = mInflater.inflate(R.layout.item_search_category, parent, false);
    final ViewHolder viewHolder = new ViewHolder(view, view.findViewById(R.id.title), view.findViewById(R.id.icon));
    viewHolder.setupClickListeners();
    return viewHolder;
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position)
  {
    holder.bind(mItems.get(position));
  }

  @Override
  public int getItemCount()
  {
    return mHidden ? 0 : mItems.size();
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
      if (item.kind == Item.ALL)
      {
        mListener.onAllCategoriesSelected();
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

      /// @todo Pass the correct input language. Now the Core always matches "en" together with "m_inputLocale".
      /// We expect that Language.getDefaultLocale() will be called further inside.
      if (mIsLangSupported)
        mListener.onSearchCategorySelected(mResources.getString(item.label) + " ");
      else
        mListener.onSearchCategorySelected(getEnglishString(item.label) + " ");
    }

    void bind(@NonNull Item item)
    {
      mTitle.setText(item.label);
      mIcon.setImageDrawable(ContextCompat.getDrawable(mInflater.getContext(), item.icon));
    }
  }
}
