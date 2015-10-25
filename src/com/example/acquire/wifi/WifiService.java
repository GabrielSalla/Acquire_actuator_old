package com.example.acquire.wifi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import com.example.acquire.Constants;
import com.example.acquire.R;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class WifiService extends Service{
	private static final int SUCCESS_CONNECT = 0;
	private static final int MESSAGE_READ = 1;
	private static final int DISCONNECTED = 2;
	
	private static final int MODE_NORMAL = 1;
	private static final int MODE_SETUP = 2;

	private BroadcastReceiver msgReceiver;
	private BroadcastReceiver wifiReceiver;
	
	private String SERVERIP;
	private int PORT;
	
	private int mode = 0;
	
	private String SSID;
	private String PSW;
	
	private boolean isConnected = false;

	private ConnectThread connect = null;
	private ConnectedThread connectedThread = null;

	//Handler to manage the connection and received messages
	private Handler mHandler=new Handler(){
		@Override
		public void handleMessage(Message msg){
			super.handleMessage(msg);
			switch(msg.what){
				//If is connected
				case SUCCESS_CONNECT:
					isConnected = true;
					
					connectedThread=new ConnectedThread((Socket)msg.obj);
					connectedThread.start();

					//If it's setup mode
					if(mode == MODE_SETUP){
						//Send the command
						String str = "WIFI:" + SSID + "-" + PSW;
						connectedThread.write(str.getBytes());

						//Show a toast
						mHandler.post(new Runnable(){
							public void run(){
								Toast.makeText(getApplicationContext(), "Setup completed", Toast.LENGTH_SHORT).show();
				            }
				        });
					}

					//Broadcast successful connected
					Intent connected = new Intent(Constants.CONNECTED);
					sendBroadcast(connected);
					
					break;

				//If received a message
				case MESSAGE_READ:
					String str;
					byte[] readbuf=(byte[])msg.obj;
					
					//Clean the string (remove all the null characters)
					str = new String(readbuf);
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

				//If the server disconnected
  				case DISCONNECTED:
  					isConnected = false;

					//Broadcast disconnected
					Intent disconnected = new Intent(Constants.DISCONNECTED);
					sendBroadcast(disconnected);

					//Show a toast
					mHandler.post(new Runnable(){
						public void run(){
							Toast.makeText(getApplicationContext(), "Server disconnected", Toast.LENGTH_SHORT).show();
						}
					});

					//Stop itself
					stopSelf();
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
				} catch(InterruptedException e){
					e.printStackTrace();
				}
				//If the watchdog hasn't been reset, stop
				if(reset)
					reset = false;
				else
					active = false;
			}

			//Broadcast to stop services
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
	
	private Watchdog watchdog;

	private UDPReceiveThread udpReceive;
	
	@Override
	public void onCreate(){
		super.onCreate();

		initBroadcastReceivers();
		
		//Starts the watchdog
		watchdog = new Watchdog();
		watchdog.start();
	}

	//Initializing broadcast receivers
	private void initBroadcastReceivers(){
        IntentFilter msgFilter;

		//Broadcast receiver to handle messages
		msgReceiver = new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent){
				String action = intent.getAction();

				//If received any broadcast, reset the watchdog
				watchdog.reset_timer();

				//Start connection
				if(action.contains(Constants.START_CONNECTION)){
					mode = MODE_NORMAL;

					//Get the IP and port
					SERVERIP = intent.getStringExtra("ip");
					PORT = Integer.parseInt(intent.getStringExtra("port"));

					//Stop the connection, if connected
					if(connect != null){
                        connect.cancel();
                    }

					if(connectedThread != null){
                        connectedThread.cancel();
                    }

					//Try to connect
					connect = new ConnectThread(SERVERIP, PORT);
					connect.start();
				}

				//Setup the wifi
				if(action.contains(Constants.SETUP_WIFI)){
					SSID = intent.getStringExtra("ssid");
					PSW = intent.getStringExtra("psw");

					//If is already connected, just send the command
					if(isConnected){
						String str;
						str = "WIFI:" + SSID + "-" + PSW;
						connectedThread.write(str.getBytes());
					}
					//Otherwise, try to connect
					else{
						mode = MODE_SETUP;

						SERVERIP = intent.getStringExtra("ip");
						PORT = Integer.parseInt(intent.getStringExtra("port"));

						//Try to connect
						connect = new ConnectThread(SERVERIP, PORT);
						connect.start();
					}
				}

				//Scan the network for devices
				if(action.contains(Constants.SCAN_NETWORK)){
					//Thread to receive the UDP messages
					udpReceive = new UDPReceiveThread();
					udpReceive.start();

					//Thread to send the UDP broadcast message
					sendUDPBroadcastThread broadcast = new sendUDPBroadcastThread();
					broadcast.start();
				}

				//Send a message
				if(action.contains(Constants.SEND_MESSAGE)){
					if(mode == MODE_NORMAL){
						String str = intent.getStringExtra("message");
						if(connectedThread != null)
							connectedThread.write(str.getBytes());
					}
				}

				//Stop the service
				if(action.contains(Constants.STOP_SERVICE)){
					int code = intent.getIntExtra("code", -1);

					if(code==Constants.CODE_ALL || code==Constants.CODE_CONNECTIONS || code==Constants.CODE_WIFI_SERVICE){
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

		msgFilter = new IntentFilter();
		msgFilter.addAction(Constants.START_CONNECTION);
		msgFilter.addAction(Constants.SCAN_NETWORK);
		msgFilter.addAction(Constants.SETUP_WIFI);
		msgFilter.addAction(Constants.SEND_MESSAGE);
		msgFilter.addAction(Constants.STOP_SERVICE);
		registerReceiver(msgReceiver, msgFilter);

		//Broadcast receiver to detect changes in the wifi connection
		wifiReceiver = new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent){
				String action = intent.getAction();

				//If wifi state changed
				if(action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)){
					WifiManager wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
					//If wifi is disabled
					if(!wifi.isWifiEnabled()){
						//Show a toast
						mHandler.post(new Runnable(){
							public void run(){
								Toast.makeText(getApplicationContext(), "WiFi must be ON", Toast.LENGTH_SHORT).show();
							}
						});

						//Close the wifi device activity
						Intent finish = new Intent(Constants.FINISH);
						sendBroadcast(finish);
						stopSelf();
					}
				}
			}
		};

		IntentFilter wifiFilter;
		wifiFilter=new IntentFilter();
		wifiFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		registerReceiver(wifiReceiver, wifiFilter);
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
		} catch(IllegalArgumentException e){
			e.printStackTrace();
		}
		try{
			unregisterReceiver(wifiReceiver);
		} catch(IllegalArgumentException e){
			e.printStackTrace();
		}
		
		if(udpReceive != null){
            udpReceive.cancel();
        }

        //Stop the connection, if connected
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

	//Thread to send a message using UDP broadcast
	private class sendUDPBroadcastThread extends Thread {
		DatagramSocket socket;
		DatagramPacket packet;
		String message = "requestIP";

		public sendUDPBroadcastThread(){
			try{
				//Creates the socket
				if(socket == null) {
					socket = new DatagramSocket(null);
					socket.setReuseAddress(true);
					socket.setBroadcast(true);
					socket.bind(new InetSocketAddress(8080));
					socket.setBroadcast(true);
				}
			} catch(SocketException e){
				e.printStackTrace();
			}
		}

		public void run(){
			try{
				packet = new DatagramPacket(message.getBytes(), message.length(), getBroadcastAddress(), 8080);

				//Send the broadcast message
				socket.send(packet);

				socket.close();
			} catch(SocketException e){
				e.printStackTrace();
			} catch(IOException e){
				Log.d("UDP", "Timed out");
				e.printStackTrace();
			} catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	//Returns the broadcast address
	InetAddress getBroadcastAddress() throws IOException{
		WifiManager wifi = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		DhcpInfo dhcp = wifi.getDhcpInfo();

		int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
		byte[] quads = new byte[4];
		for (int k = 0; k < 4; k++)
			quads[k] = (byte)((broadcast >> k * 8) & 0xFF);
		return InetAddress.getByAddress(quads);
	}

	//Thread to wait for UDP messages
	private class UDPReceiveThread extends Thread {
		DatagramSocket socket;
		DatagramPacket packet;
		boolean running = true;

		public UDPReceiveThread(){
			try{
				//Creates the socket
				if(socket == null) {
					socket = new DatagramSocket(null);
					socket.setReuseAddress(true);
					socket.setBroadcast(true);
					socket.bind(new InetSocketAddress(8080));
				}
			} catch(SocketException e){
				e.printStackTrace();
			}
		}

		public void run(){
			//Counter to wait for timeout 5 times
			int counter = 5;

			while(running) {
				try{
					byte[] buf = new byte[32];
					packet = new DatagramPacket(buf, buf.length);
					socket.setSoTimeout(1000);

					//Wait for a message
					socket.receive(packet);

					//Clean the string (remove all the null characters)
					String message = new String(packet.getData());
					int endoftext = message.indexOf("\0");
					if (endoftext != -1)
						message = message.substring(0, endoftext);

					//If the message is different from the one sent
					if (!message.equals("requestIP")) {
						//Broadcast an intent with the message
						Intent answer = new Intent(Constants.UDP_ANSWER);
						answer.putExtra("message", message);
						sendBroadcast(answer);
					}
				} catch(SocketTimeoutException e){
					//If the socket timed out, decrement the counter
					counter--;
					if(counter == 0)
						cancel();
					e.printStackTrace();
				} catch(IOException e){
					e.printStackTrace();
				}
			}
		}

		//Stop the thread
		public void cancel(){
			running = false;
			if(socket != null){
				socket.close();
			}
		}
	}

	//Thread to connect
	private class ConnectThread extends Thread{
		private String IP;
		private int port;
		private Socket socket;
		private boolean trying = true;
		private boolean connected = false;
	 
		public ConnectThread(String host, int port){
			IP = host;
			this.port = port;
		}
	 
		public void run(){
			//Counter to try to connect 5 times
			int counter = 5;
			
			while(trying){
				socket = new Socket();
				try{
					counter--;
					if(counter == 0)
						trying = false;

					socket.connect((new InetSocketAddress(IP, port)), 500);
					//If successful connected, stop trying
					trying = false;
					connected = true;
					
					//Manage the connection(in a separate thread)
					mHandler.obtainMessage(SUCCESS_CONNECT, socket).sendToTarget();
				} catch (IOException e){
					e.printStackTrace();
					try{
						socket.close();
					} catch(IOException closeException){
						closeException.printStackTrace();
					}
				}
			}
			//If connection wasn't successful
			if(!connected){
				mHandler.post(new Runnable(){
					public void run(){
						Toast.makeText(getApplicationContext(), "Could not connect, try again", Toast.LENGTH_SHORT).show();
		            }
		        });
			}
		}

		//Will cancel an in-progress connection, and close the socket
		public void cancel(){
			trying = false;
			
			try{
				socket.close();
			} catch(IOException e){
				e.printStackTrace();
			}
		}
	}

	//Thread to handle the active connection
	private class ConnectedThread extends Thread{
		private final Socket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;
	 
		public ConnectedThread(Socket socket){
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;
	 
			//Get the input and output streams, using temp objects because member streams are final
			try{
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch(IOException e){
				e.printStackTrace();
			}
	 
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
