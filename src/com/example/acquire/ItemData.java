package com.example.acquire;

import java.util.ArrayList;

import android.graphics.Color;

import com.example.acquire.graph.ColorManager;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class ItemData{
	private final String ItemName;
	private String ItemCustomName;
	private final String ItemDescription;
	private final String Code;

    private boolean actuator = false;
    private int itemRange[];

	private ArrayList<Integer> data_array;
	private int last_value;
	private int maxY, minY;
	
	//Graph
	private int seriesColor;
	private LineGraphSeries<DataPoint> dataSeries;
	private boolean isActive = false;
	
	//Constructors
	public ItemData(int number, String name, String new_name, String description, int starting_value){
		ItemName = name;
		ItemCustomName = new_name;
		ItemDescription = description;
		
		Code = "#" + number/10 + number%10;
		
		data_array = new ArrayList<Integer>();
		
		last_value = starting_value;

		maxY = 0;
		minY = 0;
		
		//Set attributes
		dataSeries = new LineGraphSeries<DataPoint>();
		dataSeries.setThickness(3);
		if(ItemCustomName.length()==0)
			dataSeries.setTitle(ItemName);
		else
			dataSeries.setTitle(ItemCustomName);
	}

    public ItemData(int number, String name, String new_name, String description, int starting_value, int range[]){
        ItemName = name;
        ItemCustomName = new_name;
        ItemDescription = description;

        Code = "#" + number/10 + number%10;

        data_array = new ArrayList<Integer>();

        last_value = starting_value;

        maxY = 0;
        minY = 0;

        //Set attributes
        dataSeries = new LineGraphSeries<DataPoint>();
        dataSeries.setThickness(3);
        if(ItemCustomName.length()==0)
            dataSeries.setTitle(ItemName);
        else
            dataSeries.setTitle(ItemCustomName);

        actuator = true;
        itemRange = range;
    }
	
	public ItemData(int number, String name, String new_name, String description, int[] values){
		ItemName = name;
		ItemCustomName = new_name;
		ItemDescription = description;
		
		Code = "#" + number/10 + number%10;
		
		data_array = new ArrayList<Integer>();
		for(int v : values){
			data_array.add(v);
		}
		
		last_value = data_array.get(data_array.size()-1);

		//Get the maxY
		maxY = -1;
		for(int v : data_array){
			if(v > maxY)
				maxY = v;
		}

		//Get the minY
		minY = 0;
		for(int v : data_array){
			if(v < minY)
				minY = v;
		}
		
		//Set attributes
		dataSeries = new LineGraphSeries<DataPoint>();
		dataSeries.setThickness(3);
		if(ItemCustomName.length()==0)
			dataSeries.setTitle(ItemName);
		else
			dataSeries.setTitle(ItemCustomName);
		
		//Reset the data points
        refreshSeriesData();
	}
	
	public String getName(){
		return ItemName;
	}
	
	public String getCustomName(){
		return ItemCustomName;
	}
	
	public void setCustomName(String customName){
		ItemCustomName = customName;

		if(ItemCustomName.length()==0)
			dataSeries.setTitle(ItemName);
		else
			dataSeries.setTitle(ItemCustomName);
	}

    public String getDescription(){
        return ItemDescription;
    }
    public boolean isActuator(){
        return actuator;
    }

    public int[] getItemRange(){
        if(actuator){
            return itemRange;
        }
        else{
            return null;
        }
    }
	
	public String getCode(){
		return Code;
	}

	public int getMaxY(){
		return maxY;
	}

	public int getMinY(){
		return minY;
	}
	
	public void setLastValue(int val){
		last_value = val;
	}
	
	public int getLastValue(){
		if(data_array.size() == 0){
			return last_value;
		}
		return data_array.get(data_array.size()-1);
	}
	
	public void ShiftData(){
		data_array.add(last_value);
		if(last_value > maxY)
			maxY = last_value;
		if(last_value < minY)
			minY = last_value;
	}
	
	public void setColor(int color){
		seriesColor = color;
		int R = ColorManager.Colors[seriesColor][0];
		int G = ColorManager.Colors[seriesColor][1];
		int B = ColorManager.Colors[seriesColor][2];
		
		dataSeries.setColor(Color.argb(128, R, G, B));
	}
	
	public void refreshSeriesData(){
		DataPoint[] data;
        
		data = new DataPoint[data_array.size()];
		int size = data_array.size();
		for(int i=0; i<size; i++){
			DataPoint v = new DataPoint(i, data_array.get(i));
			data[i] = v;
		}
		
		dataSeries.resetData(data);
	}
	
	public void clear(){
		data_array.clear();
	}
	
	public LineGraphSeries<DataPoint> getSeries(){
		return dataSeries;
	}

	public int getColor(){
		return seriesColor;
	}
	
	public void setActive(boolean act){
		isActive = act;
	}
	
	public boolean isActive(){
		return isActive;
	}
}
