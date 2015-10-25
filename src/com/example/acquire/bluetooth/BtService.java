package com.example.acquire.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import com.example.acquire.Constants;
import com.example.acquire.R;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class BtService extends Service{
	private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	protected static final int SUCCESS_CONNECT = 0;
	protected static final int MESSAGE_READ = 1;
	protected static final int DISCONNECTED = 2;

	private BroadcastReceiver msgReceiver;
	private BroadcastReceiver btReceiver;
	
	private BluetoothAdapter btAdapter;
	private BluetoothDevice selectedDevice;

	private ConnectThread connect;
	private ConnectedThread connectedThread;

    private boolean isConnected = false;

	//Handler to manage the connection and received messages
	private Handler mHandler=new Handler(){
		@Override
		public void handleMessage(Message msg){
			super.handleMessage(msg);
			switch(msg.what){
				//If is connected
				case SUCCESS_CONNECT:
                    isConnected = true;

					connectedThread=new ConnectedThread((BluetoothSocket)msg.obj);
					connectedThread.start();
					
					Intent connected = new Intent(Constants.CONNECTED);
					sendBroadcast(connected);
					
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

					//Broadcast the received message
                    if(str.length() > 0){
                        Intent message_received = new Intent(Constants.MESSAGE_RECEIVED);
                        message_received.putExtra("received_message", str);
                        sendBroadcast(message_received);
                    }
					
					break;
			}
		}
	};

	//A watchdog thread. If the connection stays idle for 3 minutes, the service stops itself
	private class Watchdog extends Thread{
		private boolean active=true;
		private boolean reset=false;
		
		public void run(){
			while(active){
				try{
					Thread.sleep(3*60*1000);
				} catch(InterruptedException e){ }
				//If the watchdog hasn't been reseted, stop
				if(reset)
					reset = false;
				else
					active = false;
			}

			Intent stop_services = new Intent(Constants.STOP_SERVICE);
			stop_services.putExtra("code", Constants.CODE_ALL);
			sendBroadcast(stop_services);
		}
		
		public void reset_timer(){
			reset = true;
		}
		
		public void finish_thread(){
			active = false;
			reset = false;
			this.interrupt();
		}
	}
	
	Watchdog watchdog;
	
	@Override
	public void onCreate(){
		super.onCreate();
		
		initBT();

		initBroadcastReceivers();
		
		//Start the watchdog
		watchdog = new Watchdog();
		watchdog.start();
	}
	
	//Initializing bluetooth
	private void initBT(){
		btAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	//Initializing broadcasts
	private void initBroadcastReceivers(){
        IntentFilter filter;

		//Broadcast receiver to handle messages
		msgReceiver = new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent){
				String action = intent.getAction();

				//If received any broadcast, reset the watchdog
				watchdog.reset_timer();
				if(action.contains(Constants.START_CONNECTION)){
					selectedDevice = intent.getExtras().getParcelable("device");

					if(connect != null){
                        connect.cancel();
                    }

					if(connectedThread != null){
                        connectedThread.cancel();
                    }

					connect = new ConnectThread(selectedDevice);
					connect.start();
				}

				if(action.contains(Constants.SEND_MESSAGE)){
					String str = intent.getStringExtra("message");
					connectedThread.write(str.getBytes());
				}

				if(action.contains(Constants.STOP_SERVICE)){
					int code = intent.getIntExtra("code", -1);

					if(code==Constants.CODE_ALL || code==Constants.CODE_CONNECTIONS || code==Constants.CODE_BLUETOOTH_SERVICE){
                        if(isConnected){
                            //Broadcast disconnected
                            Intent disconnected = new Intent(Constants.DISCONNECTED);
                            sendBroadcast(disconnected);
                        }
						stopSelf();
					}
				}
			}
		};

		filter=new IntentFilter();
		filter.addAction(Constants.START_CONNECTION);
		filter.addAction(Constants.SEND_MESSAGE);
		filter.addAction(Constants.STOP_SERVICE);
		registerReceiver(msgReceiver, filter);

		//Broadcast receiver to handle messages
		btReceiver =new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent){
				String action = intent.getAction();

				//Bluetooth disconnected
				if(action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)){
                    isConnected = false;

					Intent disconnected = new Intent(Constants.DISCONNECTED);
					sendBroadcast(disconnected);

					mHandler.post(new Runnable(){
						public void run(){
							Toast.makeText(getApplicationContext(), "Server disconnected", Toast.LENGTH_SHORT).show();
						}
					});

					stopSelf();
				}

				//If the state of the bluetooth adapter changed
				if(action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)){
					//If the state is OFF
					if(btAdapter.getState() == BluetoothAdapter.STATE_OFF){

						mHandler.post(new Runnable(){
							public void run(){
								Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_must_be_enabled), Toast.LENGTH_SHORT).show();
							}
						});

                        //Close the wbluetooth device activity
						Intent finish = new Intent(Constants.FINISH);
						sendBroadcast(finish);
						stopSelf();
					}
				}
			}
		};

		filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		registerReceiver(btReceiver, filter);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		
		try{
			unregisterReceiver(msgReceiver);
		} catch(IllegalArgumentException exp){ }
		try{
			unregisterReceiver(btReceiver);
		} catch(IllegalArgumentException exp){ }
		
		if(connect != null){
            connect.cancel();
        }
		
		if(connectedThread != null){
            connectedThread.cancel();
        }
		
		if(watchdog != null){
            watchdog.finish_thread();
        }
	}

	@Override
	public IBinder onBind(Intent intent){
		return null;
	}
	
	//Thread to connect
	private class ConnectThread extends Thread{
		private BluetoothSocket socket;
	 
		public ConnectThread(BluetoothDevice device){
			try{
				socket = device.createRfcommSocketToServiceRecord(uuid);
			} catch(IOException e){
                e.printStackTrace();
            }
		}
	 
		public void run(){
			try{
				//Connect the device through the socket. This will block until it succeeds or throws an exception
				socket.connect();
			} catch(IOException connectException){
				//Unable to connect, close the socket and get out
				try{
					socket.close();
				} catch(IOException closeException){ }
				return;
			}
	 
			//Manage the connection(in a separate thread)
			mHandler.obtainMessage(SUCCESS_CONNECT, socket).sendToTarget();
		}

		//Will cancel an in-progress connection, and close the socket
		public void cancel(){
			try{
				socket.close();
			} catch(IOException e){ }
		}
	}

    //Thread to handle the active connection
	private class ConnectedThread extends Thread{
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;
	 
		public ConnectedThread(BluetoothSocket socket){
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;
	 
			//Get the input and output streams, using temp objects because member streams are final
			try{
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch(IOException e){ }
	 
			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}
	 
		public void run(){
			byte[] buffer;
			int bytes;
	 
			//Keep listening to the InputStream until an exception occurs
			while(true){
				try{
					buffer = new byte[100];
					//Read from the InputStream
					bytes = mmInStream.read(buffer);
                    if(bytes < 0){
						mHandler.obtainMessage(DISCONNECTED).sendToTarget();
						break;
					}
                    else{
                        //Send the obtained bytes to the UI activity
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    }
				} catch(IOException e){
                    e.printStackTrace();
					break;
				}
			}
		}
	 
		//Send data through the connection
		public void write(byte[] bytes){
			try{
				mmOutStream.write(bytes);
			} catch(IOException e){
                e.printStackTrace();
            }
		}
	 
		//Shutdown the connection
		public void cancel(){
			try{
				mmInStream.close();
			} catch (IOException e){
                e.printStackTrace();
            }
			try{
				mmOutStream.close();
			} catch (IOException e){
                e.printStackTrace();
            }
			try{
				mmSocket.close();
			} catch(IOException e){
                e.printStackTrace();
            }
		}
	}
}
