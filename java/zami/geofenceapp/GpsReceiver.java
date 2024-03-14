package zami.geofenceapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.util.Log;

public class GpsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(LocationManager.MODE_CHANGED_ACTION)) {
            if (!isGpsDisabled(context)) {
                Log.i("service","gps got Enabled, so turn on service");
                Intent serviceIntent = new Intent(context, TrackingService.class);
                context.startService(serviceIntent);
            } else {
                Log.i("service","gps got Disabled, so turn off service");
                Intent serviceIntent = new Intent(context, TrackingService.class);
                context.stopService(serviceIntent);
            }
        }
    }

    private boolean isGpsDisabled(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }
}
