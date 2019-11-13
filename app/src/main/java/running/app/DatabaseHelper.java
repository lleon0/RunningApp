package running.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "trainingData.db";
    private static final String TABLE_NAME = "training_table";
    private static final String COL_1 = "ID";
    private static final String COL_2 = "DISTANCE";
    private static final String COL_3 = "TIME";
    private static final String COL_4 = "AVGKMH";
    private static final String COL_5 = "DATE";
    private static final String COL_6 = "COORDINATES";


    public DatabaseHelper(@androidx.annotation.Nullable Context context) {
        super(context, DATABASE_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table " + TABLE_NAME + "("+COL_1+" INTEGER PRIMARY KEY AUTOINCREMENT, "+COL_2+" TEXT, "+COL_3+
                " TEXT, "+COL_4+" TEXT, "+COL_5+" TEXT, "+COL_6+" TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public boolean insertData (String distance, String time, String avgKmh, String date, String coordinates) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COL_2,distance);
        contentValues.put(COL_3,time);
        contentValues.put(COL_4,avgKmh);
        contentValues.put(COL_5,date);
        contentValues.put(COL_6,coordinates);
        long result = db.insert(TABLE_NAME,null, contentValues);
        return result != -1;

    }

    public Cursor getAllData(){
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("select * from " + TABLE_NAME, null);
    }

    public Integer deleteData (String id){
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_NAME, "ID = ?",new String[] {id});
    }
}
