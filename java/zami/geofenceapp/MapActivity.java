package zami.geofenceapp;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class MapActivity extends AppCompatActivity{
    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 8; //random
    private static MapView map = null;
    private Button startButton;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private IMapController mapController;
    private final List<Polygon> circles = new ArrayList<>();
    private static final String AUTHORITY = "zami.geofenceapp";
    private static final Uri CENTERS_URI = Uri.parse("content://" + AUTHORITY + "/CENTERS");
    private static final Uri MARKERS_URI = Uri.parse("content://" + AUTHORITY + "/MARKERS");
    SharedPreferences sharedPreferences;
    private int sessionID;

    public static MapView getMap() {
        return map;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        sessionID = sharedPreferences.getInt("sessionID",0);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        //inflate and create the map
        setContentView(R.layout.activity_map);



        map = findViewById(R.id.map);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        mapController = map.getController();
        mapController.setZoom(17.5);
        GeoPoint startPoint = new GeoPoint(37.9623, 23.7009);
        mapController.setCenter(startPoint);
        setMarker(startPoint);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        locationListener = location -> {
            if (map!=null) {
                GeoPoint currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                mapController.setCenter(currentLocation);
                mapController.animateTo(currentLocation);
//              setMarker(currentLocation);
            }
        };

        startButton = findViewById(R.id.startButton);
        drawSession();
        startButton.setOnClickListener(view->{
            startButton.setEnabled(false); //disabling button after first use
            startService(new Intent(getApplicationContext(), TrackingService.class));
        });

        findViewById(R.id.cancelButton).setOnClickListener(view->{
            if (!startButton.isEnabled()){
                Log.e("cancelbuttondeletion","deleting session data");
                getContentResolver().delete(CENTERS_URI,"session_id = ?",new String[]{String.valueOf(sessionID)});
            }
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(),MainActivity.class);
            startActivity(intent);
            finish();
        });

        getLocation();

        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver(){
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint longPressLocation) { //pressing zoom buttons trigger longPressListener. Bug at: https://github.com/osmdroid/osmdroid/issues/1822
                Toast.makeText(getApplicationContext(), "Button Long Clicked", Toast.LENGTH_SHORT).show();
                //remove any circles close by, else add one.
                if(!removeCircularArea(longPressLocation)){
                    addCircularArea(longPressLocation, 100);
                }
                return true;
            }
        }));

    }

    //inserting centers to database using content resolver
    private void insertCenters(GeoPoint center){
            ContentValues values = new ContentValues();
            values.put("session_id", sessionID);
            values.put("lat", center.getLatitude());
            values.put("lon", center.getLongitude());
            getContentResolver().insert(CENTERS_URI, values);

    }

    private void addCircularArea(GeoPoint center, double radius) { //radius in meters
        // Create a list of GeoPoints to represent the vertices of the polygon
        List<GeoPoint> circlePoints = new ArrayList<>();
        int numberOfPoints = 100; // adjust this for smoother circles
        for (int i = 0; i < numberOfPoints; i++) {
            double angle = Math.toRadians((360.0 / numberOfPoints) * i);
            double lat = center.getLatitude() + ( (radius/1000) / 111.32) * Math.cos(angle);
            double lon = center.getLongitude() + ( (radius/1000) / (111.32 * Math.cos(Math.toRadians(center.getLatitude())))) * Math.sin(angle);
            circlePoints.add(new GeoPoint(lat,lon));
        }

        Polygon circlePolygon = new Polygon();
        circlePolygon.setPoints(circlePoints);
        map.getOverlays().add(circlePolygon);
        if(!startButton.isEnabled()){
           insertCenters(center);
        }
    }

    private boolean removeCircularArea(GeoPoint location) {
        if (!startButton.isEnabled()){
            deleteCenter(location);
        }
        // Iterate over the circle polygons and remove those that match the location
        boolean flag = false;
        Iterator<Polygon> iterator = circles.iterator();
        while (iterator.hasNext()) {
            Polygon polygon = iterator.next();
            if (polygon.isCloseTo(location,50,map)) { //pixel tolerance is indicative
                iterator.remove();
                map.getOverlays().remove(polygon);
                circles.remove(polygon);
                flag = true;
            }
        }
        return flag;
    }

    private void deleteCenter(GeoPoint location) {
        Cursor cursor = getContentResolver().query(CENTERS_URI, new String[]{"lat", "lon"}, "session_id = ?", new String[]{String.valueOf(sessionID)}, null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") double lat = cursor.getDouble(cursor.getColumnIndex("lat"));
                @SuppressLint("Range") double lon = cursor.getDouble(cursor.getColumnIndex("lon"));

                if (1000 * calculateDistance(location, new GeoPoint(lat, lon)) < 101) {
                    String whereClause = "session_id = ? AND lat = ? AND lon = ?";
                    String[] whereArgs = {String.valueOf(sessionID), String.valueOf(lat), String.valueOf(lon)};
                    getContentResolver().delete(CENTERS_URI, whereClause, whereArgs);
                }
            } while (cursor.moveToNext());
            cursor.close();
        }
    }

    private void setMarker(GeoPoint geoPoint) {
        Marker marker = new Marker(map);
        marker.setPosition(geoPoint);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(marker);
    }

    private void getLocation(){
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,3000,10,locationListener);
        } else{
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Configuration.getInstance().save(this, prefs);
        map.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions,grantResults);
        if (requestCode==REQUEST_PERMISSIONS_REQUEST_CODE){
            int count=0;
            for (String permission : permissions){
                if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[count] == PackageManager.PERMISSION_GRANTED){
                        getLocation();
                    }
                }
                count++;
            }
        }
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

    private void drawSession() {
        Cursor cursor = getContentResolver().query(MARKERS_URI, new String[]{"lat", "lon"}, "session_id = ?", new String[]{String.valueOf(sessionID)}, null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") double lat = cursor.getDouble(cursor.getColumnIndex("lat"));
                @SuppressLint("Range") double lon = cursor.getDouble(cursor.getColumnIndex("lon"));
                setMarker(new GeoPoint(lat,lon));
            } while (cursor.moveToNext());
            cursor.close();
        }

        cursor = getContentResolver().query(CENTERS_URI, new String[]{"lat", "lon"}, "session_id = ?", new String[]{String.valueOf(sessionID)}, null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") double lat = cursor.getDouble(cursor.getColumnIndex("lat"));
                @SuppressLint("Range") double lon = cursor.getDouble(cursor.getColumnIndex("lon"));
                addCircularArea(new GeoPoint(lat,lon),100);
            } while (cursor.moveToNext());
            cursor.close();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        locationManager.removeUpdates(locationListener);
    }
}