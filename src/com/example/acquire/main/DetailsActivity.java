package com.example.acquire.main;

import com.example.acquire.ItemData;
import com.example.acquire.R;
import com.example.acquire.acquisition.AcquisitionService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class DetailsActivity extends Activity{
	private EditText editText_CustomName;
    private SeekBar seekBar_Value;
    Button button_Set;

    private String lastCustomName;

    ItemData item;
    int position;
    int range[];

    private final Handler myHandler = new Handler();

    //Connection
    private AcquisitionService acqService = null;
    private ServiceConnection acqConnection = new ServiceConnection(){
        @Override
        public void onServiceDisconnected(ComponentName name){
            acqService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service){
            AcquisitionService.LocalBinder binder = (AcquisitionService.LocalBinder)service;
            acqService = binder.getService();

            myHandler.post(update);
        }
    };

    private Runnable update = new Runnable() {
        @Override
        public void run(){
            TextView textView_Name = (TextView)findViewById(R.id.textView_Name);
            editText_CustomName = (EditText)findViewById(R.id.editText_CustomName);
            TextView textView_Description = (TextView)findViewById(R.id.textView_Description);
            TextView textView_Actuator = (TextView)findViewById(R.id.textView_Actuator);
            TextView textView_Range = (TextView)findViewById(R.id.textView_Range);
            button_Set = (Button)findViewById(R.id.button_Set);
            seekBar_Value = (SeekBar)findViewById(R.id.seekBar_Value);


            item = acqService.getItemList().get(position);
            textView_Name.setText(item.getName());
            textView_Description.setText(item.getDescription());
            lastCustomName = item.getCustomName();
            editText_CustomName.setText(lastCustomName);

            if(item.isActuator()){
                textView_Actuator.setText(Html.fromHtml("Actuator: <font color=\"green\"><b>YES</b></font>"));
                range = item.getItemRange();
                textView_Range.setText("" + range[0] + " to " + range[1]);

                button_Set.setText("Set " + item.getLastValue());

                seekBar_Value.setMax(range[1] - range[0]);
                seekBar_Value.setProgress(item.getLastValue() - range[0]);
                seekBar_Value.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b){
                        button_Set.setText("Set " + (seekBar.getProgress() + range[0]));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar){}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar){}
                });
            }
            else{
                textView_Actuator.setText(Html.fromHtml("Actuator: <font color=\"red\"><b>NO</b></font>"));
                seekBar_Value.setEnabled(false);
                button_Set.setEnabled(false);
            }
        }
    };

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_details);

		init();
	}

	private void init(){
		Intent intent = getIntent();
        position = intent.getIntExtra("position", -1);

        //Start the acquisition service
        Intent acquisition = new Intent(getApplicationContext(), AcquisitionService.class);
        if(!bindService(acquisition, acqConnection, BIND_AUTO_CREATE)){
            Toast.makeText(getApplicationContext(), "Bind Error", Toast.LENGTH_SHORT).show();
        }
	}

    public void onButtonClick_Set(View view){
        if(item.isActuator()){
            int value = seekBar_Value.getProgress() + range[0];
            if(value >= range[0] && value <= range[1]){
                acqService.actuate(position, value);
            }
        }
    }

    public void onButtonClick_Ok(View view){
        String customName = editText_CustomName.getText().toString();

        if(!customName.equals(lastCustomName)){
            acqService.ChangeCustomName(position, customName);
        }

        finish();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        unbindService(acqConnection);
    }
}
