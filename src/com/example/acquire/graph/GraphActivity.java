package com.example.acquire.graph;

import java.util.ArrayList;

import com.example.acquire.Constants;
import com.example.acquire.ItemData;
import com.example.acquire.acquisition.AcquisitionService;
import com.example.acquire.acquisition.AcquisitionService.LocalBinder;
import com.example.acquire.R;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer.LegendAlign;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.view.MotionEventCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Toast;

public class GraphActivity extends Activity implements OnItemClickListener{
	private boolean ready = false;
	
	private ListView listView_Items;
	
	private ArrayList<ItemData> item_list;
	
	private Model[] list_data;
	private CheckBoxAdapter listAdapter;
	
	//Graph variables
	private GraphView graph;
	private SparseArray<LineGraphSeries<DataPoint>> seriesArray;
	private ColorManager colorManager;
	
	//Graph variables (drawing)
	private float x1, x2, offset = 0, lastoffset, startoffset;
	private int width;
	private CheckBox checkBox_Hold;

	private int maxY = 0;
	private int minY = 0;

	private IntentFilter msgFilter;
	private BroadcastReceiver msgReceiver;
	
	private final Handler myHandler = new Handler();
	
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
	
	private Runnable update = new Runnable() {
		@Override
		public void run() {
			item_list = acqService.getItemList();
			//period = acqService.getPeriod();
			
			initGraph();
			
			if(item_list != null){
				setViewPort();

				//Set all items as inactive
				for(ItemData it : item_list){
					it.setActive(false);
				}
				
				//Update the list
				refresh_ListView();
				
				//Allow the updates
				ready = true;
			}
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_graph);
		
		init();
		
        initBroadcastReceivers();
	}

    private void init(){
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        width = displaymetrics.widthPixels;

        listView_Items = (ListView)findViewById(R.id.listView_Items);
        listView_Items.setOnItemClickListener(this);

        checkBox_Hold = (CheckBox)findViewById(R.id.checkBox_Hold);

        Intent acquisition = new Intent(getApplicationContext(), AcquisitionService.class);
        if(!bindService(acquisition, acqConnection, BIND_AUTO_CREATE)){
            Toast.makeText(getApplicationContext(), "Bind Error", Toast.LENGTH_SHORT).show();
        }
    }

    private void initBroadcastReceivers(){
        msgReceiver =new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent){
                String action = intent.getAction();

                //If the data updated
                if(action.equals(Constants.UPDATE_DATA) && ready){
                    //The Graph Activity must refresh the data. Only the main thread can change the UI
                    acqService.refreshSeriesData();
                    int nVal = acqService.getnVal();

                    Log.d("nVal", ""+nVal);

                    if(checkBox_Hold.isChecked() && (nVal > 101)){
                        offset++;

                        //Checks if it won't go beyond the left limit
                        //Dragging and updating at the same time might add 1 more to the offset
                        if((nVal - 1) - 100 < (int)offset){
                            offset = (nVal - 1) - 100;
                        }
                    }
                    setViewPort();
                }
            }
        };

        msgFilter = new IntentFilter();
        msgFilter.addAction(Constants.UPDATE_DATA);
    }

	private void initGraph(){
		graph = (GraphView)findViewById(R.id.graph);
		
		//Setup the touch and drag functions for the graph
		graph.setOnTouchListener(new OnTouchListener(){
			public boolean onTouch(View v, MotionEvent event){
				int action = MotionEventCompat.getActionMasked(event);

				switch(action){
					//Touch started
					case MotionEvent.ACTION_DOWN:
						x1 = 100 * event.getX() / width;
						lastoffset = 0;
						startoffset = offset;
						break;

					//Dragging
					case MotionEvent.ACTION_MOVE:
						x2 = 100 * event.getX() / width;

						int nVal = acqService.getnVal();

						//Only changes the offset if nVal > 101 and there are series being displayed
						if(nVal > 101 && seriesArray.size() > 0){
							//Convert to integer and checks if the distance moved is greater than 1
							if(Math.abs((lastoffset - (x1 - x2))) > 1){
								//If variation is > 0
								if(x2 - x1 > 0)
									lastoffset = (float) Math.floor(x2 - x1);
									//If variation is < 0
								else
									lastoffset = (float) Math.ceil(x2 - x1);

								offset = startoffset + lastoffset;

								//Adjusts the offset
								//Right limit
								if(offset < 0)
									offset = 0;

								//Left limit
								if((nVal - 1) - 100 < (int)offset){
									offset = (nVal - 1) - 100;
								}

								setViewPort();

								//Have to refresh the series after changing the offset
								for(ItemData item : item_list){
									if(item.isActive()){
										item.refreshSeriesData();
									}
								}
							}
						}
						//If nVal < 100, just reset the variables
						else{
							x1 = x2;
						}
						break;

					//Touch finished
					case MotionEvent.ACTION_UP:
						long t1 = event.getDownTime();
						long t2 = event.getEventTime();
						//If the touch time is less than 180ms, it's not a drag
						if(t2 - t1 <= 180)
							ToggleLegend(null);
						break;
				}
				return true;
			}
		});
		
		colorManager = new ColorManager();

		seriesArray = new SparseArray<LineGraphSeries<DataPoint>>();
		
		//Configure the graph
		graph.getViewport().setXAxisBoundsManual(true);
		graph.getViewport().setYAxisBoundsManual(true);
		graph.getGridLabelRenderer().setNumHorizontalLabels(6);
		
		graph.setBackgroundColor(Color.WHITE);
		graph.getGridLabelRenderer().setGridColor(Color.rgb(200, 200, 200));
		graph.getGridLabelRenderer().setVerticalLabelsColor(Color.BLACK);
		graph.getGridLabelRenderer().setHorizontalLabelsColor(Color.BLACK);
		graph.getGridLabelRenderer().setLabelsSpace(3);
		graph.getGridLabelRenderer().setLabelVerticalWidth(80);
		graph.getGridLabelRenderer().setHighlightZeroLines(true);
		graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter(){
			@Override
			public String formatLabel(double value, boolean isValueX){
				if(isValueX){
					return super.formatLabel(value, isValueX);
				}
				else{
					return super.formatLabel(value, isValueX) + " ";
				}
			}
		});

		setViewPort();
	}
	
	//Refresh the items list
	private void refresh_ListView(){
		list_data = new Model[item_list.size()];
		int position = 0;
		for(ItemData item : item_list){
			Model new_item;
			if(item.getCustomName().length() == 0)
				new_item = new Model(item.getName(), false);
			else
				new_item = new Model(item.getCustomName(), false);
			list_data[position] = new_item;
			position++;
		}

		listAdapter = new CheckBoxAdapter(this, list_data);
		listView_Items.setAdapter(listAdapter);
		listAdapter.notifyDataSetChanged();
	}
	
	//Sets the number of divisions and graph limits
	private void setViewPort(){
		int minX, maxX;
		int nDiv = 0;
		
		int nVal = acqService.getnVal();
		//Max to the left is nVal-1
		int limRight = nVal - 1;

		//X axis
		//Before 100 values, expand the viewport
		if(limRight < 100){
			//Only changes maxX when left%10 == 1 (11, 21, 31...)
			maxX = ((limRight - 1)/10 +1)*10;
			minX = 0;
		}
		//After 100 values, stop expanding the viewport and start shifting the graph
		else{
			maxX = nVal - 1 - (int)offset;
			minX = maxX - 100;
		}
		graph.getViewport().setMinX(minX);
		graph.getViewport().setMaxX(maxX);
		
		//Only changes when the left%10 == 1
		if(limRight < 100 && limRight % 10==1){
			switch((limRight / 10) + 1){
				case 1:
				case 5:
				case 10:
					nDiv = 6;
					break;
				case 2:
					nDiv = 5;
					break;
				case 3:
				case 6:
					nDiv = 7;
					break;
				case 4:
				case 8:
					nDiv = 9;
					break;
				case 7:
					nDiv = 8;
					break;
				case 9:
					nDiv = 10;
					break;
			}
			graph.getGridLabelRenderer().setNumHorizontalLabels(nDiv);
		}

		//If no series are selected, just show the default for binary
		if(seriesArray.size() == 0){
			graph.getViewport().setMaxY(2);
			graph.getViewport().setMinY(0);
			graph.getGridLabelRenderer().setNumVerticalLabels(3);
			return;
		}

		//Y axis
		//Get the maximum and the minimum values
		int new_maxY = 0;
		int new_minY = 0;
		if(item_list!=null){
			for(ItemData it : item_list){
				if(it.isActive()){
					int item_max = it.getMaxY();
					if(item_max > new_maxY)
						new_maxY = item_max;

					int item_min = it.getMinY();
					if(item_min < new_minY)
						new_minY = item_min;
				}
			}
		}

		//Only updates the Y axis if maxY or minY changed
		if(new_maxY != maxY || new_minY != minY){
			maxY = new_maxY;
			minY = new_minY;

			int limTop, limBot;

			//Set the maximum value
			switch(maxY){
				//All values are negative or it's binary
				case 0:
				case 1:
					//If minY is 0, show the binary default
					if(minY == 0){
						limTop = 2;
					}
					else{
						limTop = ((maxY - 1) / 10 + 1) * 10;
					}
					break;

				//Maximum
				case 100:
					limTop = 100;
					break;

				//Others
				default:
					limTop = ((maxY - 1) / 10 + 1) * 10;
			}

			//Set the minimum value
			switch(minY){
				//All values are positive
				case 0:
					limBot = 0;
					break;

				//Minimum
				case -100:
					limBot = -100;
					break;

				//Others
				default:
					limBot = ((minY + 1) / 10 - 1) * 10;
			}

			//Check the range being shown
			int range = limTop - limBot;

			//If the range is bigger than 100, it's impracticable to divide in parts of 10 or less
			//Then, it's necessary to divide in parts of 20. It means both positive and negative
			//limits must be multiples of 20
			if(range >= 100){
				//If range/10 is odd, must add/subtract 10 to the odd limit/10
				if((range / 10) % 2 == 1){
					//limTop/10 is odd
					if((limTop / 10) % 2 == 1){
						limTop += 10;
					}
					//limBot/10 is odd
					else{
						limBot -= 10;
					}
				}
				//If range/10 is even, add/subtract 10 to the limits of limit/10 is odd
				else{
					//limTop/10 is odd, hence limBot/10 is also odd
					//Add/subtract 10 from both of them
					if((limTop / 10) % 2 == 1){
						limTop += 10;
						limBot -= 10;
					}
				}
			}

			//Update the range
			range = limTop - limBot;

			//Set the number of labels
			switch(range){
				case 2: //limTop = 2
					nDiv = 3;
					break;

				case 10:
				case 50:
				case 100:
					nDiv = 6;
					break;

				case 20:
					nDiv = 5;
					break;

				case 30:
				case 60:
				case 120:
					nDiv = 7;
					break;

				case 40:
				case 80:
				case 160:
					nDiv = 9;
					break;

				case 70:
				case 140:
					nDiv = 8;
					break;

				case 90:
				case 180:
					nDiv = 10;
					break;

				case 200:
					nDiv = 11;
			}
			graph.getViewport().setMaxY(limTop);
			graph.getViewport().setMinY(limBot);
			graph.getGridLabelRenderer().setNumVerticalLabels(nDiv);
		}
	}
	
	//Show/Hide the legend
	public void ToggleLegend(View v){
		if(seriesArray.size() != 0){
			if(graph.getLegendRenderer().isVisible()){
				graph.getLegendRenderer().setVisible(false);
			}
			else{
				graph.getLegendRenderer().setVisible(true);
			}
		}
		else{
			graph.getLegendRenderer().setVisible(false);
		}
		graph.onDataChanged(true, true);
	}
	
	@Override
	protected void onResume(){
		super.onResume();
		registerReceiver(msgReceiver, msgFilter);
	}

	@Override
	protected void onPause(){
		super.onPause();

		try{
			unregisterReceiver(msgReceiver);
		} catch(IllegalArgumentException exp){ }
	}
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
		
		unbindService(acqConnection);
	}
	
	//When the user clicks an item on the list
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id){
		CheckBox cb = (CheckBox) view.findViewById(R.id.checkBoxHold);
		cb.performClick();
		
		//If the item is now checked
		if(cb.isChecked()){
			//If can add a new series to the graph
			if(seriesArray.size() < ColorManager.ColorsCount){
				//Get a color
				int color = colorManager.getNextColor();
				
				//Create and add the series
				ItemData item = item_list.get(position);
				item.setColor(color);
				item.setActive(true);
				seriesArray.put(position, item.getSeries());
				graph.addSeries(item.getSeries());
				item.refreshSeriesData();
				
				//Reset the legend style
				graph.getLegendRenderer().resetStyles();
				graph.getLegendRenderer().setAlign(LegendAlign.TOP);

				setViewPort();
				
			}
			//If cannot add a new series to the graph
			else{
				//Uncheck the item and tells the user
				cb.setChecked(false);
				Toast.makeText(this, "Only " + ColorManager.ColorsCount + " items are allowed at once.", Toast.LENGTH_SHORT).show();
			}
		}
		//If the item is now unchecked
		else{
			//Remove the series from the graph
			ItemData remove = item_list.get(position);
			remove.setActive(false);
			colorManager.clearColor(remove.getColor());
			graph.removeSeries(remove.getSeries());
			seriesArray.remove(position);

			//Resets the legend style
			graph.getLegendRenderer().resetStyles();
			graph.getLegendRenderer().setAlign(LegendAlign.TOP);
			
			//If there are no more series on the graph, hide the legend
			if(seriesArray.size() == 0){
				graph.getLegendRenderer().setVisible(false);
			}
			
			setViewPort();
		}
	}
}
