
package zami.geofenceapp;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class TrackingService extends Service {

    private MapView map ;
    private LocationManager locationManager;
    private LocationListener locationListener;
    GeoPoint currentLocation = null;
    SharedPreferences sharedPreferences;
    private int sessionID;
    private static final String AUTHORITY = "zami.geofenceapp";
    private static final Uri CENTERS_URI = Uri.parse("content://" + AUTHORITY + "/CENTERS");
    private static final Uri MARKERS_URI = Uri.parse("content://" + AUTHORITY + "/MARKERS");



    @Override
    public void onCreate() {
        Log.i("service","in service on create");
        super.onCreate();
        map = MapActivity.getMap();
        if (map!=null){
            map.setTileSource(TileSourceFactory.MAPNIK);
        }
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = location -> {
            currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
            if (map!=null) {
                compareLocationWithCenters(currentLocation);
            }else Log.e("service","map is null in service");

        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("service","in service on startcommand");
        map = MapActivity.getMap();
        getLocation();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i("service","in service on destroy");
        super.onDestroy();
        locationManager.removeUpdates(locationListener);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void getLocation(){
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,5000,50,locationListener);
        }
    }


    private void compareLocationWithCenters(GeoPoint currentLocation){
        Log.i("service","inside compare method");
        double distanceInMeters;
        Cursor cursor = getContentResolver().query(CENTERS_URI, new String[]{"lat", "lon"}, "session_id = ?", new String[]{String.valueOf(sessionID)}, null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") double lat = cursor.getDouble(cursor.getColumnIndex("lat"));
                @SuppressLint("Range") double lon = cursor.getDouble(cursor.getColumnIndex("lon"));
                distanceInMeters = 1000 * calculateDistance(currentLocation,new GeoPoint(lat,lon));
                if(distanceInMeters>98 && distanceInMeters<102){
                    Log.i("service","statement true, setting marker");
                    insertMarkers(currentLocation);
                    setMarker(currentLocation);
                }
            } while (cursor.moveToNext());
            cursor.close();
        }
    }

    private void insertMarkers(GeoPoint marker){
        ContentValues values = new ContentValues();
        values.put("session_id", sessionID);
        values.put("lat", marker.getLatitude());
        values.put("lon", marker.getLongitude());
        getContentResolver().insert(MARKERS_URI, values);
    }

    private double calculateDistance(GeoPoint geoPoint,GeoPoint geoPoint2) {
        // Radius of the Earth in kilometers
        double earthRadius = 6371.0;
        getLocation();
        // Convert degrees to radians
        double lat1 = Math.toRadians(geoPoint.getLatitude());
        double lon1 = Math.toRadians(geoPoint.getLongitude());
        double lat2 = Math.toRadians(geoPoint2.getLatitude());
        double lon2 = Math.toRadians(geoPoint2.getLongitude());
        // Haversine formula
        double lon_diff = lon2 - lon1;
        double lat_diff = lat2 - lat1;
        double a = Math.pow(Math.sin(lat_diff / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(lon_diff / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadius * c;
    }

    private void setMarker(GeoPoint geoPoint) {
        Marker marker = new Marker(map);
        marker.setPosition(geoPoint);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(marker);
    }
}

