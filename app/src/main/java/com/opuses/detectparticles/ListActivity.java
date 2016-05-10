package com.opuses.detectparticles;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class ListActivity extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private SimpleCursorAdapter dataAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result_list);

        displayListView();

        Button deleteAllButton = (Button)findViewById(R.id.button_delete_all);
        deleteAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // create the dialog listener
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int choice) {
                        switch (choice) {
                            case DialogInterface.BUTTON_POSITIVE:
                                Uri uri = Uri.parse(ResultContentProvider.CONTENT_URI + "/");
                                getContentResolver().delete(uri, null, null);
                                finish();
                                break;
                            case DialogInterface.BUTTON_NEGATIVE:
                                break;
                        }
                    }
                };

                // create the dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(ListActivity.this);
                builder.setMessage(R.string.delete_all_confirmation)
                        .setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();

            }
        });

        Button closeButton = (Button) findViewById(R.id.button_close_transfer);
        closeButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                // Showing Fragment0
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Starts a new or restarts an existing Loader in this manager
        getLoaderManager().restartLoader(0, null, this);
    }

    private void displayListView() {


        // The desired columns to be bound
        String[] columns = new String[] {
                ResultDB.KEY_TRANSFERRED,
                ResultDB.KEY_ROWID,
                ResultDB.KEY_TIMESTAMP,
                ResultDB.KEY_REFERENCE,
                ResultDB.KEY_COMMENTS
        };

        // the XML defined views which the data will be bound to
        int[] to = new int[] {
                R.id.list_item_star_text_view,
                R.id.list_item_title_text_view,
                R.id.list_item_date_text_view,
                R.id.list_item_reference_text_view,
                R.id.list_item_comments_text_view,
        };

        // create an adapter from the SimpleCursorAdapter
        dataAdapter = new SimpleCursorAdapter(
                this,
                R.layout.result_list_items,
                null,
                columns,
                to,
                0);

        // get reference to the ListView
        ListView listView = (ListView) findViewById(R.id.resultList);

        // Assign adapter to ListView
        listView.setAdapter(dataAdapter);

        //Ensures a loader is initialized and active.
        getLoaderManager().initLoader(0, null, this);


        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> listView, View view,
                                    int position, long id) {

                // Get the cursor, positioned to the corresponding row in the result set
                Cursor cursor = (Cursor) listView.getItemAtPosition(position);

                // display the selected item
                String rowId =
                        cursor.getString(cursor.getColumnIndexOrThrow(ResultDB.KEY_ROWID));

                // starts a new Intent to
                Intent intent = new Intent(getBaseContext(), MapActivity.class);
                intent.putExtra("RowId", rowId);
                startActivity(intent);
            }
        });

    }

    // This is called when a new Loader needs to be created.
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] projection = {
                ResultDB.KEY_TRANSFERRED,
                ResultDB.KEY_ROWID,
                ResultDB.KEY_TIMESTAMP,
                ResultDB.KEY_REFERENCE,
                ResultDB.KEY_COMMENTS
        };

        return new CursorLoader(this,
                        ResultContentProvider.CONTENT_URI, projection, null, null, null);

    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in.  (The framework will take care of closing the
        // old cursor once we return.)
        dataAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no
        // longer using it.
        dataAdapter.swapCursor(null);
    }
}
