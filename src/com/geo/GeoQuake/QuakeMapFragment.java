package com.geo.GeoQuake;

import android.support.v4.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.HashMap;

import butterknife.ButterKnife;
import butterknife.Bind;
import butterknife.OnClick;


public class QuakeMapFragment extends Fragment {

    Bundle mBundle;
    HashMap<String, String> markerInfo = new HashMap<String, String>();
    SharedPreferences mSharedPreferences;
    SharedPreferences.Editor mSharedPreferencesEditor;
    boolean mRefreshMap = true;
    boolean mAsyncUnderway = false;

    private GoogleMap mMap;
    GeoQuakeDB mGeoQuakeDB;

    FeatureCollection mFeatureCollection;
    QuakeData mQuakeData;

    int mStrengthSelection = 4;
    int mDurationSelection = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mBundle = new Bundle();
        mSharedPreferences = getActivity().getSharedPreferences(Utils.QUAKE_PREFS, Context.MODE_PRIVATE);
        mGeoQuakeDB = new GeoQuakeDB(getActivity());



    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.map_fragment, container, false);
        ButterKnife.bind(this, view);


        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if(Utils.checkNetwork(getActivity())){
            if(mMap == null) {
                setUpMap();
            } else {
                mFeatureCollection = ((MainActivity)getActivity() ).getFeatures();
                placeMarkers();
            }
            //networkCheckFetchData();
        } else{
            Utils.connectToast(getActivity());
        }
    }

    /**
     * We're not using this method right now. That's how it goes.
     *
     * @return
     */
    public GoogleMapOptions mapOptions() {
        GoogleMapOptions opts = new GoogleMapOptions();
        opts.mapType(GoogleMap.MAP_TYPE_HYBRID).compassEnabled(true);
        return opts;
    }

    /**
     * Contains the basics for setting up the map.
     */
    private void setUpMap() {
        if (Utils.checkNetwork(getActivity())) {
            if (mMap == null) {
                ((SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.gmap)).getMapAsync(new OnMapReadyCallback() {
                    @Override
                    public void onMapReady(GoogleMap googleMap) {
                        mMap = googleMap;
                        postSyncMapSetup();
                    }
                });


            }

        } else {
            Utils.connectToast(getActivity());
        }
    }

    public void postSyncMapSetup() {
        if (mMap != null) {
            mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

            //check location permission
            PackageManager pm = getActivity().getPackageManager();
            int result = pm.checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION,
                    getActivity().getPackageName());

            if(PackageManager.PERMISSION_GRANTED == result) {
                mMap.setMyLocationEnabled(true);
            }

            mMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
                @Override
                public void onMyLocationChange(Location arg0) {
                    LatLng latLng = new LatLng(arg0.getLatitude(), arg0.getLongitude());
                    CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, getActivity().getResources().getInteger(R.integer.zoom_level));
                    mMap.animateCamera(cameraUpdate);
                    mMap.setOnMyLocationChangeListener(null);

                }
            });

            UiSettings settings = mMap.getUiSettings();
            settings.setCompassEnabled(true);
            settings.setMyLocationButtonEnabled(true);

            if(mFeatureCollection != null && mFeatureCollection.getFeatures().size() > 0) {
                Log.i("placeMarkers", "postSyncMapSetup");
                placeMarkers();
            }
        }
    }


    /**
     * The method that does the work of placing the markers on the map. Yes.
     */
    private void placeMarkers() {
        if (mFeatureCollection.getFeatures().size() == 0) {
            Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.empty_list)
                    , Toast.LENGTH_SHORT).show();
        } else {
            if(mMap != null) {
                mMap.clear();
            }
            try {
                if (mFeatureCollection != null && mMap != null) {
                    for (Feature feature : mFeatureCollection.getFeatures()) {
                        LatLng coords = new LatLng(feature.getLatitude(), feature.getLongitude());
                        BitmapDescriptor quakeIcon;
                        if (feature.getProperties().getMag() <= 1.00) {
                            quakeIcon = BitmapDescriptorFactory.fromResource(R.drawable.quake1);
                        } else if (feature.getProperties().getMag() <= 2.50) {
                            quakeIcon = BitmapDescriptorFactory.fromResource(R.drawable.quake2);
                        } else if (feature.getProperties().getMag() <= 4.50) {
                            quakeIcon = BitmapDescriptorFactory.fromResource(R.drawable.quake3);
                        } else {
                            quakeIcon = BitmapDescriptorFactory.fromResource(R.drawable.quake4);
                        }

                        Marker m = mMap.addMarker(new MarkerOptions().icon(quakeIcon).position(coords).title(feature.getProperties().getPlace()).snippet(getResources().getString(R.string.magnitude) + feature.getProperties().getMag()));
                        markerInfo.put(m.getId(), feature.getProperties().getUrl());

                        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                            @Override
                            public void onInfoWindowClick(Marker marker) {
                                Intent intent = new Intent(getActivity(), WebInfoActivity.class);
                                intent.putExtra("url", getURLFromMarker(marker.getId()));
                                startActivity(intent);
                            }
                        });
                    }
                }
            } catch (Exception me) {
                me.printStackTrace();
            }
        }
    }


    /**
     * @param id The map marker id
     * @return URL value in hashmap
     */
    private String getURLFromMarker(String id) {
        return markerInfo.get(id);
    }

    /**
     * @param config
     */
    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        //TODO: Possible actions for orientation change
//        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            //Log.i("config", "landscape");
//        } else if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
//            //Log.i("config", "portrait");
//        }
    }

    /**
     * @param outState
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle("mBundle", mBundle);
    }

    public void updateData(FeatureCollection data) {
        mFeatureCollection = data;
        Log.i("placeMarkers", "updateData");
        placeMarkers();
    }

}