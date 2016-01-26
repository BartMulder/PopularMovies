package com.example.android.popularmovies;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A placeholder fragment containing a simple view.
 */
public class DetailsActivityFragment extends Fragment {

    public DetailsActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_details, container, false);

        Movie mMovie = (Movie) getActivity().getIntent().getSerializableExtra(Movie.SERIALIZABLE_KEY);

        TextView title = (TextView) rootView.findViewById(R.id.title);
        TextView rating = (TextView) rootView.findViewById(R.id.user_rating);
        TextView release = (TextView) rootView.findViewById(R.id.release_date);
        TextView synopsis = (TextView) rootView.findViewById(R.id.synopsis);
        ImageView poster = (ImageView) rootView.findViewById(R.id.poster);

        title.setText(mMovie.title);
        rating.setText(getString(R.string.user_rating) + mMovie.rating);
        release.setText(getString(R.string.release_date) + localizeDate(mMovie.release_date));
        synopsis.setText(mMovie.synopsis);

        String url = buildImageUrl(mMovie.poster);

        Picasso.with(getActivity())
                .load(url)
                .error(R.drawable.unable_to_load)
                .into(poster);

        return rootView;
    }

    private String localizeDate(String sdate){
        String localDate = null;
        SimpleDateFormat tmdpFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat localFormat = new SimpleDateFormat();
        try {
            Date date = tmdpFormat.parse(sdate);
            localDate = localFormat.format(date);
            localDate = localDate.substring(0, localDate.indexOf(' ')); // remove time
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return localDate;
    }

    private String buildImageUrl(String poster){
        final String LOG_TAG = DetailsActivity.class.getSimpleName();

        String urlPoster = poster.substring(1);
        Uri.Builder builder = new Uri.Builder();

        builder.scheme("http")
                .authority("image.tmdb.org")
                .appendPath("t")
                .appendPath("p")
                .appendPath("w185") // options: w185, w342, w500
                .appendPath(urlPoster)
                .appendQueryParameter("api_key", MainActivityFragment.API_KEY);

        URL url = null;
        try {
            url = new URL(builder.build().toString());
        } catch (MalformedURLException e) {
            Log.e(LOG_TAG, "Error ", e);
        }

        return url.toString();
    }
}
