package app.organicmaps.help;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import app.organicmaps.BuildConfig;
import app.organicmaps.R;
import app.organicmaps.base.BaseMwmFragment;
import app.organicmaps.sdk.Framework;
import app.organicmaps.util.Graphics;
import app.organicmaps.util.Utils;
import app.organicmaps.util.WindowInsetUtils.ScrollableContentInsetsListener;
import com.google.android.material.textview.MaterialTextView;
import java.text.DateFormat;

public class HelpFragment extends BaseMwmFragment implements View.OnClickListener
{
  private void setupItem(@IdRes int id, boolean tint, @NonNull View frame)
  {
    final TextView view = frame.findViewById(id);
    view.setOnClickListener(this);
    if (tint)
      Graphics.tint(view);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
  {
    View root = inflater.inflate(R.layout.about, container, false);

    ((MaterialTextView) root.findViewById(R.id.version)).setText(BuildConfig.VERSION_NAME);

    // Medium format ("15 Nov 2026") so the date can't be misread as day/month or month/day.
    final String dataVersion = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Framework.getDataVersion());
    ((MaterialTextView) root.findViewById(R.id.osm_data_date)).setText(dataVersion);

    setupItem(R.id.osm_learn_more, true, root);
    setupItem(R.id.osm_website, true, root);
    setupItem(R.id.github, true, root);
    setupItem(R.id.privacy_policy, false, root);
    setupItem(R.id.term_of_use_link, false, root);
    setupItem(R.id.copyright, false, root);

    ViewCompat.setOnApplyWindowInsetsListener(root, new ScrollableContentInsetsListener(root));

    return root;
  }

  @Override
  public void onClick(View v)
  {
    final int id = v.getId();
    if (id == R.id.github)
      Utils.openUrl(requireActivity(), getString(R.string.app_repo_url));
    else if (id == R.id.osm_learn_more)
      showPage(R.string.about_osm_learn_more, R.string.osm_explainer_url);
    else if (id == R.id.osm_website)
      Utils.openUrl(requireActivity(), "https://www.openstreetmap.org");
    else if (id == R.id.privacy_policy)
      showPage(R.string.privacy_policy, R.string.privacy_policy_url);
    else if (id == R.id.term_of_use_link)
      showPage(R.string.terms_of_use, R.string.terms_of_use_url);
    else if (id == R.id.copyright)
      ((HelpActivity) requireActivity()).stackFragment(CopyrightFragment.class, getString(R.string.copyright), null);
  }

  private void showPage(int titleRes, int urlRes)
  {
    ((HelpActivity) requireActivity())
        .stackFragment(LocalPageFragment.class, getString(titleRes), LocalPageFragment.args(getString(urlRes)));
  }
}
