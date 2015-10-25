package com.example.acquire;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBAdapter{
	private String acq_name, itTable, valTable;
	private DBHelper helper;
	private int nItems = 0;
	private SQLiteDatabase database;
	
	public DBAdapter(Context context){
		helper = new DBHelper(context);
		database = helper.getWritableDatabase();
	}
	
	//Delete all acquisitions
	public void clear(){
		String[] str = getAcquisitions();
		
		for(int i=0; i<str.length; i++){
			deleteAcquisition(str[i]);
		}
	}
	
	//Create a new acquisition
	public void CreateNewAcquisition(int nItems, String name, int period, String start, String itTable, String valTable){
		ContentValues acquisition = new ContentValues();

		acquisition.put(DBHelper.COLUMN_NAME, name);
		acquisition.put(DBHelper.COLUMN_PERIOD, period);
		acquisition.put(DBHelper.COLUMN_NITEMS, nItems);
		acquisition.put(DBHelper.COLUMN_NVAL, 0);
		acquisition.put(DBHelper.COLUMN_DATE_START, start);
		acquisition.put(DBHelper.COLUMN_DATE_STOP, "");
		acquisition.put(DBHelper.COLUMN_ITEMS_TABLE, itTable);
		acquisition.put(DBHelper.COLUMN_VALUES_TABLE, valTable);
		
		database.insert(DBHelper.TABLE_ACQUISITIONS, null, acquisition);
		
		this.nItems = nItems;
		this.acq_name = name;
		this.itTable = itTable;
		this.valTable = valTable;
		
		//Create the tables
		CreateItemsTable();
		CreateValuesTable();
	}
	
	//Create the items table
	private void CreateItemsTable(){
		try{
			database.execSQL(DBHelper.CreateItemsTableString(itTable));
		} catch(SQLException e){
			e.printStackTrace();
		}
	}

	//Create the values table
	private void CreateValuesTable(){
		try{
			database.execSQL(DBHelper.CreateValuesTableString(valTable, nItems));
		} catch(SQLException e){
			e.printStackTrace();
		}
	}
	
	//Fill the list table with the items
	public void FillItemList(String[] names, String[] customNames, String[] descriptions){
		ContentValues item = new ContentValues();
		
		for(int i=0; i<nItems; i++){
			item.put(DBHelper.COLUMN_NAME, names[i]);
			item.put(DBHelper.COLUMN_CUSTOM_NAME, customNames[i]);
			item.put(DBHelper.COLUMN_DESCRIPTION, descriptions[i]);
			
			database.insert(itTable, null, item);
		}
	}
	
	//Add values for the items
	public void addValues(String[] values){
		ContentValues val = new ContentValues();
		
		for(int i=0; i<nItems; i++){
			val.put(DBHelper.MakeColString(i), values[i]);
		}
		
		database.insert(valTable, null, val);
	}

	//Update nVal on the acquisitions table
	public void setnVal(int nVal){
		ContentValues update = new ContentValues();
		update.put(DBHelper.COLUMN_NVAL, nVal);
		database.update(DBHelper.TABLE_ACQUISITIONS, update, DBHelper.COLUMN_NAME + "=?", new String[] {acq_name});
	}

	//Update nVal on the acquisitions table
	public void ChangeCustomName(String name, String customName){
		ContentValues update = new ContentValues();
		update.put(DBHelper.COLUMN_CUSTOM_NAME, customName);
		database.update(itTable, update, DBHelper.COLUMN_NAME + "=?", new String[] {name});
	}

	//Update the stop date on the acquisitions table
	public void setStop(String stop){
		ContentValues update = new ContentValues();
		update.put(DBHelper.COLUMN_DATE_STOP, stop);
		database.update(DBHelper.TABLE_ACQUISITIONS, update, DBHelper.COLUMN_NAME + "=?", new String[] {acq_name});
	}
	
	//Get the acquisitions list
	public String[] getAcquisitions(){
		String[] columns = {DBHelper.COLUMN_NAME};
		Cursor c = database.query(DBHelper.TABLE_ACQUISITIONS, columns, null, null, null, null, null);
		
		String[] acquisitions = new String[c.getCount()];
		
		int i = 0;
		while(c.moveToNext()){
			int col_name = c.getColumnIndex(DBHelper.COLUMN_NAME);
			String name = c.getString(col_name);
			
			acquisitions[i] = name;
			
			i++;
		}
		
		return acquisitions;
	}
	

	//Load the acquisition details
	public AcquisitionDetails loadAcquisitionDetails(String name){
		String[] columns = {DBHelper.COLUMN_PERIOD, DBHelper.COLUMN_NITEMS, DBHelper.COLUMN_NVAL, DBHelper.COLUMN_DATE_START, DBHelper.COLUMN_DATE_STOP};
		Cursor c = database.query(DBHelper.TABLE_ACQUISITIONS, columns, DBHelper.COLUMN_NAME + " = '" + name + "'", null, null, null, null);
		
		//Tests if the name exists
		if(c.moveToNext()){
			AcquisitionDetails details = new AcquisitionDetails();

			int period_index = c.getColumnIndex(DBHelper.COLUMN_PERIOD);
			details.period = c.getInt(period_index);

			int nItems_index = c.getColumnIndex(DBHelper.COLUMN_NITEMS);
			details.nItems = c.getInt(nItems_index);

			int nVal_index = c.getColumnIndex(DBHelper.COLUMN_NVAL);
			details.nVal = c.getInt(nVal_index);
			
			int start_index = c.getColumnIndex(DBHelper.COLUMN_DATE_START);
			details.start = c.getString(start_index);
			
			int stop_index = c.getColumnIndex(DBHelper.COLUMN_DATE_STOP);
			details.stop = c.getString(stop_index);
			
			return details;
		}
		//If doesn't exists, returns null
		else
			return null;
	}
	
	//Fully load the acquisition data
	public AcquisitionData loadAcquisition(String name){
		int i;
		
		String[] columns = {DBHelper.COLUMN_PERIOD, DBHelper.COLUMN_NITEMS, DBHelper.COLUMN_NVAL, DBHelper.COLUMN_DATE_START, DBHelper.COLUMN_DATE_STOP, DBHelper.COLUMN_ITEMS_TABLE, DBHelper.COLUMN_VALUES_TABLE};
		Cursor c = database.query(DBHelper.TABLE_ACQUISITIONS, columns, DBHelper.COLUMN_NAME + " = '" + name + "'", null, null, null, null);
		
		//Tests if the name exists
		if(c.moveToNext()){
			AcquisitionData data = new AcquisitionData();

			int period_index = c.getColumnIndex(DBHelper.COLUMN_PERIOD);
			data.period = c.getInt(period_index);

			int nItems_index = c.getColumnIndex(DBHelper.COLUMN_NITEMS);
			nItems = c.getInt(nItems_index);
			data.nItems = nItems;

			int nVal_index = c.getColumnIndex(DBHelper.COLUMN_NVAL);
			data.nVal = c.getInt(nVal_index);
			
			int start_index = c.getColumnIndex(DBHelper.COLUMN_DATE_START);
			data.start = c.getString(start_index);
			
			int stop_index = c.getColumnIndex(DBHelper.COLUMN_DATE_STOP);
			data.stop = c.getString(stop_index);
			
			//Get the tables names
			int it_index = c.getColumnIndex(DBHelper.COLUMN_ITEMS_TABLE);
			itTable = c.getString(it_index);

			int val_index = c.getColumnIndex(DBHelper.COLUMN_VALUES_TABLE);
			valTable = c.getString(val_index);
			
			//Get the names, new names and descriptions of the items
			String[] it_columns = {DBHelper.COLUMN_NAME, DBHelper.COLUMN_CUSTOM_NAME, DBHelper.COLUMN_DESCRIPTION};
			c = database.query(itTable, it_columns, null, null, null, null, null);
			
			//Save on arrays of strings
			String[] names = new String[nItems];
			String[] customNames = new String[nItems];
			String[] descriptions = new String[nItems];
			
			i = 0;
			while(c.moveToNext()){
				int col_name = c.getColumnIndex(DBHelper.COLUMN_NAME);
				names[i] = c.getString(col_name);

				int col_customNames = c.getColumnIndex(DBHelper.COLUMN_CUSTOM_NAME);
				customNames[i] = c.getString(col_customNames);
				
				int col_descriptions = c.getColumnIndex(DBHelper.COLUMN_DESCRIPTION);
				descriptions[i] = c.getString(col_descriptions);
				
				i++;
			}
			data.names = names;
			data.customNames = customNames;
			data.descriptions = descriptions;
			
			//Get the values
			//Make the columns
			String[] val_columns = new String[nItems];
			for(i = 0; i<nItems; i++){
				val_columns[i] = DBHelper.MakeColString(i);
			}
			
			c = database.query(valTable, val_columns, null, null, null, null, null);
			
			//Each String array is the values of one item (values[item number][read number])
			String[][] values = new String[nItems][data.nVal];
			
			int j = 0;
			while(c.moveToNext()){
				for(i=0; i<nItems; i++){
					int item_col = c.getColumnIndex(DBHelper.MakeColString(i));
					String val = c.getString(item_col);
					values[i][j] = val;
				}
				j++;
			}
			
			data.values = values;
			
			return data;
		}
		//If doesn't exists, returns null
		else
			return null;
	}
	
	//Delete one item of the list
	public void deleteAcquisition(String name){
		String[] columns = {DBHelper.COLUMN_ITEMS_TABLE, DBHelper.COLUMN_VALUES_TABLE};
		Cursor c = database.query(DBHelper.TABLE_ACQUISITIONS, columns, DBHelper.COLUMN_NAME + " = '" + name + "'", null, null, null, null);
		
		//Tests if the name exists
		if(c.moveToNext()){
			//Get the tables names
			int it_index = c.getColumnIndex(DBHelper.COLUMN_ITEMS_TABLE);
			String itTable = c.getString(it_index);
			//Delete the item table
			database.execSQL(DBHelper.DROP_TABLE + itTable);
			
			int val_index = c.getColumnIndex(DBHelper.COLUMN_VALUES_TABLE);
			String valTable = c.getString(val_index);
			//Delete the values table
			database.execSQL(DBHelper.DROP_TABLE + valTable);

			//Delete the row on the acquisition table
			database.delete(DBHelper.TABLE_ACQUISITIONS, DBHelper.COLUMN_NAME + " = ?", new String[] {name});
		}
	}
	
	//Query for all existing tables
	public String[] queryTables(){
		Cursor c = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
	
		String[] tables = new String[c.getCount()];
		
		int i = 0;
		while(c.moveToNext()){
			tables[i] = c.getString(0);
			i++;
		}
		
		return tables;
	}
	
	//Class that controls the database
	private static class DBHelper extends SQLiteOpenHelper{
		private static final String DATABASE_NAME = "Acquisitions";
		
		private static final int DATABASE_VERSION = 1;
		
		private static final String TABLE_ACQUISITIONS = "acquisitionsTable";
		private static final String COLUMN_UID = "_id";
		private static final String COLUMN_NAME = "name";
		private static final String COLUMN_CUSTOM_NAME = "customName";
		private static final String COLUMN_DESCRIPTION = "description";
		private static final String COLUMN_PERIOD = "period";
		private static final String COLUMN_NITEMS = "nItems";
		private static final String COLUMN_NVAL = "nVal";
		private static final String COLUMN_DATE_START = "start";
		private static final String COLUMN_DATE_STOP = "stop";
		private static final String COLUMN_ITEMS_TABLE = "itTable";
		private static final String COLUMN_VALUES_TABLE = "valTable";

		private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS ";
		
		private static final String CREATE_ACQ_TABLE = " (" + COLUMN_UID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				   									   COLUMN_NAME + " TEXT, " +
													   COLUMN_PERIOD + " INTEGER, " +
													   COLUMN_NITEMS + " INTEGER, " +
													   COLUMN_NVAL + " INTEGER, " +
													   COLUMN_DATE_START + " TEXT, " +
													   COLUMN_DATE_STOP + " TEXT, " +
													   COLUMN_ITEMS_TABLE + " TEXT, " +
													   COLUMN_VALUES_TABLE + " TEXT);";

		private static final String CREATE_ITEMS_TABLE = " (" + COLUMN_UID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
														 COLUMN_NAME + " TEXT, " +
														 COLUMN_CUSTOM_NAME + " TEXT, " +
														 COLUMN_DESCRIPTION + " TEXT);";

		private static final String CREATE_VALUES_TABLE = " (" + COLUMN_UID + " INTEGER PRIMARY KEY AUTOINCREMENT";
		
		private static final String DROP_TABLE = "DROP TABLE IF EXISTS ";
		
		
		public DBHelper(Context context){
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}
	
		@Override
		public void onCreate(SQLiteDatabase db){
			try{
				db.execSQL(CREATE_TABLE + TABLE_ACQUISITIONS + CREATE_ACQ_TABLE);
			} catch(SQLException e){
				e.printStackTrace();
			}
		}
	
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
			try{
				db.execSQL(DROP_TABLE + TABLE_ACQUISITIONS);
				onCreate(db);
			} catch(SQLException e){
				e.printStackTrace();
			}
		}
		
		//Returns the string to create an item list table
		public static String CreateItemsTableString(String name){
			return CREATE_TABLE + name + CREATE_ITEMS_TABLE;
		}
		
		//Returns the string to create a values table
		public static String CreateValuesTableString(String name, int nItems){
			StringBuffer str = new StringBuffer(CREATE_TABLE + name + CREATE_VALUES_TABLE);
			
			for(int i=0; i<nItems; i++){
				str.append(", it" + i/10 + i%10 + " TEXT");
			}
			str.append(");");
			
			return str.toString();
		}
		
		//Returns the column name for an item
		public static String MakeColString(int n){
			return "it" + n/10 + n%10;
		}
	}
	
	
	public class AcquisitionDetails{
		public int nItems;
		public int period;
		public int nVal;
		
		public String start;
		
		public String stop;
	}

	public class AcquisitionData{
		public int nItems;
		public int period;
		public int nVal;
		
		public String start;
		
		public String stop;
		
		public String[] names;
		public String[] customNames;
		public String[] descriptions;
		
		public String[][] values;
	}
}