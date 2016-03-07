package com.geo.GeoQuake;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by gstinson on 2014-08-25.
 */
public class ListFragment extends Fragment {

    @Bind(R.id.quakeListView)
    ListView mQuakeListView;

    QuakeListAdapter mQuakeListAdapter;
    SharedPreferences mSharedPreferences;
    SharedPreferences.Editor mSharedPreferencesEditor;
    Bundle mBundle;

    GeoQuakeDB mGeoQuakeDB;

    FeatureCollection mFeatureCollection;
    ArrayList<Feature> mFeatureList;

    @Bind(R.id.search_bar)
    LinearLayout mSearchBar;

    @Bind(R.id.search_edit_text)
    EditText mSearchEditText;

    @Bind(R.id.count_textview)
    TextView mQuakeCountTextView;

    @Bind(R.id.search_image_button)
    ImageView mSearchImageButton;

    @Bind(R.id.proximity_image_button)
    ImageView mProximityImageButton;

    double mUserLatitude = 0.0;
    double mUserLongitude = 0.0;

    Context mContext;

    public static ListFragment newInstance() {
        return new ListFragment();
    }

    public ListFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mSharedPreferences = getActivity().getSharedPreferences(Utils.QUAKE_PREFS, Context.MODE_PRIVATE);
        mBundle = new Bundle();
        mGeoQuakeDB = new GeoQuakeDB(getActivity());

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.list_fragment, container, false);
        ButterKnife.bind(this, view);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if(!EventBus.getDefault().isRegistered(this)){
            EventBus.getDefault().register(this);
        }

        if (Utils.checkNetwork(getActivity())) {

            setupLocation();
            if (mFeatureList != null) {
                setupList();
            } else {
                ((MainActivity)getActivity()).checkNetworkFetchData();
            }
        } else {
            Utils.connectToast(getActivity());
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;

    }

    public void setupLocation() {
        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        Location location;
        try {
            location = locationManager.getLastKnownLocation(locationManager
                    .getBestProvider(new Criteria(), false));
            if (location != null) {
                mProximityImageButton.setVisibility(View.VISIBLE);
                mUserLatitude = location.getLatitude();
                mUserLongitude = location.getLongitude();
            } else {
                mProximityImageButton.setVisibility(View.GONE);
            }
        } catch (SecurityException se) {
            mProximityImageButton.setVisibility(View.GONE);
        }

    }

    /**
     * @param config
     */
    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        //TODO: Possible actions for orientation change
    }

    /**
     * @param outState
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle("mBundle", mBundle);
    }

    /**
     * This sorts the list and sets the adapter
     */
    public void setupList() {
        if (mFeatureList != null && mContext != null) {
            mQuakeCountTextView.setText(String.format(mContext.getResources().getString(R.string.quake_count), mFeatureList.size()));
            //TODO: This could use some cleaning up
            mQuakeListAdapter = new QuakeListAdapter(mContext, mFeatureList);
            mQuakeListView.setAdapter(mQuakeListAdapter);
            mQuakeListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Intent intent = new Intent(mContext, WebInfoActivity.class);
                    intent.putExtra("url", mFeatureList.get(position).getProperties().getUrl());
                    startActivity(intent);
                }
            });
            if (mFeatureList.size() == 0) {
                Toast.makeText(mContext, mContext.getResources().getString(R.string.empty_list)
                        , Toast.LENGTH_LONG).show();
            }
        } else {
            ((MainActivity)getActivity()).checkNetworkFetchData();
        }
    }

    /**
     * Sorting a feature collection.
     *
     * @param featureCollection
     */
    public void basicSort(FeatureCollection featureCollection) {
        Collections.sort(featureCollection.getFeatures(), new Comparator<Feature>() {
            @Override
            public int compare(Feature lhs, Feature rhs) {
                //Using Double's compare method makes this pretty straightforward.
                return Double.compare(rhs.getProperties().getMag(), lhs.getProperties().getMag());
            }
        });


    }

    /**
     * Sorting list by distance from user
     */
    public void sortByProximity() {
        Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.sorting_by_proximity)
                , Toast.LENGTH_SHORT).show();
        ArrayList<Feature> proximityList = new ArrayList<>();
        TreeMap<Float, Feature> proximityMap = new TreeMap<>();
        for (Feature feature : mFeatureList) {
            float[] results = new float[3];
            Location.distanceBetween(mUserLatitude, mUserLongitude, feature.getLatitude(),
                    feature.getLongitude(), results);
            proximityMap.put(results[0] / 1000, feature);
        }
        for (Map.Entry<Float, Feature> entry : proximityMap.entrySet()) {
            proximityList.add(entry.getValue());
        }
        mFeatureList.clear();
        mFeatureList = proximityList;
        setupList();
    }

    /**
     * Crude indeed. This won't be very robust if we're getting into unicode,
     * but we can pretty well assume that the USGS data isn't giving us any
     * oddball characters in the result list
     */
    public void doSearch(View view) {
        ArrayList<Feature> searchFeatures = new ArrayList<>();
        String searchTerm = mSearchEditText.getText().toString();
        for (Feature feature : mFeatureList) {
            //For "expected" input, this should handle cases
            if (feature.properties.getPlace().toLowerCase().contains(searchTerm)) {
                searchFeatures.add(feature);
            }
        }

        if (searchFeatures.size() == 0) {
            Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.empty_search_list)
                    , Toast.LENGTH_LONG).show();
        } else {
            mQuakeCountTextView.setText(String.format(getActivity().getResources().getString(R.string.quake_count), mFeatureList.size()));
            mFeatureList.clear(); //is this needed?
            mFeatureList = searchFeatures;
            mSearchEditText.setText("");
            mSearchBar.setVisibility(View.GONE);
            mSearchImageButton.setVisibility(View.VISIBLE);
            //close keyboard
            InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);

        }
        setupList();
    }

    public void updateData(FeatureCollection data) {
        mFeatureCollection = data;
        mFeatureList = mFeatureCollection.getFeatures();
        setupList();
    }

    @Subscribe
    public void onEvent(MainActivity.QuakeDataEvent event) {
        this.mFeatureCollection = event.getFeatureCollection();
        setupList();
    }

}