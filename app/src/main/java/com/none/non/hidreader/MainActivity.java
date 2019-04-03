package com.none.non.hidreader;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    HIDReaderService mService;
    boolean mBound = false;
    private Intent intent;
    private Intent intentJoystickInfo;

    ArrayList<String> listItems=new ArrayList<String>();
    ArrayAdapter<String> adapter;
    ListView lv;

    SeekBar[] seekBarArray = new SeekBar[8];
    Spinner[] spinnerArray = new Spinner[8];
    EditText[] ChannelDefault = new EditText[8];

    private SharedPreferences sharedPref;
    private SharedPreferences.Editor ShareSettingsEditor;
    public int IsTestRunning = 0;

    private int[] ChannelOrderOneToEight = {1,2,3,4,5,6,7,8};
    private int[] ChannelDefaultValuesIntArray = new int[8];
    private int IsAutoStartEnabled = 0;
    private CheckBox IsAutoStartEnabledBox;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            HIDReaderService.LocalBinder binder = (HIDReaderService.LocalBinder)service;
            mService = binder.getService();
            mBound = true;

            if(IsAutoStartEnabled == 1)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    mService.IsAutoStartEnabled = 1;
                    startForegroundService(intent);

                }
                else
                {
                    mService.IsAutoStartEnabled = 1;
                    startService(intent);
                }

                for(int i=0;i<8;i++) {

                    mService.ChannelOrderOneToEight[i] = ChannelOrderOneToEight[i];
                }
                if(mService.IsInitRequired == true)
                {
                    mService.InitSendBuffer(ChannelDefaultValuesIntArray);
                    mService.IsInitRequired = false;
                }

                mService.GetHIDList();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        intent = new Intent(this, HIDReaderService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
        mBound = false;
   //     stopService(intentJoystickInfo);
    }

    @Override
    public void onResume() {
        super.onResume();
       // startService(intentJoystickInfo);
        registerReceiver(broadcastReceiver, new IntentFilter(HIDReaderService.BROADCAST_JoystickInfoFromService));
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
       // stopService(intentJoystickInfo);
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intentJoystickInfo) {

            if (intentJoystickInfo.getAction().equals(HIDReaderService.BROADCAST_JoystickInfoFromService)) {

                int[] AxisValues = intentJoystickInfo.getIntArrayExtra("AxisValues");
                if(AxisValues != null)
                {
                    if(AxisValues.length >= 8)
                    {
                        for(int i=0;i<8; i++)
                        {
                            int value = mService.AxisTestValues[i];
                            value = (value/10) - 100;

                            seekBarArray[i].setProgress((int)value );
                        }
                    }
                }


                try
                {
                    String Log = intentJoystickInfo.getStringExtra("DataLog");
                    int AdapterSize = adapter.getCount();

                    if(AdapterSize == 0)
                    {
                        adapter.add(Log);
                        adapter.notifyDataSetChanged();
                    }
                    else
                    {
                        String Last = adapter.getItem(AdapterSize -1 );
                        if(Log.equals(Last) == false)
                        {
                            adapter.add(Log);
                            adapter.notifyDataSetChanged();
                        }

                    }

                }
                catch (Exception e)
                {
                    adapter.add(e.toString() );
                    adapter.notifyDataSetChanged();
                }


            }
        }
    };

    private void RestoreSettings() {
        int temp = 0;
        IsAutoStartEnabled = sharedPref.getInt("IsAutoStartEnabled", 0);
        if(IsAutoStartEnabled == 1)
        {
            IsAutoStartEnabledBox.setChecked(true);
        }
        else
        {
            IsAutoStartEnabledBox.setChecked(false);
        }

        for (int i = 0; i < spinnerArray.length; i++)
        {
            String id = Integer.toString(i);
            temp = sharedPref.getInt("channel" + id, i+1);
            ChannelOrderOneToEight[i] = temp;
            temp = temp - 1;
            spinnerArray[i].setSelection(temp);

        }


        for(int i =0; i< ChannelDefault.length; i++)
        {
            try
            {
                String id = Integer.toString(i);
                temp = sharedPref.getInt("ChannelDefaultValue" + id, 1500);
                ChannelDefaultValuesIntArray[i] = temp;
                ChannelDefault[i].setText( Integer.toString(temp) );
            }
            catch (Exception e)
            {
                adapter.add(e.toString() );
                adapter.notifyDataSetChanged();
            }
        }
        try
        {
            temp = sharedPref.getInt("ChannelDefaultValue2", 1100);
            ChannelDefaultValuesIntArray[2] = temp;
            ChannelDefault[2].setText( Integer.toString(temp) );
        }
        catch (Exception e)
        {
            adapter.add(e.toString() );
            adapter.notifyDataSetChanged();
        }


    }

    private void SaveSettings()
    {
        ShareSettingsEditor = sharedPref.edit();


        Boolean IsChecked = IsAutoStartEnabledBox.isChecked();
        if(IsChecked == true)
        {
            ShareSettingsEditor.putInt( "IsAutoStartEnabled", 1 );
            ShareSettingsEditor.apply();
            IsAutoStartEnabled = 1;
            mService.IsAutoStartEnabled = 1;
        }
        else
        {
            ShareSettingsEditor.putInt( "IsAutoStartEnabled", 0 );
            ShareSettingsEditor.apply();
            IsAutoStartEnabled = 0;
            mService.IsAutoStartEnabled = 0;
        }


        for (int i = 0; i < spinnerArray.length; i++)
        {
            String id = Integer.toString(i);
            String spinnerText = spinnerArray[i].getSelectedItem().toString();
            ShareSettingsEditor.putInt( "channel" + id , Integer.parseInt(spinnerText) );
            ShareSettingsEditor.apply();

            //local
            int selectedValue = Integer.parseInt(spinnerText);
          //  selectedValue = selectedValue-1;
            ChannelOrderOneToEight[i] = selectedValue;
        }

        for(int i =0; i< ChannelDefault.length; i++)
        {
            try
            {
                String id = Integer.toString(i);
                String temValue = ChannelDefault[i].getText().toString();
                ShareSettingsEditor.putInt("ChannelDefaultValue" + id, Integer.parseInt(temValue) );
                ShareSettingsEditor.apply();
            }
            catch (Exception e)
            {
                adapter.add(e.toString() );
                adapter.notifyDataSetChanged();
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        intentJoystickInfo = new Intent(this, HIDReaderService.class);

        setContentView(R.layout.activity_main);

        ListView lv = (ListView) findViewById(R.id.LogListView);
        adapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,
                listItems);
        lv.setAdapter(adapter);

        int temp  = 0;
        String[] SeekBarIDs = new String[]{"seekBar1","seekBar2","seekBar3","seekBar4","seekBar5","seekBar6","seekBar7","seekBar8"};
        for(int i=0; i<SeekBarIDs.length; i++){
            temp = getResources().getIdentifier(SeekBarIDs[i], "id", getPackageName());
            seekBarArray[i] = (SeekBar)findViewById(temp);
            seekBarArray[i].setMax(100);
            seekBarArray[i].setProgress(50);
        }

        //Default channel value
        String[] ChannelDefauletIDs = new String[]{"Channel1Default","Channel2Default","Channel3Default","Channel4Default","Channel5Default","Channel6Default","Channel7Default","Channel8Default"};
        for(int i=0; i<ChannelDefauletIDs.length; i++) {
            temp = getResources().getIdentifier(ChannelDefauletIDs[i], "id", getPackageName());
            ChannelDefault[i] = (EditText) findViewById(temp);
        }

        ///////////////////Init spinner

        String[] SpinerIDs = new String[]{"spinner1", "spinner2","spinner3","spinner4","spinner5","spinner6","spinner7","spinner8"};
        for(int i=0; i<SpinerIDs.length; i++){
            temp = getResources().getIdentifier(SpinerIDs[i], "id", getPackageName());
            spinnerArray[i] = (Spinner) findViewById(temp);
        }


        IsAutoStartEnabledBox = (CheckBox)findViewById(R.id.AutostartCheckbox);

        //Load settings at start
        sharedPref = getSharedPreferences( "OpenHD.Joystick", Context.MODE_PRIVATE);
        RestoreSettings();


        ///////////////////SAVE Settings , Restore Settings buttons

        Button SaveSettingsButton = (Button)findViewById(R.id.SaveSettings);
        SaveSettingsButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view)
            {
                // click handling code
                SaveSettings();
                if(mService != null)
                {
                    for(int i=0;i<8;i++) {
                        mService.ChannelOrderOneToEight[i] = ChannelOrderOneToEight[i];
                    }
                }
            }
        });

        Button RestoreSettingsButton = (Button)findViewById(R.id.RestoreSettings);
        RestoreSettingsButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view)
            {
                // click handling code
                RestoreSettings();

            }
        });


        Button TestButton = (Button) findViewById(R.id.TestJoystickButton);
        TestButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                if(IsTestRunning == 0)
                {
                    mService.TestEnabled = 1;
                    IsTestRunning = 1;
                }
                else
                {
                    mService.TestEnabled = 0;
                    IsTestRunning = 0;
                }
            }
        });


        Button StopButton = (Button) findViewById(R.id.StopButton);
        StopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

               // stopService(intent);

            }
        });



        Button StartButton = (Button) findViewById(R.id.StartButton);
        StartButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    startForegroundService(intent);

                }
                else
                {

                    startService(intent);
                }

                for(int i=0;i<8;i++) {
                    mService.ChannelOrderOneToEight[i] = ChannelOrderOneToEight[i];
                }

                if(mService.IsInitRequired == true)
                {
                    mService.InitSendBuffer(ChannelDefaultValuesIntArray);
                    mService.IsInitRequired = false;
                }

                mService.GetHIDList();


               /*
                DescriptorParser hidparser = new DescriptorParser();
              //                                 0x05, 0x01,  0x09, 0x05,   0xa1, 0x01, 0xa1, 0x00,      0x05, 0x09,   0x19, 0x01,  0x29, 0x18, 0x15, 0x00,     0x25, 0x01,    0x95, 0x18, 0x75, 0x01,  0x81, 0x02, 0x05, 0x01, 0x09, 0x30, 0x09, 0x31,    0x09, 0x32,   0x09, 0x33,   0x09, 0x34,  0x09, 0x35,    0x09, 0x36,    0x09, 0x36,   0x15, 0x81,    0x25, 0x7f,   0x75, 0x08,   0x95, 0x08, 0x81, 0x02,  0xc0,    0xc0
                byte testArrX9D[] = new byte[] {0x05,0x01,0x09,0x05,(byte)0xa1, 0x01,(byte)0xa1, 0x00, 0x05, 0x09,     0x19, 0x01,    0x29, 0x18,    0x15, 0x00,    0x25, 0x01,      (byte)0x95, 0x18,      0x75, 0x01,    (byte)0x81, 0x02,     0x05, 0x01,    0x09, 0x30,    0x09, 0x31,    0x09, 0x32,    0x09, 0x33,    0x09, 0x34,    0x09, 0x35,    0x09, 0x36,    0x09, 0x36,    0x15, (byte)0x81,    0x25, 0x7f,    0x75, 0x08,    (byte)0x95, 0x08,    (byte)0x81, 0x02,  (byte) 0xc0,(byte)0xc0};
                byte textArrLogi[] = new byte[] {0x05, 0x01,0x09, 0x04,(byte)0xA1, 0x01,(byte)0xA1, 0x02,0x15, 0x00, 0x26, (byte)0xFF, 0x00, 0x35, 0x00,0x46, (byte)0xFF, 0x00, 0x75, 0x08,(byte)0x95, 0x04,0x09, 0x30, 0x09, 0x31, 0x09, 0x32,  0x09, 0x35,(byte)0x81, 0x02,0x25, 0x07,0x46, 0x3B, 0x01,0x75, 0x04,(byte)0x95, 0x01,0x65, 0x14, 0x09, 0x39,(byte) 0x81, 0x42, 0x65, 0x00,0x25, 0x01, 0x45, 0x01,0x75, 0x01, (byte)0x95, 0x0C,  0x05, 0x09, 0x19, 0x01, 0x29, 0x0C, (byte)0x81, 0x02, 0x06, 0x00, (byte)0xFF,  0x75, 0x01, (byte)0x95, 0x10, 0x25, 0x01,0x45, 0x01,0x09, 0x01,(byte) 0x81, 0x02, (byte)0xC0,(byte) 0xA1, 0x02, 0x26,(byte) 0xFF, 0x00,0x46,(byte) 0xFF, 0x00,0x75, 0x08,(byte)0x95, 0x07, 0x09, 0x02, (byte) 0x91, 0x02, (byte) 0xA1, 0x02, 0x26, (byte)0xFF, 0x00, 0x46, (byte)0xFF, 0x00,  0x75, 0x08,(byte) 0x95, 0x05,  0x06, 0x00, (byte)0xFF, 0x09, 0x01, (byte)0xB1, 0x02,(byte) 0xC0,(byte) 0xC0};
                hidparser.Parse(textArrLogi);

                if(hidparser.IsAxisValid() == true )
                {
                    int AxisNumber = hidparser.NumbersOfAxis;
                    if(hidparser.IsButtonsValid() == true)
                    {
                        int ButtonsArraySize = hidparser.ButtonsArraySize;
                        int packetSize = 64;

                        PacketProcessor processPacket = new PacketProcessor(packetSize,hidparser.ButtonsArraySize,hidparser.ButtonsOffset,hidparser.AxisOffset,
                                hidparser.AxisReportSize,hidparser.AxisReportCount, hidparser.AxisMinValue, hidparser.AxisMaxValue, hidparser.NumbersOfAxis);

                  //      PacketProcessor processPacket = new PacketProcessor(8,hidparser.ButtonsArraySize,hidparser.ButtonsOffset,hidparser.AxisOffset,
                   //             hidparser.AxisReportSize,hidparser.AxisReportCount);
                                                                //                      210u   -41s
                        byte testPacketLogitech[] = new byte[] { (byte)0x80, 0x7E,(byte)0xD2, (byte)0xD7,0x08,0x00,0x44,(byte)0xfd,    0x23, 0x34,(byte)0x80, 0x7f,0x08,0x00,0x44,(byte)0xfd,     0x23, 0x34,(byte)0x80, 0x7f,0x08,0x00,0x44,(byte)0xfd,      0x23, 0x34,(byte)0x80, 0x7f,0x08,0x00,0x44,(byte)0xfd,    0x23, 0x34,(byte)0x80, 0x7f,0x08,0x00,0x44,(byte)0xfd,     0x23, 0x34,(byte)0x80, 0x7f,0x08,0x00,0x44,(byte)0xfd};
                        byte testPacketX9D[] = new byte[] {(byte)0x84, (byte)0x84, (byte)0x84, 0x7E, 0x7E, (byte)0x79 ,(byte)0x79 , (byte)0x82, (byte)0x84,(byte) 0x81 ,(byte)0x81};
                        processPacket.ProcessNewPacket(testPacketLogitech);
                        byte tmp[] = new byte[21];
                        tmp = processPacket.SendPacket.clone();
                        int fi=0;
                    }
                }
                */

                //processPacket)
                //startService(intent);
                //mService.GetHIDList();
            }
        });

    }
}
