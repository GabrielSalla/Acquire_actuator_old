package com.example.acquire.graph;

public class Model{
	String name;
	boolean value;
	 
	Model(String name, boolean value){
		this.name = name;
		this.value = value;
	}
	
	public String getName(){
		return this.name;
	}
	
	public void setValue(boolean val){
		value = val;
	}
	
	public boolean getValue(){
		return this.value;
	}
}
