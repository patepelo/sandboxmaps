package app.organicmaps.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

/// One section label on the search start screen ("Recent", "Categories"), with an optional action
/// on the right ("Show all"). Shows nothing while hidden, so a section can disappear entirely.
class SectionHeaderAdapter extends RecyclerView.Adapter<SectionHeaderAdapter.ViewHolder>
{
  @StringRes
  private final int mTitle;
  @StringRes
  private int mAction;
  @Nullable
  private Runnable mOnAction;
  private boolean mVisible = true;

  SectionHeaderAdapter(@StringRes int title)
  {
    mTitle = title;
  }

  /// @param action 0 hides the action button.
  void setAction(@StringRes int action, @Nullable Runnable onAction)
  {
    if (mAction == action && mOnAction == onAction)
      return;
    mAction = action;
    mOnAction = onAction;
    if (mVisible)
      notifyItemChanged(0);
  }

  void setVisible(boolean visible)
  {
    if (mVisible == visible)
      return;
    mVisible = visible;
    if (visible)
      notifyItemInserted(0);
    else
      notifyItemRemoved(0);
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
  {
    return new ViewHolder(
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_section_header, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position)
  {
    holder.mTitle.setText(mTitle);
    if (mAction == 0)
    {
      holder.mAction.setVisibility(View.GONE);
      holder.mAction.setOnClickListener(null);
    }
    else
    {
      holder.mAction.setVisibility(View.VISIBLE);
      holder.mAction.setText(mAction);
      holder.mAction.setOnClickListener(v -> {
        if (mOnAction != null)
          mOnAction.run();
      });
    }
  }

  @Override
  public int getItemCount()
  {
    return mVisible ? 1 : 0;
  }

  static class ViewHolder extends RecyclerView.ViewHolder
  {
    @NonNull
    final MaterialTextView mTitle;
    @NonNull
    final MaterialButton mAction;

    ViewHolder(@NonNull View view)
    {
      super(view);
      mTitle = view.findViewById(R.id.title);
      mAction = view.findViewById(R.id.action);
    }
  }
}
