package com.none.non.hidreader;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Toast;
import android.os.AsyncTask;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.*;
import java.util.LinkedList;
import java.util.List;


import java.util.ArrayList;



public class HIDReaderService extends Service {

    UsbManager mUsbManager;
    UsbDevice device;
    private UsbInterface intf;
    private UsbEndpoint endPointRead;
    private UsbEndpoint endPointWrite;
    private UsbDeviceConnection connection;
    private int packetSize;

    private IntentFilter filter;
    private PendingIntent mPermissionIntent;
    private USBThreadDataReceiver usbThreadDataReceiver;
    private UDPSender udpSender;
    private byte[] UDPSendBufferInitValues = new byte[21];
    public boolean IsInitRequired;

    DescriptorParser hidparser = new DescriptorParser();


    public static final String BROADCAST_JoystickInfoFromService = "com.google.android.HID.action.JoystickInfoFromService";

    private Intent intentJoystickInfo;
    public int[] AxisTestValues = new int[8];
    public int[] ChannelOrderOneToEight = {1,2,3,4,5,6,7,8};
    public int TestEnabled = 0;
    public int IsAutoStartEnabled = 0;

    private final IBinder binder = new LocalBinder();
    protected Handler handler;

    public class LocalBinder extends Binder {
        public HIDReaderService getService() {
            return HIDReaderService.this;
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private static final String ACTION_USB_PERMISSION = "com.google.android.HID.action.USB_PERMISSION"; //"com.android.example.USB_PERMISSION";
    //String ACTION_USB_PERMISSION = "com.google.android.HID.action.USB_PERMISSION";

    @Override
    public void onCreate() {
        super.onCreate();
        IsInitRequired = true;
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.google.android.HID.action.USB_PERMISSION"), 0);
        filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction("ACTION_USB_SHOW_DEVICES_LIST");
        filter.addAction("ACTION_USB_DATA_TYPE");
        registerReceiver(mUsbReceiver, filter);

        intentJoystickInfo = new Intent(BROADCAST_JoystickInfoFromService);

    }

    @Override
    public void onDestroy() {
        if (usbThreadDataReceiver != null) {
            usbThreadDataReceiver.stopThis();
        }
        unregisterReceiver(mUsbReceiver);
        super.onDestroy();
    }

    private static final String CHANNEL_ID = "chan0246";
    private static final int NOTIFICATION_ID = 4326;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Notification.Builder builder = new Notification.Builder(this,CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("HID_Reader")
                    .setAutoCancel(true);

            Notification notification = builder.build();
            startForeground(NOTIFICATION_ID, notification);
        }
        else
        {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("HID_Reader")
                    .setChannelId(CHANNEL_ID)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);

            Notification notification = builder.build();
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }



    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 4];
        int pos = 0;
        for ( int j = 0; j < bytes.length; j++ )
        {
            int v = bytes[j] & 0xFF;
            hexChars[pos] = hexArray[v >>> 4];
            pos += 1;
            hexChars[pos] = hexArray[v & 0x0F];
            pos += 1;
            hexChars[pos] = ' ';
            pos += 1;
        }
        return new String(hexChars);
    }

    public void InitSendBuffer(int DefaultChannelValues[])
    {
        for(int i=0;i<8;i++)
        {
            UDPSendBufferInitValues[i*2] =  (byte)(  (int)DefaultChannelValues[i] & 0xFF);
            UDPSendBufferInitValues[i*2+1] =  (byte)(  ( (int)DefaultChannelValues[i] >> 8) & 0xFF);
        }
        for(int i=16;i<UDPSendBufferInitValues.length;i++)
            UDPSendBufferInitValues[i] = 0;
    }

    public void RequestDes()
    {
        //FIX ME!!!!!!!!!!!!!!!
        //Detect buffer size first.!!!!!!!!!!!!
        byte[] buffer = new byte[1000];
        connection.controlTransfer(0x81, 0x06, 0x2200, 0x00, buffer, 513, 2000);
        hidparser.Parse(buffer);

        String str = bytesToHex(buffer);

        int test;
        test = 1 +1 ;
    }


    public int GetHIDList()
    {

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        for (UsbDevice usbDevice : mUsbManager.getDeviceList().values() ) {

            int id =  usbDevice.getDeviceId();
            String DeviceName = usbDevice.getDeviceName();


            String ManufacturerName = usbDevice.getManufacturerName();
            intentJoystickInfo.putExtra("DataLog", "ManufacturerName: " + ManufacturerName);
            sendBroadcast(intentJoystickInfo);

            String ModelName = usbDevice.getProductName();
            intentJoystickInfo.putExtra("DataLog", "ModelName: " + ModelName);
            sendBroadcast(intentJoystickInfo);

        }

        //UsbDevice
        try
        {
            device  = (UsbDevice) mUsbManager.getDeviceList().values().toArray()[0];

            int Vid = device.getVendorId();
            int Pid = device.getDeviceId();

            mUsbManager.requestPermission(device, mPermissionIntent);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();

            intentJoystickInfo.putExtra("DataLog", "USB error: " + e.toString() );
            sendBroadcast(intentJoystickInfo);
        }



        return 0;
    }


    /**
     * receives the permission request to connect usb devices
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                setDevice(intent);
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {

                if(IsAutoStartEnabled == 1)
                {
                    intentJoystickInfo.putExtra("DataLog", "USB attached. Autostart enabled");
                    sendBroadcast(intentJoystickInfo);
                    GetHIDList();
                }
                else
                {
                    intentJoystickInfo.putExtra("DataLog", "USB attached. Autostart disabled");
                    sendBroadcast(intentJoystickInfo);
                }
                //setDevice(intent);


                if (device == null) {
                   // onDeviceConnected(device);
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (device != null) {
                    device = null;
                    if (usbThreadDataReceiver != null) {
                        usbThreadDataReceiver.stopThis();

                        intentJoystickInfo.putExtra("DataLog", "USB detached");
                        sendBroadcast(intentJoystickInfo);
                    }
                //    eventBus.post(new DeviceDetachedEvent());
               //     onDeviceDisconnected(device);
                }
            }
        }

        private void setDevice(Intent intent) {

            device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
          //  mUsbManager.requestPermission(device, mPermissionIntent);


            Boolean res = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (device != null && res != false)
            {
                //onDeviceSelected(device);
                connection = mUsbManager.openDevice(device);
                intf = device.getInterface(0);
                if (null == connection)
                {
                    // mLog("(unable to establish connection)\n");
                }
                else
                {
                    connection.claimInterface(intf, true);
                }
                try
                {
                    if (UsbConstants.USB_DIR_OUT == intf.getEndpoint(1).getDirection())
                    {
                        endPointWrite = intf.getEndpoint(1);
                    }
                }
                catch (Exception e)
                {
                    Log.e("endPointWrite", "Device have no endPointWrite", e);
                }
                try
                {
                    if (UsbConstants.USB_DIR_IN == intf.getEndpoint(0).getDirection())
                    {
                        endPointRead = intf.getEndpoint(0);
                        packetSize = endPointRead.getMaxPacketSize();
                    }
                }
                catch (Exception e)
                {
                    Log.e("endPointWrite", "Device have no endPointRead", e);
                }

                if (connection != null )
                {
                    //FIX ME!!!!!!!!!!!!!!!
                    //Detect buffer size first.!!!!!!!!!!!!
                    byte[] buffer = new byte[1000];
                    connection.controlTransfer(0x81, 0x06, 0x2200, 0x00, buffer, 513, 2000);
                    hidparser.Parse(buffer);

                    intentJoystickInfo.putExtra("DataLog", "packetSize: " + Integer.toString(packetSize));
                    sendBroadcast(intentJoystickInfo);

                    intentJoystickInfo.putExtra("DataLog", "NumbersOfAxis: " + Integer.toString(hidparser.NumbersOfAxis));
                    sendBroadcast(intentJoystickInfo);


                    intentJoystickInfo.putExtra("DataLog", "AxisMin: " + Integer.toString(hidparser.AxisMinValue));
                    sendBroadcast(intentJoystickInfo);

                    intentJoystickInfo.putExtra("DataLog", "AxisMax: " + Integer.toString(hidparser.AxisMaxValue));
                    sendBroadcast(intentJoystickInfo);

                    intentJoystickInfo.putExtra("DataLog", "AxisOffset: " + Integer.toString(hidparser.AxisOffset));
                    sendBroadcast(intentJoystickInfo);

                    intentJoystickInfo.putExtra("DataLog", "AxisReportSize: " + Integer.toString(hidparser.AxisReportSize));
                    sendBroadcast(intentJoystickInfo);

                    intentJoystickInfo.putExtra("DataLog", "AxisReportCount: " + Integer.toString(hidparser.AxisReportCount));
                    sendBroadcast(intentJoystickInfo);


                    intentJoystickInfo.putExtra("DataLog", "Buttons ArraySize: " + Integer.toString(hidparser.ButtonsArraySize));
                    sendBroadcast(intentJoystickInfo);

                    intentJoystickInfo.putExtra("DataLog", "ButtonsOffset: " + Integer.toString(hidparser.ButtonsOffset));
                    sendBroadcast(intentJoystickInfo);

                    if(usbThreadDataReceiver != null)
                    {
                        //Not first time. Stop it first
                        Boolean result = usbThreadDataReceiver.isAlive();
                        if(result == true)
                        {
                            usbThreadDataReceiver.stopThis();
                            while (result == true)
                            {
                                result = usbThreadDataReceiver.isAlive();
                                try {
                                    Thread.sleep(50);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                    }


                    usbThreadDataReceiver = new USBThreadDataReceiver();
                    usbThreadDataReceiver.start();

                    //eventBus.post(new DeviceAttachedEvent());
                }

            }
        }
    };

    private class UDPSender extends Thread
    {
        private volatile boolean isStopped;
        byte buffer[] = new byte[21];

        public UDPSender() {
            for(int i=0;i<21;i++)
                buffer[i] = 0;
        }

        public byte[] SaveSendBuffer()
        {
            return buffer;
        }

        public void RefreshBuffer(byte[] InBuffer)
        {
            for(int i=0;i<16;i++)
                buffer[i] = InBuffer[i];

            for(int i=18;i<21;i++)
                buffer[i] = InBuffer[i];
        }

        @Override
         public void run()
        {
            DatagramSocket ds = null;
            try {
                ds = new DatagramSocket();
                DatagramPacket dp;
                InetAddress local = InetAddress.getByName("192.168.2.1");
                //InetAddress local = InetAddress.getByName("192.168.0.122");
                dp = new DatagramPacket(buffer, 21, local, 5565);
                byte  seqno = 0;

                while (!isStopped) {

                 //   if(seqno == 256)
                 //       seqno = 0;

                    buffer[16] = seqno;
                    

                    dp.setData(buffer);

                    for(int i=0;i<2;i++)
                    {
                        ds.send(dp);
                        //resend packet second time
                        try{
                            Thread.sleep(10);
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    seqno++;

                    try{
                        Thread.sleep(20);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                    if (ds != null)
                    {
                        ds.close();
                    }
            }
        }

        public void stopThis() {
            isStopped = true;
        }


    }


    private class USBThreadDataReceiver extends Thread {

        private volatile boolean isStopped;

        public USBThreadDataReceiver() {
        }

        @Override
        public void run() {

            try
            {
                if (connection != null && endPointRead != null)
                {
                    if(hidparser.IsAxisValid() == true )
                    {
                        int AxisNumber = hidparser.NumbersOfAxis;
                        if(hidparser.IsButtonsValid() == true)
                        {
                            udpSender = new UDPSender();
                            udpSender.RefreshBuffer(UDPSendBufferInitValues); //Init joystick values or saved from last run

                            udpSender.start();

                            int ButtonsArraySize = hidparser.ButtonsArraySize;

                            PacketProcessor processPacket = new PacketProcessor(packetSize,hidparser.ButtonsArraySize,hidparser.ButtonsOffset,hidparser.AxisOffset,
                                    hidparser.AxisReportSize,hidparser.AxisReportCount, hidparser.AxisMinValue, hidparser.AxisMaxValue, hidparser.NumbersOfAxis);

                            byte tmp[] = new byte[21];
                            int ProcessedAtLeastOnce = 0;
                            while (!isStopped)
                            {
                                final byte[] buffer = new byte[packetSize];
                                final int status = connection.bulkTransfer(endPointRead, buffer, packetSize, 100);
                                if (status > 0)
                                {
                                    ProcessedAtLeastOnce = 1;
                                    //byte testPacket[] = new byte[] {0x23, 0x34,(byte)0x80, 0x7f,0x08,0x00,0x44,(byte)0xfd,    0x23, 0x34,(byte)0x80, 0x7f,0x08,0x00,0x44,(byte)0xfd,     0x23, 0x34,(byte)0x80, 0x7f,0x08,0x00,0x44,(byte)0xfd,      0x23, 0x34,(byte)0x80, 0x7f,0x08,0x00,0x44,(byte)0xfd,    0x23, 0x34,(byte)0x80, 0x7f,0x08,0x00,0x44,(byte)0xfd,     0x23, 0x34,(byte)0x80, 0x7f,0x08,0x00,0x44,(byte)0xfd};
                                    processPacket.ProcessNewPacket(buffer, ChannelOrderOneToEight);
                                    tmp = processPacket.SendPacket.clone();
                                    udpSender.RefreshBuffer(tmp);
                                    if(TestEnabled == 1)
                                    {
                                        for(int i =0; i<8;i++)
                                            AxisTestValues[i] = processPacket.AxisNormalized[i];

                                        intentJoystickInfo.putExtra("AxisValues",AxisTestValues);
                                        sendBroadcast(intentJoystickInfo);
                                    }
                                }
                            }

                            if( ProcessedAtLeastOnce != 0)
                            {
                                for(int i=0;i<21;i++)
                                    UDPSendBufferInitValues[i] = tmp[i];
                            }
                        }
                    }
                }

            }
            catch (Exception e){
            }

            if(udpSender != null)
            {
                udpSender.stopThis();
            }
        }

        public void stopThis() {
            isStopped = true;
        }
    }



}
