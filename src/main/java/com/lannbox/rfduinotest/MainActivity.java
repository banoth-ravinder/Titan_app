package com.lannbox.rfduinotest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;

import java.util.Arrays;
import java.util.UUID;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity implements BluetoothAdapter.LeScanCallback {

    final int WINDOW_BUFFER_SIZE = 200;

    // State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;

    private int state;

    private boolean scanStarted;
    private boolean scanning;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;

    private RFduinoService rfduinoService;
    private ServiceConnection rfduinoServiceConnection;

    private TextView sensorConnection;

    private Button enableBluetoothButton;
    private Button bluetoothSettingsButton;
    private LinearLayout settingsLayout;
    private TextView scanStatusText;
    private Button scanButton;
    private TextView deviceInfoText;
    private TextView connectionStatusText;
    private Button connectButton;
    private Button disconnectButton;
    private EditData valueEdit;
    private Button sendZeroButton;
    private Button sendValueButton;
    private Button clearButton;
    private LinearLayout dataLayout;
    private TextView heartRateText;

    private RetainedFragment dataFragment;
    private boolean serviceBound;
    private boolean connectionIsOld = false;
    private boolean fromNotification = false;
    private boolean serviceInForeground = false;

    private XYPlot plot1, plot2;
    private SimpleXYSeries series3, series4;
    private EOGFilter EOGFilter;

    private ProgressBar progressBar;
    private TextView textView;
    MediaPlayer mMediaPlayer;

    static boolean flagShowing = false;
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (state == BluetoothAdapter.STATE_ON) {
                upgradeState(STATE_DISCONNECTED);
            } else if (state == BluetoothAdapter.STATE_OFF) {
                downgradeState(STATE_BLUETOOTH_OFF);
            }
        }
    };

    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scanning = (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE);
            scanStarted &= scanning;
            updateUi();
        }
    };

    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.w("Main","rfduinoReceiver called with " + action);
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
                upgradeState(STATE_CONNECTED);
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                downgradeState(STATE_DISCONNECTED);
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        serviceInForeground = sharedPref.getBoolean("foregroundServiceRunning", false);

        // find the retained fragment on activity restarts
        FragmentManager fm = getFragmentManager();
        dataFragment = (RetainedFragment) fm.findFragmentByTag("data");

        // create the fragment and data the first time
        if (dataFragment == null) {
            // add the fragment
            dataFragment = new RetainedFragment();
            fm.beginTransaction().add(dataFragment, "data").commit();
        }
        else
        {
            BTLEBundle btleBundle = dataFragment.getData();
            if(btleBundle != null)
            {
                bluetoothDevice = btleBundle.device;
                serviceBound = btleBundle.isBound;
                scanStarted = btleBundle.scanStarted;
                scanning = btleBundle.scanning;
                if(serviceBound) {
                    // only restore the connection if there has been one
                    rfduinoServiceConnection = btleBundle.connection;
                    rfduinoService = btleBundle.service;
                    connectionIsOld = true; // setting this flag to true to indicate a rotation
                }
                state = btleBundle.state_;

                Log.w("Main", "Bundle restored from fragment, state is " + String.valueOf(state));
            }
        }

        //
        // Setup HR data
        //
        plot1 = (XYPlot) findViewById(R.id.sensorXYPlot);
        plot2 = (XYPlot) findViewById(R.id.sensorXYPlot2);
        heartRateText = (TextView) findViewById(R.id.heartRate);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        textView = (TextView) findViewById(R.id.tv_progress);

        mMediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.sound);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setLooping(true);

        //
        // Initialize heart rate filter with window size 200
        //
        EOGFilter = new EOGFilter(200);

        Number[] series1Numbers = {};

        // Turn the above arrays into XYSeries':
        series3 = new SimpleXYSeries(
                Arrays.asList(series1Numbers),              // SimpleXYSeries takes a List so turn our array into a List
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY,     // Y_VALS_ONLY means use the element index as the x value
                "Bluetooth Data");                          // Set the display title of the series

        series4 = new SimpleXYSeries(
                Arrays.asList(series1Numbers),
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY,
                "Red");

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        // and configure it from xml:
        LineAndPointFormatter series3Format = new LineAndPointFormatter(Color.CYAN, null, null, null);
        LineAndPointFormatter series4Format = new LineAndPointFormatter(Color.BLUE, null, null, null);

        plot1.addSeries(series3, series3Format);
        plot2.addSeries(series4, series4Format);

        Intent inti = getIntent();
        int flags = inti.getFlags();
        if((inti.getAction().equals("RFduinoTest_CallToMain")) || (serviceInForeground))//&& ((flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0))
        {
            Log.w("Main", "Return from notifictation");
            Intent stopForegroundIntent = new Intent(getApplicationContext(), RFduinoService.class);
            stopForegroundIntent.setAction("RFduinoService_StopForeground");
            getApplicationContext().startService(stopForegroundIntent);
            serviceInForeground = false;
            // Saving to sharedPreferences that the service is running in foreground now
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean("foregroundServiceRunning", serviceInForeground);
            editor.commit();
            fromNotification = true;
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // rebind to service if it currently isn't
        if(!serviceBound) {
            rfduinoServiceConnection = genServiceConnection();
        }

        if(fromNotification) {
            Intent rfduinoIntent = new Intent(getApplicationContext(), RFduinoService.class);
            getApplicationContext().bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
        }

        // Bluetooth
        enableBluetoothButton = (Button) findViewById(R.id.enableBluetooth);
        enableBluetoothButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableBluetoothButton.setEnabled(false);
                enableBluetoothButton.setText(
                        bluetoothAdapter.enable() ? "Enabling bluetooth..." : "Enable failed!");
            }
        });

        // Settings
        settingsLayout = (LinearLayout) findViewById(R.id.settingsLayout);

        bluetoothSettingsButton = (Button) findViewById(R.id.bluetoothSettings);
        bluetoothSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(settingsLayout.getVisibility() == View.VISIBLE){
                    settingsLayout.setVisibility(View.GONE);
                    bluetoothSettingsButton.setText("Show Bluetooth Settings");
                } else {
                    settingsLayout.setVisibility(View.VISIBLE);
                    bluetoothSettingsButton.setText("Hide Bluetooth Settings");
                }
            }
        });

        // Find Device
        scanStatusText = (TextView) findViewById(R.id.scanStatus);

        scanButton = (Button) findViewById(R.id.scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanStarted = true;
                bluetoothAdapter.startLeScan(
                        new UUID[]{ RFduinoService.UUID_SERVICE },
                        MainActivity.this);
            }
        });

        // Device Info
        deviceInfoText = (TextView) findViewById(R.id.deviceInfo);

        // Connect Device
        connectionStatusText = (TextView) findViewById(R.id.connectionStatus);

        connectButton = (Button) findViewById(R.id.connect);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                connectionStatusText.setText("Connecting...");
                // if device was rotated we need to set up a new service connection with this activity
                if (connectionIsOld) {
                    Log.w("Main", "Rebuilding connection after rotation");
                    connectionIsOld = false;
                    rfduinoServiceConnection = genServiceConnection();
                }
                if (serviceBound) {
                    if (rfduinoService.initialize()) {
                        if (rfduinoService.connect(bluetoothDevice.getAddress())) {
                            upgradeState(STATE_CONNECTING);
                        }
                    }
                } else {
                    Intent rfduinoIntent = new Intent(getApplicationContext(), RFduinoService.class);
                    getApplicationContext().bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
                }
            }
        });

        // Disconnect Device
        disconnectButton = (Button) findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);

                disconnect();
            }
        });

        // Send
        valueEdit = (EditData) findViewById(R.id.value);
        valueEdit.setImeOptions(EditorInfo.IME_ACTION_SEND);
        valueEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendValueButton.callOnClick();
                    return true;
                }
                return false;
            }
        });

        sendZeroButton = (Button) findViewById(R.id.sendZero);
        sendZeroButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.w("Main","Send to " + rfduinoService.toString());
                rfduinoService.send(new byte[]{0});
            }
        });

        sendValueButton = (Button) findViewById(R.id.sendValue);
        sendValueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.w("Main","Send to " + rfduinoService.toString());
                rfduinoService.send(valueEdit.getData());
            }
        });

        // Receive
        clearButton = (Button) findViewById(R.id.clearData);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dataLayout.removeAllViews();
            }
        });

        dataLayout = (LinearLayout) findViewById(R.id.dataLayout);

        // refresh the ui if a restored fragment was found
        if (dataFragment != null) {
            updateUi();
        }

        //sensor connection
        sensorConnection = (TextView) findViewById(R.id.sensorStatus);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.w("Main", "onStart called");
        registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());

        if(state <= STATE_DISCONNECTED) {
            updateState(bluetoothAdapter.isEnabled() ? STATE_DISCONNECTED : STATE_BLUETOOTH_OFF);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();

        bluetoothAdapter.stopLeScan(this);

        unregisterReceiver(scanModeReceiver);
        unregisterReceiver(bluetoothStateReceiver);
        unregisterReceiver(rfduinoReceiver);
    }

    @Override
    protected  void onDestroy()
    {
        if(isFinishing() && serviceBound)
        {
            // shut down service if background action is not wanted
            //if(!backgroundService) {
            if(!true) {
                Log.w("Main", "Service is unbound");
                //Intent stopBackgroundIntent = new Intent(getApplicationContext(), RFduinoService.class);
                //stopBackgroundIntent.setAction("RFduinoService_Stop");
                getApplicationContext().unbindService(rfduinoServiceConnection);
                //getApplicationContext().stopService(stopBackgroundIntent);
            }
            else {
                // store the data in the fragment
                BTLEBundle btleBundle = new BTLEBundle();
                btleBundle.device = bluetoothDevice;
                btleBundle.state_ = state;
                btleBundle.isBound = serviceBound;
                btleBundle.scanStarted = scanStarted;
                btleBundle.scanning = scanning;
                if(serviceBound) {
                    // only save the connection if there is one
                    btleBundle.connection = rfduinoServiceConnection;
                    btleBundle.service = rfduinoService;
                }
/*                if(dataFragment != null) {
                    Log.w("Main","Bundle saved to fragment");
                    dataFragment.setData(btleBundle);
                }
*/
                if(rfduinoService != null) {
                    Log.w("Main","Bundle saved to service");
                    rfduinoService.setData(btleBundle);
                }
                // Saving to sharedPreferences that the service is running in foreground now
                SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(
                        getString(R.string.preference_file_key), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean("foregroundServiceRunning", true);
                editor.commit();

                Log.w("Main","Service pushed into foreground");
                Intent startBackgroundIntent = new Intent(getApplicationContext(), RFduinoService.class);
                startBackgroundIntent.setAction("RFduinoService_StartForeground");
                getApplicationContext().startService(startBackgroundIntent);

                if(rfduinoServiceConnection != null) {
                    getApplicationContext().unbindService(rfduinoServiceConnection);
                }
            }
        }

        // rotating behaviour is handled below
        else if(!isFinishing()) {
            // store the data in the fragment
            BTLEBundle btleBundle = new BTLEBundle();
            btleBundle.device = bluetoothDevice;
            btleBundle.state_ = state;
            btleBundle.isBound = serviceBound;
            btleBundle.scanStarted = scanStarted;
            btleBundle.scanning = scanning;
            if(serviceBound) {
                // only save the connection if there is one
                btleBundle.connection = rfduinoServiceConnection;
                btleBundle.service = rfduinoService;
            }

            if(dataFragment != null) {
                Log.w("Main","Bundle saved to fragment");
                dataFragment.setData(btleBundle);
            }
        }

        super.onDestroy();
    }

    private void upgradeState(int newState) {
        if (newState > state) {
            updateState(newState);
        }
    }

    private void downgradeState(int newState) {
        if (newState < state) {
            updateState(newState);
        }
    }

    private void updateState(int newState) {
        state = newState;
        updateUi();
    }

    private void updateUi() {
        // Enable Bluetooth
        boolean on = state > STATE_BLUETOOTH_OFF;
        enableBluetoothButton.setEnabled(!on);
        enableBluetoothButton.setText(on ? "Bluetooth enabled" : "Enable Bluetooth");
        scanButton.setEnabled(on);

        // Scan
        if (scanStarted && scanning) {
            scanStatusText.setText("Scanning...");
            scanButton.setText("Stop Scan");
            scanButton.setEnabled(true);
        } else if (scanStarted) {
            scanStatusText.setText("Scan started...");
            scanButton.setEnabled(false);
        } else {
            scanStatusText.setText("");
            scanButton.setText("Scan");
            scanButton.setEnabled(true);
        }

        // Connect
        boolean connected = false;
        String connectionText = "Disconnected";
        if (state == STATE_CONNECTING) {
            connectionText = "Connecting...";
        } else if (state == STATE_CONNECTED) {
            connected = true;
            connectionText = "Connected";
        }
        connectionStatusText.setText(connectionText);
        connectButton.setEnabled(bluetoothDevice != null && state == STATE_DISCONNECTED);
        disconnectButton.setEnabled(bluetoothDevice != null && state == STATE_CONNECTED);

        // Send
        sendZeroButton.setEnabled(connected);
        sendValueButton.setEnabled(connected);

        Log.w("Main","Updated UI to state " + state);
    }

    private void addData(byte[] data) {

        long longData = HexAsciiHelper.bytesToInt(data);

        if(series3.size() > WINDOW_BUFFER_SIZE) {
            series3.removeFirst();
        }

        if(longData>3000){
            Log.i(TAG,"Sensor not connected");
            sensorConnection.setText("Sensor Not Connected");
        }
        else
            sensorConnection.setText("Sensor Connected");

        series3.addLast(null, longData);
        EOGFilter.InputData(longData);
//        series4.setModel(Arrays.asList(EOGFilter.ComputeFFT()), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);
        //compute ComputeBR
        series4.setModel(Arrays.asList(EOGFilter.ComputeBR()), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);
//        String hrString = Integer.toString(EOGFilter.HeartRate);
        EOGFilter.ComputeBR();
        int NoOfPeaks=EOGFilter.count;
        Log.i(TAG,NoOfPeaks+" this is number of peaks");
        double percentage= 100-NoOfPeaks*2.25;
        Log.i(TAG, percentage+"Percentage ");
        String hrString = Integer.toString(NoOfPeaks);
        heartRateText.setText(hrString);
        if(percentage<=0) {
            progressBar.setProgress(5);
            textView.setText("5%");
        }
        if(percentage>=100) {
            progressBar.setProgress(100);
            textView.setText("100%");
        }
        if(percentage<100 && percentage>0){
            progressBar.setProgress((int)Math.ceil(percentage));
            textView.setText(percentage+"%");
        }



        if(percentage>100 && !flagShowing){
            flagShowing = true;
            mMediaPlayer.start();
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage("drowsiness overload")
                    .setPositiveButton("Ok.", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            flagShowing=false;
                            dialog.dismiss();
                            if(mMediaPlayer.isPlaying()) {
                                mMediaPlayer.pause();
                            }
                        }
                    })
                    .show();
        }
        plot1.redraw();
        plot2.redraw();
    }

    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        bluetoothAdapter.stopLeScan(this);
        bluetoothDevice = device;
        scanning = false;

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                deviceInfoText.setText(
                        BluetoothHelper.getDeviceInfoText(bluetoothDevice, rssi, scanRecord));
                updateUi();
            }
        });
    }

    private ServiceConnection genServiceConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                serviceBound = true;
                rfduinoService = ((RFduinoService.LocalBinder) service).getService();
                Log.w("Main","onServiceConnected called, service = "+ rfduinoService.toString());
                if(fromNotification) {
                    BTLEBundle bundle = rfduinoService.restoreData();
                    if(bundle != null) {
                        state = bundle.state_;
                        bluetoothDevice = bundle.device;
                        scanStarted = bundle.scanStarted;
                        scanning = bundle.scanning;
                        Log.w("Main","State restored from service, state: "+ state);
                    }
                    Log.w("Main","Stopping service before unbinding");
                    Intent stopIntent = new Intent(getApplicationContext(),RFduinoService.class);
                    getApplicationContext().stopService(stopIntent);
                    fromNotification = false;
                    if(state<STATE_CONNECTED) {
                        disconnect();
                    }
                    updateUi();
                }
                else{
                    if (rfduinoService.initialize()) {
                        if (rfduinoService.connect(bluetoothDevice.getAddress())) {
                            upgradeState(STATE_CONNECTING);
                        }
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.w("Main","onServiceDisconnected called");
                rfduinoService = null;
                downgradeState(STATE_DISCONNECTED);
            }
        };
    }

    private void disconnect(){
        if(rfduinoService != null) {
            rfduinoService.disconnect();
            rfduinoService = null;
        }
        else {Log.w("Main","Service empty");}
        if(rfduinoServiceConnection != null) {
            getApplicationContext().unbindService(rfduinoServiceConnection);
            serviceBound = false;
        }
        else{ Log.w("Main","ServiceConnection empty");}
    }

    @Override
    public void  onNewIntent(Intent intent) {
        Log.w("Main", "onNewintent called");
    }
}

