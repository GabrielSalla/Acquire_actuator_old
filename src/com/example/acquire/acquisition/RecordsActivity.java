package com.example.acquire.acquisition;

import com.example.acquire.acquisition.AcquisitionService.LocalBinder;
import com.example.acquire.R;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class RecordsActivity extends Activity implements OnItemClickListener {
	private boolean mBound;

	private final Handler myHandler = new Handler();

	private ArrayAdapter<String> listAdapter;
	ListView listView_Acquisitions;
	String[] acquisitions;

	//Connection
	private AcquisitionService acqService;
	private ServiceConnection acqConnection = new ServiceConnection(){
		@Override
		public void onServiceDisconnected(ComponentName name){
			mBound = false;
			acqService = null;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service){
			mBound = true;
			LocalBinder binder = (LocalBinder)service;
			acqService = binder.getService();
			
			myHandler.post(updateList);
		}
	};

    //Runnable to update the information
	private Runnable updateList = new Runnable(){
		@Override
		public void run(){
			acquisitions = acqService.getAcquisitions();
			
			if(acquisitions!=null){
				for(String str : acquisitions){
					listAdapter.add(str);
				}
			}
			
			listAdapter.notifyDataSetChanged();

            //For debugging
			String[] tables = acqService.getTables();
			for(String s : tables){
				Log.d("Tables", s);
			}
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_records);

		init();
		
        bindToService();
	}


    private void init(){
        listAdapter=new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, 0);
        listView_Acquisitions = (ListView)findViewById(R.id.listView_Acquisitions);
        listView_Acquisitions.setOnItemClickListener(this);
        listView_Acquisitions.setAdapter(listAdapter);
    }

    private void bindToService(){
        Intent acquisition = new Intent(getApplicationContext(), AcquisitionService.class);
        if(!bindService(acquisition, acqConnection, BIND_AUTO_CREATE)){
            Toast.makeText(getApplicationContext(), "Bind Error", Toast.LENGTH_SHORT).show();
        }
    }
	
	@Override
	protected void onResume() {
		super.onResume();

        //Check every time the activity resumes in case the user deleted an acquisition, so it must update the list
		if(mBound){
			acquisitions = acqService.getAcquisitions();
			listAdapter.clear();
			
			if(acquisitions != null){
				for(String str : acquisitions){
					listAdapter.add(str);
				}
			}
			listAdapter.notifyDataSetChanged();
		}
	}

	//When an item is selected, show it's information
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id){
		Intent load = new Intent(this, LoadAcquisitionActivity.class);
		load.putExtra("name", acquisitions[position]);
		startActivity(load);
	}
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
		
		unbindService(acqConnection);
	}
}
