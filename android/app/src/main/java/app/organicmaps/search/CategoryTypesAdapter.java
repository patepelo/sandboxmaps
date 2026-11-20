package app.organicmaps.search;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.organicmaps.util.ThemeUtils;
import com.google.android.material.textview.MaterialTextView;
import java.util.ArrayList;
import java.util.List;

/// Every place type in "All categories", in sections ("Health", "Shops"…), filtered by the search bar.
class CategoryTypesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
{
  private static final int TYPE_HEADER = 0;
  private static final int TYPE_ROW = 1;
  private static final int TYPE_EMPTY = 2;

  interface OnTypeClickListener
  {
    void onCategoryTypeClick(@NonNull CategoryTypes.Type type);
  }

  @NonNull
  private final Context mContext;
  @NonNull
  private final OnTypeClickListener mListener;
  // Rows to show: a section index (Integer) for a header, or a Type.
  @NonNull
  private final List<Object> mRows = new ArrayList<>();
  private boolean mFiltered;

  CategoryTypesAdapter(@NonNull Context context, @NonNull OnTypeClickListener listener)
  {
    mContext = context;
    mListener = listener;
    setFilter("");
  }

  boolean isFiltered()
  {
    return mFiltered;
  }

  @SuppressLint("NotifyDataSetChanged")
  void setFilter(@NonNull String filter)
  {
    final String normalized = CategoryTypes.normalize(filter);
    mFiltered = !normalized.isEmpty();
    mRows.clear();
    int lastGroup = -1;
    for (CategoryTypes.Type type : CategoryTypes.get(mContext))
    {
      if (!type.matches(normalized))
        continue;
      if (type.group != lastGroup)
      {
        mRows.add(type.group);
        lastGroup = type.group;
      }
      mRows.add(type);
    }
    notifyDataSetChanged();
  }

  @Override
  public int getItemCount()
  {
    return mRows.isEmpty() ? 1 : mRows.size();
  }

  @Override
  public int getItemViewType(int position)
  {
    if (mRows.isEmpty())
      return TYPE_EMPTY;
    return mRows.get(position) instanceof Integer ? TYPE_HEADER : TYPE_ROW;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
  {
    final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    if (viewType == TYPE_HEADER)
    {
      final View view = inflater.inflate(R.layout.item_search_section_header, parent, false);
      view.findViewById(R.id.action).setVisibility(View.GONE);
      return new RecyclerView.ViewHolder(view) {};
    }
    if (viewType == TYPE_EMPTY)
    {
      final View view = inflater.inflate(R.layout.item_search_recent, parent, false);
      ((MaterialTextView) view).setText(R.string.search_no_matching_categories);
      ((MaterialTextView) view).setCompoundDrawablesRelative(null, null, null, null);
      return new RecyclerView.ViewHolder(view) {};
    }
    final RowHolder holder = new RowHolder(inflater.inflate(R.layout.item_search_recent_place, parent, false));
    holder.itemView.setOnClickListener(v -> {
      final int position = holder.getBindingAdapterPosition();
      if (position != RecyclerView.NO_POSITION && mRows.get(position) instanceof CategoryTypes.Type type)
        mListener.onCategoryTypeClick(type);
    });
    return holder;
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position)
  {
    if (mRows.isEmpty())
      return;
    final Object row = mRows.get(position);
    if (row instanceof Integer group)
      ((MaterialTextView) holder.itemView.findViewById(R.id.title)).setText(CategoryTypes.GROUP_NAMES[group]);
    else if (holder instanceof RowHolder rowHolder)
      rowHolder.bind((CategoryTypes.Type) row);
  }

  private static class RowHolder extends RecyclerView.ViewHolder
  {
    final ImageView mIcon;
    final MaterialTextView mTitle;

    RowHolder(@NonNull View view)
    {
      super(view);
      mIcon = view.findViewById(R.id.icon);
      mTitle = view.findViewById(R.id.title);
      view.findViewById(R.id.subtitle).setVisibility(View.GONE);
    }

    void bind(@NonNull CategoryTypes.Type type)
    {
      mTitle.setText(type.name);
      final MapIconCache cache = MapIconCache.get(itemView.getContext());
      final boolean night = ThemeUtils.isNightTheme();
      mIcon.setTag(type.icon);
      final Bitmap icon = cache.peek(type.icon, night);
      mIcon.setImageBitmap(icon);
      if (icon == null && !type.icon.isEmpty())
        cache.request(type.icon, night, (loaded, bitmap) -> {
          if (loaded.equals(mIcon.getTag()))
            mIcon.setImageBitmap(bitmap);
        });
    }
  }
}
