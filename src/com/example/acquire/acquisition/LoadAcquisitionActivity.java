package com.example.acquire.acquisition;

import com.example.acquire.DBAdapter.AcquisitionDetails;
import com.example.acquire.acquisition.AcquisitionService.LocalBinder;
import com.example.acquire.graph.GraphActivity;
import com.example.acquire.R;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

public class LoadAcquisitionActivity extends Activity{
	private final Handler myHandler = new Handler();

	TextView textView_Name;
	TextView textView_nItems;
	TextView textView_Period;
	TextView textView_nVal;
	TextView textView_Start;
	TextView textView_Stop;
	
	String name;

	//Connection
	private AcquisitionService acqService;
	private ServiceConnection acqConnection = new ServiceConnection(){
		@Override
		public void onServiceDisconnected(ComponentName name){
			acqService = null;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service){
			LocalBinder binder = (LocalBinder)service;
			acqService = binder.getService();
			
			myHandler.post(update);
		}
	};

    //Runnable to update the information
	private Runnable update = new Runnable(){
		@Override
		public void run(){
			AcquisitionDetails details = acqService.getDetails(name);

			int nItems = details.nItems;
			int period = details.period;
			int nVal = details.nVal;

			String start = details.start;
			String stop = details.stop;

			textView_Name.setText(name);
			textView_nItems.setText(String.valueOf(nItems));
			textView_Period.setText(String.valueOf(period));
			textView_nVal.setText(String.valueOf(nVal));
			textView_Start.setText(start);
			textView_Stop.setText(stop);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_load_acquisition);
		
        init();
		
		bindToService();
	}

    private void init(){
        Intent it = getIntent();
        name = it.getStringExtra("name");

        textView_Name = (TextView)findViewById(R.id.textView_Name);
        textView_nItems = (TextView)findViewById(R.id.textView_nItems);
        textView_Period = (TextView)findViewById(R.id.textView_Period);
        textView_nVal = (TextView)findViewById(R.id.textView_nVal);
        textView_Start = (TextView)findViewById(R.id.textView_Start);
        textView_Stop = (TextView)findViewById(R.id.textView_Stop);
    }

    private void bindToService(){
        Intent acquisition = new Intent(getApplicationContext(), AcquisitionService.class);
        if(!bindService(acquisition, acqConnection, BIND_AUTO_CREATE)){
            Toast.makeText(getApplicationContext(), "Bind Error", Toast.LENGTH_SHORT).show();
        }
    }
	
	//Load an acquisition
	public void onButtonClick_Load(View v){
		acqService.loadAcquisition(name);
		
		Intent graph = new Intent(this, GraphActivity.class);
		startActivity(graph);
		
		finish();
	}
	
	//Export an acquisition
	public void onButtonClick_Export(View v){
		Uri path = acqService.exportRecordXLS(name);
		
		Intent send = new Intent(Intent.ACTION_SEND);
		send.setType("file/*");
		send.putExtra(Intent.EXTRA_STREAM, path);
		
		startActivity(Intent.createChooser(send, "Export"));
	}
	
	//Delete an acquisition
	public void onButtonClick_Delete(View v){
		acqService.DeleteAcquisition(name);
		
		finish();
	}
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
		
		unbindService(acqConnection);
	}
}
