package com.twodep.www.bthelperlib;

import android.bluetooth.BluetoothDevice;

/**
 * Created by Charles on 2015. 8. 23..
 */
public class ReceivedBytes {
    private int id = -1;
    private byte[] bytes = null;
    private BluetoothDevice mBluetoothdevice = null;

    public ReceivedBytes(int id, byte[] bytes, BluetoothDevice device) {
        this.id = id;
        this.bytes = new byte[bytes.length];
        this.bytes = bytes;
        mBluetoothdevice = device;
    }

    private ReceivedBytes() {

    }

    public int getId() {
        return id;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public BluetoothDevice getBluetoothdevice() {
        return mBluetoothdevice;
    }
}
