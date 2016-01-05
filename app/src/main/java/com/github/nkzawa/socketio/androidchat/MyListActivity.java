package com.github.nkzawa.socketio.androidchat;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MyListActivity extends ListActivity implements BeaconConsumer{
    private ArrayList<HashMap<String, String>> beaconList;
    private BeaconManager beaconManager;
    private ListAdapter lv_adapter;
    private final int STOP = 2;
    private static final ScheduledExecutorService delayed_work = Executors.newSingleThreadScheduledExecutor();

    private String TAG_UUID = "uuid";
    private String TAG_MAJOR = "major";
    private String TAG_MINOR = "minor";
    private String TAG_RSSI = "rssi";
    private String TAG_TXPW = "tx_pw";
    private String TAG_PROXIMITY = "proximity";

    private String TAG = "MyListActivity";

    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_list);

        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24"));
        beaconManager.bind(this);

        beaconList = new ArrayList<HashMap<String, String>>();

        try {
            final Region myRegion = new Region("myRangingUniqueId", null, null, null);
            beaconManager.startRangingBeaconsInRegion(myRegion);
            Runnable stop_scan = new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.i(TAG, "stop the ranging");
                        beaconManager.stopRangingBeaconsInRegion(myRegion);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            };

            delayed_work.schedule(stop_scan, STOP, TimeUnit.SECONDS);

        } catch (RemoteException e) {
            e.printStackTrace();
        }

        swipeRefreshLayout= (SwipeRefreshLayout) findViewById(R.id.refreshView);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                updateList2();
            }
        });
        swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                updateList2();
                swipeRefreshLayout.setRefreshing(true);
            }
        });
    }

    // when an item of the list is clicked
    @Override
    protected void onListItemClick(ListView list, View view, int position, long id) {
        super.onListItemClick(list, view, position, id);

        //original code
        /*String selectedItem = (String) getListView().getItemAtPosition(position);
        //String selectedItem = (String) getListAdapter().getItem(position);*/

        HashMap<String, String> hash = (HashMap<String, String>)getListView().getItemAtPosition(position);
        String beacon_uuid = hash.get(TAG_UUID);
        Log.i(TAG, "onItemClick UUID : " + beacon_uuid);
        String remove_dash = beacon_uuid.replace("-", "");
        Log.i(TAG, "remove_dash : " + remove_dash);

        startSignIn(beacon_uuid);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Activity.RESULT_OK != resultCode) {
//            finish(); //when back button is clicked on the LoginActivity, the MyListActivity is still show
            return;
        }

        String mUsername = data.getStringExtra("username");
        int numUsers = data.getIntExtra("numUsers", 1);
        String position = data.getStringExtra("position");

        Intent intent = new Intent();
        intent.putExtra("position", position);
        intent.putExtra("username", mUsername);
        intent.putExtra("numUsers", numUsers);
        setResult(RESULT_OK, intent);
//        finish(); //same above
    }

    public void startSignIn(String position) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("position", position);
        startActivityForResult(intent, 1);
    }

    @Override
    public void onBeaconServiceConnect() {
        Log.i(TAG, "onBeaconServiceConnect");

        beaconManager.setMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                Log.i(TAG, "did Enter the Region");
            }

            @Override
            public void didExitRegion(Region region) {
                Log.i(TAG, "did Exit the Region");
            }

            @Override
            public void didDetermineStateForRegion(int i, Region region) {

            }
        });

        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> collection, Region region) {
                if (collection.size() > 0) {
                    Log.i(TAG, "collection.size() : " + collection.size());  //UUID
                    for (Beacon near_beacon : collection) {
                        Log.i(TAG, "UUID : " + near_beacon.getId1());  //UUID
                        Log.i(TAG, "MAJOR : " + near_beacon.getId2()); //MAJOR
                        Log.i(TAG, "MINOR : " + near_beacon.getId3()); //MINOR
                        Log.i(TAG, "Proximity : " + near_beacon.getDistance());    //PROXIMITY
                        Log.i(TAG, "TX_PW : " + near_beacon.getTxPower()); //TX_PW

                        String uuid = near_beacon.getId1().toString();
                        String major = near_beacon.getId2().toString();
                        String minor = near_beacon.getId3().toString();
                        String proximity = String.valueOf(near_beacon.getDistance());
                        String tx_pw = String.valueOf(near_beacon.getTxPower());
                        String rssi = String.valueOf(near_beacon.getRssi());

                        HashMap<String, String> beacon = new HashMap<String, String>();

                        beacon.put(TAG_UUID, uuid);
                        beacon.put(TAG_MAJOR, major);
                        beacon.put(TAG_MINOR, minor);
                        beacon.put(TAG_PROXIMITY, proximity);
                        beacon.put(TAG_TXPW, tx_pw);
                        beacon.put(TAG_RSSI, rssi);

                        //Check it's already in the list or not
                        if(beaconList.size() != collection.size()){
                            CheckOverlap co = new CheckOverlap();
                            co.setKey(TAG_UUID);
                            co.setValue(uuid);
                            co.setList(beaconList);
                            co.run();
                            if(!co.getOverlap()){
                                beaconList.add(beacon);
                            }
                        }

//                        beaconList.add(beacon);
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateList();
                    }
                });
            }
        });
    }

    public void updateList(){
        lv_adapter = new SimpleAdapter(getApplicationContext(), beaconList, R.layout.beacon_item_list,
                new String[]{TAG_UUID, TAG_MAJOR, TAG_MINOR, TAG_RSSI, TAG_TXPW, TAG_PROXIMITY},
                new int[] {R.id.uuid_content, R.id.major_content, R.id.minor_content, R.id.rssi_content, R.id.tx_pw_content, R.id.proximity_content});

        setListAdapter(lv_adapter);
        swipeRefreshLayout.setRefreshing(false);
    }

    public void updateList2() {
        beaconList.clear();
        try {
            final Region myRegion = new Region("myRangingUniqueId", null, null, null);
            beaconManager.startRangingBeaconsInRegion(myRegion);
            Runnable stop_scan = new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.i(TAG, "stop the ranging");
                        beaconManager.stopRangingBeaconsInRegion(myRegion);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            };

            delayed_work.schedule(stop_scan, STOP, TimeUnit.SECONDS);
            
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    class CheckOverlap extends Thread{
        private boolean overlap = false;
        private ArrayList<HashMap<String, String>> list;
        private String key;
        private String value;

        public boolean getOverlap(){
            return overlap;
        }

        public void setOverlap(boolean setValue){
            overlap = setValue;
        }

        public void setList(ArrayList<HashMap<String, String>> plist){
            list = plist;
        }

        public void setKey(String pKey){
            key = pKey;
        }

        public void setValue(String pValue){
            value = pValue;
        }

        public void overlapcheck(){
            for(HashMap<String, String> hash : list){
                String listed_uuid = hash.get(key);
                if(listed_uuid.matches(value)){
                    setOverlap(true);
                    Log.i(TAG, "overlap check");
                }
            }

            setOverlap(false);
        }

        @Override
        public void run(){
            super.run();
            overlapcheck();
        }
    }
}
