/*
 *******************************************************************************
 *
 * Copyright (C) 2020 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.smartconfig.activity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import com.diasemi.smartconfig.R;
import com.diasemi.smartconfig.RuntimePermissionChecker;
import com.diasemi.smartconfig.config.ConfigEvent;
import com.diasemi.smartconfig.config.ConfigSpec;
import com.diasemi.smartconfig.config.ConfigUtil;
import com.diasemi.smartconfig.view.ScanAdapter;
import com.diasemi.smartconfig.view.ScanItem;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ScanActivity extends AppCompatActivity implements OnItemClickListener {
    private final static String TAG = "ScanActivity";

    private final static int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private final static int LIST_UPDATE_INTERVAL = 1000;

    private BluetoothAdapter mBluetoothAdapter;
    private ScannerApi scannerApi;
    private boolean isScanning = false;
    private BluetoothDevice pendingConnection;
    private ArrayList<BluetoothDevice> bluetoothDeviceList;
    private ArrayList<ScanItem> deviceList;
    private ArrayList<BluetoothDevice> filterBluetoothDeviceList;
    private ArrayList<ScanItem> filterDeviceList;
    private ArrayList<AdvData> advDataList;
    private ScanAdapter bluetoothScanAdapter;
    private ListView deviceListView;
    private SwipeRefreshLayout deviceListSwipeRefresh;
    private ScanFilter scanFilter;
    private Boolean locationServicesRequired;
    private boolean locationServicesSkipCheck;
    private RuntimePermissionChecker permissionChecker;
    private Handler handler;
    private Runnable scanTimer;
    private long lastListUpdate;

    public static final UUID SUOTA_SERVICE_UUID = UUID.fromString("0000fef5-0000-1000-8000-00805f9b34fb");
    public static final UUID DSPS_SERVICE_UUID = UUID.fromString("0783b03e-8535-b5a0-7140-a304d2495cb7");
    public static final UUID IOT_SERVICE_UUID = UUID.fromString("2ea78970-7d44-44bb-b097-26183f402400");
    public static final UUID WEARABLES_580_SERVICE_UUID = UUID.fromString("00002800-0000-1000-8000-00805f9b34fb");
    public static final UUID WEARABLES_680_SERVICE_UUID = UUID.fromString("00002ea7-0000-1000-8000-00805f9b34fb");
    public static final UUID MESH_PROVISIONING_SERVICE_UUID = UUID.fromString("00001827-0000-1000-8000-00805f9b34fb");
    public static final UUID MESH_PROXY_SERVICE_UUID = UUID.fromString("00001828-0000-1000-8000-00805f9b34fb");
    public static final UUID IMMEDIATE_ALERT_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
    public static final UUID LINK_LOSS_SERVICE_UUID = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb");

    public static final int DIALOG_MANUFACTURER_ID = 0x00D2;
    public static final int APPLE_MANUFACTURER_ID = 0x004C;
    public static final int MICROSOFT_MANUFACTURER_ID = 0x0006;

    private static class AdvData {

        byte[] raw;
        String name;
        boolean discoverable;
        boolean limitedDiscoverable;
        ArrayList<UUID> services = new ArrayList<>();
        HashMap<Integer, byte[]> manufacturer = new HashMap<>();

        boolean suota;
        boolean dsps;
        boolean iot;
        boolean wearable;
        boolean mesh;
        boolean proximity;

        boolean iBeacon;
        boolean dialogBeacon;
        UUID beaconUuid;
        int beaconMajor;
        int beaconMinor;
        boolean eddystone;
        boolean microsoft;

        boolean other() {
            return iot || wearable || mesh || proximity;
        }

        boolean beacon() {
            return iBeacon || dialogBeacon || eddystone || microsoft;
        }

        boolean unknown() {
            return !suota && !dsps && !other() && !beacon();
        }
    }

    private AdvData parseAdvertisingData(byte[] data) {
        AdvData advData = new AdvData();
        advData.raw = data;

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() > 2) {
            int length = buffer.get() & 0xff;
            if (length == 0)
                break;

            int type = buffer.get() & 0xff;
            --length;

            switch (type) {
                case 0x01: // Flags
                    if (length == 0 || buffer.remaining() == 0)
                        break;
                    byte flags = buffer.get();
                    length -= 1;
                    advData.discoverable = (flags & 0x02) != 0;
                    advData.limitedDiscoverable = (flags & 0x01) != 0;
                    break;

                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (length >= 2 && buffer.remaining() >= 2) {
                        advData.services.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", buffer.getShort() & 0xffff)));
                        length -= 2;
                    }
                    break;

                case 0x04: // Partial list of 32-bit UUIDs
                case 0x05: // Complete list of 32-bit UUIDs
                    while (length >= 4 && buffer.remaining() >= 4) {
                        advData.services.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", buffer.getInt() & 0xffffffffL)));
                        length -= 4;
                    }
                    break;

                case 0x06: // Partial list of 128-bit UUIDs
                case 0x07: // Complete list of 128-bit UUIDs
                    while (length >= 16 && buffer.remaining() >= 16) {
                        long lsb = buffer.getLong();
                        long msb = buffer.getLong();
                        advData.services.add(new UUID(msb, lsb));
                        length -= 16;
                    }
                    break;

                case 0x08: // Shortened Local Name
                    if (advData.name != null)
                        break;
                    // fall through
                case 0x09: // Complete Local Name
                    if (length > buffer.remaining())
                        break;
                    byte[] name = new byte[length];
                    buffer.get(name);
                    length = 0;
                    advData.name = new String(name, StandardCharsets.UTF_8);
                    break;

                case 0xff: // Manufacturer Specific Data
                    if (length >= 2 && buffer.remaining() >= 2) {
                        int manufacturer = buffer.getShort() & 0xffff;
                        length -= 2;
                        byte[] manufacturerData = new byte[0];
                        if (length <= buffer.remaining()) {
                            manufacturerData = new byte[length];
                            length = 0;
                            buffer.get(manufacturerData);
                        }
                        advData.manufacturer.put(manufacturer, manufacturerData);
                    }
                    break;
            }

            if (length > buffer.remaining())
                break;
            buffer.position(buffer.position() + length);
        }

        advData.suota = advData.services.contains(SUOTA_SERVICE_UUID);
        advData.dsps = advData.services.contains(DSPS_SERVICE_UUID);
        advData.iot = advData.services.contains(IOT_SERVICE_UUID);
        advData.wearable = advData.services.contains(WEARABLES_580_SERVICE_UUID) || advData.services.contains(WEARABLES_680_SERVICE_UUID);
        advData.mesh = advData.services.contains(MESH_PROVISIONING_SERVICE_UUID) || advData.services.contains(MESH_PROXY_SERVICE_UUID);
        advData.proximity = advData.services.contains(IMMEDIATE_ALERT_SERVICE_UUID) && advData.services.contains(LINK_LOSS_SERVICE_UUID);

        // Check for iBeacon
        int manufacturerId = DIALOG_MANUFACTURER_ID;
        byte[] manufacturerData = advData.manufacturer.get(manufacturerId);
        if (manufacturerData == null) {
            manufacturerId = APPLE_MANUFACTURER_ID;
            manufacturerData = advData.manufacturer.get(manufacturerId);
        }
        if (manufacturerData != null && manufacturerData.length == 23) {
            ByteBuffer manufacturerDataBuffer = ByteBuffer.wrap(manufacturerData).order(ByteOrder.BIG_ENDIAN);
            // Check subtype/length
            if (buffer.get() == 2 && manufacturerDataBuffer.get() == 21) {
                advData.dialogBeacon = manufacturerId == DIALOG_MANUFACTURER_ID;
                advData.iBeacon = manufacturerId == APPLE_MANUFACTURER_ID;
                advData.beaconUuid = new UUID(manufacturerDataBuffer.getLong(), manufacturerDataBuffer.getLong());
                advData.beaconMajor = manufacturerDataBuffer.getShort() & 0xffff;
                advData.beaconMinor = manufacturerDataBuffer.getShort() & 0xffff;
            }
        }

        // Check for Microsoft beacon
        manufacturerData = advData.manufacturer.get(MICROSOFT_MANUFACTURER_ID);
        if (manufacturerData != null && manufacturerData.length == 27) {
            advData.microsoft = true;
        }

        return advData;
    }

    private String getDeviceDescription(AdvData advData) {
        ArrayList<String> description = new ArrayList<>();
        if (advData.suota)
            description.add(getString(R.string.scan_suota));
        if (advData.dsps)
            description.add(getString(R.string.scan_dsps));
        if (advData.iot)
            description.add(getString(R.string.scan_iot));
        if (advData.wearable)
            description.add(getString(R.string.scan_wearable));
        if (advData.mesh)
            description.add(getString(R.string.scan_mesh));
        if (advData.proximity)
            description.add(getString(R.string.scan_proximity));
        if (advData.iBeacon || advData.dialogBeacon)
            description.add(getString(R.string.scan_beacon, advData.beaconUuid.toString(), advData.beaconMajor, advData.beaconMinor));
        if (advData.microsoft)
            description.add(getString(R.string.scan_microsoft));

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < description.size(); ++i) {
            if (i > 0)
                text.append(", ");
            text.append(description.get(i));
        }
        return text.toString();
    }

    private int getDeviceIcon(AdvData advData) {
        if (advData.dsps)
            return R.drawable.scan_icon_dsps;
        if (advData.suota)
            return R.drawable.scan_icon_suota;
        if (advData.other())
            return R.drawable.icon_dialog;
        return R.drawable.icon_device_unknown;
    }

    private void onScanResult(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AdvData advData = parseAdvertisingData(scanRecord);
                String description = getDeviceDescription(advData);
                if (bluetoothDeviceList.contains(device)) { // update
                    int index = bluetoothDeviceList.indexOf(device);
                    deviceList.set(index, new ScanItem(getDeviceIcon(advData), device.getName(), device.getAddress(), description, rssi));
                    advDataList.set(index, advData);
                    updateList(false);
                } else {
                    bluetoothDeviceList.add(device); // add
                    deviceList.add(new ScanItem(getDeviceIcon(advData), device.getName(), device.getAddress(), description, rssi));
                    advDataList.add(advData);
                    updateList(true);
                }
            }
        });
    }

    private Runnable delayedListUpdate = new Runnable() {
        @Override
        public void run() {
            updateList(true);
        }
    };

    private void updateList(boolean force) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastListUpdate;
        if (elapsed < 0 || elapsed > LIST_UPDATE_INTERVAL || force) {
            handler.removeCallbacks(delayedListUpdate);
            lastListUpdate = now;
            applyScanFilter();
            bluetoothScanAdapter.notifyDataSetChanged();
        } else {
            handler.postDelayed(delayedListUpdate, LIST_UPDATE_INTERVAL - elapsed);
        }
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            onScanResult(device, rssi, scanRecord);
        }
    };

    private interface ScannerApi {
        void startScanning();
        void stopScanning();
    }

    @SuppressWarnings("deprecation")
    private ScannerApi scannerApi19 = new ScannerApi() {
        @Override
        public void startScanning() {
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }

        @Override
        public void stopScanning() {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    };

    private ScannerApi scannerApi21 = new ScannerApi() {
        BluetoothLeScanner scanner;
        ScanCallback callback;
        ScanSettings settings;

        @TargetApi(21)
        @Override
        public void startScanning() {
            if (scanner == null) {
                scanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build();
                callback = new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        ScanActivity.this.onScanResult(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());
                    }

                    @Override
                    public void onBatchScanResults(List<ScanResult> results) {
                        for (ScanResult result : results)
                            ScanActivity.this.onScanResult(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());
                    }
                };
            }
            scanner.startScan(null, settings, callback);
        }

        @TargetApi(21)
        @Override
        public void stopScanning() {
            if (scanner != null && mBluetoothAdapter.isEnabled())
                scanner.stopScan(callback);
        }
    };

    private static class ScanFilter {
        public Pattern name;
        public Pattern address;
        public Pattern advData;
        public int rssi = Integer.MIN_VALUE;
        public boolean suota = true;
        public boolean dsps = true;
        public boolean other = true;
        public boolean unknown = false;
        public boolean beacon = true;
        public boolean microsoft = false;
    }

    private void initScanFilter() {
        scanFilter = new ScanFilter();
        SharedPreferences preferences = getSharedPreferences(ScanFilterActivity.PREFERENCES_NAME, 0);

        if (preferences.getBoolean("rssiFilter", false)) {
            try {
                scanFilter.rssi = Integer.decode(preferences.getString("rssiLevel", ""));
            } catch (NumberFormatException e) {}
        }

        scanFilter.suota = preferences.getBoolean("suota", true);
        scanFilter.dsps = preferences.getBoolean("dsps", true);
        scanFilter.other = preferences.getBoolean("other", true);
        scanFilter.unknown = preferences.getBoolean("unknown", false);

        scanFilter.beacon = preferences.getBoolean("iBeacon", true);
        scanFilter.microsoft = preferences.getBoolean("microsoft", false);

        try {
            String pattern = preferences.getString("name", null);
            if (!TextUtils.isEmpty(pattern))
                scanFilter.name = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            Log.e(TAG, "Scan filter invalid name pattern", e);
        }
        try {
            String pattern = preferences.getString("address", null);
            if (!TextUtils.isEmpty(pattern))
                scanFilter.address = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            Log.e(TAG, "Scan filter invalid address pattern", e);
        }
        try {
            String pattern = preferences.getString("advData", null);
            if (!TextUtils.isEmpty(pattern))
                scanFilter.advData = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            Log.e(TAG, "Scan filter invalid advertising data pattern", e);
        }
    }

    private void applyScanFilter() {
        for (int i = 0; i < bluetoothDeviceList.size(); ++i) {
            BluetoothDevice device = bluetoothDeviceList.get(i);
            ScanItem scanItem = deviceList.get(i);
            AdvData advData = advDataList.get(i);
            int index = filterBluetoothDeviceList.indexOf(device);

            boolean add = false;
            if (advData.suota && scanFilter.suota)
                add = true;
            if (!add && advData.dsps && scanFilter.dsps)
                add = true;
            if (!add && advData.other() && scanFilter.other)
                add = true;
            if (!add && advData.unknown() && scanFilter.unknown)
                add = true;
            if (!add && (advData.iBeacon || advData.dialogBeacon) && scanFilter.beacon)
                add = true;
            if (!add && advData.microsoft && scanFilter.microsoft)
                add = true;

            if (add && scanFilter.name != null)
                add = scanFilter.name.matcher(scanItem.name != null ? scanItem.name : "").matches();
            if (add && scanFilter.address != null)
                add = scanFilter.address.matcher(scanItem.address).matches();
            if (add && scanFilter.advData != null)
                add = scanFilter.advData.matcher(ConfigUtil.hex(advData.raw)).matches();

            if (scanItem.signal < scanFilter.rssi && add && index == -1) {
                continue;
            }

            if (scanItem.signal < scanFilter.rssi && index != -1) {
                filterBluetoothDeviceList.remove(device);
                filterDeviceList.remove(index);
                continue;
            }

            if (add) {
                if (index == -1) {
                    filterBluetoothDeviceList.add(device);
                    filterDeviceList.add(scanItem);
                } else {
                    filterDeviceList.set(index, scanItem);
                }
            } else if (index != -1) {
                filterBluetoothDeviceList.remove(device);
                filterDeviceList.remove(index);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onScanFilterChanged(ScanFilterActivity.ScanFilterChanged event) {
        initScanFilter();
        filterBluetoothDeviceList.clear();
        filterDeviceList.clear();
        updateList(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.status_bar_background));
            getWindow().setNavigationBarColor(getResources().getColor(R.color.navigation_bar_background));
        }
        ColorDrawable colorDrawable = new ColorDrawable(Color.parseColor("#347ab8"));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setBackgroundDrawable(colorDrawable);
            actionBar.setTitle(R.string.app_name);
            actionBar.setSubtitle(R.string.dialog_semiconductor);
        }

        permissionChecker = new RuntimePermissionChecker(this, savedInstanceState);
        if (getPreferences(MODE_PRIVATE).getBoolean("oneTimePermissionRationale", true)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("oneTimePermissionRationale", false).apply();
            permissionChecker.setOneTimeRationale(getString(R.string.permission_rationale));
        }
        permissionChecker.registerPermissionRequestCallback(REQUEST_LOCATION_PERMISSION, new RuntimePermissionChecker.PermissionRequestCallback() {
            @Override
            public void onPermissionRequestResult(int requestCode, String[] permissions, String[] denied) {
                if (denied == null)
                    startDeviceScan();
            }
        });

        EventBus.getDefault().register(this);
        handler = new Handler();
        scannerApi = Build.VERSION.SDK_INT < 21 ? scannerApi19 : scannerApi21;
        bluetoothDeviceList = new ArrayList<>();
        deviceList = new ArrayList<>();
        filterBluetoothDeviceList = new ArrayList<>();
        filterDeviceList = new ArrayList<>();
        advDataList = new ArrayList<>();
        bluetoothScanAdapter = new ScanAdapter(this, R.layout.scan_item_row, filterDeviceList);
        deviceListView = findViewById(R.id.deviceListView);
        deviceListView.setAdapter(bluetoothScanAdapter);
        deviceListView.setOnItemClickListener(this);
        deviceListSwipeRefresh = findViewById(R.id.deviceListSwipeRefresh);
        deviceListSwipeRefresh.setColorSchemeResources(R.color.primary);
        deviceListSwipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                startDeviceScan();
                deviceListSwipeRefresh.setRefreshing(false);
            }
        });
//        scanTimer = new Runnable() {
//            @Override
//            public void run() {
//                stopDeviceScan();
//            }
//        };
        initScanFilter();

        // Initialize Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            // Device does not support Bluetooth Low Energy
            Log.e(TAG, "Bluetooth Low Energy not supported.");
            Toast.makeText(getApplicationContext(), "Bluetooth Low Energy is not supported on this device", Toast.LENGTH_LONG).show();
            finish();
        }
        startDeviceScan();

        ConfigSpec.loadConfigSpec(this);
    }

    @Override
    protected void onDestroy() {
        stopDeviceScan();
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        deviceListView.setOnItemClickListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                startDeviceScan();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        permissionChecker.saveState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        permissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private boolean checkLocationServices() {
        if (Build.VERSION.SDK_INT < 23 || locationServicesSkipCheck)
            return true;
        // Check if location services are required by reading the setting from Bluetooth app.
        if (locationServicesRequired == null) {
            locationServicesRequired = true;
            try {
                Resources res = getPackageManager().getResourcesForApplication("com.android.bluetooth");
                int id = res.getIdentifier("strict_location_check", "bool", "com.android.bluetooth");
                locationServicesRequired = res.getBoolean(id);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
                Log.e(TAG, "Failed to read location services requirement setting", e);
            }
            Log.d(TAG, "Location services requirement setting: " + locationServicesRequired);
        }
        if (!locationServicesRequired)
            return true;
        // Check location services setting. Prompt the user to enable them.
        if (Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF)
            return true;
        Log.d(TAG, "Location services disabled");
        new AlertDialog.Builder(this)
                .setTitle(R.string.no_location_services_title)
                .setMessage(R.string.no_location_services_msg)
                .setPositiveButton(R.string.enable_location_services, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton(R.string.no_location_services_scan, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        locationServicesSkipCheck = true;
                        startDeviceScan();
                    }
                })
                .show();
        return false;
    }

    private void startDeviceScan() {
        if (!mBluetoothAdapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
            return;
        }
        if (!permissionChecker.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, R.string.location_permission_rationale, REQUEST_LOCATION_PERMISSION))
            return;
        if (!checkLocationServices())
            return;
        if (isScanning)
            stopDeviceScan();
        Log.d(TAG, "Start scanning");
        isScanning = true;
        bluetoothDeviceList.clear();
        deviceList.clear();
        filterBluetoothDeviceList.clear();
        filterDeviceList.clear();
        advDataList.clear();
        bluetoothScanAdapter.clear();
        bluetoothScanAdapter.notifyDataSetChanged();
        scannerApi.startScanning();
//        handler.postDelayed(scanTimer, 10000);
        invalidateOptionsMenu();
    }

    private void stopDeviceScan() {
        handler.removeCallbacks(delayedListUpdate);
        if (isScanning) {
            Log.d(TAG, "Stop scanning");
            isScanning = false;
//            handler.removeCallbacks(scanTimer);
            scannerApi.stopScanning();
            invalidateOptionsMenu();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scan, menu);
        MenuItem menuItemScan = menu.findItem(R.id.menu_scan);
        menuItemScan.setTitle(isScanning ? R.string.menu_stop_scan : R.string.menu_scan);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        Intent intent;
        switch (id) {
            case R.id.menu_scan:
                if (isScanning) {
                    stopDeviceScan();
                } else {
                    startDeviceScan();
                }
                break;

            case R.id.menu_filter:
                intent = new Intent(this, ScanFilterActivity.class);
                startActivity(intent);
                break;

            case R.id.menu_info:
                intent = new Intent(this, DisclaimerActivity.class);
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * On click listener for scanned devices
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        stopDeviceScan();
        if (pendingConnection != null)
            return;
        pendingConnection = filterBluetoothDeviceList.get(position);
        deviceListView.setOnItemClickListener(null);
        Intent intent = new Intent(ScanActivity.this, DeviceActivity.class);
        intent.putExtra("device", pendingConnection);
        startActivity(intent);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onConnection(ConfigEvent.Connection event) {
        if (pendingConnection != null && pendingConnection.equals(event.manager.getDevice()) && (event.manager.isConnected() || event.manager.isDisconnected()))
            pendingConnection = null;
    }
}
