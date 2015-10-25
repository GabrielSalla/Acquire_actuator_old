package com.example.acquire;

public class Constants{
	public static final String VERSION = "2015.10.19-1";
	
	public static final int REQUEST_ENABLE_BLUETOOTH = 1;
	public static final int REQUEST_BTDEVICE = 2;
	public static final int REQUEST_WIFIDEVICE = 3;
	public static final int REQUEST_WIFISSID = 4;

	public static final int CODE_ALL = 0;
	public static final int CODE_ACQUISITION_SERVICE = 1;
	public static final int CODE_WIFI_SERVICE = 2;
	public static final int CODE_BLUETOOTH_SERVICE = 3;
	public static final int CODE_CONNECTIONS = 4;
	
	//Broadcast Messages
	public static final String START_CONNECTION = "com.example.acquire.start_connection";
	public static final String MESSAGE_RECEIVED = "com.example.acquire.message_received";
	public static final String SEND_MESSAGE = "com.example.acquire.send_message";
	public static final String CONNECTED = "com.example.acquire.connected";
	public static final String DISCONNECTED = "com.example.acquire.disconnected";
	public static final String STOP_SERVICE = "com.example.acquire.stop_service";
	public static final String FINISH = "com.example.acquire.finish";
	public static final String UPDATE_DATA = "com.example.acquire.update_data";
	public static final String SCAN_NETWORK = "com.example.acquire.scan_network";
	public static final String UDP_ANSWER = "com.example.acquire.udp_answer";
	public static final String SETUP_WIFI = "com.example.acquire.setup_wifi";
}
