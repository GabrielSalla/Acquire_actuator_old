package com.example.acquire.wifi;

import com.example.acquire.Constants;
import com.example.acquire.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

public class WifiSetupActivity extends Activity {
	private EditText editText_IP;
	private EditText editText_Port;
	private EditText editText_SSID;
	private EditText editText_PSW;

    private boolean valid = true;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_wifi_setup);

        checkWifi();

        if(valid)
		    init();
	}

    private void init(){
        editText_IP = (EditText)findViewById(R.id.editText_IP);
        editText_Port = (EditText)findViewById(R.id.editText_Port);
        editText_SSID = (EditText)findViewById(R.id.editText_SSID);
        editText_PSW = (EditText)findViewById(R.id.editText_PSW);

        //Loading last inputs
        SharedPreferences lastInput= getSharedPreferences("WifiSetup", 0);
        String IP;
        String PORT;
        String SSID = lastInput.getString("ssid", "");
        String PSW = lastInput.getString("psw", "");

        Intent it = getIntent();
        //If is connected, get the connection information
        if(it.getBooleanExtra("status", false)){
            IP = it.getStringExtra("ip");
            PORT = it.getStringExtra("port");
            //Don't allow to change
            editText_IP.setEnabled(false);
            editText_Port.setEnabled(false);
        }
        //If isn't connected, get the last input used
        else{
            Intent wifiService = new Intent(this, WifiService.class);
            startService(wifiService);
            IP = lastInput.getString("ip", "");
            PORT = lastInput.getString("port", "");
        }

        editText_IP.setText(IP);
        editText_IP.setText(PORT);
        editText_SSID.setText(SSID);
        editText_PSW.setText(PSW);
    }

    //Check if wifi is enabled and is connected to a network
    //Used in onResume() because must check every time the activity resumes
    private void checkWifi(){
        //Check if wifi is on
        WifiManager wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        if(!wifi.isWifiEnabled()){
            Toast.makeText(this, "WiFi must be ON", Toast.LENGTH_SHORT).show();
            valid = false;
            finish();
        }

        if(valid){
            //Check if is connected to a network
            ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if(!mWifi.isConnected()){
                Toast.makeText(this, "You must be connected to a network", Toast.LENGTH_SHORT).show();
                valid = false;
                finish();
            }
        }
    }

	//Method called after picking an wifi network
	protected void onActivityResult(int requestCode, int resultCode, Intent result){
		if(resultCode == RESULT_OK){
			editText_SSID.setText(result.getStringExtra("ssid"));
		}
	}
	
	//Search for nearby wifi networks
	public void onButtonClick_Search(View v){
		Intent search = new Intent(this, WifiSearchActivity.class);
		startActivityForResult(search, Constants.REQUEST_WIFISSID);
	}
	
	//Setup the wifi connection
	public void onButtonClick_Ok(View v){
		String IP =  editText_IP.getText().toString();
		String PORT =  editText_Port.getText().toString();
		String SSID = editText_SSID.getText().toString();
		String PSW = editText_PSW.getText().toString();
		
		SharedPreferences lastInput= getSharedPreferences("WifiSetup", 0);
		SharedPreferences.Editor editor = lastInput.edit();
		editor.putString("ip", IP);
		editor.putString("port", PORT);
		editor.putString("ssid", SSID);
		editor.putString("psw", PSW);
		editor.apply();

		setup_wifi(IP, PORT, SSID, PSW);
		
		finish();
	}
	
	//Send the command
	private void setup_wifi(String IP, String PORT, String SSID, String PSW){
		Intent wifi_info = new Intent(Constants.SETUP_WIFI);
		if(IP.length() == 0)
			IP = "192.168.4.1";
		if(PORT.length() == 0)
			PORT = "8080";
		wifi_info.putExtra("ip", IP);
		wifi_info.putExtra("port", PORT);
		wifi_info.putExtra("ssid", SSID);
		wifi_info.putExtra("psw", PSW);
		sendBroadcast(wifi_info);
	}
}
