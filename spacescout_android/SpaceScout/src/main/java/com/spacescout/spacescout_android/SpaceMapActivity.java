package com.spacescout.spacescout_android;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.algo.PreCachingAlgorithmDecorator;
import com.google.maps.android.ui.IconGenerator;
import com.spacescout.spacescout_android.TouchableWrapper.UpdateMapAfterUserInteraction;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.WeakHashMap;
/**
 *
 * Created by ajay alfred on 11/5/13.
 * Modified by azri92.
 *
 * This class displays the map and its markers/clusters
 * It is extending a fragment so that it can be embedded into the MainActivity
 * It is implementing the UpdateMapAfterUserInteraction class for Callback Method for Map touch or zoom change
 *
 */
public class SpaceMapActivity extends Fragment implements UpdateMapAfterUserInteraction {

    static final LatLng UnivWashington = new LatLng(47.655263166697765, -122.30669233862307);

    private GoogleMap map;
    private MapFragment mapFragment;
    private FragmentManager fm;
    private View view;

    public TouchableWrapper mTouchView;
    public static boolean mMapIsTouched = false;
    public float zoomLevel = 0;
    public IconGenerator tc;
    public ClusterManager<MyItem> mClusterManager;
    public JSONArray mJson;
    public WeakHashMap<String, AlertDialog> alertDialogues;
    public WeakHashMap<String, Toast> toasts;

    final String url = "http://ketchup.eplt.washington.edu:8000/api/v1/spot/all";
    private JSONParser jParser;

    public SpaceMapActivity() {
        ///empty constructor required for fragment subclasses
    }

    // This is the default method needed for Android Fragments
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        fm = getFragmentManager();
        mapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        alertDialogues = new WeakHashMap<>();
        toasts = new WeakHashMap<>();

        // TODO: handle map fragment null error for API 21
        if(mapFragment == null)
        {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().replace(R.id.map, mapFragment).commit();
        }
        map = mapFragment.getMap();

        UiSettings uiSettings = map.getUiSettings();
        uiSettings.setZoomControlsEnabled(false);

        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(UnivWashington, 17.2f));
        Log.d("SpaceMapActivity", "checking");

        tc = new IconGenerator(getActivity());

        jParser = new JSONParser(getActivity());
        connectToServer();
    }

    public void connectToServer() {
        new getJson().execute();
    }

    // Setting up the ClusterManager which would contain all the clusters
    // This is only used by DisplayClustersByDistance() method
    public void setUpClusterer() {

        // Initialize the manager with the context and the map.
        // (Activity extends context, so we can pass 'this' in the constructor.)
        mClusterManager = new ClusterManager<MyItem>(this.getActivity(), map);

        // Point the map's listeners at the listeners implemented by the cluster
        // manager.
        map.setOnCameraChangeListener(mClusterManager);
        map.setOnMarkerClickListener(mClusterManager);

    }

    // This method is being implemented as part of the interface UpdateMapAfterUserInteraction
    // This is essentially a callback for touch event on the map
    public void onUpdateMapAfterUserInteraction() {
        System.out.println("hello");
    }

    // This is the default method needed for Android Frafments
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle bundle) {
        super.onCreate(bundle);
        if(view == null)
            view = inflater.inflate(R.layout.fragment_space_map, container, false);
        mTouchView = new TouchableWrapper(getActivity());
        mTouchView.addView(view);
        return mTouchView;
    }

    // This is the default method needed for Android Fragments
    @Override
    public void onResume() {
        super.onResume();
        setUpMap();
    }

    // Setting up a Map
    private void setUpMap() {
        if (map != null)
            return;
        map = mapFragment.getMap();
        if(map == null)
            return;
    }

    // This adds a marker to the map with IconGenerator class
    // The method takes the LatLng object location and text to be put on the marker/cluster as an Integer
    protected void addMarkerToMap(LatLng loc, int text) {
        IconGenerator iconFactory = new IconGenerator(getActivity());
        addIcon(iconFactory, Integer.toString(text), loc);
    }

    // This is the helper method for adding a marker to the map
    // This is invoked by addMarkerToMap
    private void addIcon(IconGenerator iconFactory, String text, LatLng position) {
        iconFactory.setStyle(IconGenerator.STYLE_PURPLE);
        MarkerOptions markerOptions = new MarkerOptions().
                icon(BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(text))).
                position(position).
                anchor(iconFactory.getAnchorU(), iconFactory.getAnchorV());

        map.addMarker(markerOptions);
    }

    // This method displays the clusters on the map by clustering by distance
    // It takes the json data as parameter
    public void DisplayClustersByDistance(JSONArray mJson){

        try {

            // Setting up cluster manager with the CustomClusteringAlgorithm Class
            setUpClusterer();
            mClusterManager.setAlgorithm(new PreCachingAlgorithmDecorator<MyItem>(new CustomClusteringAlgorithm<MyItem>()));

            LatLngBounds.Builder builder = new LatLngBounds.Builder();

            // Looping through JSON Data to add it to the Cluster Manager
            for(int i = 0; i < mJson.length(); i++){
                JSONObject curr = mJson.getJSONObject(i);
                JSONObject info = curr.getJSONObject("extended_info");

                JSONObject location = curr.getJSONObject("location");
                double lng = Double.parseDouble(location.getString("longitude"));
                double lat = Double.parseDouble(location.getString("latitude"));
                String name = curr.getString("name");
                String campus = info.getString("campus");

                if(campus.equals("seattle")){
                    LatLng currLoc = new LatLng(lat, lng);
                    mClusterManager.addItem(new MyItem(lat, lng, name));
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    // This method displays the clusters on the map by clustering by Building Names
    // It takes the json data as parameter
    public void DisplayClustersByBuilding(){

        // HashMap to keep track of all buildings with their building objects
        HashMap<String, Building> building_cluster = new HashMap<String, Building>();
        try {

            // Builder object to build bound for all clusters/markers
            LatLngBounds.Builder builder = new LatLngBounds.Builder();

            // Looping through all json data
            for(int i = 0; i < mJson.length(); i++){
                JSONObject curr = mJson.getJSONObject(i);
                JSONObject info = curr.getJSONObject("extended_info");

                JSONObject location = curr.getJSONObject("location");
                double lng = Double.parseDouble(location.getString("longitude"));
                double lat = Double.parseDouble(location.getString("latitude"));
                String name = curr.getString("name");
                String building_name = location.getString("building_name");
                String campus = info.getString("campus");

                if(campus.equals("seattle") && (!building_cluster.containsKey(building_name))){
                    LatLng currLoc = new LatLng(lat, lng);

                    // Creating Building Objects with location, building name
                    Building buil = new Building(currLoc, building_name, 1);
                    building_cluster.put(building_name, buil);
                    builder.include(currLoc);
                }else if(building_cluster.containsKey(building_name)){

                    // Increasing the number of spots in the current building
                    Building temp = building_cluster.get(building_name);
                    temp.increaseSpots();
                }
            }

            // Iterating through the hashmap of all buildings to add to the maps
            Iterator it = building_cluster.keySet().iterator();
            while (it.hasNext()) {
                String key = (String)it.next();
                Building b = building_cluster.get(key);
                addMarkerToMap(b.getPosition(), b.getSpots());
            }

            LatLngBounds bounds = builder.build();
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 70));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public JSONArray getJSONFromUrl() {
        JSONArray json = new JSONArray();
        try {
            json = jParser.getJSONFromUrl(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return json;
    }

    public int getHttpStatus() {
        return jParser.getStatusCode();
    }

    // A class to asynchronously get JSON data from API
    // Purposely wrote "Json" in titleCase to differentiate from Android methods
    public class getJson extends AsyncTask<String, String, JSONArray>  {
        private ProgressDialog pDialog;
        protected int statusCode;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected JSONArray doInBackground(String... args){
            // Getting JSON from URL
            JSONArray json = getJSONFromUrl();
            statusCode = getHttpStatus();

            return json;
        }

        @Override
        protected void onPostExecute(JSONArray json) {
            handleHttpResponse(statusCode, json);
        }
    }

    public void handleHttpResponse(int statusCode, JSONArray json) {
        // handle different status codes
        // only continue processing json if code 200
        switch (statusCode) {
            case 200:
                // CALLING THE CLUSTERING METHOD.
                // THIS CAN BE CHANGED TO DisplayClustersByDistance().
                if (json == null || json.length() == 0) {
                    Toast toast = Toast.makeText(getActivity(), "Sorry, no spaces found", Toast.LENGTH_SHORT);
                    toasts.put("Sorry, no spaces found" ,toast);
                    toast.show();
                } else {
                    mJson = json;
                    DisplayClustersByBuilding();
                }
                break;
            case 401:
                showStatusDialog("Authentication Issue", "Check key & secret.");
                break;
            default:
                showStatusDialog("Connection Issue", "Can't connect to server. Status code: " + statusCode + ".");
                break;
        }
    }

    // creates and shows a new dialog modal
    // let's user retry connecting to the server
    private void showStatusDialog(String title, String message) {
        AlertDialog.Builder dialogueBuilder = new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Try Again", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // retry
                        Log.d("oauth", "Retrying connection.");
                        connectToServer();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert);
        AlertDialog dialogue = dialogueBuilder.create();
        alertDialogues.put(title, dialogue);
        dialogue.show();

        Log.d("oauth", "Showing dialogue " + title);
    }

    public AlertDialog getUsedDialogue(String key) {
        return alertDialogues.get(key);
    }
}
