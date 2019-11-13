package running.app;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

public class GpsService extends Service {
    private LocationListener listener;
    private LocationManager locationManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        listener = new LocationListener() {
            ArrayList<LatLng> arrayListLatLon = new ArrayList<>();

            @Override
            public void onLocationChanged(Location location) {
                arrayListLatLon.add(new LatLng(location.getLatitude(), location.getLongitude()));

                Intent arrayListIntent = new Intent("coordinatesUpdate");
                Bundle extra = new Bundle();
                extra.putSerializable("coordinateArray", arrayListLatLon);
                arrayListIntent.putExtra("extra", extra);
                sendBroadcast(arrayListIntent);
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {}

            @Override
            public void onProviderEnabled(String s) {}

            @Override
            public void onProviderDisabled(String s) {}
        };

        locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 5000, 10, listener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationManager != null){
            locationManager.removeUpdates(listener);
        }
    }
}