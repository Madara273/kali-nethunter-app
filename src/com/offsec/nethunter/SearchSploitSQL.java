package com.offsec.nethunter;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;

import java.util.LinkedList;
import java.util.List;

class SearchSploitSQL extends SQLiteOpenHelper {
    private final ShellExecuter exe = new ShellExecuter();
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "SearchSploit";
    SearchSploitSQL(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase database) {
        String CREATE_SEARCHSPLOIT_TABLE = "CREATE TABLE  IF NOT EXISTS " + SearchSploit.TABLE +
                " (" + SearchSploit.ID + " INTEGER PRIMARY KEY, " +
                SearchSploit.FILE + " TEXT," +
                SearchSploit.DESCRIPTION + " TEXT," +
                SearchSploit.DATE + " TEXT," +
                SearchSploit.AUTHOR + " TEXT," +
                SearchSploit.TYPE + " TEXT," +
           		  SearchSploit.PLATFORM + " TEXT," +
                SearchSploit.PORT + " INTEGER DEFAULT 0)";

        database.execSQL(CREATE_SEARCHSPLOIT_TABLE);
        database.disableWriteAheadLogging();
    }

    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        database.execSQL("DROP TABLE IF EXISTS " + SearchSploit.TABLE);
        onCreate(database);
    }

    public void onDowngrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        onUpgrade(database, oldVersion, newVersion);
    }

    public void doDrop() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + SearchSploit.TABLE);
    }

    Boolean doDbFeed() {
        // Generate the csv to kali /root first as temp (so we can read it)
        String _cmd1 = "su -c " + NhPaths.APP_SCRIPTS_PATH + "/bootkali custom_cmd 'usr/bin/python3 /sdcard/nh_files/modules/csv2sqlite.py /usr/share/exploitdb/files_exploits.csv /tmp/SearchSploit " + SearchSploit.TABLE + "'";
        String result1 = exe.RunAsRootOutput(_cmd1);
        Log.d("SearchSploitSQL", "doDbFeed csv2sqlite output: " + result1);

        // Check the generated DB file exists before attempting to move it
        String checkCmd = "test -f " + NhPaths.CHROOT_SYMLINK_PATH + "/tmp/SearchSploit && echo exists || echo missing";
        String checkResult = exe.RunAsRootOutput(checkCmd);
        if (!checkResult.contains("exists")) {
            Log.e("SearchSploitSQL", "doDbFeed: generated DB file not found after csv2sqlite run");
            return false;
        }

        // Then move it to app db folder
        String _cmd2 = "mv " + NhPaths.CHROOT_SYMLINK_PATH + "/tmp/SearchSploit /sdcard/nh_files/SearchSploit";
        String result2 = exe.RunAsRootOutput(_cmd2);
        Log.d("SearchSploitSQL", "doDbFeed mv output: " + result2);

        // Verify the file now exists at the destination
        String verifyCmd = "test -f /sdcard/nh_files/SearchSploit && echo exists || echo missing";
        String verifyResult = exe.RunAsRootOutput(verifyCmd);
        if (!verifyResult.contains("exists")) {
            Log.e("SearchSploitSQL", "doDbFeed: DB file not found at destination after mv");
            return false;
        }

        return true;
    }

    long getCount() {
        String sql = "SELECT COUNT(*) FROM " + SearchSploit.TABLE;
        SQLiteDatabase db = this.getWritableDatabase();
        SQLiteStatement statement = db.compileStatement(sql);
        return statement.simpleQueryForLong();
    }

    public List<SearchSploit> getAllExploits() {
        String query = "SELECT  * FROM " + SearchSploit.TABLE + " LIMIT 100";
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(query, null);
        List<SearchSploit> _List = createExploitList(cursor);
        db.close();
        return _List;
    }

    List<SearchSploit> getAllExploitsFiltered(String filter, String type, String platform) {
        String wildcard = "%" + filter + "%";
        String query = "SELECT * FROM " + SearchSploit.TABLE
                + " WHERE " + SearchSploit.DESCRIPTION + " like ?" +
                " and " + SearchSploit.TYPE + "=?" +
		" and " + SearchSploit.PLATFORM + "=?" +
		" GROUP BY " + SearchSploit.ID;
        SQLiteDatabase db = this.getWritableDatabase();
        Log.d("QUERYYY", query);
        Cursor cursor = db.rawQuery(query, new String[]{wildcard, type, platform});
        List<SearchSploit> _List = createExploitList(cursor);
        db.close();
        return _List;
    }

    List<SearchSploit> getAllExploitsRaw(String filter) {
        String wildcard = "%" + filter + "%";
        String query = "SELECT * FROM " + SearchSploit.TABLE
                + " WHERE ( " + SearchSploit.DESCRIPTION + " like ? or " + SearchSploit.AUTHOR + " like ? or " + SearchSploit.TYPE + " like ? or " + SearchSploit.PLATFORM + " like ? ) GROUP BY " + SearchSploit.ID;
        SQLiteDatabase db = this.getWritableDatabase();
        Log.d("EXPLOIT_QUERY", query);
        Cursor cursor = db.rawQuery(query, new String[]{wildcard, wildcard, wildcard, wildcard});
        List<SearchSploit> _List = createExploitList(cursor);
        db.close();
        return _List;
    }

    private List<SearchSploit> createExploitList(Cursor cursor) {
        List<SearchSploit> commandList = new LinkedList<>();
        if (cursor.moveToFirst()) {
            do {
                SearchSploit _exploit = new SearchSploit();
                _exploit.setId(cursor.getInt(0));                  // id
                _exploit.setFile(cursor.getString(1));             // file
                _exploit.setDescription(cursor.getString(2));      // desc
                _exploit.setDate(cursor.getString(3));             // date
                _exploit.setAuthor(cursor.getString(4));           // author
                _exploit.setPlatform(cursor.getString(5));         // platform
                _exploit.setType(cursor.getString(6));             // type
                _exploit.setPort(cursor.getInt(7));                // port
                commandList.add(_exploit);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return commandList;
    }

    List<String> getTypes() {
        String query = "SELECT DISTINCT " + SearchSploit.TYPE +
                " FROM " + SearchSploit.TABLE +
                " ORDER BY " + SearchSploit.TYPE + " ASC";
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(query, null);
        List<String> _List = createStringList(cursor);
        db.close();
        return _List;
    }

    List<String> getPlatforms() {
        String query = "SELECT DISTINCT " + SearchSploit.PLATFORM + " FROM " + SearchSploit.TABLE + " ORDER BY " + SearchSploit.PLATFORM + " ASC";
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(query, null);
        List<String> _List = createStringList(cursor);
        db.close();
        return _List;
    }

    private List<String> createStringList(Cursor cursor) {
        List<String> strList = new LinkedList<>();
        if (cursor.moveToFirst()) {
            do {
                strList.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return strList;
    }
}
