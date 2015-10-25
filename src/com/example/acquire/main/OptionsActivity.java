package com.example.acquire.main;

import com.example.acquire.acquisition.AcquisitionActivity;
import com.example.acquire.acquisition.RecordsActivity;
import com.example.acquire.graph.GraphActivity;
import com.example.acquire.wifi.WifiSetupActivity;
import com.example.acquire.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;

public class OptionsActivity extends Activity{
	private String IP;
	private String port;
	private boolean developer;
	private boolean isRunning;
	private boolean isConnected;
	private boolean usingWifi;
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_options);

		init();
	}

	private void init(){
		Intent it = getIntent();
		IP = it.getStringExtra("ip");
		port = it.getStringExtra("port");
		developer = it.getBooleanExtra("developer", false);
		isRunning = it.getBooleanExtra("running", false);
		isConnected = it.getBooleanExtra("status", false);
		usingWifi = it.getBooleanExtra("mode", false);

		Button button_Acquisition = (Button)findViewById(R.id.button_Acquisition);
		Button button_WifiSetup = (Button)findViewById(R.id.button_WifiSetup);
		Button button_Records = (Button)findViewById(R.id.button_Records);

		button_Acquisition.setEnabled(isConnected || developer);
		button_WifiSetup.setEnabled(!isConnected || usingWifi);
		button_Records.setEnabled(!isRunning || developer);
	}
	
	//Start Acquisition Activity
	public void onButtonClick_Acquisition(View v){
		Intent acquisition = new Intent(this, AcquisitionActivity.class);
		acquisition.putExtra("developer", developer);
		startActivity(acquisition);
		finish();
	}

	//Start Graph Activity
	public void onButtonClick_Graph(View v){
		Intent graph= new Intent(this, GraphActivity.class);
		startActivity(graph);
		finish();
	}

	//Start WifiSetup Activity
	public void onButtonClick_WifiSetup(View v){
		Intent wifiSetup= new Intent(this, WifiSetupActivity.class);
		wifiSetup.putExtra("ip", IP);
		wifiSetup.putExtra("port", port);
		wifiSetup.putExtra("status", isConnected);
		startActivity(wifiSetup);
		finish();
	}

	//Start Records Activity
	public void onButtonClick_Records(View v){
		Intent records= new Intent(this, RecordsActivity.class);
		startActivity(records);
		finish();
	}
}
