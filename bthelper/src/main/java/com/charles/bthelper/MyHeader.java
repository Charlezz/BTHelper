package com.charles.bthelper;

import java.nio.ByteBuffer;

/**
 * Created by Charles on 2015. 8. 22..
 */
public class MyHeader {

    public static final int TYPE_BINARY = 0;
    public static final int TYPE_STRING = 1;
    public static final int TYPE_JSON = 2;
    private static final String TAG = "MyHeader";
    private int type;
    private int capacity;

    public MyHeader(int type, int capacity) {
        this.type = type;
        this.capacity = capacity;
    }

    protected MyHeader(byte[] bytes) {
        this.type = bytes[0];
        String bitString1 = String.format("%8s", Integer.toBinaryString(bytes[1] & 0xFF)).replace(' ', '0');
        String bitString2 = String.format("%8s", Integer.toBinaryString(bytes[2] & 0xFF)).replace(' ', '0');
        String bitString3 = String.format("%8s", Integer.toBinaryString(bytes[3] & 0xFF)).replace(' ', '0');
        String bitString4 = String.format("%8s", Integer.toBinaryString(bytes[4] & 0xFF)).replace(' ', '0');
        this.capacity = Integer.parseInt(bitString1 + bitString2 + bitString3 + bitString4, 2);
    }

    private MyHeader() {
    }

    public int getType() {
        return type;
    }

    public int getCapacity() {
        return capacity;
    }

    public byte[] getHeaderBytes() {
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.put((byte) type);
        bb.put(ByteBuffer.allocate(4).putInt(capacity).array());
        bb.put((byte) 0);
        bb.put((byte) 0);
        bb.put((byte) 0);
        return bb.array();
    }
}
