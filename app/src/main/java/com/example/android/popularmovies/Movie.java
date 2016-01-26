package com.example.android.popularmovies;

import java.io.Serializable;

/**
 * Created by Bart on 23-Jan-16.
 */
public class Movie implements Serializable {
    // no serialVersionUID necessary, class is not going to change in between two activities
    public final static String SERIALIZABLE_KEY = "com.example.android.popularmovies.movie";

    public String poster;
    public String title;
    public String synopsis;
    public String rating;
    public String release_date;
}
