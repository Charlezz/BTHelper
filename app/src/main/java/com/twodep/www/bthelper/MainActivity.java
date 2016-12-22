package com.twodep.www.bthelper;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.charles.bthelper.BluetoothHelper;
import com.charles.bthelper.HelperListener;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button scan;
    private TextView state;
    private ListView listView;
    private EditText editText;
    private Button send;

    BluetoothHelper bh;

    ArrayAdapter<String> mAdapter;

    private boolean isConnected = false;

    int id = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.send = (Button) findViewById(R.id.send);
        this.editText = (EditText) findViewById(R.id.editText);
        this.listView = (ListView) findViewById(R.id.listView);
        this.state = (TextView) findViewById(R.id.state);
        this.scan = (Button) findViewById(R.id.scan);

        send.setOnClickListener(this);
        scan.setOnClickListener(this);


        bh = new BluetoothHelper(this);
        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(mAdapter);


        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isConnected) {
                    String address = mAdapter.getItem(position).split("#")[0];
                    Toast.makeText(MainActivity.this, address, Toast.LENGTH_SHORT).show();
                    bh.connect(address, false);
                    state.setText("연결시도");
                }

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bh.setOnFoundListener(new BluetoothHelper.OnScanListener() {
            @Override
            public void onFound(BluetoothDevice device, short rssi) {
                mAdapter.add(device.getAddress() + "#\n" + device.getName() + "#" + rssi);
                state.setText("검색중");
            }

            @Override
            public void onDiscoveryStarted() {
                mAdapter.clear();
                state.setText("스캔시작");
            }

            @Override
            public void onDiscoveryFinished() {
                Toast.makeText(MainActivity.this, "discovery finished", Toast.LENGTH_SHORT).show();
                state.setText("스캔끝");
            }
        });
        bh.setOnConnectedListener(new HelperListener.OnConncetedListener() {
            @Override
            public void onConnected(int id) {
                MainActivity.this.id = id;
                isConnected = true;
                mAdapter.clear();
                state.setText("연결됨:" + bh.getConnection(id).getDevice().getName());
            }

            @Override
            public void onFailed(int id) {
                isConnected = false;
                state.setText("연결 실패");
            }

            @Override
            public void onDisconnected(int id) {
                isConnected = false;
                mAdapter.clear();
                state.setText("연결 끊음");
            }

            @Override
            public void onDisconnectedByAccident(int id) {
                isConnected = false;
                mAdapter.clear();
                state.setText("연결 끊김");
            }
        });
        bh.setOnStringReceivedListener(new HelperListener.OnStringReceivedListener() {
            @Override
            public void onReceived(String message, int id) {
                mAdapter.add("you:" + message);
                listView.smoothScrollToPosition(mAdapter.getCount() - 1);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });

        id = bh.listen(false);
    }

    @Override
    public void onClick(View v) {
        if (v.equals(scan)) {
            bh.startDiscovery();
        } else if (v.equals(send)) {
            bh.write(id, editText.getText().toString());
            mAdapter.add("me:" + editText.getText().toString());
            listView.smoothScrollToPosition(mAdapter.getCount() - 1);
            editText.setText("");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        bh.release();
    }
}
