package com.opuses.detectparticles;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class ResultDB {

    private static final String LOG_TAG = "ResultDB";

    public static final String TABLE_NAME = "TestReport";

    public static final String KEY_ROWID = "_id";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_LONGITUDE = "longitude";
    public static final String KEY_LATITUDE = "latitude";
    public static final String KEY_REFERENCE = "reference";
    public static final String KEY_COMMENTS = "comments";
    public static final String KEY_TRANSFERRED = "transferred";
    public static final String KEY_INFO_FILENAME = "info_filename";
    public static final String KEY_DNG_FILENAME = "dng_filename";

    private static final String DATABASE_CREATE =
            "CREATE TABLE if not exists " + TABLE_NAME + " (" +
                    KEY_ROWID + " integer PRIMARY KEY autoincrement," +
                    KEY_TIMESTAMP + "," +
                    KEY_LATITUDE + "," +
                    KEY_LONGITUDE + "," +
                    KEY_REFERENCE + "," +
                    KEY_COMMENTS + "," +
                    KEY_TRANSFERRED + "," +
                    KEY_INFO_FILENAME + "," +
                    KEY_DNG_FILENAME + "," +
                    " UNIQUE (" + KEY_TIMESTAMP +"));";

    public static void onCreate(SQLiteDatabase db) {
        Log.w(LOG_TAG, DATABASE_CREATE);
        db.execSQL(DATABASE_CREATE);
    }

    public static void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(LOG_TAG, "Upgrading database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

}