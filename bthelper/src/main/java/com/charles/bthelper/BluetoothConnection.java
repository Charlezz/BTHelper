package com.charles.bthelper;

/**
 * Created by Charles on 2015. 8. 23..
 */

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

@TargetApi(10)
@SuppressLint("NewApi")
public class BluetoothConnection {
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    public static final int STATE_DISCONNECTED = 4; //now finished all the threads
    public static final int STATE_CONNECTION_FAILED = 5;
    public static final int STATE_DISCONNECTED_BY_ACCIDENT = 6; //now finished all the threads
    // Debugging
    private static final String TAG = "BluetoothChatService";
    private static final boolean D = false;
    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothChatSecure";
    private static final String NAME_INSECURE = "BluetoothChatInsecure";
    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MY_UUID_INSECURE = UUID.fromString("96588092-7650-0410-2573-557629979369");
    // Member fields
    private BluetoothAdapter mAdapter;
    private Handler mHandler;
    //    private AcceptThread mSecureAcceptThread;
//    private AcceptThread mInsecureAcceptThread;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private int id = -1;
    private boolean stopFlag = false;
    private BluetoothDevice mDevice;

    public BluetoothConnection(int id, Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        this.id = id;
        Log.e(TAG, "코넥션 생성자");
    }

    private BluetoothConnection() {
    }

    public synchronized int getState() {
        return mState;
    }

    private synchronized void setState(int state) {
        if (D) Log.e(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        mHandler.obtainMessage(BluetoothHelper.MESSAGE_STATE_CHANGE, state, id).sendToTarget();
    }

    public synchronized void listen(boolean secure) {
        if (D) Log.e(TAG, "listen");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_LISTEN);

        if (mAcceptThread == null) {
            Log.e(TAG, "억셉트 스레드 만들기전");
            mAcceptThread = new AcceptThread(secure);
            mAcceptThread.start();
        }
    }

    public synchronized void connect(BluetoothDevice device, boolean secure) {
        if (D) Log.e(TAG, "connect to: " + device);

        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    @TargetApi(5)
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        mDevice = device;
        if (D) Log.e(TAG, "connected, Socket Type:" + socketType);

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        setState(STATE_CONNECTED);
    }

    public synchronized void stop() {
        if (D) Log.e(TAG, "stop");
        stopFlag = true;

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        setState(STATE_DISCONNECTED);
    }

    public synchronized void stopByAccident() {
        if (D) Log.e(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        setState(STATE_DISCONNECTED_BY_ACCIDENT);
    }

    public void write(byte[] out) {
        ConnectedThread r;
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        r.write(out);
    }


    private void connectionFailed() {

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        mHandler.obtainMessage(BluetoothHelper.MESSAGE_STATE_CHANGE, STATE_CONNECTION_FAILED, id).sendToTarget();
    }


    private void connectionLost() {
        BluetoothConnection.this.stopByAccident();
    }


    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            Log.e(TAG, "억셉트 스레드 초기화");
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE);
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + " listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.e(TAG, "Socket Type: " + mSocketType + "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

            while (mState != STATE_CONNECTED) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                if (socket != null) {
                    synchronized (BluetoothConnection.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                connected(socket, socket.getRemoteDevice(), mSocketType);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            if (D) Log.e(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            mAdapter.cancelDiscovery();

            try {
                mmSocket.connect();
            } catch (IOException e) {
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType + " socket during connection failure", e2);
                }
                connectionFailed();

                return;
            }

            synchronized (BluetoothConnection.this) {
                mConnectThread = null;
            }

            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private BluetoothDevice mDevice = null;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.e(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;

            Log.i(TAG, "send the connected device to target");
            mDevice = socket.getRemoteDevice();
        }

        public void run() {
            setName("mConnectedThread begins");
            int singleByte = 0;
            MyHeader mHeader = null;
            ByteBuffer headerBuffer = ByteBuffer.allocate(8);
            ByteBuffer dataBuffer = null;
            while (true) {
                headerBuffer.clear();
                mHeader = null;
                if (dataBuffer != null) {
                    dataBuffer.clear();
                }

                try {
                    while (true) {
                        singleByte = mmInStream.read();
                        Log.e(TAG, "singlebyte:" + singleByte);
                        headerBuffer.put((byte) singleByte);
                        if (headerBuffer.position() == 8) {
                            mHeader = new MyHeader(headerBuffer.array());
                            break;
                        }
                    }
                    if (mHeader == null) {
                        write("error:check your header".getBytes());
                        cancel();
                        return;
                    }
                    dataBuffer = ByteBuffer.allocate(mHeader.getCapacity());
                    for (int i = mHeader.getCapacity(); i > 0; i--) {
                        singleByte = mmInStream.read();
                        dataBuffer.put((byte) singleByte);
                    }

                    mHandler.obtainMessage(BluetoothHelper.MESSAGE_READ, mHeader.getType(), mHeader.getCapacity(), new ReceivedBytes(id, dataBuffer.array(), mDevice)).sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    if (!stopFlag) {
                        connectionLost();
                    }
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                mHandler.obtainMessage(BluetoothHelper.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }
}

