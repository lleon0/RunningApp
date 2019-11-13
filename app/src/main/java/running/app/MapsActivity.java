package running.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

@EActivity(R.layout.activity_maps)
public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private  BroadcastReceiver broadcastReceiver;
    private GoogleMap myMap;

    boolean isTrainingRunning = false;
    boolean isTrainingViewed = false;

    private Handler customHandler = new Handler();
    private long startTime = 0L;
    long timeInMilliseconds = 0L, timeSwapBuff = 0L, updatedTime = 0L;

    double distanceInKilometers;
    String Id = "id", Distance = "distance", Time = "time", AvgKmh = "avgKmh", Date = "date", Coordinates = "coordinates", fullDate; // Hashmap tunnisteita + //Päivämäärä jonka sovellus hakee käynnistäessä

    ArrayList<LatLng> coordinateArray = new ArrayList<>();
    DatabaseHelper myDb;

    @ViewById
    Button buttonStart, buttonViewData;

    @ViewById
    TextView tvTime, tvDistance, tvAvgSpeed;

    @Override
    protected void onResume() {
        super.onResume();
        if(broadcastReceiver == null){
            broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Bundle extra = intent.getBundleExtra("extra");
                    coordinateArray = (ArrayList<LatLng>) extra.getSerializable("coordinateArray");

                    if(coordinateArray != null){
                        PolylineOptions polylineOptions = new PolylineOptions().addAll(coordinateArray).color(Color.RED).width(6);
                        myMap.addPolyline(polylineOptions);

                        double totalDistanceInMeters = SphericalUtil.computeLength(coordinateArray);
                        distanceInKilometers = totalDistanceInMeters / 1000;
                        String distanceString = new DecimalFormat("#.##").format(distanceInKilometers);
                        distanceString = getString(R.string.distance)+ " " + distanceString;
                        tvDistance.setText(distanceString);
                        if(coordinateArray.size() > 2){
                            LatLng currentLocation;
                            currentLocation = coordinateArray.get(coordinateArray.size() - 1);
                            myMap.moveCamera(CameraUpdateFactory.newLatLng(currentLocation));
                        }
                    }
                }
            };
        }
        registerReceiver(broadcastReceiver, new IntentFilter("coordinatesUpdate"));
    }

    @AfterViews
    void setGoogleMaps() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        myDb = new DatabaseHelper(this);
        java.util.Date date = Calendar.getInstance().getTime();
        fullDate = (String) DateFormat.format("dd.MM.yyyy", date);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        myMap = googleMap;
        enableMyLocation();
    }

    @UiThread
    void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET
            }, 10);
        }
        if (myMap != null) {
            // Access to the location has been granted to the app.
            myMap.setMyLocationEnabled(true);

                LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
                if (locationManager != null) {
                    Location lastKnownLocationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastKnownLocationGPS != null) {
                        LatLng currentLocation = new LatLng(lastKnownLocationGPS.getLatitude(),lastKnownLocationGPS.getLongitude());
                        myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 13f));
                    }
                }
        }
    }

    @Click({R.id.buttonViewData})
    void viewData(){
        if (isTrainingViewed){
            closeViewMode();
            isTrainingViewed = false;
        }else {
            getData();
        }
    }

    @Click({R.id.buttonStart})
    void startTraining(){
        if (isTrainingRunning){
            isTrainingRunning = false;
            stopService(new Intent(this, GpsService.class));
            Toast.makeText(MapsActivity.this, "Service stopped", Toast.LENGTH_SHORT).show();
            timeSwapBuff = 0L;
            customHandler.removeCallbacks(updateTimerThread);


            String coordinates = "";
            if(coordinateArray.size() > 1){
                coordinates = convertCoordinatesToJSON(coordinateArray);
            }
            String distance = tvDistance.getText().toString();
            String time = tvTime.getText().toString();
            String avgKmh = tvAvgSpeed.getText().toString();
            String date = fullDate;
            addData(distance,time,avgKmh,date,coordinates);
            setUiToStoppedMode();
        }
        else{
            isTrainingRunning = true;
            startService(new Intent(this, GpsService.class));
            Toast.makeText(MapsActivity.this, "Service started", Toast.LENGTH_SHORT).show();

            startTime = SystemClock.uptimeMillis();
            customHandler.postDelayed(updateTimerThread, 500);
            setUiToRunningMode();
        }
    }

    private String convertCoordinatesToJSON(ArrayList<LatLng> coordinateArray){
        JSONObject locationsObj = new JSONObject();
        JSONArray locationArray = new JSONArray();
        try {
            for (int i = 0; i < coordinateArray.size(); i++){
                JSONObject locationObj = new JSONObject();
                String lat = String.valueOf(coordinateArray.get(i).latitude);
                String lng = String.valueOf(coordinateArray.get(i).longitude);
                locationObj.put("lat",lat);
                locationObj.put("lon",lng);
                locationArray.put(locationObj);
            }
            locationsObj.put("locations",locationArray);
            return locationsObj.toString();
        } catch (JSONException e){
            e.printStackTrace();
        }
        return "";
    }

    public void addData(String distance, String time, String avgKmh, String date, String coordinates){
        if(myDb.insertData(distance, time, avgKmh, date, coordinates)){
            Toast.makeText(MapsActivity.this,"Data Inserted", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(MapsActivity.this,"Error Inserting Data", Toast.LENGTH_LONG).show();
        }
    }

    protected void deleteData(String rowToDelete){
        if (myDb.deleteData(rowToDelete) > 0){
            Toast.makeText(MapsActivity.this,"Data Deleted", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(MapsActivity.this,"Data not deleted", Toast.LENGTH_LONG).show();
        }
    }

    @Background
    public void getData(){
        String title = "";
        ArrayList<HashMap<String, String>> dataList = new ArrayList<HashMap<String, String>>();
        try {
            Cursor res = myDb.getAllData();
            if(res.getCount() == 0){
                title = "No data found";
            } else {
                while (res.moveToNext()){
                    HashMap<String, String> data = new HashMap<String, String>();
                    data.put(Id , res.getString(0));
                    data.put(Distance, res.getString(1));
                    data.put(Time, res.getString(2));
                    data.put(AvgKmh, res.getString(3));
                    data.put(Date, "Date: "+ res.getString(4));
                    data.put(Coordinates, res.getString(5));
                    dataList.add(data);
                    title = "Training Data";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        showMessage(title,dataList);
    }

    @UiThread
    public void showMessage(String title, final ArrayList<HashMap<String, String>> dataList){
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View row = getLayoutInflater().inflate(R.layout.list_view_dialog,null);

        builder.setCancelable(true);
        builder.setView(row);
        final AlertDialog dialog = builder.show();

        Button button = row.findViewById(R.id.button);
        TextView textView = row.findViewById(R.id.title);
        final ListView l1 = row.findViewById(R.id.listView);
        textView.setText(title);

        final ListAdapter adapter = new SimpleAdapter(this, dataList, R.layout.custom_list_item,
                new String[] { Id, Distance, Time, AvgKmh, Date, Coordinates},
                new int[] { R.id.text1, R.id.text2, R.id.text3, R.id.text4, R.id.text5, R.id.text6});

        l1.setAdapter(adapter);

        l1.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String clickedIdDate = ((TextView) view.findViewById(R.id.text5)).getText().toString();
                Toast.makeText(MapsActivity.this,"Showing Data From "+ clickedIdDate, Toast.LENGTH_LONG).show();

                String distance = ((TextView) view.findViewById(R.id.text2)).getText().toString();
                String time = ((TextView) view.findViewById(R.id.text3)).getText().toString();
                String avgKmh = ((TextView) view.findViewById(R.id.text4)).getText().toString();
                String coordinates = ((TextView) view.findViewById(R.id.text6)).getText().toString();
                showDataOnMap(distance, time, avgKmh, coordinates);
                dialog.dismiss();
            }
        });

        l1.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(final AdapterView<?> parent, View view, final int position, long id) {
                final String clickedId = ((TextView) view.findViewById(R.id.text1)).getText().toString();

                AlertDialog.Builder adb = new AlertDialog.Builder(MapsActivity.this);
                adb.setMessage("Delete selected item?");
                adb.setCancelable(false);
                adb.setNegativeButton("Cancel", null);
                adb.setPositiveButton("Delete", new AlertDialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        deleteData(clickedId);
                        dataList.remove(position);
                        ((SimpleAdapter) adapter).notifyDataSetChanged();
                    }
                });
                adb.show();
                return true;
            }
        });

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    private Runnable updateTimerThread = new Runnable() {
        public void run() {
            //time calculator
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
            updatedTime = timeSwapBuff + timeInMilliseconds;
            int secs = (int) (updatedTime / 1000);
            int mins = secs / 60;
            int hours = mins / 60;
            secs = secs % 60;
            String time = getString(R.string.time) + " " + String.format(Locale.US,"%02d", hours) + ":"
                    + String.format(Locale.US,"%02d", mins) + ":" + String.format(Locale.US,"%02d", secs);
            tvTime.setText(time);

            double timeSeconds = (hours*3600) + (mins*60) + secs;
            tvAvgSpeed.setText(calculateSpeed(timeSeconds));

            customHandler.postDelayed(this, 500);
        }
    };

    private String calculateSpeed(double timeSeconds){
        double avgSpeedInDouble = ( distanceInKilometers ) / ( timeSeconds/3600.0f );
        return getString(R.string.avgSpeed) + " " + new DecimalFormat("#.##").format(avgSpeedInDouble);
    }

    @UiThread
    public void setUiToRunningMode(){
        buttonViewData.setVisibility(View.GONE);
        buttonStart.setText(getString(R.string.buttonStop));
    }

    @UiThread
    public void setUiToStoppedMode(){
        buttonViewData.setVisibility(View.VISIBLE);
        buttonStart.setText(getString(R.string.buttonStart));
        tvTime.setText(getString(R.string.time));
        tvDistance.setText(getString(R.string.distance));
        tvAvgSpeed.setText(getString(R.string.avgSpeed));
        myMap.clear();
    }

    @UiThread
    public void showDataOnMap(String distance, String time, String avgKmh, String coordinates){
        tvDistance.setText(distance);
        tvTime.setText(time);
        tvAvgSpeed.setText(avgKmh);

        buttonStart.setVisibility(View.GONE);
        buttonViewData.setText(getString(R.string.buttonCloseData));
        isTrainingViewed = true;

        ArrayList<LatLng> coordinatesToShow = new ArrayList<>();
        try {
            JSONObject coordinatesObject = new JSONObject(coordinates);
            JSONArray coordinatesArray = coordinatesObject.getJSONArray("locations");
            for (int i = 0; i < coordinatesArray.length(); i++) {
                JSONObject getCoordinateObject = coordinatesArray.getJSONObject(i);
                double lat = Double.parseDouble(getCoordinateObject.getString("lat"));
                double lng = Double.parseDouble(getCoordinateObject.getString("lon"));
                coordinatesToShow.add(new LatLng(lat, lng));
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        if(coordinatesToShow.size() >  1){
            PolylineOptions polyline_options = new PolylineOptions().addAll(coordinatesToShow).color(Color.RED).width(6);
            myMap.addPolyline(polyline_options);
        }
    }

    @UiThread
    public void closeViewMode(){
        setUiToStoppedMode();
        buttonStart.setVisibility(View.VISIBLE);
        buttonViewData.setText(getString(R.string.buttonViewData));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(broadcastReceiver != null){
            stopService(new Intent(this, GpsService.class));
            unregisterReceiver(broadcastReceiver);
        }
    }
}
