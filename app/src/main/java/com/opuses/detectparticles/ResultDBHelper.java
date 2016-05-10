package com.opuses.detectparticles;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ResultDBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ParticleTestResult";
    private static final int DATABASE_VERSION = 1;

    ResultDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        ResultDB.onCreate(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ResultDB.onUpgrade(db, oldVersion, newVersion);
    }
}
