package com.example.acquire.wifi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

import com.example.acquire.R;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class WifiServerActivity extends Activity{
	protected static final int SUCCESS_CONNECT = 0;
	protected static final int MESSAGE_READ = 1;
	protected static final int DISCONNECTED = 2;
	
	TextView textView_status;
	
	String SERVERIP = "";
	int SERVERPORT = 8080;
	
	int val[] = new int[4];
	
	String msgrec;
	
	Intent info;
	
	//Handler to handle messages inside the same activity
	Handler mHandler=new Handler(){
		@Override
		public void handleMessage(Message msg){
			super.handleMessage(msg);
			switch(msg.what){
				//If is connected
				case SUCCESS_CONNECT:
					connectedThread=new ConnectedThread((Socket)msg.obj);
					connectedThread.start();
					
					break;
				
				//If received a message
				case MESSAGE_READ:
					String str;
					byte[] readbuf=(byte[])msg.obj;

					//Clean the string (remove all the null characters)
					str=new String(readbuf);
					int endoftext = str.indexOf("\0");
					if(endoftext!=-1)
						str = str.substring(0, endoftext);

					//Check if the first character is $ (a new message)
					if(str.charAt(0) == '$'){
						msgrec = str;

						//Check if the message is complete
						if(str.charAt(str.length()-1) == '$'){
							decode_message();
						}
					}
					else{
						//Append the received message
						msgrec = msgrec + str;
						//Check if the message is complete
						if(str.charAt(str.length()-1) == '$'){
							decode_message();
						}
					}
					break;
					
				//If the client disconnectes
				case DISCONNECTED:
					Toast.makeText(getApplicationContext(), "Client disconnected", Toast.LENGTH_SHORT).show();
					finish();
					break;
			}
		}
	};

	//Threads to control the connection
	AcceptThread acceptThread;
	ConnectedThread connectedThread;

	//Receivers
	IntentFilter filter;
	BroadcastReceiver receiver;

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_server);
		
		init();
		
		initConnection();
	}
	
	private void init(){
		textView_status = (TextView)findViewById(R.id.textView_status);
		
		for(int i=0; i<4; i++)
			val[i]=0;
	}
	
	private void initConnection(){
		SERVERIP = getIP();
		
		SERVERPORT = 8080;

		//Start accepting connections
		acceptThread=new AcceptThread();
		acceptThread.start();
	}
	
	//Get local IP
	String getIP(){
		WifiManager wifiMan = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInf = wifiMan.getConnectionInfo();
		int ipAddress = wifiInf.getIpAddress();
		String ip = String.format("%d.%d.%d.%d", (ipAddress & 0xff),(ipAddress >> 8 & 0xff),(ipAddress >> 16 & 0xff),(ipAddress >> 24 & 0xff));
		return ip;
	}
	
	public void randomize(View v){
		Random r = new Random();
		
		val[0]=r.nextInt(101);
		val[1]=r.nextInt(101);
		val[2]=r.nextInt(2);
		val[3]=r.nextInt(2);
	}
	
	//Decode the received message
	void decode_message(){
		String ans = null;
		
		//If request is the list of items
		if(msgrec.contains("#req_list")){
			ans = new String("$#it_list");
			
			//First item
			ans = ans + "#00nSeekBar 1"; //Name
			ans = ans + "#00dInteger 0 to 100"; //Description
			ans = ans + "#00v"+val[0];
			
			//First item
			ans = ans + "#01nSeekBar 2"; //Name
			ans = ans + "#01dInteger 0 to 100"; //Description
			ans = ans + "#01v"+val[1];
			
			//First item
			ans = ans + "#02nDig. Input 1"; //Name
			ans = ans + "#02dBoolean"; //Description
			ans = ans + "#02v"+val[2];
			
			//First item
			ans = ans + "#03nDig. Input 2"; //Name
			ans = ans + "#03dBoolean"; //Description
			ans = ans + "#03v"+val[3];
			
			
			ans = ans + "$";
			
			//Send the answer
			connectedThread.write(ans.getBytes());
		}
		
		//If the request is the values of one or more items
		if(msgrec.contains("#req_val")){
			ans = new String("$#it_val");

			ans = ans + "#00v"+val[0];
			ans = ans + "#01v"+val[1];
			ans = ans + "#02v"+val[2];
			ans = ans + "#03v"+val[3];
			
			ans = ans + "$";
			
			//Send the answer
			connectedThread.write(ans.getBytes());
		}
	}
	
	@Override
	protected void onDestroy(){
		super.onDestroy();

		try{
			acceptThread.cancel();
		} catch(NullPointerException e){ }
		
		try{
			connectedThread.cancel();
		} catch(NullPointerException e){ }
	}

	//Waits for a client to connect
	private class AcceptThread extends Thread{
		ServerSocket serverSocket;
		
		public AcceptThread(){
			//Use a temporary object that is later assigned to mmServerSocket,
			//because mmServerSocket is final
			try{
				serverSocket = new ServerSocket(SERVERPORT);
			} catch (IOException e){ }

			mHandler.post(new Runnable(){
				@Override
				public void run(){
					textView_status.setText("Listening on: " + SERVERIP + ":" + SERVERPORT);
				}
			});
		}
	 
		public void run(){
			Socket client = null;
			//Keep listening until exception occurs or a socket is returned
			while(true){
				try{
					client = serverSocket.accept();
				} catch(IOException e){ 
					break;
				}
				//If a connection was accepted
				if(client != null){
					mHandler.obtainMessage(SUCCESS_CONNECT, client).sendToTarget();
					try{
						serverSocket.close();
					} catch(IOException e){
						e.printStackTrace();
					}
					break;
				}
			}
		}
		
		public void cancel(){
			try{
				serverSocket.close();
			} catch(IOException e){ }
		}
	}

	//Socket is connected
	private class ConnectedThread extends Thread{
		private final Socket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;
	 
		public ConnectedThread(Socket socket){
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;
	 
			//Get the input and output streams, using temp objects because
			//member streams are final
			try{
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch(IOException e){ }
	 
			mmInStream = tmpIn;
			mmOutStream = tmpOut;
			
			//Changes the status
			mHandler.post(new Runnable(){
				@Override
				public void run(){
					textView_status.setText("Connected!");
				}
			});
		}
	 
		public void run(){
			byte[] buffer;  //buffer store for the stream
			int bytes; //bytes returned from read()
	 
			//Keep listening to the InputStream until an exception occurs
			while(true){
				try{
					buffer=new byte[100];
					//Read from the InputStream
					bytes = mmInStream.read(buffer);
					if(bytes<0){
						mHandler.obtainMessage(DISCONNECTED).sendToTarget();
						break;
					}
					//Send the obtained bytes to the UI activity
					mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
				} catch(IOException e){
					break;
				}
			}
		}
	 
		//Call this from the main activity to send data to the remote device
		public void write(byte[] bytes){
			try{
				mmOutStream.write(bytes);
			} catch(IOException e){ }
		}
	 
		//Call this from the main activity to shutdown the connection 
		public void cancel(){
			try{
				mmInStream.close();
			} catch (IOException e){ }

			try{
				mmOutStream.close();
			} catch (IOException e){ }
			
			try{
				mmSocket.close();
			} catch(IOException e){ }
		}
	}
}
