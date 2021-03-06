package com.rawr.remotemouse_client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.os.Binder;

public class SocketService extends Service {
	private String m_ip;
	private String m_id;
	private String m_key;
//	private SocketCallback m_callback = null;
	private StatusCallback m_statusCallback = null;
	private VerificationCallback m_verificationCallback = null;
	private Socket m_socket;
	private final IBinder m_binder = new SocketBinder();
	private BufferedOutputStream m_out = null;
	private BufferedInputStream m_in = null;
	
	private final int SERVER_PORT = 48048;
	
	public SocketService() {
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (m_socket.isConnected()) {
			try {
				m_socket.close();
			} catch (IOException e) {
				Log.e("socket_serv", "Failed to close socket.");
				e.printStackTrace();
			}
		}
	}
	
	public class SocketBinder extends Binder {
		SocketService getService() {
			return SocketService.this;
		}
	}
	
	private class ConnectRunnable implements Runnable {
		@Override
		public void run() {
			try {
				Log.d("socket_serv", "Connecting socket...");
				m_socket = new Socket();
				m_socket.setSoTimeout(10000);
				m_socket.connect(new InetSocketAddress(m_ip, SERVER_PORT), 1000);
				
				Log.d("socket_serv", "Initializing input/output streams");
				m_out = new BufferedOutputStream(m_socket.getOutputStream());
				m_in = new BufferedInputStream(m_socket.getInputStream());
				
				Log.d("socket_serv", "Performing challenge validation");
				issueNewStatus("Validating Key...");
				byte[] challenge = getChallenge();
				if (challenge != null) {
					issueNewStatus("Challenge received.");
					sendChallengeResponse(challenge);
					if (getVerificationResponse() && m_verificationCallback != null) {
						m_verificationCallback.verificationPass();
					} else {
						m_verificationCallback.verificationFail();
					}
				} else {
					issueNewStatus("Failed to get challenge.");
					Log.e("socket_serv", "Failed to retreive challenge");
				}
			} catch (IOException e) {
				issueNewStatus("Failed to connect.");
				Log.e("socket_serv", "IOException: " + e.toString());
			}
		}
	}
	
	public interface SocketCallback {
		void socketRead(String str);
	}
	
	public interface StatusCallback {
		void newStatus(String str);
	}
	
	public interface VerificationCallback {
		void verificationPass();
		void verificationFail();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return m_binder;
	}
		
//	public void setCallback(SocketCallback callback) {
//		m_callback = callback;
//	}
	
	public void setStatusCallback(StatusCallback callback) {
		m_statusCallback = callback;
	}
	
	public void setVerificationCallback(VerificationCallback callback) {
		m_verificationCallback = callback;
	}
	
	public void connectSocket(String ip, String id, String key) {
		m_ip = ip;
		m_id = id;
		m_key = key;
		new Thread(new ConnectRunnable()).start();
	}
	
	private byte[] getChallenge() {
		try {
			m_out.write("CHAL_REQ\n".getBytes("ASCII"));
			m_out.flush();
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			byte[] buf = new byte[256];
			m_in.read(buf, 0, 256);
			return buf;
		} catch(IOException e) {
			Log.e("socket_serv", "IOException: " + e.toString());
		}
		return null;
	}
	
	private void sendChallengeResponse(byte[] challenge) {
		int chalLen = getChallengeLength(challenge);
		int keyLen = m_key.length();
		byte[] hash = new byte[keyLen];
		byte[] keyBytes = strToBytes(m_key);
		int k = 0;
		for (int i = 0; i < chalLen; i++) {
			if (k >= keyLen) {
				k = 0;
			}
			hash[k] = (byte)((challenge[i] ^ keyBytes[k]) ^ hash[k]);
			k++;
		}
		try {
			m_out.write("CHAL_RSP".getBytes("ASCII"));
			m_out.write((byte)m_id.length());
			m_out.write(m_id.getBytes("ASCII"));
			m_out.write(hash);
			m_out.write('\n');
			m_out.flush();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private boolean getVerificationResponse() {
		int responseLen = 8;
		byte[] response = new byte[responseLen];
		byte[] passResponse = strToBytes("CHALPASS");
//		byte[] failResponse = strToBytes("CHALFAIL");
		try {
			m_in.read(response, 0, responseLen);
		} catch (IOException e) {
			Log.e("socket_serv", "Error: failed to read socket at verification response.");
			e.printStackTrace();
			return false;
		}
		boolean success = false;
		if (Arrays.equals(response, passResponse)) {
			success = true;
		}
		return success;
	}
	
	private void issueNewStatus(String str) {
		if (m_statusCallback != null) m_statusCallback.newStatus(str);
	}
	
	private byte[] strToBytes(String str) {
		char[] chars = str.toCharArray();
		byte[] bytes = new byte[str.length()];
		
		for (int i = 0; i < str.length(); i++) {
			bytes[i] = (byte)chars[i];
		}
		
		return bytes;
	}
	
	private int getChallengeLength(byte[] challenge) {
		int i = 0;
		while (challenge[i] != '\n' && i < challenge.length) {
			i++;
		}
		return i;
	}
}
