package com.example.acquire.graph;

public class ColorManager{
	public static final int ColorsCount = 6;
	public static final int[][] Colors = {
		{20, 20, 248},
		{255, 150, 0},
		{255, 0, 0},
		{0, 255, 0},
		{128, 0, 128},
		{83, 199, 255}};
	
	private boolean[] isUsed = {false, false, false, false, false, false};
	
	public ColorManager(){ }
	
	public int getNextColor(){
		int i;
		
		for(i=0; i<ColorsCount; i++){
			if(!isUsed[i])
				break;
		}
		isUsed[i] = true;
		return i;
	}
	
	public void clearColor(int i){
		isUsed[i] = false;
	}
}
