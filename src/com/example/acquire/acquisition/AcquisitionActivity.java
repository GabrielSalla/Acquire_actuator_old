package com.example.acquire.acquisition;

import com.example.acquire.acquisition.AcquisitionService.LocalBinder;
import com.example.acquire.R;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Html;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class AcquisitionActivity extends Activity{
	private boolean developer;
	
	private EditText editText_Period;
	private EditText editText_Time;
	private TextView textView_Status;
	private CheckBox checkBox_Save;
	
	private int period = 0;

	private final Handler myHandler = new Handler();
	
	//Connection to the acquisition service
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
	private Runnable update = new Runnable() {
		@Override
		public void run() {
			acqService.setDeveloper(developer);

            //Update the period
			period = acqService.getPeriod();
			editText_Period.setText(String.valueOf(period));

            //Update the status
			if(acqService.isRunning()){
				textView_Status.setText(Html.fromHtml("Status: <font color=\"green\"> <b>Running</b>"));
			}
			else{
				textView_Status.setText(Html.fromHtml("Status: <font color=\"red\"> <b>Stopped</b>"));
			}
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_acquisition);
		
		init();

		bindToService();
	}

	private void init(){
		Intent it = getIntent();
		developer = it.getBooleanExtra("developer", false);

		editText_Period = (EditText)findViewById(R.id.editText_Period);
		editText_Time = (EditText)findViewById(R.id.editText_Time);
		textView_Status = (TextView)findViewById(R.id.textView_Status);
		checkBox_Save = (CheckBox)findViewById(R.id.checkBox_Save);

		textView_Status.setText(Html.fromHtml("Status: <font color=\"red\"> <b>Stopped</b>"));

		editText_Time.setEnabled(false);

		checkBox_Save.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View v){
                if(checkBox_Save.isChecked()){
                    editText_Time.setEnabled(true);
                }else{
                    editText_Time.setEnabled(false);
                }
            }
        });
	}

	private void bindToService(){
		Intent acquisition = new Intent(getApplicationContext(), AcquisitionService.class);
		if(!bindService(acquisition, acqConnection, BIND_AUTO_CREATE)){
			Toast.makeText(getApplicationContext(), "Bind Error", Toast.LENGTH_SHORT).show();
		}
	}
	
	//Start the acquisition
	public void onButtonClick_StartAcquisition(View v){
		int new_period = Integer.parseInt(editText_Period.getText().toString());
		
		//Set the focus to the status TextView (hide the keyboard)
		InputMethodManager imm = (InputMethodManager)this.getSystemService(Context.INPUT_METHOD_SERVICE);
	    imm.hideSoftInputFromWindow(textView_Status.getApplicationWindowToken(), 0);
		
	    //If the period is valid
		if(new_period >= 200 && new_period <= 10000){
			period = new_period;
			boolean started = acqService.startAcquisition(period, checkBox_Save.isChecked(), Integer.parseInt(editText_Time.getText().toString()));
			if(started)
				textView_Status.setText(Html.fromHtml("Status: <font color=\"green\"> <b>Running</b>"));
		}
		else{
			Toast.makeText(this, "Period must be between 200ms and 10.000ms", Toast.LENGTH_SHORT).show();
		}
	}
	
	//Stop the acquisition
	public void onButtonClick_StopAcquisition(View v){
		acqService.stopAcquisition();

		textView_Status.setText(Html.fromHtml("Status: <font color=\"red\"> <b>Stopped</b>"));
	}

    //When destroying the activity, unbind the service
	@Override
	protected void onDestroy(){
		super.onDestroy();
		
		unbindService(acqConnection);
	}
}
