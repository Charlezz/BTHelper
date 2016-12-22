package com.charles.bthelper;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Set;

/**
 * Created by Charles on 2015. 8. 18..
 */
public class BluetoothHelper extends HelperListener {

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final String DEVICE_NAME = "device_name";

    //    public static final int MESSAGE_DISCONNECTED = 7;
    public static final String TOAST = "toast";
    private static final String TAG = "BluetoothHelper";
    private HelperHandler handler;
    private int _id = -1;

    private Context mContext;
    private BluetoothAdapter mBTAdapter;

    private HashMap<Integer, BluetoothConnection> connHashMap = new HashMap<>();
    //바이트 리스너
    private OnBinaryReceivedListener mBinaryListener;
    //String Listener
    private OnStringReceivedListener mStringListener;
    //JSON Listener
    private OnJsonReceivedListener mJsonListener;
    //Connceted Listener
    private OnConncetedListener mConnectedListener;


    public BluetoothHelper(Context context) {
        mContext = context;
        handler = new HelperHandler(this);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private BluetoothHelper() {
    }

    public void release() {
        stopAll();
        setOnFoundListener(null);
        setOnConnectedListener(null);
    }


    public boolean isEnabled() {
        return mBTAdapter.isEnabled();
    }

    public void enable() {
        mBTAdapter.enable();
    }

    public void disable() {
        mBTAdapter.disable();
    }

    public void setDiscoverable(int duration) {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
        mContext.startActivity(discoverableIntent);
    }

    public void setDiscoverable() {
        setDiscoverable(0);
    }

    public void startDiscovery(int duration) {
        if (mBTAdapter.isDiscovering()) {
            Log.e(TAG, "already discovering");
            return;
        }
        Log.e(TAG, "startDiscovery:" + duration);
        mBTAdapter.startDiscovery();
        if (0 >= duration || duration > 10) {
            Log.e(TAG, "duration must be 1~10");
        } else {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBTAdapter.cancelDiscovery();
                }
            }, duration * 1000);
        }
    }

    public void startDiscovery() {
        startDiscovery(0);
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return mBTAdapter;
    }


    private BTReceiver receiver = null;

    public void registerReceiver() {
        IntentFilter iFilter = new IntentFilter();
        iFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        iFilter.addAction(BluetoothDevice.ACTION_FOUND);
        iFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (receiver == null) {
            receiver = new BTReceiver();
            mContext.registerReceiver(receiver, iFilter);
            Log.e(TAG, "Receiver registered");
        } else {
            Log.e(TAG, "Receiver has been already REGISTERED");
        }

    }

    public void unregisterReceiver() {
        if (receiver != null) {
            mContext.unregisterReceiver(receiver);
            receiver = null;
            Log.e(TAG, "Receiver UNregistered");
        } else {
            Log.e(TAG, "Receiver has been already UNREGISTERED");
        }
    }

    private class BTReceiver extends BroadcastReceiver {
        public static final String TAG = "BTReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                if (mScanListener != null) {
                    mScanListener.onDiscoveryStarted();
                }
            } else if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                if (mScanListener != null) {
                    mScanListener.onFound(device, rssi);
                }
            } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                if (mScanListener != null) {
                    mScanListener.onDiscoveryFinished();
                }
            }
        }
    }


    public interface OnScanListener {
        void onFound(BluetoothDevice device, short rssi);

        void onDiscoveryStarted();

        void onDiscoveryFinished();
    }

    private OnScanListener mScanListener;

    public void setOnFoundListener(OnScanListener listener) {
        if (listener == null) {
            unregisterReceiver();
        } else {
            registerReceiver();
        }
        mScanListener = listener;
    }

    private void handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                switch (msg.arg1) {
                    case BluetoothConnection.STATE_CONNECTED:
                        if (mConnectedListener != null) {
                            mConnectedListener.onConnected(msg.arg2);
                        }
                        break;
                    case BluetoothConnection.STATE_CONNECTION_FAILED:
                        if (mConnectedListener != null) {
                            mConnectedListener.onFailed(msg.arg2);
                        }
                        break;
                    case BluetoothConnection.STATE_CONNECTING:
                        break;
                    case BluetoothConnection.STATE_LISTEN:
                    case BluetoothConnection.STATE_NONE:
                        break;
                    case BluetoothConnection.STATE_DISCONNECTED:
                        connHashMap.remove(msg.arg2);
                        if (mConnectedListener != null) {
                            mConnectedListener.onDisconnected(msg.arg2);
                        }
                        break;
                    case BluetoothConnection.STATE_DISCONNECTED_BY_ACCIDENT:
                        connHashMap.remove(msg.arg2);
                        if (mConnectedListener != null) {
                            mConnectedListener.onDisconnectedByAccident(msg.arg2);
                        }
                        break;


                }
                break;
            case MESSAGE_READ:
                switch (msg.arg1) {
                    case MyHeader.TYPE_BINARY:
                        if (mBinaryListener != null) {
                            int id = ((ReceivedBytes) msg.obj).getId();
                            mBinaryListener.onReceived(((ReceivedBytes) msg.obj).getBytes(), id);
                        }
                        break;
                    case MyHeader.TYPE_STRING:
                        if (mStringListener != null) {
                            int id = ((ReceivedBytes) msg.obj).getId();
                            String str = new String(((ReceivedBytes) msg.obj).getBytes(), 0, msg.arg2);
                            mStringListener.onReceived(str, id);
                        }
                        break;
                    case MyHeader.TYPE_JSON:
                        Log.e(TAG, "제이슨으로 받음1");
                        if (mJsonListener != null) {
                            Log.e(TAG, "제이슨으로 받음2");
                            String str = new String(((ReceivedBytes) msg.obj).getBytes(), 0, msg.arg2);
                            int id = ((ReceivedBytes) msg.obj).getId();
                            try {
                                Log.e(TAG, "id:" + id);
                                mJsonListener.onReceived(new JSONObject(str), id);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                }
                break;
            case MESSAGE_DEVICE_NAME:
                String connectedDevice = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(mContext, connectedDevice, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                String toast = msg.getData().getString(TOAST);
                Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    public int listen(boolean secure) {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Log.e(TAG, "블루투스 켜주세요 listen이 안되요");
            return -1;
        }
        Log.e(TAG, "헬퍼 안의 리슨 메소드");
        _id++;
        BluetoothConnection server = new BluetoothConnection(_id, mContext, handler);
        server.listen(secure);//insecure
        Log.e(TAG, "코넥션 객체 만들고 다시 리슨");
        this.connHashMap.put(_id, server);
        return _id;
    }

    public void stopAll() {
        Set set = connHashMap.keySet();
        Object[] keyset = set.toArray();
        for (int i = 0; i < keyset.length; i++) {
            int key = (int) keyset[i];
            connHashMap.get(key).stop();
        }
    }

    public boolean stop(int id) {
        Set set = connHashMap.keySet();
        Object[] keyset = set.toArray();
        for (int i = 0; i < keyset.length; i++) {
            int key = (int) keyset[i];
            if (key == id) {
                connHashMap.get(key).stop();
                return true;
            }
        }
        return false;
    }

    public boolean isConnected(int id) {
        Set set = connHashMap.keySet();
        Object[] keyset = set.toArray();
        for (int i = 0; i < keyset.length; i++) {
            int key = (int) keyset[i];
            if (key == id) {
                return true;
            }
        }
        return false;
    }

    public boolean isBluetoothAvailable() {
        try {
            if (mBTAdapter == null || mBTAdapter.getAddress() == null)
                return false;
        } catch (NullPointerException e) {
            return false;
        }
        return true;
    }

    public int connect(BluetoothDevice device, boolean secure) {
        _id++;
        BluetoothConnection client = new BluetoothConnection(_id, mContext, handler);
        client.connect(device, secure);
        connHashMap.put(_id, client);
        return _id;
    }

    public int connect(String address, boolean secure) {
        return connect(mBTAdapter.getRemoteDevice(address), secure);
    }

    public BluetoothConnection getConnection(int id) {
        if (connHashMap.containsKey(id)) {
            return connHashMap.get(id);
        }
        return null;
    }

    public boolean write(int id, String msg) {
        if (!connHashMap.containsKey(id)) {
            return false;
        }
        MyHeader header = new MyHeader(MyHeader.TYPE_STRING, msg.getBytes().length);
        ByteBuffer bb = ByteBuffer.allocate(header.getHeaderBytes().length + header.getCapacity()).put(header.getHeaderBytes());
        bb.put(msg.getBytes());
        connHashMap.get(id).write(bb.array());
        return true;
    }

    public boolean write(int id, byte[] bytes) {
        if (!connHashMap.containsKey(id)) {
            return false;
        }
        MyHeader header = new MyHeader(MyHeader.TYPE_BINARY, bytes.length);
        ByteBuffer bb = ByteBuffer.allocate(header.getHeaderBytes().length + header.getCapacity()).put(header.getHeaderBytes());
        bb.put(bytes);
        connHashMap.get(id).write(bb.array());
        return true;
    }

    public boolean write(int id, JSONObject json) {
        if (!connHashMap.containsKey(id)) {
            return false;
        }
        MyHeader header = new MyHeader(MyHeader.TYPE_JSON, json.toString().getBytes().length);
        ByteBuffer bb = ByteBuffer.allocate(header.getHeaderBytes().length + header.getCapacity()).put(header.getHeaderBytes());
        bb.put(json.toString().getBytes());
        connHashMap.get(id).write(bb.array());
        return write(id, json.toString());
    }

    public HashMap<Integer, BluetoothConnection> getConnHashMap() {
        return connHashMap;
    }

    public void setOnBinaryReceivedListener(OnBinaryReceivedListener listener) {
        mBinaryListener = listener;
    }

    public void setOnStringReceivedListener(OnStringReceivedListener listener) {
        mStringListener = listener;
    }

    public void setOnJsonReceivedListener(OnJsonReceivedListener listener) {
        mJsonListener = listener;
    }

    public void setOnConnectedListener(OnConncetedListener listener) {
        mConnectedListener = listener;
    }

    //Handler protected for leaking memory
    private static class HelperHandler extends Handler {
        private final WeakReference<BluetoothHelper> mHelper;

        public HelperHandler(BluetoothHelper helper) {
            mHelper = new WeakReference<>(helper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            BluetoothHelper helper = mHelper.get();
            if (helper != null) {
                helper.handleMessage(msg);
            }
        }
    }


}
