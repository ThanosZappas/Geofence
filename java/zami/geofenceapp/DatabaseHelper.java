package zami.geofenceapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "huafence.db";
    private static final int DATABASE_VERSION = 1;
    Context context;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String firstTableQuery = "CREATE TABLE IF NOT EXISTS CENTERS (_id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER, lat DOUBLE, lon DOUBLE);";
        String secondTableQuery = "CREATE TABLE IF NOT EXISTS MARKERS (_id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER, lat DOUBLE, lon DOUBLE);";
        db.execSQL(firstTableQuery);
        db.execSQL(secondTableQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(newVersion>oldVersion ) {
            db.execSQL("DROP TABLE IF EXISTS CENTERS;");
            db.execSQL("DROP TABLE IF EXISTS MARKERS;");
            onCreate(db);
        }
    }

}
