package app.organicmaps.help;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import app.organicmaps.R;
import app.organicmaps.WebContainerDelegate;
import app.organicmaps.base.BaseMwmFragment;
import app.organicmaps.util.WindowInsetUtils;

/// Shows a page bundled with the app (e.g. the privacy policy) inside the About screen.
public class LocalPageFragment extends BaseMwmFragment
{
  private static final String ARG_URL = "url";

  private WebContainerDelegate mDelegate;

  @NonNull
  public static Bundle args(@NonNull String url)
  {
    final Bundle args = new Bundle();
    args.putString(ARG_URL, url);
    return args;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
  {
    View root = inflater.inflate(R.layout.fragment_web_view_with_progress, container, false);

    ViewCompat.setOnApplyWindowInsetsListener(root, WindowInsetUtils.PaddingInsetsListener.excludeTop());

    mDelegate = new WebContainerDelegate(root, requireArguments().getString(ARG_URL)) {
      @Override
      protected void doStartActivity(Intent intent)
      {
        startActivity(intent);
      }
    };

    return root;
  }

  @Override
  public boolean onBackPressed()
  {
    // Go back inside the page first; otherwise let the activity return to About (it also restores the title).
    return mDelegate.onBackPressed();
  }
}
