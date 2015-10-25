package com.example.acquire.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;
import java.util.UUID;

import com.example.acquire.Constants;
import com.example.acquire.R;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class BtServerActivity extends Activity{
	public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	protected static final int SUCCESS_CONNECT = 0; 
	protected static final int MESSAGE_READ = 1;
	
	TextView textView_Status;
	
	BluetoothDevice device=null;
	
	int val[] = new int[4];
	
	String msgrec;

	/*ToggleButton bt1;
	ToggleButton bt2;
	SeekBar seekbar1;
	SeekBar seekbar2;*/
	
	BluetoothAdapter btAdapter=BluetoothAdapter.getDefaultAdapter();

	//Handler to handle messages inside the same activity
	Handler mHandler=new Handler(){
		@Override
		public void handleMessage(Message msg){
			super.handleMessage(msg);
			switch(msg.what){
				//If is connected
				case SUCCESS_CONNECT:
					connectedThread=new ConnectedThread((BluetoothSocket)msg.obj);
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
		textView_Status = (TextView)findViewById(R.id.textView_status);

		/*bt1 = (ToggleButton)findViewById(R.id.toggleButton1);
		bt2 = (ToggleButton)findViewById(R.id.toggleButton2);

		seekbar1 = (SeekBar)findViewById(R.id.seekBar1);
		seekbar2 = (SeekBar)findViewById(R.id.seekBar2);*/
	}
	
	private void initConnection(){
		receiver=new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent){
				String action = intent.getAction();

				//If is connected
				if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
					device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					
					textView_Status.setText(Html.fromHtml("<font color=\"green\"> <b>" + device.getName() + "</b><br>" + device.getAddress() + "</font>"));
				}
				
				//If its disconnected
				if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action) || BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED .equals(action)){
					textView_Status.setText(Html.fromHtml("<font color=\"red\"> <b>" + device.getName() + "</b><br>" + device.getAddress() + "</font>"));
					
					connectedThread.cancel();
					acceptThread=new AcceptThread();
					acceptThread.start();
				}
				
				//If the state of the bluetooth adapter changed
				if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
					//If the state is OFF
					if(btAdapter.getState()==BluetoothAdapter.STATE_OFF){
						Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_must_be_enabled), Toast.LENGTH_SHORT).show();
						finish();
					}
				}
			}
		};
		
		//The actions that are important for the application are registered on the BroadcastReceiver
		filter=new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

		//Check if bluetooth is enabled
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		if(!btAdapter.isEnabled()){
			Intent enable_bluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    startActivityForResult(enable_bluetooth, Constants.REQUEST_ENABLE_BLUETOOTH);
		}
		else{	
			//Start accepting connections
			acceptThread=new AcceptThread();
			acceptThread.start();
		}
	}
	
	//After user enable (or not) bluetooth, this method is called 
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data){
		super.onActivityResult(requestCode, resultCode, data);
		//If the user doesn't enable bluetooth, the application is finished

	    if(requestCode == Constants.REQUEST_ENABLE_BLUETOOTH){
			if(resultCode != RESULT_OK){
				Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_must_be_enabled), Toast.LENGTH_SHORT).show();
				finish();
			}
			//Start accepting connections
			acceptThread=new AcceptThread();
			acceptThread.start();
	    }
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
	
	//Decode the received message
	/*void decode_message(){
		String ans = null;
		
		//If request is the list of items
		if(msgrec.contains("#req_list")){
			ans = new String("$#it_list");
			
			//First item
			ans = ans + "#00nToggleButton1"; //Name
			ans = ans + "#00dJust a toggle button"; //Description
			if(bt1.isChecked()) //Value
				ans = ans + "#00v1";
			else
				ans = ans + "#00v0";
			
			//Second item
			ans = ans + "#01nToggleButton2"; //Name
			ans = ans + "#01dJust another toggle button"; //Description
			if(bt2.isChecked()) //Value
				ans = ans + "#01v1";
			else
				ans = ans + "#01v0";

			//Third item
			ans = ans + "#02nSeekBar 1"; //Name
			ans = ans + "#02dThis is a seek bar"; //Description
			ans = ans + "#02v" + seekbar1.getProgress(); //Value
			
			//Fourth item
			ans = ans + "#03nSeekBar 2"; //Name
			ans = ans + "#03dThis is another seek bar"; //Description
			ans = ans + "#03v" + seekbar2.getProgress(); //Value
			
			ans = ans + "$";
			
			//Send the answer
			connectedThread.write(ans.getBytes());
		}
		
		//If the request is the values of one or more items
		if(msgrec.contains("#req_val")){
			ans = new String("$#it_val");
			
			//First item
			if(bt1.isChecked())
				ans = ans + "#00v1";
			else
				ans = ans + "#00v0";

			//Second item
			if(bt2.isChecked())
				ans = ans + "#01v1";
			else
				ans = ans + "#01v0";

			//Third item
			ans = ans + "#02v" + seekbar1.getProgress();
			
			//Third item
			ans = ans + "#03v" + seekbar2.getProgress();
			
			ans = ans + "$";
			
			//Send the answer
			connectedThread.write(ans.getBytes());
		}
	}*/
	
	@Override
	protected void onResume(){
		super.onResume();
		registerReceiver(receiver, filter);	
	}
	
	@Override
	protected void onPause(){
		super.onPause();
		try{
			unregisterReceiver(receiver);
		} catch(IllegalArgumentException exp){ }
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

		try{
			unregisterReceiver(receiver);
		} catch(IllegalArgumentException exp){ }
	}
	
	//Waits for a client to connect
	private class AcceptThread extends Thread{
	    private final BluetoothServerSocket mmServerSocket;
	 
	    public AcceptThread(){
	        //Use a temporary object that is later assigned to mmServerSocket,
	        //because mmServerSocket is final
	        BluetoothServerSocket tmp = null;
	        btAdapter.cancelDiscovery();
	        try{
	            //MY_UUID is the app's UUID string, also used by the client code
	            tmp = btAdapter.listenUsingRfcommWithServiceRecord("Bluetooth test", MY_UUID);
	        } catch(IOException e){ }
	        mmServerSocket = tmp;
	    }
	 
	    public void run(){
	        BluetoothSocket socket = null;
	        //Keep listening until exception occurs or a socket is returned
	        btAdapter.cancelDiscovery();
	        while(true){
	            try{
	                socket = mmServerSocket.accept();
	            } catch(IOException e){ 
	            	break;
	            }
	            //If a connection was accepted
	            if(socket != null){
	    	        mHandler.obtainMessage(SUCCESS_CONNECT, socket).sendToTarget();
	            	try{
						mmServerSocket.close();
					} catch(IOException e){
						e.printStackTrace();
					}
	                break;
	            }
	        }
	    }
	    
	    public void cancel(){
	        try{
	            mmServerSocket.close();
	        } catch(IOException e){ }
	    }
	}
	
	//Socket is connected
	private class ConnectedThread extends Thread{
	    private final BluetoothSocket mmSocket;
	    private final InputStream mmInStream;
	    private final OutputStream mmOutStream;
	 
	    public ConnectedThread(BluetoothSocket socket){
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
