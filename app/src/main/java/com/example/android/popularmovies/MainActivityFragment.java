package com.example.android.popularmovies;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

/**
 * Displays grid of movie thumbnails, allows sorting by popularity or user rating.
 */
public class MainActivityFragment extends Fragment {


    private GridView mGridview;

    private enum SortOrder {
        Popularity,
        Rating
    }

    // TMDB API Strings
    public final static String API_KEY = "xxxx"; // TODO: Insert your TMDb API key here
    private final String STATE_SORT_KEY = "state_sort_key";
    private final String SORT_POPULARITY = "popularity.desc";
    private final String SORT_RATING = "vote_average.desc";
    private final String STATE_GRID_POSITION = "state_grid_position";

    private PostersAdapter mGridAdapter;
    private SortOrder mSortOrder;
    private int mScrollPos = -1;

    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        mGridview = (GridView) rootView.findViewById(R.id.gridview);
        mGridAdapter = new PostersAdapter(getActivity(), 0, new ArrayList<Movie>());
        mGridview.setAdapter(mGridAdapter);

        mGridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Movie movie = (Movie) mGridAdapter.getItem(position);

                Intent detail = new Intent(getActivity(), DetailsActivity.class);
                Bundle bundle = new Bundle();
                bundle.putSerializable(Movie.SERIALIZABLE_KEY, movie);
                detail.putExtras(bundle);
                startActivity(detail);
            }
        });

        // This does not work, seems to be a known bug:
        // https://stackoverflow.com/questions/14479078/smoothscrolltopositionfromtop-is-not-always-working-like-it-should/20997828#20997828
//        if (mScrollPos > 0)
//            mGridview.setSelection(mScrollPos);

        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_SORT_KEY))
            mSortOrder = SortOrder.values()[savedInstanceState.getInt(STATE_SORT_KEY)];
        else
            mSortOrder = SortOrder.Popularity;

        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_GRID_POSITION))
            mScrollPos = savedInstanceState.getInt(STATE_GRID_POSITION);

        setHasOptionsMenu(true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_SORT_KEY, mSortOrder.ordinal());
        int pos = mGridview.getFirstVisiblePosition();
        outState.putInt(STATE_GRID_POSITION, mGridview.getFirstVisiblePosition());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_fragment_main, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.popularity:
            case R.id.rating:
                item.setChecked(!item.isChecked());
                if (mSortOrder == SortOrder.Popularity)
                    mSortOrder = SortOrder.Rating;
                else
                    mSortOrder = SortOrder.Popularity;

                getMovies();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private class PostersAdapter extends ArrayAdapter {
        private Context mContext;
        private ArrayList<Movie> mMovies;
        private int posterWidth, posterHeight;

        public PostersAdapter(Context context, int resource, ArrayList<Movie> movies) {
            super(context, resource, movies);
            mContext = context;
            mMovies = movies;
            int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView = (ImageView) convertView;
            if (imageView == null) {
                imageView = new ImageView(mContext);
                imageView.setLayoutParams(new GridView.LayoutParams(300, 430));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
//                imageView.setPadding(4, 4, 4, 4);
            }

            String url = buildImageUrl(mMovies.get(position).poster);

            Picasso.with(mContext)
                    .load(url)
                    .error(R.drawable.unable_to_load)
                    .into(imageView);

            return imageView;
        }

        private String buildImageUrl(String poster){
            final String LOG_TAG = PostersAdapter.class.getSimpleName();

            String urlPoster = poster.substring(1);
            Uri.Builder builder = new Uri.Builder();

            builder.scheme("http")
                    .authority("image.tmdb.org")
                    .appendPath("t")
                    .appendPath("p")
                    .appendPath("w185") // options: w185, w342, w500
                    .appendPath(urlPoster)
                    .appendQueryParameter("api_key", API_KEY);

            URL url = null;
            try {
                url = new URL(builder.build().toString());
            } catch (MalformedURLException e) {
                Log.e(LOG_TAG, "Error ", e);
            }

            return url.toString();
        }
    }

    @Override
    public void onStart() {
        getMovies();
        super.onStart();
    }

    private void getMovies(){
        new FetchMoviesTask().execute(mSortOrder);
    }

    private class FetchMoviesTask extends AsyncTask<SortOrder, Void, ArrayList<Movie>> {

        private final String LOG_TAG = FetchMoviesTask.class.getSimpleName();

        @Override
        protected ArrayList<Movie> doInBackground(SortOrder ... params) {

            if (params.length < 1) // sort parameter
                return null;

            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            String moviesJsonStr = null;
            SortOrder order = params[0];
            String sort;
            if (order == SortOrder.Popularity)
                sort = SORT_POPULARITY;
            else
                sort = SORT_RATING;

            try {
                Uri.Builder builder = new Uri.Builder();

                // http://api.themoviedb.org/3/discover/movie?sort_by=popularity.desc&api_key=xxxxx

                builder.scheme("http")
                        .authority("api.tmdb.org")
                        .appendPath("3")
                        .appendPath("discover")
                        .appendPath("movie")
                        .appendQueryParameter("sort_by", sort)
                        .appendQueryParameter("api_key", API_KEY);

                URL url = new URL(builder.build().toString());

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    return null;
                }

                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n"); // Not necessary in JSON, but makes debugging easier
                }

                if (buffer.length() == 0) {
                    return null; // Don't parse
                }

                moviesJsonStr = buffer.toString();

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                return null; // Don't parse
            } finally{
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            ArrayList<Movie> movies = null;
            try {

                movies =  geMoviesDataFromJson(moviesJsonStr);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return movies;
        }

        @Override
        protected void onPostExecute(ArrayList<Movie> movies) {
            mGridAdapter.clear();
            for (Movie m : movies)
                mGridAdapter.add(m);

//            mListAdapter.notifyDataSetChanged(); -> called implicitely in ArrayAdapter, so not necessary

            super.onPostExecute(movies);
        }

        /**
         * Create JSON object hierarchy and parse movies data from it
         */
        private ArrayList<Movie> geMoviesDataFromJson(String moviesJsonStr)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String TMDB_TITLE = "original_title";
            final String TMDB_POSTER = "poster_path";
            final String TMDB_SYNOPSIS = "overview";
            final String TMDB_RATING = "vote_average";
            final String TMDB_RELEASEDATE = "release_date";
            final String TMDB_LIST = "results";

            JSONObject forecastJson = new JSONObject(moviesJsonStr);
            JSONArray moviesArray = forecastJson.getJSONArray(TMDB_LIST);

            ArrayList<Movie> movies = new ArrayList<Movie>();

            for(int i = 0; i < moviesArray.length(); i++) {

                JSONObject jsonMovie = moviesArray.getJSONObject(i);

                Movie movie = new Movie();
                movie.title = jsonMovie.getString(TMDB_TITLE);
                movie.poster = jsonMovie.getString(TMDB_POSTER);
                movie.synopsis = jsonMovie.getString(TMDB_SYNOPSIS);
                movie.rating = jsonMovie.getString(TMDB_RATING);
                movie.release_date = jsonMovie.getString(TMDB_RELEASEDATE);

                movies.add(movie);
            }

            return movies;

        }

    }

}


