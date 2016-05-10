package com.opuses.detectparticles;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

/**
 * this class enable us to store result date in DB through ContentProvider
 */

public class ResultContentProvider extends ContentProvider {

    private ResultDBHelper dbHelper;

    private static final int ALL_RESULTS = 1;
    private static final int SINGLE_RESULT = 2;

    // the symbolic name of your provider -
    // To avoid conflicts with other providers
    private static final String AUTHORITY = "com.opuses.ResultContentProvider";

    // create content URIs from the authority by appending path to database table
    public static final Uri CONTENT_URI =
            Uri.parse("content://" + AUTHORITY + "/results");

    // a content URI pattern matches content URIs using wildcard characters:
    // *: Matches a string of any valid characters of any length.
    // #: Matches a string of numeric characters of any length.
    private static final UriMatcher uriMatcher;
    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, "results", ALL_RESULTS);
        uriMatcher.addURI(AUTHORITY, "results/#", SINGLE_RESULT);
    }

    @Override
    public boolean onCreate() {
        dbHelper = new ResultDBHelper(getContext());
        return false;
    }

    //Return the MIME type corresponding to a content URI
    @Override
    public String getType(@NonNull Uri uri) {

        switch (uriMatcher.match(uri)) {
            case ALL_RESULTS:
                return "vnd.android.cursor.dir/vnd.com.opuses.ResultContentProvider.results";
            case SINGLE_RESULT:
                return "vnd.android.cursor.item/vnd.com.opuses.ResultContentProvider.results";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        switch (uriMatcher.match(uri)) {
            case ALL_RESULTS:
                //do nothing
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        long id = db.insert(ResultDB.TABLE_NAME, null, values);
        getContext().getContentResolver().notifyChange(uri, null);
        return Uri.parse(CONTENT_URI + "/" + id);
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(ResultDB.TABLE_NAME);

        switch (uriMatcher.match(uri)) {
            case ALL_RESULTS:
                //do nothing
                break;
            case SINGLE_RESULT:
                String id = uri.getPathSegments().get(1);
                queryBuilder.appendWhere(ResultDB.KEY_ROWID + "=" + id);
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        return queryBuilder.query(db, projection, selection,
                selectionArgs, null, null, sortOrder);
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs){

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        switch (uriMatcher.match(uri)) {
            case ALL_RESULTS:
                //do nothing
                break;
            case SINGLE_RESULT:
                String id = uri.getPathSegments().get(1);
                selection = ResultDB.KEY_ROWID + "=" + id
                        + (!TextUtils.isEmpty(selection) ?
                        " AND (" + selection + ')' : "");
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        int deleteCount = db.delete(ResultDB.TABLE_NAME, selection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);
        return deleteCount;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        switch (uriMatcher.match(uri)) {
            case ALL_RESULTS:
                //do nothing
                break;
            case SINGLE_RESULT:
                String id = uri.getPathSegments().get(1);
                selection = ResultDB.KEY_ROWID + "=" + id
                        + (!TextUtils.isEmpty(selection) ?
                        " AND (" + selection + ')' : "");
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        int updateCount = db.update(ResultDB.TABLE_NAME, values, selection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);
        return updateCount;
    }
}