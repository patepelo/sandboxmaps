package app.organicmaps.search;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.search.SearchRecents;
import app.organicmaps.util.Graphics;
import app.organicmaps.util.ThemeUtils;
import app.organicmaps.widget.SearchToolbarController;
import com.google.android.material.textview.MaterialTextView;
import java.util.ArrayList;
import java.util.List;

/// Recent on the search start screen: places opened from search (RecentPlaces) first, then typed searches.
/// Collapsed, it shows the first COLLAPSED_COUNT entries; expanded, all of them and "Clear search history".
class SearchHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
{
  private static final int TYPE_QUERY = 0;
  private static final int TYPE_CLEAR = 1;
  private static final int TYPE_MY_POSITION = 2;
  private static final int TYPE_PLACE = 3;

  static final int COLLAPSED_COUNT = 3;

  interface OnPlaceClickListener
  {
    void onRecentPlaceClick(@NonNull RecentPlaces.Entry place);
  }

  @NonNull
  private final SearchToolbarController mSearchToolbarController;
  @NonNull
  private final OnPlaceClickListener mOnPlaceClick;
  private final boolean mShowMyPosition;
  @NonNull
  private List<RecentPlaces.Entry> mPlaces = new ArrayList<>();
  private boolean mExpanded;
  @Nullable
  private Runnable mOnCleared;

  private static class TextHolder extends RecyclerView.ViewHolder
  {
    final MaterialTextView mText;

    TextHolder(View itemView)
    {
      super(itemView);
      mText = (MaterialTextView) itemView;
      Graphics.tint(mText);
    }
  }

  private static class PlaceHolder extends RecyclerView.ViewHolder
  {
    final ImageView mIcon;
    final MaterialTextView mTitle;
    final MaterialTextView mSubtitle;

    PlaceHolder(View itemView)
    {
      super(itemView);
      mIcon = itemView.findViewById(R.id.icon);
      mTitle = itemView.findViewById(R.id.title);
      mSubtitle = itemView.findViewById(R.id.subtitle);
    }
  }

  SearchHistoryAdapter(@NonNull SearchToolbarController searchToolbarController,
                       @NonNull OnPlaceClickListener onPlaceClick, boolean showMyPosition)
  {
    mSearchToolbarController = searchToolbarController;
    mOnPlaceClick = onPlaceClick;
    mShowMyPosition = showMyPosition;
    reload();
  }

  private void reload()
  {
    SearchRecents.refresh();
    mPlaces = RecentPlaces.load(mSearchToolbarController.getToolbar().getContext());
  }

  int getRecentCount()
  {
    return mPlaces.size() + SearchRecents.getSize();
  }

  boolean isExpanded()
  {
    return mExpanded;
  }

  @SuppressLint("NotifyDataSetChanged")
  void setExpanded(boolean expanded)
  {
    if (mExpanded == expanded)
      return;
    mExpanded = expanded;
    notifyDataSetChanged();
  }

  void setOnCleared(@Nullable Runnable onCleared)
  {
    mOnCleared = onCleared;
  }

  @SuppressLint("NotifyDataSetChanged")
  void refresh()
  {
    reload();
    notifyDataSetChanged();
  }

  private int visibleEntries()
  {
    final int count = getRecentCount();
    return mExpanded ? count : Math.min(count, COLLAPSED_COUNT);
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type)
  {
    final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    switch (type)
    {
    case TYPE_PLACE:
    {
      final PlaceHolder holder = new PlaceHolder(inflater.inflate(R.layout.item_search_recent_place, parent, false));
      holder.itemView.setOnClickListener(v -> {
        final int index = entryIndex(holder.getBindingAdapterPosition());
        if (index >= 0 && index < mPlaces.size())
          mOnPlaceClick.onRecentPlaceClick(mPlaces.get(index));
      });
      return holder;
    }
    case TYPE_QUERY:
    {
      final TextHolder holder = new TextHolder(inflater.inflate(R.layout.item_search_recent, parent, false));
      holder.mText.setOnClickListener(v -> mSearchToolbarController.setQuery(holder.mText.getText()));
      return holder;
    }
    case TYPE_CLEAR:
    {
      final TextHolder holder = new TextHolder(inflater.inflate(R.layout.item_search_clear_history, parent, false));
      holder.mText.setOnClickListener(v -> {
        SearchRecents.clear();
        RecentPlaces.clear(v.getContext());
        mPlaces = new ArrayList<>();
        mExpanded = false;
        notifyDataSetChanged();
        if (mOnCleared != null)
          mOnCleared.run();
      });
      return holder;
    }
    case TYPE_MY_POSITION:
    {
      final TextHolder holder = new TextHolder(inflater.inflate(R.layout.item_search_my_position, parent, false));
      holder.mText.setOnClickListener(v -> {
        RoutingController.get().onPoiSelected(
            MwmApplication.from(parent.getContext()).getLocationHelper().getMyPosition());
        mSearchToolbarController.onUpClick();
      });
      return holder;
    }
    default: throw new IllegalArgumentException("Unsupported ViewHolder type given");
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position)
  {
    final int index = entryIndex(position);
    if (holder instanceof PlaceHolder placeHolder)
      bindPlace(placeHolder, mPlaces.get(index));
    else if (getItemViewType(position) == TYPE_QUERY)
      ((TextHolder) holder).mText.setText(SearchRecents.get(index - mPlaces.size()));
  }

  private static void bindPlace(@NonNull PlaceHolder holder, @NonNull RecentPlaces.Entry place)
  {
    holder.mTitle.setText(place.title);
    holder.mSubtitle.setText(place.subtitle);
    holder.mSubtitle.setVisibility(place.subtitle.isEmpty() ? View.GONE : View.VISIBLE);

    final MapIconCache cache = MapIconCache.get(holder.itemView.getContext());
    final boolean night = ThemeUtils.isNightTheme();
    holder.mIcon.setTag(place.icon);
    final Bitmap icon = cache.peek(place.icon, night);
    holder.mIcon.setImageBitmap(icon);
    if (icon == null && !place.icon.isEmpty())
      cache.request(place.icon, night, (loaded, bitmap) -> {
        if (loaded.equals(holder.mIcon.getTag()))
          holder.mIcon.setImageBitmap(bitmap);
      });
  }

  /// Index into "places, then queries" for an adapter position, or -1 for the "My position" row.
  private int entryIndex(int position)
  {
    return mShowMyPosition ? position - 1 : position;
  }

  @Override
  public int getItemCount()
  {
    int res = visibleEntries();
    if (mExpanded && res > 0)
      res++; // "Clear search history"
    if (mShowMyPosition)
      res++;
    return res;
  }

  @Override
  public int getItemViewType(int position)
  {
    final int index = entryIndex(position);
    if (index < 0)
      return TYPE_MY_POSITION;
    if (index >= visibleEntries())
      return TYPE_CLEAR;
    return index < mPlaces.size() ? TYPE_PLACE : TYPE_QUERY;
  }
}
