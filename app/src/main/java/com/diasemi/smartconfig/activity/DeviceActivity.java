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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.diasemi.smartconfig.R;
import com.diasemi.smartconfig.RuntimePermissionChecker;
import com.diasemi.smartconfig.config.ConfigEvent;
import com.diasemi.smartconfig.config.ConfigSpec;
import com.diasemi.smartconfig.config.ConfigurationManager;
import com.diasemi.smartconfig.fragment.DeviceFragment;
import com.diasemi.smartconfig.fragment.DisclaimerFragment;
import com.diasemi.smartconfig.fragment.InfoFragment;
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.OnPostBindViewListener;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import java.util.List;
import java.util.UUID;

public class DeviceActivity extends AppCompatActivity implements ConfigurationManager.Holder {
    private final static String TAG = "DeviceActivity";

    private static final int REQUEST_STORAGE_PERMISSION = 1;

    private static final int MENU_CONFIGURATION = 1;
    private static final int MENU_INFO = 10;
    private static final int MENU_DISCLAIMER = 11;
    private static final int MENU_DISCONNECT = -1;

    private BluetoothDevice device;
    private ConfigurationManager manager;
    private Toolbar toolbar;
    private Drawer drawer;
    private Fragment deviceFragment, infoFragment, disclaimerFragment;
    private RuntimePermissionChecker permissionChecker;


    private BluetoothGatt mybluetoothGatt;
    final public String FromTBS = "a304d2495cb8"; // WJZ. cant find the name
    final public String ToTBS = "a304d2495cba";


    public BluetoothGattCharacteristic ch_write;
    public BluetoothGattCharacteristic ch_read;

    protected View.OnClickListener bt_click = new View.OnClickListener() {
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.b_card:
                    byte[] swnp_init = {0,0,0,1,0};
                    Write_BLE(swnp_init);
                    break;
            }
        }
    };


    public void Write_BLE(byte[] towrite){
        ch_write.setValue(towrite);
        ch_write.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        mybluetoothGatt.writeCharacteristic(ch_write);
    }


    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
//                broadcastUpdate(ACTION_GATT_CONNECTED);
                // Attempts to discover services after successful connection.
                mybluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            final byte[] value = characteristic.getValue();
            if (characteristic == ch_read){
                Log.e("CHAR234","onCharacteristicChanged TBS2" + characteristic.getUuid());
//                BLERXQueu.add(value);
//                TBS2_received((value));
            }
        }
        @Override
        public void onServicesDiscovered(@NotNull final BluetoothGatt gatt, final int status) {
            final List<BluetoothGattService> services = gatt.getServices();
            for ( BluetoothGattService service : services){
                Log.e("CHAR234 #","service " + service.getUuid().toString());
                for (BluetoothGattCharacteristic ristic : service.getCharacteristics()){
                    String uid = ristic.getUuid().toString();
                    Log.e("CHAR234","BluetoothGattCharacteristic " + uid);
                    if (uid.contains(FromTBS)){
                        Log.e("CHAR234","char read " + uid);
                        ch_read=ristic;
                        UUID uid2=ch_read.getUuid();
                        gatt.setCharacteristicNotification(ch_read,true);
                        int i=0;
                        for (BluetoothGattDescriptor descriptor:ch_read.getDescriptors()){
                            // getDescriptor did not work. somehow it has 2. the 2nd blocks writing
                            if (i == 0) {
                                Log.e(TAG, "BluetoothGattDescriptor: " + descriptor.getUuid().toString());
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                boolean writeok = gatt.writeDescriptor(descriptor);
                                Log.e(TAG, "   write GattDescriptor: " + writeok);
                            }
                            i++;
                        }

                    }
                    if (uid.contains(ToTBS)){
                        Log.e("CHAR234","char write " + uid);
                        ch_write=ristic;
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_device);

        Button b_open= (Button) findViewById(R.id.b_card);
        b_open.setOnClickListener(bt_click);

        permissionChecker = new RuntimePermissionChecker(this, savedInstanceState);
        permissionChecker.registerPermissionRequestCallback(REQUEST_STORAGE_PERMISSION, new RuntimePermissionChecker.PermissionRequestCallback() {
            @Override
            public void onPermissionRequestResult(int requestCode, String[] permissions, String[] denied) {
                ConfigSpec.loadConfigSpec(DeviceActivity.this);
                manager.connect();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.status_bar_background));
            getWindow().setNavigationBarColor(getResources().getColor(R.color.navigation_bar_background));
        }
        toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitleTextColor(Color.WHITE);
            setSupportActionBar(toolbar);
        }
        ColorDrawable colorDrawable = new ColorDrawable(Color.parseColor("#347ab8"));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setBackgroundDrawable(colorDrawable);
            actionBar.setTitle(R.string.app_name);
            actionBar.setSubtitle(R.string.dialog_semiconductor);
        }

        device = getIntent().getParcelableExtra("device");
        String address = getIntent().getStringExtra("address");
        if (device == null && address != null && BluetoothAdapter.checkBluetoothAddress(address)) {
            device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        }
        if (device == null) {
            Log.e(TAG, "No device set in intent");
            finish();
            Toast.makeText(this, R.string.no_device_set, Toast.LENGTH_LONG).show();
            return;
        }
        mybluetoothGatt = device.connectGatt(this,true, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);

        EventBus.getDefault().register(this);
        manager = new ConfigurationManager(this, device);
        if (permissionChecker.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, R.string.storage_permission_rationale, REQUEST_STORAGE_PERMISSION)) {
            ConfigSpec.loadConfigSpec(this);
            //manager.connect();
        }

        drawer = createDrawer();
        drawer.setSelection(MENU_CONFIGURATION);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        EventBus.getDefault().unregister(this);
        if (manager != null)
            manager.disconnect();
        super.onDestroy();
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

    public BluetoothDevice getDevice() {
        return device;
    }

    @Override
    public ConfigurationManager getConfigurationManager() {
        return manager;
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen()) {
            drawer.closeDrawer();
            return;
        }
        super.onBackPressed();
    }

    private Drawer createDrawer() {
        AccountHeader accountHeader = new AccountHeaderBuilder()
                .withActivity(this)
                .withHeaderBackground(R.color.colorPrimary)
                .addProfiles(new ProfileDrawerItem().withName(getString(R.string.app_name)).withEmail(getString(R.string.dialog_semiconductor)))
                .withProfileImagesClickable(false)
                .withProfileImagesVisible(false)
                .withSelectionListEnabledForSingleProfile(false)
                .withTextColor(Color.WHITE)
                .build();

        SecondaryDrawerItem disconnectButton = new SecondaryDrawerItem() {
            @Override
            @LayoutRes
            public int getLayoutRes() {
                return R.layout.disconnect_drawer_button;
            }
        };
        disconnectButton
                .withTextColor(Color.WHITE)
                .withName(R.string.drawer_disconnect)
                .withIdentifier(MENU_DISCONNECT)
                .withEnabled(true)
                .withPostOnBindViewListener(new OnPostBindViewListener() {
                    @Override
                    public void onBindView(@NotNull IDrawerItem<?> iDrawerItem, @NotNull View view) {
                        view.setBackground(getResources().getDrawable(R.drawable.button_disconnect));
                    }
                });

        IDrawerItem[] drawerItems = new IDrawerItem[] {
                new PrimaryDrawerItem().withName(R.string.drawer_configuration).withIcon(GoogleMaterial.Icon.gmd_settings).withIdentifier(MENU_CONFIGURATION),
                new DividerDrawerItem(),
                new PrimaryDrawerItem().withName(R.string.drawer_information).withIcon(R.drawable.cic_info).withSelectedIcon(R.drawable.cic_info_selected).withIdentifier(MENU_INFO),
                new PrimaryDrawerItem().withName(R.string.drawer_disclaimer).withIcon(R.drawable.cic_disclaimer).withSelectedIcon(R.drawable.cic_disclaimer_selected).withIdentifier(MENU_DISCLAIMER),
        };

        Drawer drawer = new DrawerBuilder().withActivity(this)
                .withAccountHeader(accountHeader)
                .withToolbar(toolbar)
                .addDrawerItems(drawerItems)
                .addStickyDrawerItems(disconnectButton)
                .withStickyFooterShadow(false)
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, @NotNull IDrawerItem drawerItem) {
                        int id = (int) drawerItem.getIdentifier();
                        Log.d(TAG, "Menu ID: " + id);
                        if (id == MENU_DISCONNECT)
                            finish();
                        else
                            changeFragment(getFragment(id));
                        return false;
                    }
                })
                .build();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            drawer.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);
        }

        return drawer;
    }

    private Fragment getFragment(int id) {
        switch (id) {
            case MENU_CONFIGURATION:
                toolbar.setSubtitle(R.string.dialog_semiconductor);
                if (deviceFragment == null)
                    deviceFragment = new DeviceFragment();
                return deviceFragment;

            case MENU_INFO:
                toolbar.setSubtitle(R.string.drawer_information);
                if (infoFragment == null)
                    infoFragment = new InfoFragment();
                return infoFragment;

            case MENU_DISCLAIMER:
                toolbar.setSubtitle(R.string.drawer_disclaimer);
                if (disclaimerFragment == null)
                    disclaimerFragment = new DisclaimerFragment();
                return disclaimerFragment;

            default:
                return new Fragment();
        }
    }

    private void changeFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onConnection(ConfigEvent.Connection event) {
        if (manager != event.manager)
            return;
        if (manager.isDisconnected()) {
            finish();
            Toast.makeText(this, R.string.device_disconnected, Toast.LENGTH_SHORT).show();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onDeviceReady(ConfigEvent.Ready event) {
        if (manager != event.manager)
            return;
        manager.readAllElements();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onNotSupported(ConfigEvent.NotSupported event) {
        if (manager != event.manager)
            return;
        Toast.makeText(this, R.string.config_not_supported, Toast.LENGTH_LONG).show();
    }
}
