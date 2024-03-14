package zami.geofenceapp;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;


public class MainActivity extends AppCompatActivity{
    SharedPreferences sharedPreferences;
    private int sessionID;
    SharedPreferences.Editor editor;
    GpsReceiver gpsReceiver = new GpsReceiver();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getApplicationContext().getPackageName());
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationManager.MODE_CHANGED_ACTION);



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(gpsReceiver,filter, Context.RECEIVER_NOT_EXPORTED);
        } else registerReceiver(gpsReceiver,filter);




        sharedPreferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        sessionID = sharedPreferences.getInt("sessionID",0);
        setContentView(R.layout.activity_main);
        findViewById(R.id.mapButton).setOnClickListener(view->{
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(),MapActivity.class);
            startActivity(intent);
        });

        Button stopTrackingButton = findViewById(R.id.stopTrackingButton);
        stopTrackingButton.setOnClickListener(view->{
            stopService(new Intent( getApplicationContext(), TrackingService.class));
            sessionID++; //when process is killed or when button stopService is stopped by main
            editor.putInt("sessionID", sessionID);
            editor.apply();
        });

        findViewById(R.id.resultsButton).setOnClickListener(view->{
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(),ResultsMapActivity.class);
            startActivity(intent);
        });


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(gpsReceiver);
    }
}