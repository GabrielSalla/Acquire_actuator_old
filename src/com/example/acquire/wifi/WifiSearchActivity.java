package com.example.acquire.wifi;

import java.util.ArrayList;
import java.util.List;

import com.example.acquire.R;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

public class WifiSearchActivity extends Activity implements OnItemClickListener{
	private ListView listView_Networks;
	private ArrayList<String> list_data;
	private ArrayAdapter<String> listAdapter;
	
	private TextView status;
	
	private WifiManager wifiManager;

	private BroadcastReceiver wifiReceiver;
    private IntentFilter wifiFilter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_wifi_search);
		
		init();

        initBroadcastReceivers();
		
		//Start scanning for wifi networks
		wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
		wifiManager.startScan();
	}

    private void init(){
        status = (TextView)findViewById(R.id.textView_Status);
        status.setText("Searching Wifi Networks");

        list_data = new ArrayList<String>();

        listView_Networks = (ListView)findViewById(R.id.listView_Networks);
        listAdapter =  new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1, list_data);
        listView_Networks.setAdapter(listAdapter);
        listView_Networks.setOnItemClickListener(this);
        listView_Networks.getLayoutParams().width = (int) (getWidestView(this, listAdapter)*1.05);
    }

    private void initBroadcastReceivers(){
        wifiReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent){
                //Scan if finished
                if(intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)){
                    List<ScanResult> mScanResults = wifiManager.getScanResults();

                    for(ScanResult result : mScanResults){
                        list_data.add(result.SSID);
                    }

                    listAdapter.notifyDataSetChanged();
                    status.setText("Search complete\n\nFound:");

                    listView_Networks.getLayoutParams().width = (int) (getWidestView(getApplicationContext(), listAdapter)*1.05);
                }
            }
        };

        wifiFilter =new IntentFilter();
        wifiFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
    }

	@Override
	protected void onResume() {
		super.onResume();
		
		registerReceiver(wifiReceiver, wifiFilter);
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		try{
			unregisterReceiver(wifiReceiver);
		} catch(IllegalArgumentException e){ }
	}

	//Get the widest view in the list
	private static int getWidestView(Context context, Adapter adapter){
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
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		Intent result = new Intent();
		result.putExtra("ssid", list_data.get(position));
		setResult(RESULT_OK, result);
		finish();
	}
}
