package com.example.acquire.wifi;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.acquire.Constants;
import com.example.acquire.R;


public class WifiDeviceActivity extends Activity{
	private IntentFilter msgFilter;
	private BroadcastReceiver msgReceiver;

	private EditText editText_Ip;
	private EditText editText_Port;
	
	private TextView textView_Devices;

    private boolean valid = true;

	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_wifi_device);

		init();

        initBroadcastReceivers();
	}

    //Check if wifi is enabled and is connected to a network
    //Used in onResume() because must check every time the activity resumes
    private void checkWifi(){
        valid = true;
        //Check if wifi is on
        WifiManager wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        if(!wifi.isWifiEnabled()){
            Toast.makeText(this, "WiFi must be ON", Toast.LENGTH_SHORT).show();
            valid = false;
            finish();
        }

        if(valid){
            //Check if is connected to a network
            ConnectivityManager connManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if(!mWifi.isConnected()){
                Toast.makeText(this, "You must be connected to a network", Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        if(valid){
            initWifi();
        }
    }

    private void initWifi(){
        //Start the wifi service to hold the connection
        Intent wifiService = new Intent(this, WifiService.class);
        startService(wifiService);
    }

	//Initialize variables
	private void init(){
		editText_Ip = (EditText)findViewById(R.id.editText_Ip);
		editText_Port = (EditText)findViewById(R.id.editText_Port);
		
		textView_Devices = (TextView)findViewById(R.id.textView_Devices);
		
		//Loading last inputs
		SharedPreferences lastInput= getSharedPreferences("WifiLastInput", 0);
		String ip = lastInput.getString("ip", "192.168.1.");
		String port = lastInput.getString("port", "");

		editText_Ip.setText(ip);
		editText_Port.setText(port);
	}

    private void initBroadcastReceivers(){
        msgReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent){
                String action = intent.getAction();

                if(action.equals(Constants.UDP_ANSWER)){
                    textView_Devices.setText(textView_Devices.getText().toString() + "\n" + intent.getStringExtra("message"));
                }

                if(action.equals(Constants.FINISH)){
                    finish();
                }
            }
        };

        msgFilter =new IntentFilter();
        msgFilter.addAction(Constants.UDP_ANSWER);
        msgFilter.addAction(Constants.FINISH);
    }

	//Button OK
	public void onButtonClick_Ok(View v){
		//Returns the IP and Port
		Intent result = new Intent();
		
		String ip =  editText_Ip.getText().toString();
		String port = editText_Port.getText().toString();

        if(ip.length() == 0 || port.length() == 0)
            return;
		
		result.putExtra("ip", ip);
		result.putExtra("port", port);
		
		setResult(RESULT_OK, result);
		
		//Save the input
		SharedPreferences lastInput= getSharedPreferences("WifiLastInput", 0);
		SharedPreferences.Editor editor = lastInput.edit();
		editor.putString("ip", ip);
		editor.putString("port", port);
		editor.apply();
		
		finish();
	}

	public void onButtonClick_Scan(View v){
		//Reset the text
		textView_Devices.setText("Devices on the network:");

		//Broadcast a SCAN_NETWORK intent. WifiService will handle it
		Intent broadcast = new Intent(Constants.SCAN_NETWORK);
		sendBroadcast(broadcast);
	}
	
	@Override
	protected void onResume(){
		super.onResume();

        checkWifi();

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
