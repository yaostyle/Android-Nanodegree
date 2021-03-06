package fyi.jackson.drew.popularmovies.fragment;

import android.graphics.Point;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatRatingBar;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.transition.TransitionInflater;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.util.List;

import fyi.jackson.drew.popularmovies.MainActivity;
import fyi.jackson.drew.popularmovies.R;
import fyi.jackson.drew.popularmovies.data.MovieContract;
import fyi.jackson.drew.popularmovies.model.Movie;
import fyi.jackson.drew.popularmovies.model.Review;
import fyi.jackson.drew.popularmovies.model.ReviewList;
import fyi.jackson.drew.popularmovies.model.Video;
import fyi.jackson.drew.popularmovies.model.VideoList;
import fyi.jackson.drew.popularmovies.network.MovieApiService;
import fyi.jackson.drew.popularmovies.recycler.ReviewAdapter;
import fyi.jackson.drew.popularmovies.recycler.VideoAdapter;
import fyi.jackson.drew.popularmovies.utils.MovieUtils;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static fyi.jackson.drew.popularmovies.MainActivity.API_BASE_URL;

public class MovieDetailFragment extends Fragment {

    public static final String TAG = MovieDetailFragment.class.getSimpleName();

    public static final String EXTRA_MOVIE_ITEM = "EXTRA_MOVIE_ITEM";
    public static final String EXTRA_TRANSITION_NAME = "EXTRA_TRANSITION_NAME";

    private MovieApiService apiService;
    private RecyclerView reviewRecycler;
    private RecyclerView videoRecycler;

    public MovieDetailFragment() {}

    public static MovieDetailFragment newInstance(Movie movie, String transitionName) {
        MovieDetailFragment movieDetailFragment = new MovieDetailFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_MOVIE_ITEM, movie);
        bundle.putString(EXTRA_TRANSITION_NAME, transitionName);
        movieDetailFragment.setArguments(bundle);
        return movieDetailFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        postponeEnterTransition();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setSharedElementEnterTransition(
                    TransitionInflater.from(getContext())
                            .inflateTransition(android.R.transition.move));
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_movie_detail, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHasOptionsMenu(true);

        MainActivity fragmentActivity = (MainActivity) getActivity();
        fragmentActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        fragmentActivity.enableAppBar();
        fragmentActivity.appBarLayout.setExpanded(true);
        fragmentActivity.trailerButton.setVisibility(View.INVISIBLE);

        Point screenSize = new Point();
        fragmentActivity.getWindowManager().getDefaultDisplay().getSize(screenSize);

        final Movie movie = getArguments().getParcelable(EXTRA_MOVIE_ITEM);
        String transitionName = getArguments().getString(EXTRA_TRANSITION_NAME);

        fragmentActivity.toolbarLayout.setTitle(movie.getTitle());
        final FloatingActionButton fab = fragmentActivity.fab;
        fab.setImageResource(
                movie.isFavorite() ?
                        R.drawable.ic_favorite_black_24dp :
                        R.drawable.ic_favorite_border_black_24dp);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                movie.setFavorite(!movie.isFavorite());
                updateDb(movie);
                if (movie.isFavorite()) {
                    playAvd(fab,
                            R.drawable.avd_heart_border_to_heart,
                            R.drawable.ic_favorite_black_24dp);
                } else {
                    playAvd(fab,
                            R.drawable.avd_heart_to_heart_border,
                            R.drawable.ic_favorite_border_black_24dp);
                }
            }
        });

        String posterUrl = MovieUtils.buildPosterUrl(movie.getPosterPath(), MovieUtils.API_POSTER_SIZE_W342);
        String backdropUrl = MovieUtils.buildPosterUrl(movie.getBackdropPath(), screenSize.x);

        TextView plotTextView = view.findViewById(R.id.tv_plot);
        plotTextView.setText(movie.getOverview());

        float voteAverage = (float) (movie.getVoteAverage() / 2);

        TextView voteAverageTextView = view.findViewById(R.id.tv_vote_average);
        voteAverageTextView.setText(getString(R.string.template_vote_average, voteAverage));

        int voteCount = movie.getVoteCount();
        String voteCountString = getResources().getQuantityString(
                R.plurals.template_vote_count, voteCount, voteCount);

        TextView voteCountTextView = view.findViewById(R.id.tv_vote_count);
        voteCountTextView.setText(voteCountString);

        TextView releasedDateTextView = view.findViewById(R.id.tv_release_date);
        releasedDateTextView.setText(MovieUtils.formatDateString(movie.getReleaseDate()));

        ImageView posterImageView = view.findViewById(R.id.iv_poster);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            posterImageView.setTransitionName(transitionName);
        }

        reviewRecycler = view.findViewById(R.id.rv_reviews);
        videoRecycler = view.findViewById(R.id.rv_videos);

        Picasso.get()
                .load(posterUrl)
                .noFade()
                .into(posterImageView, new Callback() {
                    @Override
                    public void onSuccess() {
                        startPostponedEnterTransition();
                    }

                    @Override
                    public void onError(Exception e) {
                        startPostponedEnterTransition();
                    }
                });


        Picasso.get()
                .load(backdropUrl)
                .into(fragmentActivity.appBarImageView);

        setupRetrofit();
        loadReviews(movie);
        loadVideos(movie, fragmentActivity.trailerButton);

        fragmentActivity.checkNetworkConnection();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getFragmentManager().popBackStack();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void playAvd(FloatingActionButton fab, int avdRes, int fallbackRes) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            AnimatedVectorDrawable avd = (AnimatedVectorDrawable) getResources().getDrawable(avdRes);
            fab.setImageDrawable(avd);
            avd.start();
        } else {
            fab.setImageResource(fallbackRes);
        }
    }

    private void updateDb(final Movie movie) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int rowsUpdated = getContext()
                        .getContentResolver()
                        .update(
                                MovieContract.MovieEntry.CONTENT_URI,
                                movie.toContentValues(),
                                MovieContract.MovieEntry.getSqlWhereClauseForMovie(movie),
                                null);
                Log.d(TAG, "updateDb: Rows changed: " + rowsUpdated);
            }
        }).start();
    }

    private void setupRetrofit() {
        // Add the interceptor to OkHttpClient
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.interceptors().add(MovieUtils.apiKeyInterceptor(getContext()));
        OkHttpClient client = builder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(API_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
        apiService = retrofit.create(MovieApiService.class);
    }

    private void loadReviews(Movie movie) {
        Call<ReviewList> reviewListCall = apiService.getReviews(movie.getId());
        reviewListCall.enqueue(new retrofit2.Callback<ReviewList>() {
            @Override
            public void onResponse(Call<ReviewList> call, Response<ReviewList> response) {
                setupReviewRecycler(response.body().getResults());
            }

            @Override
            public void onFailure(Call<ReviewList> call, Throwable t) {

            }
        });
    }

    private void loadVideos(Movie movie, final ImageView trailerButton) {
        Call<VideoList> videoListCall = apiService.getVideos(movie.getId());
        videoListCall.enqueue(new retrofit2.Callback<VideoList>() {
            @Override
            public void onResponse(Call<VideoList> call, final Response<VideoList> response) {
                if (response.body().getResults().size() > 0) {
                    trailerButton.setVisibility(View.VISIBLE);
                    trailerButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            MovieUtils.launchVideo(
                                    getContext(), response.body().getResults().get(0).getKey());
                        }
                    });
                }
                setupVideoRecycler(response.body().getResults());
            }

            @Override
            public void onFailure(Call<VideoList> call, Throwable t) {

            }
        });
    }

    private void setupReviewRecycler(List<Review> reviewList) {
        ReviewAdapter reviewAdapter = new ReviewAdapter(reviewList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        reviewRecycler.setLayoutManager(layoutManager);
        reviewRecycler.setAdapter(reviewAdapter);
    }

    private void setupVideoRecycler(List<Video> videoList) {
        VideoAdapter videoAdapter = new VideoAdapter(videoList);
        StaggeredGridLayoutManager layoutManager =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        videoRecycler.setLayoutManager(layoutManager);
        videoRecycler.setAdapter(videoAdapter);
    }
}
