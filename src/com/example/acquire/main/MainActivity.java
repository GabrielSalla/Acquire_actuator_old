package com.example.acquire.main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.acquire.Constants;
import com.example.acquire.ItemData;
import com.example.acquire.acquisition.AcquisitionService;
import com.example.acquire.acquisition.AcquisitionService.LocalBinder;
import com.example.acquire.bluetooth.BtDeviceActivity;
import com.example.acquire.bluetooth.BtServerActivity;
import com.example.acquire.wifi.WifiDeviceActivity;
import com.example.acquire.wifi.WifiServerActivity;
import com.example.acquire.R;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnItemClickListener{
    private Button button_Disconnect;

	private TextView textView_Name;
	
	private CheckBox checkBox_Developer;
	
	//Bluetooth data
	private String deviceName;
	private String deviceAddress;
	
	//Wifi data
	private String SERVERIP;
	private int PORT;
	
	//Holding the items info
	private ArrayList<ItemData> itemList;
	private List<Map<String, String>> listData;
	private SimpleAdapter listAdapter;
	
	//The position of the item that was selected. Used to change the item name
	private int posDetails;
	
	//Control flags
	private boolean usingWifi = false;
	private boolean hasList = false;
    //private boolean isConnected = false;

	//Broadcast receiver variables
    private BroadcastReceiver msgReceiver;
	private IntentFilter msgFilter;
	
	//Connection
	private AcquisitionService acqService = null;
	private ServiceConnection acqConnection = new ServiceConnection(){
		@Override
		public void onServiceDisconnected(ComponentName name){
			acqService = null;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service){
			LocalBinder binder = (LocalBinder)service;
			acqService = binder.getService();
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		init();

		initBroadcastReceivers();
		
		//Start the acquisition service
		Intent acquisition = new Intent(getApplicationContext(), AcquisitionService.class);
		startService(acquisition);
		if(!bindService(acquisition, acqConnection, BIND_AUTO_CREATE)){
			Toast.makeText(getApplicationContext(), "Bind Error", Toast.LENGTH_SHORT).show();
		}
	}

	//Initializing variables
	private void init(){
        button_Disconnect = (Button)findViewById(R.id.button_Disconnect);
		textView_Name = (TextView)findViewById(R.id.textView_Name);
		
		checkBox_Developer = (CheckBox)findViewById(R.id.checkBox_Developer);
		checkBox_Developer.setChecked(false);
		
		//Shows the app version
		TextView version = (TextView)findViewById(R.id.textView_Version);
		version.setText(Constants.VERSION);
		
		//Bind the list view with the data list
		listData = new ArrayList<Map<String, String>>();
		listAdapter = new SimpleAdapter(this, listData, android.R.layout.simple_list_item_2, new String[]{"name", "value"}, new int[]{android.R.id.text1, android.R.id.text2});

		ListView listView_Items = (ListView)findViewById(R.id.listView_Items);
		listView_Items.setAdapter(listAdapter);
		listView_Items.setOnItemClickListener(this);
	}

	//Initialize the broadcast receivers
	private void initBroadcastReceivers(){
		//Configure the Broadcast receiver
		msgReceiver =new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent){
				String action = intent.getAction();

				//Received an update
				if(action.equals(Constants.UPDATE_DATA)){
                    if(!hasList){
						itemList = acqService.getItemList();
						if(itemList != null){
							refresh_ListView();
							hasList = true;
						}
					}
					else{
						refresh_ListView();
					}
				}

				//When is connected
				if(action.equals(Constants.CONNECTED)){
                    //isConnected = true;

					if(listData != null){
                        listData.clear();
                    }

					listAdapter.notifyDataSetChanged();

					if(itemList != null){
                        itemList.clear();
                    }

					refreshStatus();
				}

				//When is disconnected
				if(action.equals(Constants.DISCONNECTED)){
                    //isConnected = false;
					refreshStatus();
				}
			}
		};

		//Set the filters
		msgFilter = new IntentFilter();
		msgFilter.addAction(Constants.UPDATE_DATA);
		msgFilter.addAction(Constants.CONNECTED);
		msgFilter.addAction(Constants.DISCONNECTED);
	}

	//When an activity returns a result
	protected void onActivityResult(int requestCode, int resultCode, Intent result){
		//Request for IP and port
		if(requestCode == Constants.REQUEST_WIFIDEVICE){
			//Make sure the request was successful
			if(resultCode == RESULT_OK){
				usingWifi = true;
				
				//Get the IP and port
				SERVERIP = result.getStringExtra("ip");
				PORT = Integer.parseInt(result.getStringExtra("port"));

                //isConnected = false;
				refreshStatus();
				
				//Start connection
				Intent start_connection = new Intent(Constants.START_CONNECTION);
				start_connection.putExtra("ip", SERVERIP);
				start_connection.putExtra("port", Integer.toString(PORT));
				sendBroadcast(start_connection);
			}
			//Otherwise, stops the service
			else{
				Intent stop_wifi = new Intent(Constants.STOP_SERVICE);
				stop_wifi.putExtra("code", Constants.CODE_WIFI_SERVICE);
				sendBroadcast(stop_wifi);
			}
		}
		
		//Request for a bluetooth device
		if(requestCode == Constants.REQUEST_BTDEVICE){
			//Make sure the request was successful
			if(resultCode == RESULT_OK){
				usingWifi = false;
				
				BluetoothDevice selectedDevice;
				
				//The user picked a paired device
				selectedDevice = result.getExtras().getParcelable("device");
				
				//Gets name and address of the device
				deviceName = selectedDevice.getName();
				deviceAddress = selectedDevice.getAddress();

                //isConnected = false;
				refreshStatus();
				
				//Start connection
				Intent start_connection = new Intent(Constants.START_CONNECTION);
				start_connection.putExtra("device", selectedDevice);
				sendBroadcast(start_connection);
			}
			//Otherwise, stops the service
			else{
				Intent stop_bluetooth = new Intent(Constants.STOP_SERVICE);
				stop_bluetooth.putExtra("code", Constants.CODE_BLUETOOTH_SERVICE);
				sendBroadcast(stop_bluetooth);
			}
		}
	}
	
	//Refresh the list view
	private void refresh_ListView(){
		//Clear the list
		listData.clear();
		//Set each item name and value
		for(ItemData it : itemList){
			Map<String, String> dat = new HashMap<String, String>(2);
			if(it.getCustomName().length()==0)
				dat.put("name", it.getName());
			else
				dat.put("name", it.getCustomName());
			dat.put("value", "Value: " + it.getLastValue());
			listData.add(dat);
		}
		listAdapter.notifyDataSetChanged();
	}


    //Refresh the connection status
	private void refreshStatus(){
        boolean isConnected = acqService.isConnected();

        button_Disconnect.setEnabled(isConnected);

		if(SERVERIP == null && deviceName == null)
			return;
		
		//Show the connection info
		String status = "<font color=\"";
		if(isConnected)
			status += "green";
		else
			status += "red";
		status += "\"> <b>";
		
		if(usingWifi)
			status += SERVERIP + "</b><br> Port: " + PORT;
		else
			status += deviceName + "</b><br>" + deviceAddress;
		
		status += "</font>";
			
		textView_Name.setText(Html.fromHtml(status));
	}

	public void onButtonClick_Wifi(View view){
		//Stop all connection services
		Intent stop_connections = new Intent(Constants.STOP_SERVICE);
		stop_connections.putExtra("code", Constants.CODE_CONNECTIONS);
		sendBroadcast(stop_connections);
		
		if(!checkBox_Developer.isChecked()){
			//Start activity requesting for a wifi device
			Intent intent = new Intent(this, WifiDeviceActivity.class);
			startActivityForResult(intent, Constants.REQUEST_WIFIDEVICE);
		}
		else{
			Intent intent = new Intent(this, WifiServerActivity.class);
			startActivity(intent);
		}

        //isConnected = false;
		refreshStatus();
	}

	public void onButtonClick_Bt(View view){
		//Stop all connection services
		Intent stop_connections = new Intent(Constants.STOP_SERVICE);
		stop_connections.putExtra("code", Constants.CODE_CONNECTIONS);
		sendBroadcast(stop_connections);

		if(!checkBox_Developer.isChecked()){
			//Start activity requesting for a bluetooth device
			Intent intent = new Intent(this, BtDeviceActivity.class);
			startActivityForResult(intent, Constants.REQUEST_BTDEVICE);
		}
		else{
			Intent bt_server = new Intent(this, BtServerActivity.class);
			startActivity(bt_server);
		}

        //isConnected = false;
		refreshStatus();
	}

    public void onButtonClick_Disconnect(View view){
        //Stop all connection services
        Intent stop_connections = new Intent(Constants.STOP_SERVICE);
        stop_connections.putExtra("code", Constants.CODE_CONNECTIONS);
        sendBroadcast(stop_connections);
    }

	public void onButtonClick_Options(View view){
		Intent options = new Intent(this, OptionsActivity.class);
		options.putExtra("developer", checkBox_Developer.isChecked());
		options.putExtra("running", acqService.isRunning());
		options.putExtra("status", acqService.isConnected());
		options.putExtra("mode", usingWifi);
		options.putExtra("ip", SERVERIP);
		options.putExtra("port", String.valueOf(PORT));
		startActivity(options);
	}
	
	@Override
	protected void onResume(){
		super.onResume();

		if(acqService != null){
            //isConnected = acqService.isConnected();

            //Checks with the acquisition service if the connection is still active
            refreshStatus();

			if(!hasList){
				itemList = acqService.getItemList();
				if(itemList != null){
					refresh_ListView();
					hasList = true;
                }
            }
			else{
				refresh_ListView();
			}
		}
		
		//Register the receiver
		registerReceiver(msgReceiver, msgFilter);
	}
	
	@Override
	protected void onPause(){
		super.onPause();
		//Unregister the receiver
		try{
			unregisterReceiver(msgReceiver);
		} catch(IllegalArgumentException e){
            e.printStackTrace();
        }
	}
	
	//Disconnect when ending
	@Override
	protected void onDestroy(){
		super.onDestroy();
		
		//Stop all services and unregister the receiver
		Intent stop_services = new Intent(Constants.STOP_SERVICE);
		stop_services.putExtra("code", Constants.CODE_ALL);
		sendBroadcast(stop_services);
		
		unbindService(acqConnection);
	}

	//When the user clicks on an item. Opens the details of that item
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,	long id){
		Intent details = new Intent(this, DetailsActivity.class);
		
		//Insert the information
        details.putExtra("position", position);
		startActivity(details);
		
		//Save the position of the selected item in case the user changes the name (custom name)
		posDetails = position;
	}
}

