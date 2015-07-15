package com.geo.GeoQuake;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Class to be used for handling data fetching
 */
public class QuakeData {

    protected String usgsUrl;
    protected FeatureCollection mFeatureCollection = new FeatureCollection();
    protected IDataCallback mDataCallback;
    protected int mQuakeType;
    protected int mQuakeDuration;
    GeoQuakeDB mGeoQuakeDB;

    /**
     * Constructor... takes only the url for USGS, which should be a resource
     * @param usgsUrl
     */
    public QuakeData(String usgsUrl, int quakeDuration, int quakeType, IDataCallback dataCallback, Context context){
        this.usgsUrl = usgsUrl;
        this.mQuakeDuration = quakeDuration;
        this.mQuakeType = quakeType;
        this.mDataCallback = dataCallback;
        this.mGeoQuakeDB = new GeoQuakeDB(context);
    }

    /**
     *
     * @return
     */
    public FeatureCollection getFeatureCollection(){
        return mFeatureCollection;
    }

    /**
     *
     * @param context
     * @return
     */
    public void fetchData(Context context){
        processData(context);
    }

    /**
     *
     * @param context
     */
    private void processData(Context context) {
        try {
            new AsyncTask<URL, Void, FeatureCollection>() {
                @Override
                protected FeatureCollection doInBackground(URL... params) {
                    try {
                        return getJSON(new URL(usgsUrl + Utils.getURLFrag(
                                mQuakeType, mQuakeDuration, context)));
                    } catch (MalformedURLException me) {
                        return null;
                    }

                }

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                }

                @Override
                protected void onPostExecute(FeatureCollection featureCollection) {
                    super.onPostExecute(featureCollection);
                    mFeatureCollection = featureCollection;
                    mDataCallback.dataCallback();

                }
            }.execute(new URL(usgsUrl + Utils.getURLFrag(mQuakeType,
                    mQuakeDuration, context)));
        } catch (MalformedURLException me) {
            Log.e(me.getMessage(), "URL Problem...");

        }
    }

    /**
     *
     * @param url The url we'll use to fetch the data
     * @return A JSONObject containing the requested data
     */
    private FeatureCollection getJSON(URL url) {
        //TODO: refactor to get rid of deprecated libraries
        HttpClient client = new DefaultHttpClient();
        HttpResponse response;
        try {
            response = client.execute(new HttpGet(url.toURI()));
            StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                response.getEntity().writeTo(out);
                out.close();
                return new FeatureCollection(out.toString());
            } else {
                response.getEntity().getContent().close();
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
