package com.example.a18hcoffee.bluetoothconnectiontext;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BluetoothMainActivity extends AppCompatActivity implements View.OnClickListener {
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothManager mBluetoothManager;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothDevice device;
    private BluetoothGatt BTGatt;
    public Boolean CharacteristicWrite = true;
    public UUID UUID_SERV = UUID.fromString("0000ffe0-0000-1000-8000-00805F9B34FB");//SERVER的UUID
    public UUID UUID_DATA = UUID.fromString("0000ffe1-0000-1000-8000-00805F9B34FB");//特徵的UUID
    public UUID CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //定義手機的UUID
    private BluetoothGattCharacteristic mWriteCharacteristic;

    private ScanFilter filter;

    private Button Btn_Start_Scan;
    private Button Btn_Stop_Scan;
    private TextView Txt_Scan;

    public Handler mHandler;
    private List<ScanFilter> scanFilters;
    private ScanSettings settings;

    private int scan_count=0;   //用來區分機台是否為第一個搜尋到
    private int flag = 0;       //用來以防開啟scan後馬上被關掉的情況
    private int Ble_Uni_limit = 254;
    private ArrayList<byte[]> queueArray = new ArrayList<>();   //傳送資料的array
    private int SendDataCount;  //用來防止資料傳送衝突

    String s1,s2,s3,s4,f1,f2,f3,f4; //s為轉速;f為風扇
    StringBuffer stringBuffer = new StringBuffer("$COE");   //用來儲存所有COE指令的參數
    StringBuffer pow = new StringBuffer("$POW");    //用來儲存所有POW指令的參數


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_main);

        Btn_Start_Scan = (Button)findViewById(R.id.Btn_Start_BluScan);
        Btn_Stop_Scan = (Button)findViewById(R.id.Btn_Stop_BluScan);
        Txt_Scan = (TextView)findViewById(R.id.Txt_Scan);

        Btn_Start_Scan.setOnClickListener(this);
        Btn_Stop_Scan.setOnClickListener(this);

        mHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                //之後再針對接收之msg處理回傳事件
            }
        };
    }

    public void onClick(View v){
        switch (v.getId()){
            case R.id.Btn_Start_BluScan:
                Txt_Scan.setText("");
                Bluetooth();
            case R.id.Btn_Stop_BluScan:
                //stopScan();

                //測試方便先寫重開指令
                String StopBreak = new String("$RST");
                try {
                    final byte[][] Stop = dataTobyte(StopBreak.getBytes(StandardCharsets.US_ASCII));
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            sendData(Stop[0]);
                        }
                    },200);
                } catch (Exception e) {}
        }
    }


    public void Bluetooth(){
        mBluetoothManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if(mBluetoothAdapter == null){
            Toast.makeText(this , "此裝置不支援藍芽" , Toast.LENGTH_LONG).show();
            finish();
        }

        if(!(mBluetoothAdapter.isEnabled())){
            Intent mBluetoothRequest = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(mBluetoothRequest);
        }else{
            Toast.makeText(this , "藍芽裝置已開啟" , Toast.LENGTH_LONG).show();
            startScan();
        }
    }



    //開始藍芽搜尋HMSoft
    private void startScan(){
        if(Build.VERSION.SDK_INT < 25){
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }else{
            filter = new ScanFilter.Builder().setDeviceName("HMSoft").build();
            scanFilters.add(filter);
            scanSitting();
            mBluetoothLeScanner.startScan(scanFilters,settings,mScanCallback);
        }

    }

    //停止藍芽搜尋
    private void stopScan(){
        if(flag == 1){
            if(Build.VERSION.SDK_INT < 25){
                flag = 0;
                Log.e("StopScan" , "Bluetooth Scan Stop");
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }else{
                mBluetoothLeScanner.stopScan(mScanCallback);
            }
        }
    }


    //藍芽搜尋到裝置所call的函式(舊版本) < 23
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            scan_count++;
                            try{
                                if(bluetoothDevice.getName().equals("HMSoft")){
                                    if(scan_count==1 ){
                                        flag = 1;

                                        //測試搜尋成功
                                        Txt_Scan.setText("Success");

                                        //取得所搜尋到的裝置
                                        device = bluetoothDevice;

                                        //連線Gatt
                                        BTGatt = device.connectGatt(getApplicationContext() , false , GattCallback);

                                        //連線完成後停止繼續搜尋裝置
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                stopScan();
                                            }
                                        }).start();
                                        Message msg = new Message();
                                        msg.what = 1;
                                        mHandler.sendMessage(msg);
                                    }
                                }else{
                                    scan_count = 0;
                                }
                            }catch (Exception e){
                                scan_count = 0;
                            }
                        }
                    });
                }
            };


    //藍芽搜尋到裝置所call的函式(新版本) > 23
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult (callbackType, result);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    scan_count++;
                    try{
                        if(scan_count == 0){

                            flag = 1;

                            //取得所搜尋到的裝置
                            device = result.getDevice();

                            //連線Gatt
                            BTGatt = device.connectGatt(getApplicationContext() , false , GattCallback);

                            //連線完成後停止繼續搜尋裝置
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    stopScan();
                                }
                            }).start();
                            Message msg = new Message();
                            msg.what = 1;
                            mHandler.sendMessage(msg);

                        }
                    }catch(Exception e){
                        scan_count = 0;
                    }
                }
            });
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    @TargetApi(23)
    private void scanSitting(){
        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                .build();
    }


    //傳送資料function
    public Boolean sendData(byte[] data){
        try{
            //將Send的內容轉為中文
            String AsciiSend = "";
            for (int i = 0; i < data.length; i++){
                AsciiSend += Character.toString((char)data[i]);
            }
            Log.e("SendData" , AsciiSend);


            if (data != null && data.length > 0 && data.length < 255) {
                if (data.length <= Ble_Uni_limit) {
                    Boolean mWritech = mWriteCharacteristic.setValue(data);
                    Boolean BTWrite = BTGatt.writeCharacteristic(mWriteCharacteristic);
                    while (!mWritech || !BTWrite) {
                        if (CharacteristicWrite && mWritech && BTWrite) {
                            CharacteristicWrite = false;
                            return true;
                        }
                    }
                }else{
                    return false;
                }
            }//End  Of  if (data != null && data.length > 0 && data.length < 255)

        }catch (Exception e){
            Log.e("SendDataException" , String.valueOf(e));
        }
        Log.e("Send" , "sendsuccess");
        return true;
    }


    //資料傳送後的callback function
    public BluetoothGattCallback GattCallback = new BluetoothGattCallback() {
        int ssstep = 0;

        public void SetUpSensorStep(BluetoothGatt gatt){
            if(ssstep == 1){
                BluetoothGattCharacteristic characteristic;
                BluetoothGattDescriptor descriptor;
                // Enable local notifications
                characteristic = gatt.getService(UUID_SERV).getCharacteristic(UUID_DATA);

                gatt.setCharacteristicNotification(characteristic, true);
                mWriteCharacteristic = characteristic;

                //連線完成開始資料寫入(之後改為呼叫model)
                DataBundle();
                // Enabled remote notifications
                descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);

            }
        }

        //偵測Gatt client連線或斷線
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState){
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            } else {
                gatt.disconnect();
                gatt.close();
                runOnUiThread(new Runnable() {
                    public void run() {
                        Message msg = new Message();
                        msg.what = 2;
                        mHandler.sendMessage(msg);
                    }
                });
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        scan_count = 0;
                        startScan();
                    }
                }).start();
            }
        }

        //發現新的服務
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            ssstep++;
            SetUpSensorStep(gatt);
        }

        //特徵寫入結果
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);


            SendDataCount++;
            Log.e("SendDataCount" , String.valueOf(SendDataCount));
            Log.e("queueArray" , String.valueOf(queueArray.size()));
            if(SendDataCount < queueArray.size()){
                sendData(queueArray.get(SendDataCount));
            }
            if (SendDataCount == queueArray.size()){
                queueArray.clear();
                SendDataCount = 100;
                Log.e("clear" , String.valueOf(SendDataCount));
            }

            CharacteristicWrite = true;
            ssstep++;
            SetUpSensorStep(gatt);

        }

        //描述寫入結果
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            ssstep++;
            SetUpSensorStep(gatt);
        }

        //遠端特徵通知結果
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            ssstep = 0;
            readCharacterisricValue(characteristic);
        }
    };


    //用來讀取bluetooth回傳資料
    private void readCharacterisricValue(final BluetoothGattCharacteristic characteristic) {

        byte[] data = characteristic.getValue();
        //將Ascii轉中文
        String MechineReturn = "";
        for (int i = 0; i < data.length; i++){
            MechineReturn += Character.toString((char)data[i]);
        }
        Log.e("Mechine Return" , MechineReturn);


        String[] SplitterStr = MechineReturn.split(",");
        int Splitterlength = SplitterStr.length - 1;
        if (SplitterStr != null){
            if (SplitterStr[Splitterlength].contains("FFFFF")){
                Log.e("現在溫度" , SplitterStr[1]);
                Log.e("風扇轉速" , SplitterStr[2]);
            }else {
                Log.e("現在溫度" , SplitterStr[1]);
                Log.e("風扇轉速" , SplitterStr[2]);
                Log.e("剩餘時間" , SplitterStr[3]);
            }

        }

//        final String str;
//        final StringBuffer buffer = new StringBuffer("0x");
//        for (byte b : data) {
//            int i = b & 0x00ff;
//            buffer.append(Integer.toHexString(i));
//        }
//        try{
//        }catch (Exception e){}
    }



    //收集欲傳送資料的function
    public Boolean DataQueues(byte[] data){
        queueArray.add(data);

        return true;
    }


    //將欲傳送資料轉態成byte各自傳送
    public void DataBundle(){

        /***這邊數值只是暫定測試用，之後需要再用變數去修改***/
        String tempformatStr = "%03d";
        String formatStr = "%04d";

        //重量設定
        String weight = String.format(formatStr,80);

        //時間設定
        String heating_time_change = String.format(formatStr,60);
        String steam_time_change = String.format(formatStr,60);
        String roast_time_change = String.format(formatStr,60);

        //溫度設定
        String heating_temp_change= String.format(tempformatStr,60);
        String steam_temp_change= String.format(tempformatStr,60);
        String roast_temp_change= String.format(tempformatStr,60);

        String Mode = new String("$MODE1");
        String bean_name = "伯朗";

        /***將咖啡豆的名稱轉成big5***/
        String name = "";
        byte[] big5;
        byte[] x;
        try {
            big5 = bean_name.getBytes("big5");
            x = big5;
            for (int i = 0; i < x.length; i++){
                name = name + Integer.toString((x[i] & 0xff) + 0x100 , 16).substring(1);
            }
        } catch (Exception e) {
            Log.i("ConvertError" , "string to big5 error");
        }
        Log.e("品項" , name);
        /***將咖啡豆名稱轉成big5***/


        if(name.length() > 26) name = name.substring(0 , 25);
        //COE參數設定
        stringBuffer.append(",")
                .append(name).append(",")
                .append(weight).append(",")
                .append(heating_temp_change).append(",").append(heating_time_change).append(",")
                .append(steam_temp_change).append(",").append(steam_time_change).append(",")
                .append(roast_temp_change).append(",").append(roast_time_change);

        Log.e("StringBuffer" , String.valueOf(stringBuffer));

        //Power參數設定
        pow.append(String.format(tempformatStr,50)).append(",")
                .append(String.format(tempformatStr,50)).append(",")
                .append(String.format(tempformatStr,50));




        try {
            String VER = new String("$COK");
            final byte[][] VER_test = dataTobyte(VER.getBytes(StandardCharsets.US_ASCII));
            if(VER_test[0][0] != 0){
                if(VER_test.length > 0){
                    if(DataQueues(VER_test[0])){
                        Log.e("COK send success" , Arrays.toString(VER_test[0]));
                    }
                }
            }else{
                if(!DataQueues(VER_test[0])){
                    DataQueues(VER_test[0]);
                }
            }


            final byte[][] themode =  dataTobyte(Mode.getBytes(StandardCharsets.US_ASCII));
            if(themode[0][0]!=0){
                if(themode.length > 0 ) {
                    if(DataQueues(themode[0])){
                        Log.e("Mode send success" , Arrays.toString(themode[0]));
                    }
                }
            }else{
                if(!DataQueues(themode[0])){
                    DataQueues(themode[0]);
                }
            }//傳送MODE

            //設定滾筒轉速以及風扇轉速數值
            s1 = new String("$CR1" + String.format(tempformatStr,50)); //之後變數填入warmRoller
            s2 = new String("$CR2" + String.format(tempformatStr,50)); //之後變數填入steamRoller
            s3 = new String("$CR3" + String.format(tempformatStr,50)); //之後變數填入bakingRoller
            s4 = new String("$CR4" + String.format(tempformatStr,50));
            f1 = new String("$CF1" + String.format(formatStr,1500));          //之後變數填入warmFan
            f2 = new String("$CF2" + String.format(formatStr,1500));         //之後變數填入steamFan
            f3 = new String("$CF3" + String.format(formatStr,1500));        //之後變數填入bakingFan
            f4 = new String("$CF4" + String.format(formatStr,3900));

            //取得各項資料的Ascii編碼
            final byte[][] data = dataTobyte(stringBuffer.toString().getBytes(StandardCharsets.US_ASCII));
            final byte[][] power = dataTobyte(pow.toString().getBytes(StandardCharsets.US_ASCII));
            final byte[][] R = dataTobyte(s1.getBytes(StandardCharsets.US_ASCII));
            final byte[][] R2 = dataTobyte(s2.getBytes(StandardCharsets.US_ASCII));
            final byte[][] R3 = dataTobyte(s3.getBytes(StandardCharsets.US_ASCII));
            final byte[][] R4 = dataTobyte(s4.getBytes(StandardCharsets.US_ASCII));
            final byte[][] F  = dataTobyte(f1.getBytes(StandardCharsets.US_ASCII));
            final byte[][] F2 = dataTobyte(f2.getBytes(StandardCharsets.US_ASCII));
            final byte[][] F3 = dataTobyte(f3.getBytes(StandardCharsets.US_ASCII));
            final byte[][] F4 = dataTobyte(f4.getBytes(StandardCharsets.US_ASCII));



            if(power[0][0]!=0){
                if(power.length > 0 ) {
                    int k = 0;
                    for (int i = k; i < power.length; i++) {
                        if (DataQueues(power[i])) {
                            k++;

                        } else {
                            i = k;
                        }
                    }
                }
            }else{
                if(!DataQueues(power[0])){
                    DataQueues(power[0]);
                }
            }//傳送POW

            if(data[0][0]!=0){
                if(data.length > 0 ){
                    int len1 = (data[0].length)/2;
                    int len2 = (data[0].length - len1);
                    int count = 0;
                    //這邊COE指令過長需拆成2段傳送
                    byte[][] bytes1 = new byte[1][len1];
                    byte[][] bytes2 = new byte[1][len2];
                    for (int i = 0; i < data[0].length; i++){
                        if (i < (data[0].length/2)){
                            bytes1[0][i] = data[0][i];
                        }else{
                            bytes2[0][count] = data[0][i];
                            count++;
                        }
                    }
                    DataQueues(bytes1[0]);
                    DataQueues(bytes2[0]);

                }else{
                    if(!DataQueues(data[0])){
                        DataQueues(data[0]);
                    }
                }
            }//傳送COE





            if(R[0][0]!=0){
                if(R.length > 0 ){
                    int k = 0;
                    for (int i = k ; i < R.length ; i++){
                        if(DataQueues(R[i])){
                            k++;
                        }
                    }
                }else{
                    if(!DataQueues(R[0])){
                        DataQueues(R[0]);
                    }
                }
            }//傳送暖爐轉速




            if(R2[0][0]!=0){
                if(R2.length > 0 ){
                    int k = 0;
                    for (int i = k ; i < R2.length ; i++){
                        if(DataQueues(R2[i])){
                            k++;
                        }
                    }
                }else{
                    if(!DataQueues(R2[0])){
                        DataQueues(R2[0]);
                    }
                }
            }//傳送蒸焙轉速



            if(R3[0][0]!=0){
                if(R3.length > 0 ){
                    int k = 0;
                    for (int i = k ; i < R3.length ; i++){
                        if(DataQueues(R3[i])){
                            k++;
                        }
                    }
                }else{
                    if(!DataQueues(R3[0])){
                        DataQueues(R3[0]);
                    }
                }
            }//傳送烘焙轉速



            if(R4[0][0]!=0){
                if(R4.length > 0 ){
                    int k = 0;
                    for (int i = k ; i < R4.length ; i++){
                        if(DataQueues(R4[i])){
                            k++;
                        }
                    }
                }else{
                    if(!DataQueues(R4[0])){
                        DataQueues(R4[0]);
                    }
                }
            }//傳送下豆轉速



            if(F[0][0]!=0){
                if(F.length > 0 ){
                    int k = 0;
                    for (int i = k ; i < F.length ; i++){
                        if(DataQueues(F[i])){
                            k++;
                        }
                    }
                }else{
                    if(!DataQueues(F[0])){
                        DataQueues(F[0]);
                    }
                }
            }//傳送暖爐風扇


            if(F2[0][0]!=0){
                if(F2.length > 0 ){
                    int k = 0;
                    for (int i = k ; i < F2.length ; i++){
                        if(DataQueues(F2[i])){
                            k++;
                        }
                    }
                }else{
                    if(!DataQueues(F2[0])){
                        DataQueues(F2[0]);
                    }
                }
            }//傳送蒸焙風扇



            if(F3[0][0]!=0){
                if(F3.length > 0 ){
                    int k = 0;
                    for (int i = k ; i < F3.length ; i++){
                        if(DataQueues(F3[i])){
                            k++;
                        }
                    }
                }else{
                    if(!DataQueues(F3[0])){
                        DataQueues(F3[0]);
                    }
                }
            }//傳送烘焙風扇



            if(F4[0][0]!=0){
                if(F4.length > 0 ){
                    int k = 0;
                    for (int i = k ; i < F4.length ; i++){
                        if(DataQueues(F4[i])){
                            k++;
                        }
                    }
                }else{
                    if(!DataQueues(F4[0])){
                        DataQueues(F4[0]);
                    }
                }
            }//傳送下豆風扇



            SendDataCount = 0;
            sendData(queueArray.get(0));    //先將首筆資料傳送




        }catch(Exception e){
            Log.e("DataBundleError" , String.valueOf(e));
        }
    }



    /*** 將data轉為byte ***/
    private byte[][] dataTobyte(byte[] rdata){
        try {
            byte[][] buffer_array;
            int ten_count;
            /*加上\r\n(0x0d和0x0a)*/
            List<byte[]> bytes = new ArrayList<byte[]>();
            byte[] byte_end = new byte[]{(byte) 0x0d, (byte) 0x0a};
            bytes.add(rdata);
            bytes.add(byte_end);
            byte[] newByte = streamCopy(bytes);
            /*End Of 加上\r\n (0x0d和0x0a)*/
            if (rdata != null && rdata.length > 0 && rdata.length < 255) {
                if (newByte.length < Ble_Uni_limit) {
                    buffer_array = new byte[1][1];
                    buffer_array[0] = newByte;
                }else{
                    ten_count = (newByte.length % Ble_Uni_limit != 0) ? ((newByte.length / Ble_Uni_limit) + 1) : (newByte.length / Ble_Uni_limit);
                    buffer_array = new byte[ten_count][];
                    for (int i = 0; i < ten_count; i++) {
                        int uni_array = (((newByte.length - i * Ble_Uni_limit) / Ble_Uni_limit) == 0) ? (newByte.length % Ble_Uni_limit) : Ble_Uni_limit;
                        buffer_array[i] = new byte[uni_array];
                        for (int j = 0; j < Ble_Uni_limit && (((i * Ble_Uni_limit) + j) < newByte.length); j++) {
                            int k = (i * Ble_Uni_limit) + j;
                        }//End   Of   for(int j=0;j<20&&(((i*20)+j)<newByte.length);j++)
                    }//End   Of   for(int i=0;i<ten_count;i++)
                }


                return buffer_array;
            }
        }catch (Exception e){}
        byte[][] buffer_array= new byte[1][1];
        buffer_array[0][0]=0;
        return buffer_array;
    }

    public static byte[] streamCopy(List<byte[]> srcArrays) {
        byte[] destAray = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            for (byte[] srcArray : srcArrays) {
                bos.write(srcArray);
            }
            bos.flush();
            destAray = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bos.close();
            } catch (IOException e) {}
        }
        return destAray;
    }//END  OF  streamCopy

    /*** 將data轉為byte ***/


}
