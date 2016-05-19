/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseSchema.BookEntry;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Book;

import java.io.File;
import java.io.IOException;

/**
 * Database helper for managing database which stores information about the books in the application
 * This is a different database from the one which contains the accounts and transaction data because
 * there are multiple accounts/transactions databases in the system and this one will be used to
 * switch between them.
 */
public class BookDbHelper extends SQLiteOpenHelper {

    public static final String LOG_TAG = "BookDbHelper";
    /**
     * Create the books table
     */
    private static final String BOOKS_TABLE_CREATE = "CREATE TABLE " + BookEntry.TABLE_NAME + " ("
            + BookEntry._ID 		         + " integer primary key autoincrement, "
            + BookEntry.COLUMN_UID 		     + " varchar(255) not null UNIQUE, "
            + BookEntry.COLUMN_DISPLAY_NAME  + " varchar(255) not null, "
            + BookEntry.COLUMN_ROOT_GUID     + " varchar(255) not null, "
            + BookEntry.COLUMN_TEMPLATE_GUID + " varchar(255), "
            + BookEntry.COLUMN_ACTIVE        + " tinyint default 0, "
            + BookEntry.COLUMN_SOURCE_URI    + " varchar(255), "
            + BookEntry.COLUMN_LAST_SYNC     + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + BookEntry.COLUMN_CREATED_AT    + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + BookEntry.COLUMN_MODIFIED_AT   + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP "
            + ");" + DatabaseHelper.createUpdatedAtTrigger(BookEntry.TABLE_NAME);

    public BookDbHelper(Context context) {
        super(context, DatabaseSchema.BOOK_DATABASE_NAME, null, DatabaseSchema.BOOK_DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(BOOKS_TABLE_CREATE);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);

        if (db.isReadOnly()) {
            Log.w(LOG_TAG, "Database was opened in read-only mode");
            return;
        }

        String sql = "SELECT COUNT(*) FROM " + BookEntry.TABLE_NAME;
        SQLiteStatement statement = db.compileStatement(sql);
        long count = statement.simpleQueryForLong();
        if (count == 0) { //there is currently no book in the database
            DatabaseHelper helper = new DatabaseHelper(GnuCashApplication.getAppContext(),
                    DatabaseSchema.LEGACY_DATABASE_NAME);
            SQLiteDatabase mainDb = helper.getWritableDatabase();
            AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(mainDb,
                    new TransactionsDbAdapter(mainDb, new SplitsDbAdapter(mainDb)));

            String rootAccountUID = accountsDbAdapter.getOrCreateGnuCashRootAccountUID();

            Book book = new Book(rootAccountUID);
            ContentValues contentValues = new ContentValues();
            contentValues.put(BookEntry.COLUMN_UID, book.getUID());
            contentValues.put(BookEntry.COLUMN_ROOT_GUID, rootAccountUID);
            contentValues.put(BookEntry.COLUMN_TEMPLATE_GUID, Book.generateUID());
            contentValues.put(BookEntry.COLUMN_DISPLAY_NAME, new BooksDbAdapter(db).generateDefaultBookName());
            contentValues.put(BookEntry.COLUMN_ACTIVE, 1);

            db.insert(BookEntry.TABLE_NAME, null, contentValues);

            String mainDbPath = mainDb.getPath();
            helper.close();

            File src = new File(mainDbPath);
            File dst = new File(src.getParent(), book.getUID());
            try {
                MigrationHelper.moveFile(src, dst);
            } catch (IOException e) {
                String err_msg = "Error renaming database file";
                Crashlytics.log(err_msg);
                Log.e(LOG_TAG, err_msg, e);
            }
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //nothing to see here yet, move along
    }
}