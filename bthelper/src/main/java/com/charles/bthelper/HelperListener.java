package com.charles.bthelper;

import org.json.JSONObject;

/**
 * Created by Charles on 2015. 8. 23..
 */
public class HelperListener {

    //Data Flow listener

    public interface OnBinaryReceivedListener {
        void onReceived(byte[] binary, int id);
    }

    public interface OnStringReceivedListener {
        void onReceived(String message, int id);
    }

    public interface OnJsonReceivedListener {
        void onReceived(JSONObject json, int id);
    }


    //Connection Listener

    public interface OnConncetedListener {
        void onConnected(int id);

        void onFailed(int id);

        void onDisconnected(int id);

        void onDisconnectedByAccident(int id);
    }

}
