package com.example.acquire;

import java.util.Calendar;

public class Time{
	//Returns time as DD/MM/YYYY - HH:MM:SS
	public static String getTime(){
		Calendar c = Calendar.getInstance();
		
		String time = c.get(Calendar.DAY_OF_MONTH) + "/" + (c.get(Calendar.MONTH)+1) + "/" + c.get(Calendar.YEAR);
		time += " - " + c.get(Calendar.HOUR) + ":" + c.get(Calendar.MINUTE) + ":" + c.get(Calendar.SECOND);
		
		return time;
	}

	//Returns time as DDMMYYYYHHMMSS
	public static String getRawTime(){
		Calendar c = Calendar.getInstance();
		
		String time = "" + c.get(Calendar.DAY_OF_MONTH) + (c.get(Calendar.MONTH)+1) + c.get(Calendar.YEAR);
		time += c.get(Calendar.HOUR) + c.get(Calendar.MINUTE) + c.get(Calendar.SECOND);
		
		return time;
	}
}
