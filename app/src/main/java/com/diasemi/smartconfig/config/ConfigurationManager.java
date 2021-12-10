/*
 *******************************************************************************
 *
 * Copyright (C) 2020 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.smartconfig.config;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.diasemi.smartconfig.config.ConfigEvent.Connection;
import com.diasemi.smartconfig.config.ConfigEvent.DeviceInfo;
import com.diasemi.smartconfig.config.ConfigEvent.ElementDiscovery;
import com.diasemi.smartconfig.config.ConfigEvent.NotSupported;
import com.diasemi.smartconfig.config.ConfigEvent.Read;
import com.diasemi.smartconfig.config.ConfigEvent.Ready;
import com.diasemi.smartconfig.config.ConfigEvent.ServiceDiscovery;
import com.diasemi.smartconfig.config.ConfigEvent.Version;
import com.diasemi.smartconfig.config.ConfigEvent.Write;

import org.greenrobot.eventbus.EventBus;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static com.diasemi.smartconfig.config.ConfigEvent.ERROR_GATT_OPERATION;
import static com.diasemi.smartconfig.config.ConfigEvent.ERROR_INIT_CONFIGURATION_SERVICE;
import static com.diasemi.smartconfig.config.ConfigEvent.ERROR_INIT_READ;
import static com.diasemi.smartconfig.config.ConfigEvent.ERROR_INIT_WRITE;
import static com.diasemi.smartconfig.config.ConfigEvent.ERROR_NOT_READY;

public class ConfigurationManager {
    private final static String TAG = "ConfigurationManager";

    public interface Holder {
        ConfigurationManager getConfigurationManager();
    }

    // State
    public static final int DISCONNECTED = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    public static final int SERVICE_DISCOVERY = 3;
    public static final int ELEMENT_DISCOVERY = 4;
    public static final int READY = 5;

    private Context context;
    private BluetoothDevice device;
    private BluetoothGatt gatt;
    private int state = DISCONNECTED;
    private String version;
    private int mtu = 23;
    private boolean mtuRequestPending;
    private ArrayList<ConfigElement> elements = new ArrayList<>();
    private HashMap<Integer, ConfigElement> elementsMap = new HashMap<>();
    private ArrayDeque<GattOperation> gattQueue = new ArrayDeque<>();
    private boolean gattOperationPending;
    private ArrayDeque<ConfigOperation> configQueue = new ArrayDeque<>();
    private boolean configDequeueInProgress;
    private boolean writePending;
    private ArrayList<Integer> readPending = new ArrayList<>();
    private Integer fragmentedRead;
    private byte[] fragmentedReadData;
    private ArrayList<ConfigElement> pendingWriteElements = new ArrayList<>();
    private HashMap<ConfigElement, byte[]> pendingWriteData = new HashMap<>();
    private boolean servicesDiscovered;
    private boolean configNotSupported;
    private BluetoothGattService configService;
    private BluetoothGattCharacteristic configVersion;
    private BluetoothGattCharacteristic discoverConfig;
    private BluetoothGattCharacteristic writeConfig;
    private BluetoothGattCharacteristic readConfig;
    private BluetoothGattService deviceInfoService;
    private ArrayList<BluetoothGattCharacteristic> pendingEnableNotifications;
    private ArrayList<Integer> discoveredElements;
    private String logPrefix;

    public ConfigurationManager(Context context, BluetoothDevice device) {
        this.context = context.getApplicationContext();
        this.device = device;
        logPrefix = "[" + device.getAddress() + "] ";
    }

    public Context getContext() {
        return context;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public int getState() {
        return state;
    }

    public String getVersion() {
        return version;
    }

    public int getMtu() {
        return mtu;
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    public void requestMtu(int mtu) {
        if (Build.VERSION.SDK_INT < 21)
            return;
        enqueueGattOperation(new GattOperation(mtu));
    }

    public Collection<ConfigElement> getElements() {
        return elements;
    }

    public ConfigElement getElement(int id) {
        return elementsMap.get(id);
    }

    public boolean hasElement(int id) {
        return getElement(id) != null;
    }

    synchronized public void connect() {
        Log.d(TAG, logPrefix + "Connect");
        if (state != DISCONNECTED)
            return;
        state = CONNECTING;
        EventBus.getDefault().post(new Connection(this));
//        if (Build.VERSION.SDK_INT < 23) {
//            gatt = device.connectGatt(context, false, gattCallback);
//        } else {
//            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
//        }
    }

    synchronized public void disconnect() {
        Log.d(TAG, logPrefix + "Disconnect");
        if (gatt == null)
            return;
        gatt.disconnect();
        if (state == CONNECTING) {
            state = DISCONNECTED;
            gatt.close();
            gatt = null;
            EventBus.getDefault().post(new Connection(this));
        }
    }

    public boolean isConnected() {
        return state >= CONNECTED;
    }

    public boolean isConnecting() {
        return state == CONNECTING;
    }

    public boolean isDisconnected() {
        return state == DISCONNECTED;
    }

    public boolean servicesDiscovered() {
        return servicesDiscovered;
    }

    public boolean configNotSupported() {
        return configNotSupported;
    }

    public boolean isReady() {
        return state == READY;
    }

    private boolean checkReady() {
        if (!isReady()) {
            Log.e(TAG, logPrefix + "Device not ready. Operation not allowed.");
            EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_NOT_READY));
            return false;
        } else {
            return true;
        }
    }

    public boolean isReadPending() {
        return !readPending.isEmpty();
    }

    public boolean isWritePending() {
        return writePending;
    }

    public void readVersion() {
        if (configVersion == null)
            return;
        Log.d(TAG, logPrefix + "Read version");
        readCharacteristic(configVersion);
    }

    private void onVersionRead() {
        version = new String(configVersion.getValue(), StandardCharsets.UTF_8);
        Log.d(TAG, logPrefix + "Configuration structure version: " + version);
        EventBus.getDefault().post(new Version(this));
    }

    public boolean hasDeviceInfo(UUID uuid) {
        return deviceInfoService != null && (uuid == null || deviceInfoService.getCharacteristic(uuid) != null);
    }

    public void readDeviceInfo(UUID uuid) {
        if (!hasDeviceInfo(uuid)) {
            Log.e(TAG, logPrefix + "Device information not available: " + uuid);
            return;
        }
        readCharacteristic(deviceInfoService.getCharacteristic(uuid));
    }

    private void onDeviceInfoRead(BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        byte[] value = characteristic.getValue();
        String info = new String(value, StandardCharsets.UTF_8);
        Log.d(TAG, logPrefix + "Device information ["+ uuid + "]: " + info);
        EventBus.getDefault().post(new DeviceInfo(this, uuid, value, info));
    }

    public void discoverElements() {
        Log.d(TAG, logPrefix + "Discover elements");
        elements.clear();
        elementsMap.clear();
        state = ELEMENT_DISCOVERY;
        EventBus.getDefault().post(new ElementDiscovery(this, false));
        writeCharacteristic(discoverConfig, new byte[] { 64 });
    }

    private void onElementsDiscovered() {
        if (state != ELEMENT_DISCOVERY) {
            Log.e(TAG, logPrefix + "Unexpected elements discovery message");
            return;
        }
        if (discoveredElements == null)
            discoveredElements = new ArrayList<>();
        byte[] data = discoverConfig.getValue();
        Log.d(TAG, logPrefix + "Elements discovery: " + ConfigUtil.hexArrayLog(data));
        int number = data[0] & 0xff;
        boolean more = data[1] != 0;
        ByteBuffer elementsBuffer = ByteBuffer.wrap(data, 2, data.length - 2).order(ByteOrder.LITTLE_ENDIAN);
        if (elementsBuffer.remaining() / 2 != number)
            Log.e(TAG, logPrefix + "WARNING: Element discovery number mismatch");
        while (elementsBuffer.remaining() >= 2) {
            discoveredElements.add(elementsBuffer.getShort() & 0xffff);
        }
        if (!more) {
            Log.d(TAG, logPrefix + "Element discovery complete");
            initElements();
        }
    }

    private void initElements() {
        for (int id : discoveredElements) {
            ConfigElement element = new ConfigElement(id);
            elements.add(element);
            elementsMap.put(id, element);
        }
        discoveredElements = null;
        state = READY;
        EventBus.getDefault().post(new ElementDiscovery(this, true));
        EventBus.getDefault().post(new Ready(this));
    }

    private void onElementsDiscoveryStartError() {
        Log.e(TAG, logPrefix + "Failed to start elements discovery");
        state = CONNECTED;
        EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_INIT_CONFIGURATION_SERVICE));
    }

    public void readElement(int id) {
        enqueueReadOperation(new ConfigOperation(id));
    }

    public void readElement(ConfigElement element) {
        enqueueReadOperation(new ConfigOperation(element, true));
    }

    private void executeReadElement(ConfigElement element) {
        if (!checkReady()) {
            readOperationComplete(-1);
            return;
        }
        Log.d(TAG, logPrefix + "Read element: " + element);
        readPending.add(element.getId());
        writeCharacteristic(readConfig, ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short)element.getId()).array());
    }

    public void readElements(List<ConfigElement> elements) {
        enqueueReadOperation(new ConfigOperation(elements, true));
    }

    public void readAllElements() {
        enqueueReadOperation(new ConfigOperation());
    }

    private void executeReadElements(List<ConfigElement> elements) {
        if (!checkReady() || elements.isEmpty()) {
            readOperationComplete(-1);
            return;
        }
        // Prepare all read operations in advance
        ArrayList<GattOperation> operations = new ArrayList<>();
        for (ConfigElement element : elements) {
            readPending.add(element.getId());
            operations.add(new GattOperation(readConfig, ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short)element.getId()).array()));
        }
        enqueueGattOperations(operations);
    }

    private void executeReadAllElements() {
        if (!checkReady()) {
            readOperationComplete(-1);
            return;
        }
        Log.d(TAG, logPrefix + "Read all elements");
        executeReadElements(elements);
    }

    private void onElementRead() {
        byte[] response = readConfig.getValue();
        Log.d(TAG, logPrefix + "Read response: " + ConfigUtil.hexArrayLog(response));
        ByteBuffer buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() < 4) {
            Log.e(TAG, logPrefix + "Invalid read response");
            return;
        }
        int id = buffer.getShort() & 0xffff;
        int length = buffer.get() & 0xff;
        int status = buffer.get() & 0xff;
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        boolean fragment = status == ConfigSpec.STATUS_LARGE_RESPONSE;
        boolean failed = status != ConfigSpec.STATUS_SUCCESS && !fragment;

        if (fragment || fragmentedRead != null && fragmentedRead == id) {
            Log.d(TAG, logPrefix + "Read fragment: " + String.format("%04x", id) + " " + ConfigUtil.hexArrayLog(data));
            if (fragmentedRead == null) {
                fragmentedRead = id;
                fragmentedReadData = data;
            } else {
                if (fragmentedRead != id)
                    Log.e(TAG, logPrefix + "WARNING: Fragmented read ID mismatch: " + String.format("%04x", fragmentedRead));
                int offset = fragmentedReadData.length;
                fragmentedReadData = Arrays.copyOf(fragmentedReadData, offset + data.length);
                System.arraycopy(data, 0, fragmentedReadData, offset, data.length);
            }
            if (fragment)
                return;
            // Last fragment
            Log.d(TAG, logPrefix + "Fragmented read complete: " + String.format("%04x", fragmentedRead));
            data = fragmentedReadData;
            fragmentedReadData = null;
            fragmentedRead = null;
        }

        if (data.length != length)
            Log.e(TAG, logPrefix + "WARNING: Read response length mismatch");
        if (failed)
            Log.e(TAG, logPrefix + "Read failed: " + String.format("%04x", id) + " error=" + status);
        else
            Log.d(TAG, logPrefix + "Read succeeded: " + String.format("%04x", id) + " " + ConfigUtil.hexArrayLog(data));

        ConfigElement element = getElement(id);
        if (element != null) {
            if (!failed) {
                element.setData(data);
                if (element.getWriteData().length == 0)
                    element.resetWriteData();
            }
        } else {
            Log.e(TAG, logPrefix + "Read response for unknown element");
            element = new ConfigElement(id, data);
        }
        readOperationComplete(id);
        EventBus.getDefault().post(new Read(this, element, status));
    }

    private void onElementReadStartError() {
        Log.e(TAG, logPrefix + "Failed to start read operation");
        // Remove all pending read operations
        Iterator<GattOperation> i = gattQueue.iterator();
        while (i.hasNext()) {
            if (i.next().getGattObject().equals(readConfig)) {
                i.remove();
            }
        }
        readPending.clear();
        readOperationComplete(-1);
        EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_INIT_READ));
    }

    public void writeElement(int id, byte[] data) {
        enqueueWriteOperation(new ConfigOperation(new ConfigElement(id, data), false));
    }

    public void writeElement(ConfigElement element) {
        enqueueWriteOperation(new ConfigOperation(element, false));
    }

    private void executeWriteElement(ConfigElement element, byte[] data) {
        writePending = true;
        if (!checkReady()) {
            writeOperationComplete();
            return;
        }
        pendingWriteElements.add(element);
        pendingWriteData.put(element, data);
        byte[] writeData = element.getWriteConfigData(data);
        Log.d(TAG, logPrefix + "Write element: " + element + " " + ConfigUtil.hexArrayLog(writeData));
        ByteBuffer buffer = ByteBuffer.allocate(2 + writeData.length).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 1) // 1 element
                .put((byte) 0) // more
                .put(writeData);
        writeCharacteristic(writeConfig, buffer.array());
    }

    public void writeElements(List<ConfigElement> elements) {
        enqueueWriteOperation(new ConfigOperation(elements, false));
    }

    public void writeAllElements() {
        enqueueWriteOperation(new ConfigOperation(this));
    }

    public void writeModifiedElements() {
        ArrayList<ConfigElement> modified = new ArrayList<>();
        for (ConfigElement element : elements) {
            if (element.modified())
                modified.add(element);
        }
        if (!modified.isEmpty())
            enqueueWriteOperation(new ConfigOperation(modified, false));
    }

    private void executeWriteElements(List<ConfigElement> elements, HashMap<ConfigElement, byte[]> writeData) {
        writePending = true;
        if (!checkReady() || elements.isEmpty()) {
            writeOperationComplete();
            return;
        }
        Log.d(TAG, logPrefix + "Write elements: " + elements);

        // Prepare all write operations in advance
        ArrayList<GattOperation> operations = new ArrayList<>();
        int number = 0;
        byte[] data = new byte[0];
        for (int i = 0; i < elements.size(); ++i) {
            ConfigElement element = elements.get(i);
            byte[] currData = element.getWriteConfigData(writeData.get(element));
            Log.d(TAG, logPrefix + "Write element: " + element + " " + ConfigUtil.hexArrayLog(currData));

            // Send previous if current doesn't fit
            if (i > 0 && data.length + currData.length > ConfigSpec.MAX_WRITE_DATA_LENGTH) {
                ByteBuffer buffer = ByteBuffer.allocate(2 + data.length)
                        .put((byte) number)
                        .put((byte) 1)
                        .put(data);
                operations.add(new GattOperation(writeConfig, buffer.array()));
                number = 0;
                data = new byte[0];
            }
            number++;
            int offset = data.length;
            data = Arrays.copyOf(data, data.length + currData.length);
            System.arraycopy(currData, 0, data, offset, currData.length);

            // Send the last one
            if (i == elements.size() - 1) {
                ByteBuffer buffer = ByteBuffer.allocate(2 + data.length)
                        .put((byte) number)
                        .put((byte) 0)
                        .put(data);
                operations.add(new GattOperation(writeConfig, buffer.array()));
            }
        }

        pendingWriteElements.addAll(elements);
        pendingWriteData.putAll(writeData);
        enqueueGattOperations(operations);
    }

    private void executeWriteAllElements(HashMap<ConfigElement, byte[]> data) {
        writePending = true;
        if (!checkReady()) {
            writeOperationComplete();
            return;
        }
        Log.d(TAG, logPrefix + "Write all elements");
        executeWriteElements(elements, data);
    }

    private void onElementWrite() {
        byte[] response = writeConfig.getValue();
        Log.d(TAG, logPrefix + "Write response: " + ConfigUtil.hexArrayLog(response));
        ByteBuffer buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() != 3) {
            Log.e(TAG, logPrefix + "Invalid write response");
            return;
        }
        int status = buffer.get() & 0xff;
        int errorElement = buffer.getShort() & 0xffff;
        boolean failed = status != ConfigSpec.STATUS_SUCCESS;

        if (!failed) {
            Log.d(TAG, logPrefix + "Write succeeded");
            for (ConfigElement element : pendingWriteElements) {
                boolean modified = element.modified();
                element.setData(pendingWriteData.get(element));
                if (!modified)
                    element.resetWriteData();
            }
        } else {
            Log.e(TAG, logPrefix + "Write failed: element=" + errorElement + " error=" + status);
            Log.d(TAG, logPrefix + "Read elements after write failure");
            readElements(new ArrayList<>(pendingWriteElements));
        }

        Write event = new Write(this, new ArrayList<>(pendingWriteElements), new HashMap<>(pendingWriteData), status);
        pendingWriteElements.clear();
        pendingWriteData.clear();
        writeOperationComplete();
        EventBus.getDefault().post(event);
    }

    private void onElementWriteStartError() {
        Log.e(TAG, logPrefix + "Failed to start write operation");
        // Remove all pending write operations
        Iterator<GattOperation> i = gattQueue.iterator();
        while (i.hasNext()) {
            if (i.next().getGattObject().equals(writeConfig)) {
                i.remove();
            }
        }
        pendingWriteElements.clear();
        pendingWriteData.clear();
        writeOperationComplete();
        EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_INIT_WRITE));
    }

    private void resetDatabase() {
        servicesDiscovered = false;
        configNotSupported = false;
        configService = null;
        configVersion = null;
        discoverConfig = null;
        writeConfig = null;
        readConfig = null;
        deviceInfoService = null;
    }

    synchronized private void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        this.gatt = gatt;
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(TAG, logPrefix + "Connected");
            state = CONNECTED;
            EventBus.getDefault().post(new Connection(this));
            Log.d(TAG, logPrefix + "Discover services");
            state = SERVICE_DISCOVERY;
            EventBus.getDefault().post(new ServiceDiscovery(this, false));
            gatt.discoverServices();
        } else {
            Log.d(TAG, logPrefix + "Disconnected: status=" + status);
            state = DISCONNECTED;
            gatt.close();
            this.gatt = null;
            mtu = 23;
            mtuRequestPending = false;
            gattOperationPending = false;
            gattQueue.clear();
            pendingWriteElements.clear();
            pendingWriteData.clear();
            writePending = false;
            readPending.clear();
            fragmentedRead = null;
            fragmentedReadData = null;
            configQueue.clear();
            resetDatabase();
            EventBus.getDefault().post(new Connection(this));
        }
    }

    private void onDatabaseError() {
        Log.e(TAG, logPrefix + "Configuration service not supported");
        configNotSupported = true;
        EventBus.getDefault().post(new NotSupported(this));
        servicesDiscovered = true;
        state = CONNECTED;
        EventBus.getDefault().post(new ServiceDiscovery(this, true));
    }

    private void onServicesDiscovered(int status) {
        Log.d(TAG, logPrefix + "Services discovered: status=" + status);

        deviceInfoService = gatt.getService(ConfigSpec.SERVICE_DEVICE_INFORMATION);

        configService = gatt.getService(ConfigSpec.SERVICE_UUID);
        if (configService == null) {
            Log.e(TAG, logPrefix + "Missing service " + ConfigSpec.SERVICE_UUID);
            onDatabaseError();
            return;
        }

        configVersion = configService.getCharacteristic(ConfigSpec.VERSION_CHARACTERISTIC_UUID);
        if (configVersion == null) {
            Log.e(TAG, logPrefix + "Missing version characteristic " + ConfigSpec.VERSION_CHARACTERISTIC_UUID);
            onDatabaseError();
            return;
        }

        discoverConfig = configService.getCharacteristic(ConfigSpec.DISCOVER_CHARACTERISTIC_UUID);
        if (discoverConfig == null) {
            Log.e(TAG, logPrefix + "Missing discover characteristic " + ConfigSpec.DISCOVER_CHARACTERISTIC_UUID);
            onDatabaseError();
            return;
        }

        writeConfig = configService.getCharacteristic(ConfigSpec.WRITE_CHARACTERISTIC_UUID);
        if (writeConfig == null) {
            Log.e(TAG, logPrefix + "Missing write characteristic " + ConfigSpec.WRITE_CHARACTERISTIC_UUID);
            onDatabaseError();
            return;
        }

        readConfig = configService.getCharacteristic(ConfigSpec.READ_CHARACTERISTIC_UUID);
        if (readConfig == null) {
            Log.e(TAG, logPrefix + "Missing read characteristic " + ConfigSpec.READ_CHARACTERISTIC_UUID);
            onDatabaseError();
            return;
        }

        BluetoothGattDescriptor ccc;
        ccc = discoverConfig.getDescriptor(ConfigSpec.CLIENT_CONFIG_DESCRIPTOR);
        if (ccc == null) {
            Log.e(TAG, logPrefix + "Missing discover characteristic client configuration");
            onDatabaseError();
            return;
        }
        ccc = writeConfig.getDescriptor(ConfigSpec.CLIENT_CONFIG_DESCRIPTOR);
        if (ccc == null) {
            Log.e(TAG, logPrefix + "Missing write characteristic client configuration");
            onDatabaseError();
            return;
        }
        ccc = readConfig.getDescriptor(ConfigSpec.CLIENT_CONFIG_DESCRIPTOR);
        if (ccc == null) {
            Log.e(TAG, logPrefix + "Missing read characteristic client configuration");
            onDatabaseError();
            return;
        }

        servicesDiscovered = true;
        state = CONNECTED;
        EventBus.getDefault().post(new ServiceDiscovery(this, true));

        readVersion();
        pendingEnableNotifications = new ArrayList<>(Arrays.asList(discoverConfig, writeConfig, readConfig));
        for (BluetoothGattCharacteristic characteristic : pendingEnableNotifications) {
            enableNotifications(characteristic);
        }
    }

    private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        enqueueGattOperation(new GattOperation(characteristic));
    }

    private void executeReadCharacteristic(BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, logPrefix + "Read characteristic: " + characteristic.getUuid());
        if (gatt.readCharacteristic(characteristic)) {
            gattOperationPending = true;
        } else {
            Log.e(TAG, logPrefix + "Error reading characteristic: " + characteristic.getUuid());
            EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_GATT_OPERATION));
            dequeueGattOperation();
        }
    }

    private void onCharacteristicRead(BluetoothGattCharacteristic characteristic, int status) {
        Log.d(TAG, logPrefix + "onCharacteristicRead: " + status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (characteristic.equals(configVersion)) {
                onVersionRead();
            } else if (characteristic.getService().equals(deviceInfoService)) {
                onDeviceInfoRead(characteristic);
            }
        } else {
            Log.e(TAG, logPrefix + "Failed to read characteristic");
            EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_GATT_OPERATION));
        }
        dequeueGattOperation();
    }

    private void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value) {
        enqueueGattOperation(new GattOperation(characteristic, value));
    }

    private void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value, boolean response) {
        enqueueGattOperation(new GattOperation(characteristic, value, response));
    }

    private void executeWriteCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value, boolean response) {
        Log.d(TAG, logPrefix + "Write characteristic: " + characteristic.getUuid() + " " + ConfigUtil.hexArrayLog(value));
        characteristic.setWriteType(response ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        characteristic.setValue(value);
        if (gatt.writeCharacteristic(characteristic)) {
            gattOperationPending = true;
        } else {
            Log.e(TAG, logPrefix + "Error writing characteristic: " + characteristic.getUuid());
            EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_GATT_OPERATION));
            onConfigOperationWriteError(characteristic);
            dequeueGattOperation();
        }
    }

    private void onCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status) {
        Log.d(TAG, logPrefix + "onCharacteristicWrite: " + status);
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, logPrefix + "Failed to write characteristic");
            EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_GATT_OPERATION));
            onConfigOperationWriteError(characteristic);
        }
        dequeueGattOperation();
    }

    private void onConfigOperationWriteError(BluetoothGattCharacteristic characteristic) {
        if (characteristic.equals(discoverConfig)) {
            onElementsDiscoveryStartError();
        } else if (characteristic.equals(readConfig)) {
            onElementReadStartError();
        } else if (characteristic.equals(writeConfig)) {
            onElementWriteStartError();
        }
    }

    private void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, logPrefix + "onCharacteristicChanged: " + characteristic.getUuid() + " " + ConfigUtil.hexArrayLog(characteristic.getValue()));
        if (characteristic.equals(discoverConfig)) {
            onElementsDiscovered();
        } else if (characteristic.equals(readConfig)) {
            onElementRead();
        } else if (characteristic.equals(writeConfig)) {
            onElementWrite();
        }
    }

    private void readDescriptor(BluetoothGattDescriptor descriptor) {
        enqueueGattOperation(new GattOperation(descriptor));
    }

    private void executeReadDescriptor(BluetoothGattDescriptor descriptor) {
        BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
        Log.d(TAG, logPrefix + "Read descriptor: " + characteristic.getUuid() + " " + descriptor.getUuid());
        if (gatt.readDescriptor(descriptor)) {
            gattOperationPending = true;
        } else {
            Log.e(TAG, logPrefix + "Error reading descriptor: " + characteristic.getUuid() + " " + descriptor.getUuid());
            EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_GATT_OPERATION));
            dequeueGattOperation();
        }
    }

    private void onDescriptorRead(BluetoothGattDescriptor descriptor, int status) {
        Log.d(TAG, logPrefix + "onDescriptorRead: " + status);
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, logPrefix + "Failed to read descriptor");
            EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_GATT_OPERATION));
        }
        dequeueGattOperation();
    }

    private void writeDescriptor(BluetoothGattDescriptor descriptor, byte[] value) {
        enqueueGattOperation(new GattOperation(descriptor, value));
    }

    private void executeWriteDescriptor(BluetoothGattDescriptor descriptor, byte[] value) {
        BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
        Log.d(TAG, logPrefix + "Write descriptor: " + characteristic.getUuid() + " " + descriptor.getUuid() + " " + ConfigUtil.hexArrayLog(value));
        descriptor.setValue(value);
        if (gatt.writeDescriptor(descriptor)) {
            gattOperationPending = true;
        } else {
            Log.e(TAG, logPrefix + "Error writing descriptor: " + characteristic.getUuid() + " " + descriptor.getUuid());
            EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_GATT_OPERATION));
            if (pendingEnableNotifications.contains(descriptor.getCharacteristic()))
                EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_INIT_CONFIGURATION_SERVICE));
            dequeueGattOperation();
        }
    }

    private void onDescriptorWrite(BluetoothGattDescriptor descriptor, int status) {
        Log.d(TAG, logPrefix + "onDescriptorWrite: " + status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (pendingEnableNotifications != null) {
                pendingEnableNotifications.remove(descriptor.getCharacteristic());
                if (pendingEnableNotifications.isEmpty()) {
                    pendingEnableNotifications = null;
                    discoverElements();
                }
            }
        } else {
            Log.e(TAG, logPrefix + "Failed to write descriptor");
            EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_GATT_OPERATION));
            if (pendingEnableNotifications.contains(descriptor.getCharacteristic()))
                EventBus.getDefault().post(new ConfigEvent.Error(this, ERROR_INIT_CONFIGURATION_SERVICE));
        }
        dequeueGattOperation();
    }

    private void enableNotifications(BluetoothGattCharacteristic characteristic) {
        BluetoothGattDescriptor ccc = characteristic.getDescriptor(ConfigSpec.CLIENT_CONFIG_DESCRIPTOR);
        if (ccc == null) {
            Log.e(TAG, logPrefix + "Missing client configuration descriptor: " + characteristic.getUuid());
            return;
        }
        boolean notify = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
        byte[] value = notify ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        Log.d(TAG, logPrefix + "Enable " + (notify ? "notifications" : "indications") + ": " + characteristic.getUuid());
        gatt.setCharacteristicNotification(characteristic, true);
        writeDescriptor(ccc, value);
    }

    private void executeMtuRequest(int mtu) {
        if (Build.VERSION.SDK_INT > 21) {
            Log.d(TAG, logPrefix + "MTU request: " + mtu);
            if (gatt.requestMtu(mtu)) {
                gattOperationPending = true;
                mtuRequestPending = true;
            } else {
                Log.e(TAG, logPrefix + "MTU request error");
                dequeueGattOperation();
            }
        }
    }

    private void onMtuChanged(int mtu, int status) {
        Log.d(TAG, logPrefix + "onMtuChanged: " + status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, logPrefix + "MTU changed to " + mtu);
            this.mtu = mtu;
        } else {
            Log.e(TAG, logPrefix + "Failed to change MTU");
        }
        if (mtuRequestPending) {
            mtuRequestPending = false;
            dequeueGattOperation();
        }
    }

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            ConfigurationManager.this.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            ConfigurationManager.this.onServicesDiscovered(status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            ConfigurationManager.this.onCharacteristicRead(characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            ConfigurationManager.this.onCharacteristicWrite(characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            ConfigurationManager.this.onCharacteristicChanged(characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            ConfigurationManager.this.onDescriptorRead(descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            ConfigurationManager.this.onDescriptorWrite(descriptor, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, logPrefix + "onReadRemoteRssi: " + rssi);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            ConfigurationManager.this.onMtuChanged(mtu, status);
        }
    };

    private static class GattOperation {

        public enum Type {
            ReadCharacteristic,
            WriteCharacteristic,
            WriteCommand,
            ReadDescriptor,
            WriteDescriptor,
            MtuRequest
        }

        private Type type;
        private Object gattObject;
        private byte[] value;

        public GattOperation(Object gattObject) {
            this.gattObject = gattObject;
            type = gattObject instanceof BluetoothGattCharacteristic ? Type.ReadCharacteristic : Type.ReadDescriptor;
        }

        public GattOperation(Object gattObject, byte[] value) {
            this.gattObject = gattObject;
            type = gattObject instanceof BluetoothGattCharacteristic ? Type.WriteCharacteristic : Type.WriteDescriptor;
            this.value = value.clone();
        }

        public GattOperation(BluetoothGattCharacteristic gattObject, byte[] value, boolean response) {
            this.gattObject = gattObject;
            type = response ? Type.WriteCharacteristic : Type.WriteCommand;
            this.value = value.clone();
        }

        public GattOperation(int mtu) {
            type = Type.MtuRequest;
            value = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short)mtu).array();
        }

        public Type getType() {
            return type;
        }

        public Object getGattObject() {
            return gattObject;
        }

        public BluetoothGattCharacteristic getCharacteristic() {
            return (BluetoothGattCharacteristic) gattObject;
        }

        public BluetoothGattDescriptor getDescriptor() {
            return (BluetoothGattDescriptor) gattObject;
        }

        public byte[] getValue() {
            return value;
        }
    }

    synchronized private void enqueueGattOperation(GattOperation operation) {
        if (gatt == null)
            return;
        if (gattOperationPending) {
            gattQueue.add(operation);
        } else {
            executeGattOperation(operation);
        }
    }

    synchronized private void enqueueGattOperations(List<GattOperation> operations) {
        if (gatt == null)
            return;
        gattQueue.addAll(operations);
        if (!gattOperationPending) {
            dequeueGattOperation();
        }
    }

    synchronized private void dequeueGattOperation() {
        gattOperationPending = false;
        if (gattQueue.isEmpty())
            return;
        executeGattOperation(gattQueue.poll());
    }

    private void executeGattOperation(GattOperation operation) {
        switch (operation.getType()) {
            case ReadCharacteristic:
                executeReadCharacteristic(operation.getCharacteristic());
                break;
            case WriteCharacteristic:
            case WriteCommand:
                executeWriteCharacteristic(operation.getCharacteristic(), operation.getValue(), operation.getType() == GattOperation.Type.WriteCharacteristic);
                break;
            case ReadDescriptor:
                executeReadDescriptor(operation.getDescriptor());
                break;
            case WriteDescriptor:
                executeWriteDescriptor(operation.getDescriptor(), operation.getValue());
                break;
            case MtuRequest:
                executeMtuRequest(ByteBuffer.wrap(operation.getValue()).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff);
                break;
        }
    }

    private static class ConfigOperation {

        public enum Type {
            Read,
            ReadMulti,
            ReadAll,
            Write,
            WriteMulti,
            WriteAll,
        }

        private Type type;
        private List<ConfigElement> elements;
        private HashMap<ConfigElement, byte[]> data;

        public ConfigOperation() {
            type = Type.ReadAll;
        }

        public ConfigOperation(ConfigurationManager manager) {
            type = Type.WriteAll;
            data = new HashMap<>();
            for (ConfigElement element : manager.getElements()) {
                data.put(element, Arrays.copyOf(element.getWriteData(), element.getWriteData().length));
            }
        }

        public ConfigOperation(int id) {
            type = Type.Read;
            elements = Collections.singletonList(new ConfigElement(id));
        }

        public ConfigOperation(ConfigElement element, boolean read) {
            type = read ? Type.Read : Type.Write;
            elements = Collections.singletonList(element);
            if (!read) {
                data = new HashMap<>();
                data.put(element, Arrays.copyOf(element.getWriteData(), element.getWriteData().length));
            }
        }

        public ConfigOperation(List<ConfigElement> elements, boolean read) {
            type = read ? Type.ReadMulti : Type.WriteMulti;
            this.elements = elements;
            if (!read) {
                data = new HashMap<>();
                for (ConfigElement element : elements) {
                    data.put(element, Arrays.copyOf(element.getWriteData(), element.getWriteData().length));
                }
            }
        }

        public Type getType() {
            return type;
        }

        public List<ConfigElement> getElements() {
            return elements;
        }

        public HashMap<ConfigElement, byte[]> getData() {
            return data;
        }

        public boolean isRead() {
            return type == Type.ReadAll || type == Type.Read || type == Type.ReadMulti;
        }

        public boolean isWrite() {
            return type == Type.Write || type == Type.WriteMulti || type == Type.WriteAll;
        }
    }

    synchronized private void enqueueReadOperation(ConfigOperation operation) {
        if (writePending || !configQueue.isEmpty()) {
            configQueue.add(operation);
        } else {
            executeConfigOperation(operation);
        }
    }

    synchronized private void enqueueWriteOperation(ConfigOperation operation) {
        if (writePending || !readPending.isEmpty() || !configQueue.isEmpty()) {
            configQueue.add(operation);
        } else {
            executeConfigOperation(operation);
        }
    }

    synchronized private void readOperationComplete(int id) {
        Iterator<Integer> i = readPending.iterator();
        while (i.hasNext()) {
            if (i.next() == id) {
                i.remove();
                break;
            }
        }
        dequeueConfigOperation();
    }

    synchronized private void writeOperationComplete() {
        writePending = false;
        dequeueConfigOperation();
    }

    synchronized private void dequeueConfigOperation() {
        if (configDequeueInProgress)
            return;
        configDequeueInProgress = true;
        while (!configQueue.isEmpty()) {
            if (writePending || configQueue.peek().isWrite() && !readPending.isEmpty())
                break;
            executeConfigOperation(configQueue.poll());
        }
        configDequeueInProgress = false;
    }

    private void executeConfigOperation(ConfigOperation operation) {
        switch (operation.getType()) {
            case Read:
                executeReadElement(operation.getElements().get(0));
                break;
            case ReadMulti:
                executeReadElements(operation.getElements());
                break;
            case ReadAll:
                executeReadAllElements();
                break;
            case Write:
                executeWriteElement(operation.getElements().get(0), operation.getData().get(operation.getElements().get(0)));
                break;
            case WriteMulti:
                executeWriteElements(operation.getElements(), operation.getData());
                break;
            case WriteAll:
                executeWriteAllElements(operation.getData());
                break;
        }
    }
}
