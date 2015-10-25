package com.example.acquire.bluetooth;

import java.util.ArrayList;
import java.util.Set;

import com.example.acquire.Constants;
import com.example.acquire.R;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.Window;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;


public class BtDeviceActivity extends Activity implements OnItemClickListener{
	private ListView listView_Devices;
	private ArrayAdapter<String> listAdapter;
	
	//Devices list
	private ArrayList<BluetoothDevice> devices;
    
	private BluetoothAdapter btAdapter;

	//Receiver
    private BroadcastReceiver msgReceiver;
	private IntentFilter msgFilter;
	
	@Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_bt_device);

        init();

        initBroadcastReceivers();

        //Start the bluetooth service to hold the connection
        Intent it = new Intent(this, BtService.class);
		startService(it);
    }

    //Initialize variables
	private void init(){
		listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, 0);

		listView_Devices =(ListView)findViewById(R.id.listView_Devices);
		listView_Devices.setOnItemClickListener(this);
		listView_Devices.setAdapter(listAdapter);

		devices=new ArrayList<BluetoothDevice>();
	}

    //Check if bluetooth is enabled
    //Used in onResume() because must check every time the activity resumes
    private void checkBT(){
        //Check if bluetooth is enabled
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if(!btAdapter.isEnabled()){
            Intent enable_bluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enable_bluetooth, Constants.REQUEST_ENABLE_BLUETOOTH);
        }
        else{
            getPairedDevices();
        }
    }

    private void initBroadcastReceivers(){
        msgReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent){
                String action = intent.getAction();

                if(action.equals(Constants.FINISH)){
                    finish();
                }
            }
        };

        msgFilter =new IntentFilter();
        msgFilter.addAction(Constants.FINISH);
    }
	
	//Load paired devices
	private void getPairedDevices(){
		Set<BluetoothDevice> devicesArray;
		devicesArray = btAdapter.getBondedDevices();

        listAdapter.clear();

		//Add to the devices list
		if(devicesArray.size()>0){
			for(BluetoothDevice device:devicesArray){
				devices.add(device);
				listAdapter.add(Html.fromHtml("<b>" + device.getName() + "</b>") + "\n" + device.getAddress());
			}
    		listView_Devices.getLayoutParams().width = (int)(getWidestView(getApplicationContext(), listAdapter)*1.05);
			listAdapter.notifyDataSetChanged();
		}
	}
	
	//Get the widest text in the list
	public static int getWidestView(Context context, Adapter adapter){
	    int maxWidth = 0;
	    View view = null;
	    FrameLayout fakeParent = new FrameLayout(context);
	    for(int i=0, count=adapter.getCount(); i<count; i++){
	        view = adapter.getView(i, view, fakeParent);
	        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
	        int width = view.getMeasuredWidth();
	        if (width > maxWidth){
	            maxWidth = width;
	        }
	    }
	    return maxWidth;
	}
	
	//After user enable (or not) bluetooth, this method is called 
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data){
		super.onActivityResult(requestCode, resultCode, data);
		
		//If the user doesn't enable bluetooth, the application is finished
	    if(requestCode == Constants.REQUEST_ENABLE_BLUETOOTH){
			if(resultCode != RESULT_OK){
				Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_must_be_enabled), Toast.LENGTH_SHORT).show();
				finish();
			}

			getPairedDevices();
	    }
	}

	//Method is called when the user clicks on an item on the list
	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3){
		//Returns the selected device
		Intent result = new Intent();
		result.putExtra("device", devices.get(arg2));
		setResult(RESULT_OK, result);
		finish();
	}

    @Override
    protected void onResume(){
        super.onResume();

        checkBT();

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
}