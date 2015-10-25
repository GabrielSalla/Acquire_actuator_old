package com.example.acquire.acquisition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import com.example.acquire.Constants;
import com.example.acquire.DBAdapter;
import com.example.acquire.ExportHelper;
import com.example.acquire.ItemData;
import com.example.acquire.Time;
import com.example.acquire.DBAdapter.AcquisitionData;
import com.example.acquire.DBAdapter.AcquisitionDetails;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public class AcquisitionService extends Service{
	boolean developer;
	int v[] = {-50, 50, 0};
	
	private BroadcastReceiver msgReceiver;

	private String msgrec;
	private boolean isValid = false;
	
	private Timer myTimer = null;

	private ArrayList<ItemData> itemList;
	
	private int nVal = 0;
	private int period = 0;
	private long stopTime;
	private long timeNow;

	private boolean has_list = false;
	private boolean saving = false;
	private boolean isRunning = false;
	private boolean isConnected = false;
	
	private IBinder mBinder = new LocalBinder();
	
	private Handler mHandler=new Handler(Looper.getMainLooper());
	
	DBAdapter database;
	
	//Periodically request the values
	private class MyTimerTask extends TimerTask{
		@Override
		public void run(){
            //If already have the list
			if(has_list){
                //If developer mode is activated, change the values randomly
				if(developer){
					Random r = new Random();
					String ans = "$#it_val";

                    //Item 0
					int n = r.nextInt(5)-2;
					int var = 0;
					if(n == 0)
						var = 0;
					if(n > 0)
						var = 1;
					if(n < 0)
						var = -1;
					v[0] += var;
					if(v[0] > 100)
						v[0] = 100;
					if(v[0] < -100)
						v[0] = -100;
					ans = ans + "#00v"+v[0];

                    //Item 1
					n = r.nextInt(5)-2;
					if(n == 0)
						var = 0;
					if(n > 0)
						var = 1;
					if(n < 0)
						var = -1;
					v[1] += var;
					if(v[1] > 100)
						v[1] = 100;
					if(v[1] < -100)
						v[1] = -100;
                    ans = ans + "#01v"+v[1];

                    ans = ans + "#02v"+v[2];

                    ans = ans + "$";
					
					msgrec = ans;
					
					decode_message();
				}
                //If developer mode is deactivated, request the values
				else{
					//Request for update
					send_message("$#req_val$");
				}
				
				nVal++;
				
				String[] values = new String[itemList.size()];
				
				//Shift the data
				int i = 0;
				for(ItemData item : itemList){
					item.ShiftData();
					if(saving)
						values[i] = String.valueOf(item.getLastValue());
					i++;
				}
				
				//If is saving, update the time
				if(saving){
					database.addValues(values);
					timeNow += period;
					if(timeNow >= stopTime)
						stopAcquisition();
				}
			}
            //If doesn't have the list
			else{
                //If developer mode is activated, create the list
				if(developer){
					String ans = "$#it_list";
					
					//First item
					ans = ans + "#00nSeekBar 1"; //Name
                    ans = ans + "#00dInteger -100 to 100"; //Description
                    ans = ans + "#00v" + v[0];

                    //Second item
                    ans = ans + "#01nSeekBar 2"; //Name
                    ans = ans + "#01dInteger -100 to 100"; //Description
                    ans = ans + "#01v" + v[1];

                    //Third item
                    ans = ans + "#02nActuator"; //Name
                    ans = ans + "#02dLight"; //Description
                    ans = ans + "#02v" + v[2];
                    ans = ans + "#02a-100|100";
					
					ans = ans + "$";
					
					msgrec = ans;
					
					decode_message();
				}
                //If developer mode is deactivated, request the list
				else{
					//Request for the list
					send_message("$#req_list$");
				}
			}
			broadcastUpdate();
		}
	}
	
	//A watchdog thread. If the acquisition stays idle for 3 minutes, the service stops itself
	private class Watchdog extends Thread{
		private boolean active = true;
		private boolean reset = false;
		
		public void run(){
			while(active){
				try{
					Thread.sleep(3*60*1000);
				} catch(InterruptedException e){
                    e.printStackTrace();
                }
				//If the watchdog hasn't been reset, stop
				if(reset)
					reset = false;
				else
					active = false;
			}

            //Stop every service
			Intent stop_services = new Intent(Constants.STOP_SERVICE);
			stop_services.putExtra("code", Constants.CODE_ALL);
			sendBroadcast(stop_services);
		}
		
		public void reset_timer(){
			reset = true;
		}
		
		public void finish_thread(){
			active = false;
			reset = false;
			this.interrupt();
		}
	}
	
	Watchdog watchdog;
	
	@Override
	public void onCreate(){
		super.onCreate();
		
		init();

		initBroadcastReceivers();
		
		//Starts the watchdog
		watchdog = new Watchdog();
		watchdog.start();
	}

	private void init(){
		itemList = new ArrayList<ItemData>();
		database = new DBAdapter(this);
	}

	private void initBroadcastReceivers(){
        IntentFilter msgFilter;

		msgReceiver = new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent){
				String action = intent.getAction();

				//If received any broadcast, reset the watchdog
				watchdog.reset_timer();

				if(action.equals(Constants.MESSAGE_RECEIVED)){
					String str = intent.getStringExtra("received_message");

					//Check if the first character is $ (a new message)
					if(str.charAt(0) == '$'){
						//If the second character is #, it's a valid message
						if(str.charAt(1) == '#'){
							isValid = true;
							msgrec = str;
                            //Check if the message is complete (without a '\r' at the end)
                            if(msgrec.charAt(msgrec.length()-1) == '$'){
                                isValid = false;
                                decode_message();
                            }
                            //Check if the message is complete (with a '\r' at the end)
                            if(msgrec.charAt(msgrec.length()-1) == '\r' && msgrec.charAt(msgrec.length()-2) == '$'){
                                isValid = false;
                                decode_message();
                            }
						}
					}
					else{
                        //Keep appending while the message is valid, until the end of the message
						if(isValid){
							//Append the received message
							msgrec = msgrec + str;
							//Check if the message is complete (without a '\r' at the end)
							if(msgrec.charAt(msgrec.length()-1) == '$'){
								isValid = false;
								decode_message();
							}
                            //Check if the message is complete (with a '\r' at the end)
                            if(msgrec.charAt(msgrec.length()-1) == '\r' && msgrec.charAt(msgrec.length()-2) == '$'){
                                isValid = false;
                                decode_message();
                            }
						}
					}
				}

				//When connects, resets the stored data
				if(action.equals(Constants.CONNECTED)){
					isValid = false;
					has_list = false;
					itemList.clear();
					saving = false;
					msgrec = "";
					nVal = 0;
					period = 0;

					send_message("$#req_list$");

					isConnected = true;
				}

				//When disconnects, stop the acquisition
				if(action.equals(Constants.DISCONNECTED)){
					stopAcquisition();

					isConnected = false;
				}

				//Stop service
				if(action.equals(Constants.STOP_SERVICE)){
					int code = intent.getIntExtra("code", -1);

					if(code==Constants.CODE_ALL || code==Constants.CODE_ACQUISITION_SERVICE){
						stopSelf();
					}
				}
			}
		};

		msgFilter = new IntentFilter();
		msgFilter.addAction(Constants.MESSAGE_RECEIVED);
		msgFilter.addAction(Constants.CONNECTED);
		msgFilter.addAction(Constants.DISCONNECTED);
		msgFilter.addAction(Constants.STOP_SERVICE);
		registerReceiver(msgReceiver, msgFilter);
	}

	//Send a message through the connection
	private void send_message(String msg){
		Intent send_message = new Intent(Constants.SEND_MESSAGE);
		send_message.putExtra("message", msg);
		sendBroadcast(send_message);
	}

	//Returns the item list
	public ArrayList<ItemData> getItemList(){
		if(has_list){
			return itemList;
		}
		else{
			return null;
		}
	}

    //Broadcast the update
    private void broadcastUpdate(){
        Intent update = new Intent(Constants.UPDATE_DATA);
        sendBroadcast(update);
    }

    public void actuate(int position, int value){
        if(isConnected){
            ItemData item = itemList.get(position);
            send_message("$#act" + item.getCode() + "a" + value + "$");
        }
        if(developer){
            v[position] = value;
        }
    }

	//Returns the acquisition period
	public int getPeriod(){
		if(period == 0)
			return 500;
		return period;
	}

	//Returns the number of readings
	public int getnVal(){
		return nVal;
	}

	//Returns the running status
	public boolean isRunning(){
		return isRunning;
	}

	//Returns the connection status
	public boolean isConnected(){
		return isConnected;
	}

	//Returns the list of acquisitions
	public String[] getAcquisitions(){
		return database.getAcquisitions();
	}

	//Returns an acquisition details
	public AcquisitionDetails getDetails(String name){
		return database.loadAcquisitionDetails(name);
	}

	//Returns an acquisition data
	public void loadAcquisition(String name){
		AcquisitionData data = database.loadAcquisition(name);

		//Load the data to the variables
		if(data!=null){
			period = data.period;
			nVal = data.nVal;

			itemList.clear();
			for(int i=0; i<data.nItems; i++){
				int[] values = new int[data.nVal];

				for(int j=0; j<data.nVal; j++){
					values[j] = Integer.parseInt(data.values[i][j]);
				}

				ItemData item = new ItemData(i, data.names[i], data.customNames[i], data.descriptions[i], values);
				itemList.add(item);
			}
			has_list = true;
		}
	}

	//Export a record using a XLM file
	public Uri exportRecordXLS(String name){
		//Get the acquisition
		AcquisitionData data = database.loadAcquisition(name);

		//Create the sheet
		HSSFWorkbook workbook = ExportHelper.MakeSheetFromData(data);

		//Save the file to the downloads directory
		File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Data.xls");
		FileOutputStream fileOut;

		try{
			fileOut = new FileOutputStream(file);
	        workbook.write(fileOut);
	        fileOut.close();
		} catch(IOException e){
			e.printStackTrace();
		}

		//Returns the file path
		return Uri.fromFile(file);
	}

	//Returns a list of tables (used for debugging)
	public String[] getTables(){
		return database.queryTables();
	}

	//Changes the custom name of an item
	public void ChangeCustomName(int position, String customName){
		ItemData item_update = itemList.get(position);
		item_update.setCustomName(customName);

        //If is saving, update in the database
		if(saving){
			String name = item_update.getName();
			database.ChangeCustomName(name, customName);
		}
	}

	//Delete an acquisition from the database
	public void DeleteAcquisition(String name){
		database.deleteAcquisition(name);
	}

	//Update the data for the graph activity (only the main thread can change the UI)
	public void refreshSeriesData(){
		for(ItemData item : itemList){
			item.refreshSeriesData();
		}
	}

	//Start an acquisition
	public boolean startAcquisition(int per, boolean save, int time){
		boolean start = false;

		if(isConnected || developer){
			//If is running and a configuration changed
			if(isRunning){
				if((period != per) || (saving != save)){
					stopAcquisition();
					period = per;
					start = true;
				}
			}
			else{
				period = per;
				start = true;
			}
		}
		else{
			return false;
		}

		//If the acquisition must start
		if(start){
			//Clear all values on the list
			for(ItemData it : itemList){
				it.clear();
			}
			nVal = 0;

			saving = save;
			//Create new acquisition
			if(has_list){
				if(saving){
					//Create a new entry in the database
					String strTime = Time.getTime();
					String strRawTime = Time.getRawTime();
					int nItems = itemList.size();
					database.CreateNewAcquisition(nItems, strTime, period, strTime, "it" + strRawTime, "val" + strRawTime);

					String[] names = new String[nItems];
					String[] new_names = new String[nItems];
					String[] descriptions = new String[nItems];

					int i = 0;
					for(ItemData it : itemList){
						names[i] = it.getName();
						new_names[i] = it.getCustomName();
						descriptions[i] = it.getDescription();
						i++;
					}

					database.FillItemList(names, new_names, descriptions);
				}
				//Request for update
				send_message("$#req_val$");
			}
			else{
				//Request for the list
				send_message("$#req_list$");
			}

			if(saving){
				//Time to ms
				stopTime = time*60000;
				timeNow = 0;
			}

			//Start the timer
			myTimer = new Timer();
            MyTimerTask refreshAll = new MyTimerTask();
			myTimer.schedule(refreshAll, 0, period);

			isRunning = true;

			mHandler.post(new Runnable(){
				public void run(){
					Toast.makeText(getApplicationContext(), "Acquisition started with period=" + period + "ms", Toast.LENGTH_SHORT).show();
	            }
	        });
		}
		return true;
	}

	//Stops the acquisition
	public void stopAcquisition(){
		if(myTimer != null){
			myTimer.cancel();
			myTimer = null;

			//If was saving, finish it
			if(saving){
				database.setnVal(getnVal());
				database.setStop(Time.getTime());
				saving = false;
			}

			mHandler.post(new Runnable(){
				public void run(){
					Toast.makeText(getApplicationContext(), "Acquisition stopped", Toast.LENGTH_SHORT).show();
	            }
	        });

			isRunning = false;
		}
	}

	//Decode the received message
	private void decode_message(){
		//If received the items list
		if(msgrec.contains("#it_list")){
			if(has_list)
				return;

			int str_pos, next_symbol, item_number = 0;

			itemList.clear();
			while(true){
                ItemData new_item;

				str_pos = 9;
				//Search for the name of the item in the message
				String it_name = "#" + item_number/10 + item_number%10 + "n";
				str_pos = msgrec.indexOf(it_name);
				//If didn't find the name on the message, stop looking
				if(str_pos == -1)
					break;
				str_pos += 4;
				//Gets the name
				next_symbol = msgrec.indexOf("#", str_pos);
				if(next_symbol == -1){
					next_symbol = msgrec.indexOf("$", str_pos);
				}
				String name = msgrec.substring(str_pos, next_symbol);

				//Search for the description of the item in the message
				String it_desc = "#" + item_number/10 + item_number%10 + "d";
				str_pos = msgrec.indexOf(it_desc);
				str_pos += 4;
				//Gets the description
				next_symbol = msgrec.indexOf("#", str_pos);
				if(next_symbol == -1){
					next_symbol = msgrec.indexOf("$", str_pos);
				}
				String desc = msgrec.substring(str_pos, next_symbol);

                //Search for the value of the item in the message
                String it_val = "#" + item_number/10 + item_number%10 + "v";
                str_pos = msgrec.indexOf(it_val);
                str_pos += 4;
                //Gets the value
                next_symbol = msgrec.indexOf("#", str_pos);
                if(next_symbol == -1){
                    next_symbol = msgrec.indexOf("$", str_pos);
                }
                String val = msgrec.substring(str_pos, next_symbol);

                int iVal = Integer.parseInt(val);


                //Check if it's an actuator
                String it_act = "#" + item_number/10 + item_number%10 + "a";
                str_pos = msgrec.indexOf(it_act);
                if(str_pos != -1){
                    str_pos += 4;
                    next_symbol = msgrec.indexOf("#", str_pos);
                    if(next_symbol == -1){
                        next_symbol = msgrec.indexOf("$", str_pos);
                    }
                    String strRange = msgrec.substring(str_pos, next_symbol);

                    int divider = strRange.indexOf("|");

                    int range[] = new int[2];
                    range[0] = Integer.parseInt(strRange.substring(0, divider));
                    range[1] = Integer.parseInt(strRange.substring(divider + 1));

                    new_item = new ItemData(item_number, name, "", desc, iVal, range);
                }
                else{
                    new_item = new ItemData(item_number, name, "", desc, iVal);
                }
				itemList.add(new_item);
				item_number++;
			}

			has_list = true;

			//If saving, create a new entry in the database
			if(saving){
				String t = Time.getTime();
				String rawt = Time.getRawTime();
				int nItems = itemList.size();
				database.CreateNewAcquisition(nItems, t, period, t, "it" + rawt, "val" + rawt);

				String names[] = new String[nItems];
				String customNames[] = new String[nItems];
				String descriptions[] = new String[nItems];

				int i = 0;
				for(ItemData it : itemList){
					names[i] = it.getName();
					customNames[i] = it.getCustomName();
					descriptions[i] = it.getDescription();
					i++;
				}

				database.FillItemList(names, customNames, descriptions);
			}

			broadcastUpdate();
		}
		
		//If received values of the items
		if(msgrec.contains("#it_val")){
			int str_pos, next_symbol;
			
			for(ItemData item : itemList){
				str_pos = 8;
				//Search for the value of the item in the message
				String it_val = item.getCode() + "v";
				str_pos = msgrec.indexOf(it_val, str_pos);
				//If didn't find the name on the message, check the next item
				if(str_pos == -1){
					continue;
				}
				str_pos += 4;
				//Gets the value
				next_symbol = msgrec.indexOf("#", str_pos);
				if(next_symbol == -1){
					next_symbol = msgrec.indexOf("$", str_pos);
				}
				String val = msgrec.substring(str_pos, next_symbol);
				
				int ival = Integer.parseInt(val);
				
				item.setLastValue(ival);
			}
		}
	}
	
	//Set the developer status (used for debugging)
	public void setDeveloper(boolean developer){
		this.developer = developer;
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		
		stopAcquisition();
		
		try{
			unregisterReceiver(msgReceiver);
		} catch(IllegalArgumentException e){
            e.printStackTrace();
        }
		
		if(myTimer != null){
            myTimer.cancel();
        }
		
		if(watchdog != null){
			watchdog.finish_thread();
        }
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		return super.onStartCommand(intent, flags, startId);
	}
	
	public class LocalBinder extends Binder{
		public AcquisitionService getService(){
			return AcquisitionService.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent){
		return mBinder;
	}
}
