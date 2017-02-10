package tn.esprit.team.watchlistapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import tn.esprit.team.watchlistapp.ApiHelper;
import  tn.esprit.team.watchlistapp.R;
import tn.esprit.team.watchlistapp.VolleySingleton;
import  tn.esprit.team.watchlistapp.WatchlistApp;
import  tn.esprit.team.watchlistapp.ui.activity.MovieActivity;
import  tn.esprit.team.watchlistapp.ui.activity.MovieDetailActivity;
import  tn.esprit.team.watchlistapp.ui.adapter.MovieAdapter;
import  tn.esprit.team.watchlistapp.model.Movie;

import  tn.esprit.team.watchlistapp.util.TextUtil;

import  tn.esprit.team.watchlistapp.ui.view.PaddingDecorationView;

import org.json.JSONArray;
import org.json.JSONObject;

import butterknife.BindView;
import butterknife.BindBool;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

public class MovieListFragment extends Fragment implements MovieAdapter.OnMovieClickListener {

    private Context context;
    private Unbinder unbinder;

    private MovieAdapter adapter;
    private GridLayoutManager layoutManager;

    private int pageToDownload;
    private static final int TOTAL_PAGES = 999;

    private int viewType;
    private boolean isLoading;
    private boolean isLoadingLocked;
    @BindBool(R.bool.is_tablet) boolean isTablet;

    @BindView(R.id.error_message)       View errorMessage;
    @BindView(R.id.progress_circle)     View progressCircle;
    @BindView(R.id.loading_more)        View loadingMore;
    @BindView(R.id.swipe_refresh)       SwipeRefreshLayout swipeRefreshLayout;
    @BindView(R.id.movie_grid)          RecyclerView recyclerView;

    // Fragment lifecycle
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_movie_list,container,false);
        context = getContext();
        unbinder = ButterKnife.bind(this, v);

        // Initialize variables
        pageToDownload = 1;
        viewType = getArguments().getInt(WatchlistApp.VIEW_TYPE);

        // Setup RecyclerView
        adapter = new MovieAdapter(context, this);
        layoutManager = new GridLayoutManager(context, getNumberOfColumns());
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new PaddingDecorationView(context, R.dimen.recycler_item_padding));
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // Load more if RecyclerView has reached the end and isn't already loading
                if (layoutManager.findLastVisibleItemPosition() == adapter.movieList.size() - 1 && !isLoadingLocked && !isLoading) {
                    if (pageToDownload < TOTAL_PAGES) {
                        loadingMore.setVisibility(View.VISIBLE);
                        downloadMoviesList();
                    }
                }
            }
        });

        // Setup swipe refresh
        swipeRefreshLayout.setColorSchemeResources(R.color.accent);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Toggle visibility
                errorMessage.setVisibility(View.GONE);
                progressCircle.setVisibility(View.GONE);
                recyclerView.setVisibility(View.GONE);
                // Remove cache
                VolleySingleton.getInstance().requestQueue.getCache().remove(getUrlToDownload(1));
                // Download again
                pageToDownload = 1;
                adapter = null;
                downloadMoviesList();
            }
        });

        // Get the movies list
        if (savedInstanceState == null || !savedInstanceState.containsKey(WatchlistApp.MOVIE_LIST)) {
//            downloadMoviesList();
        } else {
            adapter.movieList = savedInstanceState.getParcelableArrayList(WatchlistApp.MOVIE_LIST);
            pageToDownload = savedInstanceState.getInt(WatchlistApp.PAGE_TO_DOWNLOAD);
            isLoadingLocked = savedInstanceState.getBoolean(WatchlistApp.IS_LOCKED);
            isLoading = savedInstanceState.getBoolean(WatchlistApp.IS_LOADING);
            // Download again if stopped, else show list
            if (isLoading) {
                if (pageToDownload == 1) {
                    progressCircle.setVisibility(View.VISIBLE);
                    loadingMore.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.GONE);
                    swipeRefreshLayout.setVisibility(View.GONE);
                } else {
                    progressCircle.setVisibility(View.GONE);
                    loadingMore.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.VISIBLE);
                    swipeRefreshLayout.setVisibility(View.VISIBLE);
                }
                downloadMoviesList();
            } else {
                onDownloadSuccessful();
            }
        }

        return v;
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (layoutManager != null && adapter != null) {
            outState.putBoolean(WatchlistApp.IS_LOADING, isLoading);
            outState.putBoolean(WatchlistApp.IS_LOCKED, isLoadingLocked);
            outState.putInt(WatchlistApp.PAGE_TO_DOWNLOAD, pageToDownload);
            outState.putParcelableArrayList(WatchlistApp.MOVIE_LIST, adapter.movieList);
        }
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
//
//        VolleySingleton.getInstance().requestQueue.cancelAll(this.getClass().getName());
        unbinder.unbind();
    }

    // JSON parsing and display
    public String getUrlToDownload(int page) {
        if (viewType == WatchlistApp.VIEW_TYPE_POPULAR) {
            return ApiHelper.getMostPopularMoviesLink(getActivity(), page);
        } else if (viewType == WatchlistApp.VIEW_TYPE_RATED) {
            return ApiHelper.getHighestRatedMoviesLink(getActivity(), page);
        } else if (viewType == WatchlistApp.VIEW_TYPE_UPCOMING) {
            return ApiHelper.getUpcomingMoviesLink(getActivity(), page);
        } else if (viewType == WatchlistApp.VIEW_TYPE_PLAYING) {
            return ApiHelper.getNowPlayingMoviesLink(getActivity(), page);
        }
        return null;
    }
    private void downloadMoviesList() {
        if (adapter == null) {
            adapter = new MovieAdapter(context, this);
            recyclerView.setAdapter(adapter);
        }
        String urlToDownload = getUrlToDownload(pageToDownload);
        final JsonObjectRequest request = new JsonObjectRequest (
                Request.Method.GET, urlToDownload, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        try {
                            JSONArray result = jsonObject.getJSONArray("results");
                            for (int i = 0; i < result.length(); i++) {
                                JSONObject movie = (JSONObject) result.get(i);
                                String poster = movie.getString("poster_path");
                                String overview = movie.getString("overview");
                                String year = movie.getString("release_date");
                                if (!TextUtil.isNullOrEmpty(year)) {
                                    year = year.substring(0, 4);
                                }
                                String id = movie.getString("id");
                                String title = movie.getString("title");
                                String backdrop = movie.getString("backdrop_path");
                                String rating = movie.getString("vote_average");

                                Movie thumb = new Movie(id, title, year, overview, rating, poster, backdrop);
                                adapter.movieList.add(thumb);
                            }

                            // Load detail fragment if in tablet mode
                            if (isTablet && pageToDownload == 1 && adapter.movieList.size() > 0) {
                                ((MovieActivity)getActivity()).loadDetailFragmentWith(adapter.movieList.get(0).id);
                            }

                            pageToDownload++;
                            onDownloadSuccessful();

                        } catch (Exception ex) {
                            // JSON parsing error
                            onDownloadFailed();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        // Network error
                        onDownloadFailed();
                    }
                });
        isLoading = true;
        request.setTag(this.getClass().getName());
        VolleySingleton.getInstance().requestQueue.add(request);
    }
    private void onDownloadSuccessful() {
        isLoading = false;
        errorMessage.setVisibility(View.GONE);
        progressCircle.setVisibility(View.GONE);
        loadingMore.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setRefreshing(false);
        swipeRefreshLayout.setEnabled(true);
        adapter.notifyDataSetChanged();
    }
    private void onDownloadFailed() {
        isLoading = false;
        if (pageToDownload == 1) {
            progressCircle.setVisibility(View.GONE);
            loadingMore.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            swipeRefreshLayout.setVisibility(View.GONE);
            errorMessage.setVisibility(View.VISIBLE);
        } else {
            progressCircle.setVisibility(View.GONE);
            loadingMore.setVisibility(View.GONE);
            errorMessage.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            swipeRefreshLayout.setVisibility(View.VISIBLE);
            swipeRefreshLayout.setRefreshing(false);
            swipeRefreshLayout.setEnabled(true);
            isLoadingLocked = true;
        }
    }

    // Helper methods
    public void refreshLayout() {
        Parcelable state = layoutManager.onSaveInstanceState();
        layoutManager = new GridLayoutManager(getContext(), getNumberOfColumns());
        recyclerView.setLayoutManager(layoutManager);
        layoutManager.onRestoreInstanceState(state);
    }
    public int getNumberOfColumns() {
        // Get screen width
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float widthPx = displayMetrics.widthPixels;
        if (isTablet) {
            widthPx = widthPx / 3;
        }
        // Calculate desired width
        SharedPreferences preferences = context.getSharedPreferences(WatchlistApp.TABLE_USER, Context.MODE_PRIVATE);
        if (preferences.getInt(WatchlistApp.VIEW_MODE, WatchlistApp.VIEW_MODE_GRID) == WatchlistApp.VIEW_MODE_GRID) {
            float desiredPx = getResources().getDimensionPixelSize(R.dimen.movie_card_width);
            int columns = Math.round(widthPx / desiredPx);
            return columns > 2 ? columns : 2;
        } else {
            float desiredPx = getResources().getDimensionPixelSize(R.dimen.movie_list_card_width);
            int columns = Math.round(widthPx / desiredPx);
            return columns > 1 ? columns : 1;
        }
    }

    // Click events
    @OnClick(R.id.try_again)
    public void onTryAgainClicked() {
        // Hide all views
        errorMessage.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        swipeRefreshLayout.setVisibility(View.GONE);
        // Show progress circle
        progressCircle.setVisibility(View.VISIBLE);
        // Try to download the data again
        pageToDownload = 1;
        adapter = null;
        downloadMoviesList();
    }
    @Override
    public void onMovieClicked(int position) {
        if (isTablet) {
            ((MovieActivity)getActivity()).loadDetailFragmentWith(adapter.movieList.get(position).id);
        } else {
            Intent intent = new Intent(context, MovieDetailActivity.class);
            intent.putExtra(WatchlistApp.MOVIE_ID, adapter.movieList.get(position).id);
            startActivity(intent);
        }
    }
}
