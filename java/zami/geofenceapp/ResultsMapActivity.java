package zami.geofenceapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.List;

public class ResultsMapActivity extends AppCompatActivity {
    SharedPreferences sharedPreferences;
    private int sessionID ;
    private MapView map = null;
    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 8; //random
    private LocationListener locationListener;
    private LocationManager locationManager;
    private IMapController mapController;
    private final List<Polygon> circles = new ArrayList<>();
    private final List<GeoPoint> circleCenters = new ArrayList<>();

    private static final String AUTHORITY = "zami.geofenceapp";
    private static final Uri CENTERS_URI = Uri.parse("content://" + AUTHORITY + "/CENTERS");
    private static final Uri MARKERS_URI = Uri.parse("content://" + AUTHORITY + "/MARKERS");



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_results_map);
        sharedPreferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        sessionID = getSessionID();
        map = findViewById(R.id.resultsMap);
        map.setTileSource(TileSourceFactory.MAPNIK);
        getSessionID();
        findViewById(R.id.mainMenuButton).setOnClickListener(view->{
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(),MainActivity.class);
            startActivity(intent);
            finish();
        });

        Button serviceButton = findViewById(R.id.restartServiceButton);
        serviceButton.setOnClickListener(view->{
            //if service is running, stop it and start it, else just start it
            if (isServiceRunning()) {
                stopService(new Intent( getApplicationContext(), TrackingService.class));
            }
            startService(new Intent(getApplicationContext(), TrackingService.class));

        });

        mapController = map.getController();
        mapController.setZoom(17.0);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        locationListener = location -> {
            if (map!=null) {
                GeoPoint currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                mapController.setCenter(currentLocation);
                mapController.animateTo(currentLocation);
                //setMarker(currentLocation);
                updateCircleColors(currentLocation);
            }
        };

        getLocation();

        if (map!=null){
            drawMap();
        }

    }

    private void drawMap() {
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

    private void addCircularArea(GeoPoint center, double radius) { //radius in meters
        // Create a list of GeoPoints to represent the vertices of the polygon
        List<GeoPoint> circlePoints = new ArrayList<>();
        int numberOfPoints = 100; //adjust this for smoother circles
        for (int i = 0; i < numberOfPoints; i++) {
            double angle = Math.toRadians((360.0 / numberOfPoints) * i);
            double lat = center.getLatitude() + ( (radius/1000) / 111.32) * Math.cos(angle);
            double lon = center.getLongitude() + ( (radius/1000) / (111.32 * Math.cos(Math.toRadians(center.getLatitude())))) * Math.sin(angle);
            circlePoints.add(new GeoPoint(lat,lon));
        }

        Polygon circlePolygon = new Polygon();
        circlePolygon.setPoints(circlePoints);
        map.getOverlays().add(circlePolygon);
        circles.add(circlePolygon);
        circleCenters.add(center);
    }

    /** @noinspection deprecation*/
    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)){
            if("zami.geofenceapp.TrackingService".equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
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

    private int getSessionID(){
        Cursor cursor = getContentResolver().query(CENTERS_URI, new String[]{"MAX(session_id)"}, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            sessionID = cursor.getInt(0);
            cursor.close();
        }
        Log.i("query result","last session id is :"+sessionID);
        return sessionID;
    }

    private void updateCircleColors(GeoPoint currentLocation){
        drawAllCirclesBlack();
        Polygon circle = findClosestPolygon(currentLocation);
        circle.getOutlinePaint().setColor(Color.RED);
    }
    private void drawAllCirclesBlack(){
        for (Polygon circle : circles) {
            circle.getOutlinePaint().setColor(Color.BLACK);
        }
    }

    public Polygon findClosestPolygon(GeoPoint currentLocation) {
        double minDistance = Double.MAX_VALUE;
        Polygon closestPolygon = null;

        // Iterate through all polygons
        for (Polygon circle : circles) {
            List<GeoPoint> points = circle.getActualPoints();
            // Iterate through all points of the polygon
            for (GeoPoint point : points) {
                // Calculate distance between current location and each point of the polygon
                double distance = currentLocation.distanceToAsDouble(point);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestPolygon = circle;
                }
            }
        }
        return closestPolygon;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        locationManager.removeUpdates(locationListener);
    }
}
