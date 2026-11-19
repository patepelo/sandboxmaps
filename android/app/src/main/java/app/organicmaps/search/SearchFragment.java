package app.organicmaps.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.MwmActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.base.BaseMwmFragment;
import app.organicmaps.downloader.CountrySuggestFragment;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.bookmarks.data.FeatureId;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.downloader.MapManager;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.search.SearchEngine;
import app.organicmaps.sdk.search.SearchListener;
import app.organicmaps.sdk.search.SearchRecents;
import app.organicmaps.sdk.search.SearchResult;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.Language;
import app.organicmaps.sdk.util.SharedPropertiesUtils;
import app.organicmaps.sdk.util.log.Logger;
import app.organicmaps.util.UiUtils;
import app.organicmaps.util.Utils;
import app.organicmaps.util.WindowInsetUtils;
import app.organicmaps.widget.PlaceholderView;
import app.organicmaps.widget.SearchToolbarController;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchFragment extends BaseMwmFragment implements SearchListener, CategoriesAdapter.CategoriesUiListener
{
  private static final int RECENT_PLACE_ZOOM = 17;
  private long mLastQueryTimestamp;
  private boolean compatibleContactsAppInstalled = true;
  @NonNull
  private final List<HiddenCommand> mHiddenCommands = new ArrayList<>();

  private static class LastPosition
  {
    double lat;
    double lon;
    boolean valid;

    public void set(double lat, double lon)
    {
      this.lat = lat;
      this.lon = lon;
      valid = true;
    }
  }

  private class ToolbarController extends SearchToolbarController
  {
    public ToolbarController(View root)
    {
      super(root, SearchFragment.this.requireActivity());
    }

    @Override
    protected void onTextChanged(String query)
    {
      if (!isAdded())
        return;

      if (mCategoryMode)
      {
        onCategoryFilterChanged(query);
        return;
      }

      if (TextUtils.isEmpty(query))
      {
        mSearchAdapter.clear();
        stopSearch();
        return;
      }

      if (tryRecognizeHiddenCommand(query))
      {
        mSearchAdapter.clear();
        stopSearch();
        closeSearch();
        return;
      }

      runSearch();
    }

    @Override
    protected boolean onStartSearchClick()
    {
      deactivate();
      return true;
    }

    @Override
    protected int getVoiceInputPrompt()
    {
      return R.string.search_map;
    }

    @Override
    protected void startVoiceRecognition(Intent intent)
    {
      startVoiceRecognitionForResult.launch(intent);
    }

    @Override
    protected boolean supportsVoiceSearch()
    {
      return true;
    }

    @Override
    public void onUpClick()
    {
      if (!onBackPressed())
        super.onUpClick();
    }

    @Override
    public void clear()
    {
      super.clear();
    }
  }

  private View mResultsFrame;
  // Start screen (no query yet): Recent and Categories sections in one grid list.
  private View mTabFrame;
  @Nullable
  private GridLayoutManager mStartLayout;
  @Nullable
  private SectionHeaderAdapter mRecentHeader;
  @Nullable
  private SearchHistoryAdapter mHistoryAdapter;
  @Nullable
  private SectionHeaderAdapter mCategoriesHeader;
  @Nullable
  private CategoriesAdapter mCategoriesAdapter;
  // "All categories" mode: the bar filters place types shown in mCategoryFrame.
  private View mCategoryFrame;
  private boolean mCategoryMode;
  @Nullable
  private String mQueryBeforeCategories;
  @Nullable
  private GridLayoutManager mCategoryLayout;
  @SuppressWarnings("NullableProblems")
  @NonNull
  private SectionHeaderAdapter mFeaturedHeader;
  @SuppressWarnings("NullableProblems")
  @NonNull
  private CategoriesAdapter mFeaturedAdapter;
  @SuppressWarnings("NullableProblems")
  @NonNull
  private CategoryTypesAdapter mTypesAdapter;
  private PlaceholderView mResultsPlaceholder;
  private ExtendedFloatingActionButton mShowOnMapFab;
  private FloatingActionButton mOpenContactPicker;

  @NonNull
  private SearchToolbarController mToolbarController;

  @SuppressWarnings("NullableProblems")
  @NonNull
  private SearchAdapter mSearchAdapter;

  private final List<RecyclerView> mAttachedRecyclers = new ArrayList<>();
  private final RecyclerView.OnScrollListener mRecyclerListener = new RecyclerView.OnScrollListener() {
    @Override
    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState)
    {
      if (newState == RecyclerView.SCROLL_STATE_DRAGGING)
        mToolbarController.deactivate();
    }
  };

  private final LastPosition mLastPosition = new LastPosition();
  private boolean mSearchRunning;
  private String mInitialQuery;
  @Nullable
  private String mInitialLocale;
  private boolean mInitialSearchOnMap = false;

  private final ActivityResultLauncher<Intent> startVoiceRecognitionForResult =
      registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                                activityResult -> mToolbarController.onVoiceRecognitionResult(activityResult));

  private final ActivityResultLauncher<Intent> mContactPickerLauncher =
          registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null)
            {
              mToolbarController.setQuery(Utils.getContactAddress(requireContext(), result.getData().getData()));
            }
          }
  );

  private final LocationListener mLocationListener = new LocationListener() {
    @Override
    public void onLocationUpdated(@NonNull Location location)
    {
      mLastPosition.set(location.getLatitude(), location.getLongitude());
    }
  };

  private static boolean doShowDownloadSuggest()
  {
    return (MapManager.nativeGetDownloadedCount() == 0 && !MapManager.nativeIsDownloading());
  }

  private void showDownloadSuggest()
  {
    final FragmentManager fm = getChildFragmentManager();
    final String fragmentName = CountrySuggestFragment.class.getName();
    Fragment fragment = fm.findFragmentByTag(fragmentName);

    if (fragment == null || fragment.isDetached() || fragment.isRemoving())
    {
      fragment = fm.getFragmentFactory().instantiate(requireActivity().getClassLoader(), fragmentName);
      fm.beginTransaction().add(R.id.download_suggest_frame, fragment, fragmentName).commit();
    }
  }

  private void hideDownloadSuggest()
  {
    if (!isAdded())
      return;

    final FragmentManager manager = getChildFragmentManager();
    final Fragment fragment = manager.findFragmentByTag(CountrySuggestFragment.class.getName());
    if (fragment != null && !fragment.isDetached() && !fragment.isRemoving())
      manager.beginTransaction().remove(fragment).commitAllowingStateLoss();
  }

  private void updateFrames()
  {
    // In category mode the bar's text filters categories; it isn't a place search.
    final boolean hasQuery = !mCategoryMode && mToolbarController.hasQuery();
    MaterialToolbar toolbar = mToolbarController.getToolbar();
    AppBarLayout.LayoutParams lp = (AppBarLayout.LayoutParams) toolbar.getLayoutParams();
    lp.setScrollFlags(hasQuery ? AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                                     | AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                               : 0);
    toolbar.setLayoutParams(lp);

    UiUtils.showIf(hasQuery, mResultsFrame);
    // Only one of results, start screen and category list shows at a time.
    UiUtils.showIf(!hasQuery && !mCategoryMode, mTabFrame);
    UiUtils.showIf(mCategoryMode, mCategoryFrame);
    UiUtils.showIf(hasQuery, mShowOnMapFab);

    if (hasQuery)
      mTabFrame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    else
    {
      mTabFrame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
      // A search may have just been added to Recent.
      if (mHistoryAdapter != null)
      {
        mHistoryAdapter.refresh();
        updateStartHeaders();
      }
    }
    UiUtils.showIf(!hasQuery && !mCategoryMode && compatibleContactsAppInstalled, mOpenContactPicker);
    if (hasQuery || mCategoryMode)
      hideDownloadSuggest();
    else if (doShowDownloadSuggest())
      showDownloadSuggest();
    else
      hideDownloadSuggest();
  }

  private void updateResultsPlaceholder()
  {
    final boolean show = !mSearchRunning && mSearchAdapter.getItemCount() == 0 && mToolbarController.hasQuery();

    UiUtils.showIf(show, mResultsPlaceholder);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.fragment_search, container, false);
  }

  @CallSuper
  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    mSearchAdapter = new SearchAdapter(this);
    readArguments();

    ViewGroup root = (ViewGroup) view;
    mTabFrame = root.findViewById(R.id.tab_frame);

    mToolbarController = new ToolbarController(view);
    setUpStartList((RecyclerView) mTabFrame);
    mCategoryFrame = root.findViewById(R.id.category_frame);
    setUpCategoryList((RecyclerView) mCategoryFrame);

    mResultsFrame = root.findViewById(R.id.results_frame);
    RecyclerView mResults = mResultsFrame.findViewById(R.id.recycler);
    setRecyclerScrollListener(mResults);
    mResultsPlaceholder = mResultsFrame.findViewById(R.id.placeholder);
    mResultsPlaceholder.setContent(R.string.search_not_found, R.string.search_not_found_query,
                                   R.drawable.ic_search_fail);
    mSearchAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver()

                                               {
                                                 @Override
                                                 public void onChanged()
                                                 {
                                                   updateResultsPlaceholder();
                                                 }
                                               });
    mShowOnMapFab = root.findViewById(R.id.show_on_map_fab);
    mShowOnMapFab.setOnClickListener(v -> showAllResultsOnMap());
    mOpenContactPicker = root.findViewById(R.id.open_contact_picker);
    mOpenContactPicker.setOnClickListener(v -> {
      if (Utils.openContactPicker().resolveActivity(getContext().getPackageManager()) != null) {
        mContactPickerLauncher.launch(Utils.openContactPicker());
      }
      else {
        Logger.i("mOpenContactPicker", "No compatible contacts app (needs to support postal-address_v2) installed, hiding contacts picker...");
        mOpenContactPicker.hide();
      }
    });

    if (Utils.openContactPicker().resolveActivity(getContext().getPackageManager()) == null) {
      Logger.i("mOpenContactPicker", "No compatible contacts app (needs to support postal-address_v2) installed, hiding contacts picker...");
      compatibleContactsAppInstalled = false;
    }

    ViewCompat.setOnApplyWindowInsetsListener(mResultsPlaceholder, new WindowInsetUtils.ScrollableContentInsetsListener(mResultsPlaceholder, mOpenContactPicker));
    // Keep the last category row clear of the floating contacts button.
    ViewCompat.setOnApplyWindowInsetsListener(mTabFrame, new WindowInsetUtils.ScrollableContentInsetsListener((RecyclerView) mTabFrame, mOpenContactPicker));

    mResults.setLayoutManager(new LinearLayoutManager(view.getContext()));
    mResults.setAdapter(mSearchAdapter);

    updateFrames();
    updateResultsPlaceholder();
    ViewCompat.setOnApplyWindowInsetsListener(
        mResults, new WindowInsetUtils.ScrollableContentInsetsListener(mResults, mShowOnMapFab));

    mToolbarController.activate();

    SearchEngine.INSTANCE.addListener(this);

    if (mInitialSearchOnMap)
      showAllResultsOnMap();
  }

  @Override
  public void onStart()
  {
    super.onStart();
    mToolbarController.attach(requireActivity());
  }

  public void onResume()
  {
    super.onResume();
    MwmApplication.from(requireContext()).getLocationHelper().addListener(mLocationListener);
    if (mInitialQuery != null)
    {
      setQuery(mInitialQuery, false);
      mInitialQuery = null;
    }
  }

  @Override
  public void onPause()
  {
    MwmApplication.from(requireContext()).getLocationHelper().removeListener(mLocationListener);
    super.onPause();
  }

  @Override
  public void onStop()
  {
    super.onStop();
    mToolbarController.detach();
  }

  @Override
  public void onDestroy()
  {
    for (RecyclerView v : mAttachedRecyclers)
      v.removeOnScrollListener(mRecyclerListener);

    mAttachedRecyclers.clear();
    SearchEngine.INSTANCE.removeListener(this);
    super.onDestroy();
  }

  private String getQuery()
  {
    return mToolbarController.getQuery();
  }
  private boolean isCategory()
  {
    return mToolbarController.isCategory();
  }
  void setQuery(String text, boolean isCategory)
  {
    mToolbarController.setQuery(text, isCategory);
  }

  private void readArguments()
  {
    final Bundle arguments = getArguments();
    if (arguments == null)
      return;

    mInitialQuery = arguments.getString(SearchActivity.EXTRA_QUERY);
    mInitialLocale = arguments.getString(SearchActivity.EXTRA_LOCALE);
    mInitialSearchOnMap = arguments.getBoolean(SearchActivity.EXTRA_SEARCH_ON_MAP);
  }

  private boolean tryRecognizeHiddenCommand(@NonNull String query)
  {
    for (HiddenCommand command : getHiddenCommands())
    {
      if (command.execute(query))
        return true;
    }

    return false;
  }

  @NonNull
  private List<HiddenCommand> getHiddenCommands()
  {
    if (mHiddenCommands.isEmpty())
    {
      mHiddenCommands.addAll(Arrays.asList(
          new BadStorageCommand("?emulateBadStorage", requireContext()), new JavaCrashCommand("?emulateJavaCrash"),
          new NativeCrashCommand("?emulateNativeCrash"), new PushTokenCommand("?pushToken")));
    }

    return mHiddenCommands;
  }

  void showSingleResultOnMap(@NonNull SearchResult result, int resultIndex)
  {
    final String query = getQuery();
    if (Config.isSearchHistoryEnabled())
      SearchRecents.add(query, requireContext());
    SearchEngine.INSTANCE.cancel();
    SearchEngine.INSTANCE.setQuery(query);

    if (RoutingController.get().isWaitingPoiPick())
    {
      final String subtitle = (result.description != null) ? result.description.localizedFeatureType : "";
      final String title = TextUtils.isEmpty(result.name) ? subtitle : result.name;

      final MapObject point =
          MapObject.createMapObject(FeatureId.EMPTY, MapObject.SEARCH, title, subtitle, result.lat, result.lon);
      RoutingController.get().onPoiSelected(point);
      // Choosing it as a route point is a deliberate action: keep it in Recent.
      RecentPlaces.saveNow(requireContext(), result);
    }
    else
    {
      // Only a candidate: saved to Recent if the user then acts on the place page.
      RecentPlaces.setCandidate(result);
      SearchEngine.INSTANCE.showResult(resultIndex);
    }

    mToolbarController.deactivate();

    if (requireActivity() instanceof SearchActivity)
      Utils.navigateToParent(requireActivity());
  }

  /// Opens a place from Recent the same way as a search result.
  private void showRecentPlace(@NonNull RecentPlaces.Entry place)
  {
    if (RoutingController.get().isWaitingPoiPick())
    {
      final MapObject point = MapObject.createMapObject(FeatureId.EMPTY, MapObject.SEARCH, place.title,
                                                        place.subtitle, place.lat, place.lon);
      RoutingController.get().onPoiSelected(point);
    }
    else if (place.canShowFeature())
      Framework.nativeShowFeature(place.featureId);
    else
      // Maps were updated since: the saved feature id may point elsewhere, so just go to the spot.
      Framework.nativeSetViewportCenter(place.lat, place.lon, RECENT_PLACE_ZOOM);

    mToolbarController.deactivate();
    if (requireActivity() instanceof SearchActivity)
      Utils.navigateToParent(requireActivity());
  }

  void showAllResultsOnMap()
  {
    SearchEngine.INSTANCE.updateViewportWithLastResults();

    // The previous search should be cancelled before the new one is started, since previous search
    // results are no longer needed.
    SearchEngine.INSTANCE.cancel();

    final String query = getQuery();
    if (Config.isSearchHistoryEnabled())
      SearchRecents.add(query, requireContext());
    mLastQueryTimestamp = System.nanoTime();

    SearchEngine.INSTANCE.searchInteractive(
        query, isCategory(),
        !TextUtils.isEmpty(mInitialLocale) ? mInitialLocale : Language.getKeyboardLocale(requireContext()),
        mLastQueryTimestamp, false /* isMapAndTable */);

    SearchEngine.INSTANCE.setQuery(query);
    Utils.navigateToParent(requireActivity());
  }

  private void onSearchEnd()
  {
    if (mSearchRunning && isAdded())
      updateSearchView();
  }

  private void updateSearchView()
  {
    mSearchRunning = false;
    mToolbarController.showProgress(false);
    updateFrames();
    updateResultsPlaceholder();
  }

  private void stopSearch()
  {
    SearchEngine.INSTANCE.cancel();
    updateSearchView();
  }

  private boolean isTabletSearch()
  {
    // TODO @yunitsky Implement more elegant solution.
    return requireActivity() instanceof MwmActivity;
  }

  private void runSearch()
  {
    // The previous search should be cancelled before the new one is started, since previous search
    // results are no longer needed.
    SearchEngine.INSTANCE.cancel();

    mLastQueryTimestamp = System.nanoTime();
    if (isTabletSearch())
    {
      SearchEngine.INSTANCE.searchInteractive(requireContext(), getQuery(), isCategory(), mLastQueryTimestamp,
                                              true /* isMapAndTable */);
    }
    else
    {
      if (!SearchEngine.INSTANCE.search(requireContext(), getQuery(), isCategory(), mLastQueryTimestamp,
                                        mLastPosition.valid, mLastPosition.lat, mLastPosition.lon))
      {
        return;
      }
    }

    mSearchRunning = true;
    mToolbarController.showProgress(true);

    updateFrames();
  }

  @Override
  public void onResultsUpdate(@NonNull SearchResult[] results, long timestamp)
  {
    if (!isAdded() || !mToolbarController.hasQuery())
      return;

    refreshSearchResults(results);
  }

  @Override
  public void onResultsEnd(long timestamp)
  {
    onSearchEnd();
  }

  @Override
  public void onSearchCategorySelected(@Nullable String category)
  {
    leaveCategoryMode(false);
    mToolbarController.setQuery(category, true);
  }

  @Override
  public void onAllCategoriesSelected()
  {
    enterCategoryMode();
  }

  private void refreshSearchResults(@NonNull SearchResult[] results)
  {
    mSearchRunning = true;
    updateFrames();
    mSearchAdapter.refreshData(results);
    mToolbarController.showProgress(true);
  }

  @Override
  public boolean onBackPressed()
  {
    if (mCategoryMode)
    {
      leaveCategoryMode(true);
      return true;
    }

    if (mToolbarController.hasQuery())
    {
      mToolbarController.clear();
      return true;
    }

    boolean isSearchActivity = requireActivity() instanceof SearchActivity;
    mToolbarController.deactivate();
    if (RoutingController.get().isWaitingPoiPick())
    {
      RoutingController.get().onPoiSelected(null);
      if (isSearchActivity)
        closeSearch();
      return !isSearchActivity;
    }

    if (isSearchActivity)
      closeSearch();
    return isSearchActivity;
  }

  private void closeSearch()
  {
    final Activity activity = requireActivity();
    activity.finish();
  }

  private void setUpStartList(@NonNull RecyclerView list)
  {
    final ConcatAdapter concat = new ConcatAdapter();
    // Recent follows the "Search history" privacy setting, like the old History tab.
    if (Config.isSearchHistoryEnabled())
    {
      final boolean showMyPosition = RoutingController.get().isWaitingPoiPick()
                                  && MwmApplication.from(requireContext()).getLocationHelper().getMyPosition() != null;
      mRecentHeader = new SectionHeaderAdapter(R.string.search_recent);
      mHistoryAdapter = new SearchHistoryAdapter(mToolbarController, this::showRecentPlace, showMyPosition);
      mHistoryAdapter.setOnCleared(this::updateStartHeaders);
      concat.addAdapter(mRecentHeader);
      concat.addAdapter(mHistoryAdapter);
    }
    mCategoriesHeader = new SectionHeaderAdapter(R.string.categories);
    mCategoriesAdapter = new CategoriesAdapter(this, CategoriesAdapter.COMPACT);
    mCategoriesAdapter.updateCategories(this);
    concat.addAdapter(mCategoriesHeader);
    concat.addAdapter(mCategoriesAdapter);

    final int span = getResources().getInteger(R.integer.search_category_span);
    mStartLayout = new GridLayoutManager(requireContext(), span);
    // Category tiles fill one cell each; headers and Recent rows take the full width.
    mStartLayout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize(int position)
      {
        int beforeTiles = mCategoriesHeader.getItemCount();
        if (mHistoryAdapter != null)
          beforeTiles += mRecentHeader.getItemCount() + mHistoryAdapter.getItemCount();
        return position >= beforeTiles ? 1 : mStartLayout.getSpanCount();
      }
    });
    mCategoriesAdapter.setCompactCount(span * 2);
    list.setLayoutManager(mStartLayout);
    list.setAdapter(concat);
    setRecyclerScrollListener(list);
    updateStartHeaders();
  }

  private void updateStartHeaders()
  {
    if (mHistoryAdapter != null && mRecentHeader != null)
    {
      final int count = mHistoryAdapter.getRecentCount();
      mRecentHeader.setVisible(count > 0);
      if (count > SearchHistoryAdapter.COLLAPSED_COUNT)
      {
        final boolean expanded = mHistoryAdapter.isExpanded();
        mRecentHeader.setAction(expanded ? R.string.search_show_less : R.string.search_show_all, () -> {
          mHistoryAdapter.setExpanded(!mHistoryAdapter.isExpanded());
          updateStartHeaders();
        });
      }
      else
        mRecentHeader.setAction(0, null);
    }
  }

  /// "All categories": Featured tiles (built-in categories and Random), then every place type in sections.
  private void setUpCategoryList(@NonNull RecyclerView list)
  {
    mFeaturedHeader = new SectionHeaderAdapter(R.string.search_featured);
    mFeaturedAdapter = new CategoriesAdapter(this, CategoriesAdapter.FEATURED);
    mFeaturedAdapter.updateCategories(this);
    mTypesAdapter = new CategoryTypesAdapter(requireContext(), type -> onSearchCategorySelected(type.query + " "));
    final ConcatAdapter concat = new ConcatAdapter(mFeaturedHeader, mFeaturedAdapter, mTypesAdapter);

    final int span = getResources().getInteger(R.integer.search_category_span);
    mCategoryLayout = new GridLayoutManager(requireContext(), span);
    // Featured tiles fill one cell each; headers and type rows take the full width.
    mCategoryLayout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize(int position)
      {
        final int tilesStart = mFeaturedHeader.getItemCount();
        final boolean tile = position >= tilesStart && position < tilesStart + mFeaturedAdapter.getItemCount();
        return tile ? 1 : mCategoryLayout.getSpanCount();
      }
    });
    list.setLayoutManager(mCategoryLayout);
    list.setAdapter(concat);
    setRecyclerScrollListener(list);
  }

  /// The search bar switches to filtering categories; a "Categories" chip shows the mode.
  private void enterCategoryMode()
  {
    if (mCategoryMode)
      return;
    mQueryBeforeCategories = mToolbarController.getQuery();
    mCategoryMode = true;
    mSearchAdapter.clear();
    stopSearch();
    mToolbarController.showModeChip(R.string.search_categories_mode, () -> leaveCategoryMode(true));
    mToolbarController.setHint(R.string.search_filter_categories);
    mToolbarController.setQuery("");
    onCategoryFilterChanged("");
    ((RecyclerView) mCategoryFrame).scrollToPosition(0);
    updateFrames();
  }

  /// Back to place search. restoreQuery brings back what was typed before entering the mode.
  private void leaveCategoryMode(boolean restoreQuery)
  {
    if (!mCategoryMode)
      return;
    mCategoryMode = false;
    final String query = restoreQuery && mQueryBeforeCategories != null ? mQueryBeforeCategories : "";
    mQueryBeforeCategories = null;
    mToolbarController.showModeChip(0, null);
    mToolbarController.setHint(R.string.search);
    mToolbarController.setQuery(query);
    updateFrames();
  }

  private void onCategoryFilterChanged(@NonNull String filter)
  {
    mTypesAdapter.setFilter(filter);
    final boolean filtering = mTypesAdapter.isFiltered();
    mFeaturedHeader.setVisible(!filtering);
    mFeaturedAdapter.setHidden(filtering);
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig)
  {
    super.onConfigurationChanged(newConfig);
    final int span = getResources().getInteger(R.integer.search_category_span);
    if (mStartLayout != null && mCategoriesAdapter != null)
    {
      mStartLayout.setSpanCount(span);
      mCategoriesAdapter.setCompactCount(span * 2);
    }
    if (mCategoryLayout != null)
      mCategoryLayout.setSpanCount(span);
  }

  public void setRecyclerScrollListener(RecyclerView recycler)
  {
    recycler.addOnScrollListener(mRecyclerListener);
    mAttachedRecyclers.add(recycler);
  }

  @NonNull
  public SearchToolbarController requireController()
  {
    return mToolbarController;
  }

  private static class BadStorageCommand extends HiddenCommand.BaseHiddenCommand
  {
    @NonNull
    Context mContext;

    BadStorageCommand(@NonNull String command, @NonNull Context context)
    {
      super(command);
      mContext = context;
    }

    @Override
    void executeInternal()
    {
      SharedPropertiesUtils.setShouldShowEmulateBadStorageSetting(true);
    }
  }

  private static class JavaCrashCommand extends HiddenCommand.BaseHiddenCommand
  {
    JavaCrashCommand(@NonNull String command)
    {
      super(command);
    }

    @Override
    void executeInternal()
    {
      throw new RuntimeException("Diagnostic java crash!");
    }
  }

  private static class NativeCrashCommand extends HiddenCommand.BaseHiddenCommand
  {
    NativeCrashCommand(@NonNull String command)
    {
      super(command);
    }

    @Override
    void executeInternal()
    {
      Framework.nativeMakeCrash();
    }
  }

  private static class PushTokenCommand extends HiddenCommand.BaseHiddenCommand
  {
    PushTokenCommand(@NonNull String command)
    {
      super(command);
    }

    @Override
    void executeInternal()
    {}
  }
}
