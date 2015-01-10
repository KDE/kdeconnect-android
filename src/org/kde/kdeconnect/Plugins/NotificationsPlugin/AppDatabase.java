/*
 * Copyright 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

package org.kde.kdeconnect.Plugins.NotificationsPlugin;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class AppDatabase {

    public static final String KEY_ROW_ID = "id";
    public static final String KEY_NAME = "app";
    public static final String KEY_PACKAGE_NAME = "packageName";
    public static final String KEY_IS_FILTERED = "isFiltered";


    private static final String DATABASE_NAME = "Applications";
    private static final String DATABASE_TABLE = "Applications";
    private static final int DATABASE_VESRION = 1;

    private Context ourContext;
    private SQLiteDatabase ourDatabase;
    private DbHelper ourHelper;

    public AppDatabase(Context c) {
        ourContext = c;
    }

    private static class DbHelper extends SQLiteOpenHelper{

        public DbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VESRION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + DATABASE_TABLE + "(" + KEY_ROW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + KEY_NAME + " TEXT NOT NULL, " + KEY_PACKAGE_NAME + " TEXT NOT NULL, " + KEY_IS_FILTERED + " TEXT NOT NULL); ");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int i, int i2) {
            db.execSQL("DROP TABLE IF EXISTS "+ DATABASE_TABLE);
            onCreate(db);

        }

    }

    public AppDatabase open(){
        ourHelper = new DbHelper(ourContext);
        ourDatabase = ourHelper.getWritableDatabase();
        return this;
    }


    public void close(){
        ourHelper.close();
    }

    public Cursor getAllApplications()
    {
        String[] columns = new String []{KEY_ROW_ID,KEY_NAME,KEY_PACKAGE_NAME,KEY_IS_FILTERED} ;
        Cursor res =  ourDatabase.query(DATABASE_TABLE,columns,null,null,null,null,KEY_NAME);
        return res;
    }


    public long createEntry(String app, String packagename,String isFiltered) {
        ContentValues cv = new ContentValues();
        cv.put(KEY_NAME,app);
        cv.put(KEY_PACKAGE_NAME,packagename);
        cv.put(KEY_IS_FILTERED,isFiltered);
        return ourDatabase.insert(DATABASE_TABLE,null,cv);
    }

    public long updateEntry(String packageName, String value) {
        ContentValues cvUpdate = new ContentValues();
        cvUpdate.put(KEY_IS_FILTERED,value);
        return ourDatabase.update(DATABASE_TABLE,cvUpdate,KEY_PACKAGE_NAME + "=?",new String[]{packageName});
    }

    public boolean checkEntry(String packageName) {
        String[] columns = new String []{KEY_ROW_ID,KEY_NAME,KEY_PACKAGE_NAME,KEY_IS_FILTERED} ;
        Cursor res =  ourDatabase.query(DATABASE_TABLE,columns,KEY_PACKAGE_NAME + " =? ",new String[]{packageName},null,null,null);
        if (res.getCount() != 0){
            return true;
        }else {
            return false;
        }

    }

    public boolean isFilterEnabled(String packageName){
        String[] columns = new String []{KEY_ROW_ID,KEY_NAME,KEY_PACKAGE_NAME,KEY_IS_FILTERED} ;
        Cursor res =  ourDatabase.query(DATABASE_TABLE,columns,KEY_PACKAGE_NAME + " =? ",new String[]{packageName},null,null,null);
        if (res.getCount() > 0){
            res.moveToFirst();
            if ((res.getString(res.getColumnIndex(KEY_IS_FILTERED))).equals("true")){
                return true;
            }else{
                return false;
            }
        }else{
            return true;
        }
    }
    public void delete(String packageName){
        ourDatabase.delete(DATABASE_TABLE,KEY_PACKAGE_NAME + " =? ",new String[]{packageName} );
    }


}
