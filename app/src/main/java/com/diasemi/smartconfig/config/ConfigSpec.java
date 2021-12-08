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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ConfigSpec {
    private static final String TAG = "ConfigSpec";

    private static final String CONFIG_XML_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Dialog Semiconductor/SmartConfig";
    private static final String CONFIG_XML = "config-spec.xml";
    private static final String USER_CONFIG_XML = "user-config-spec.xml";

    public static final UUID SERVICE_UUID = UUID.fromString("a247a39b-0a3d-42c1-80d1-ea762753c6a0");
    public static final UUID VERSION_CHARACTERISTIC_UUID = UUID.fromString("a247a39b-0a3d-42c1-80d1-ea762753c6a1");
    public static final UUID DISCOVER_CHARACTERISTIC_UUID = UUID.fromString("a247a39b-0a3d-42c1-80d1-ea762753c6a2");
    public static final UUID WRITE_CHARACTERISTIC_UUID = UUID.fromString("a247a39b-0a3d-42c1-80d1-ea762753c6a3");
    public static final UUID READ_CHARACTERISTIC_UUID = UUID.fromString("a247a39b-0a3d-42c1-80d1-ea762753c6a4");
    public static final UUID CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final UUID SERVICE_DEVICE_INFORMATION = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARACTERISTIC_MANUFACTURER_NAME_STRING = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARACTERISTIC_MODEL_NUMBER_STRING = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARACTERISTIC_SERIAL_NUMBER_STRING = UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARACTERISTIC_HARDWARE_REVISION_STRING = UUID.fromString("00002A27-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARACTERISTIC_FIRMWARE_REVISION_STRING = UUID.fromString("00002A26-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARACTERISTIC_SOFTWARE_REVISION_STRING = UUID.fromString("00002A28-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARACTERISTIC_SYSTEM_ID = UUID.fromString("00002A23-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARACTERISTIC_IEEE_11073 = UUID.fromString("00002A2A-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARACTERISTIC_PNP_ID = UUID.fromString("00002A50-0000-1000-8000-00805f9b34fb");

    public static final int MAX_WRITE_DATA_LENGTH = 131;

    public static int STATUS_SUCCESS = 0;
    public static int STATUS_LARGE_RESPONSE = 1;
    public static int STATUS_ERROR_INVALID_NUMBER = 2;
    public static int STATUS_ERROR_UNKNOWN_ELEMENT = 3;
    public static int STATUS_ERROR_INVALID_LENGTH = 4;
    public static int STATUS_ERROR_INVALID_DATA = 5;

    public static int elementId(int group, int id) {
        return ((group & 0xff) << 8) | (id & 0xff);
    }

    public enum Type {
        string,
        uint8,
        uint16,
        uint32,
        uint64,
        int8,
        int16,
        int32,
        int64,
        float32,
        float64,
        array,
        address,
        gpio,
        custom;

        public boolean isText() {
            return this == string;
        }

        public boolean isInteger() {
            return this.compareTo(uint8) >= 0 && this.compareTo(int64) <= 0;
        }

        public boolean isArray() {
            return this == array;
        }

        public boolean isAddress() {
            return this == address;
        }

        public boolean isGpio() {
            return this == gpio;
        }

        public int getSize() {
            switch (this) {
                case uint8:
                case int8:
                    return 1;
                case uint16:
                case int16:
                case gpio:
                    return 2;
                case uint32:
                case int32:
                case float32:
                    return 4;
                case uint64:
                case int64:
                case float64:
                    return 8;
                case address:
                    return 6;
                default:
                    return -1;
            }
        }
    }

    public static class ElementSpec {
        public int id;
        public int groupId;
        public int groupElementId;
        public String groupName;
        public String name;
        public String description;
        public Type type;
        public int size;
        public long min = Long.MIN_VALUE;
        public long max = Long.MAX_VALUE;
        public List<String> enumNames;
        public List<Integer> enumValues;
        public String unit;

        public boolean isEnum() {
            return enumValues != null;
        }
    }

    public static HashMap<Integer, ElementSpec> elementSpecMap;
    private static HashMap<Integer, ElementSpec> defaultElementSpecMap;

    public static void loadConfigSpec(Context context) {
        ConfigXmlParser parser = new ConfigXmlParser();
        File xml;

        boolean storagePermission = Build.VERSION.SDK_INT < 23 || context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        if (!storagePermission)
            Log.d(TAG, "Storage permission not granted");

        if (elementSpecMap == null) {
            xml = new File(CONFIG_XML_PATH + "/" + CONFIG_XML);
            if (!storagePermission || !xml.exists()) {
                Log.d(TAG, "Loading default spec from asset");
                elementSpecMap = parser.loadConfig(context);
            } else {
                Log.d(TAG, "Loading default spec from file: " + xml.getAbsolutePath());
                elementSpecMap = parser.readConfigXml(xml);
            }
        }
        if (elementSpecMap == null) {
            Log.e(TAG, "Failed to load default spec");
            elementSpecMap = new HashMap<>();
        }

        xml = new File(CONFIG_XML_PATH + "/" + USER_CONFIG_XML);
        if (storagePermission && xml.exists()) {
            Log.d(TAG, "Found user spec extension: " + xml.getAbsolutePath());
            HashMap<Integer, ElementSpec> userSpec = parser.readConfigXml(xml);
            if (userSpec == null) {
                Log.e(TAG, "Failed to load user spec");
                return;
            }

            // Save/Restore default spec before applying user spec
            if (defaultElementSpecMap == null)
                defaultElementSpecMap = new HashMap<>(elementSpecMap);
            else
                elementSpecMap = new HashMap<>(defaultElementSpecMap);

            for (Integer id : userSpec.keySet()) {
                elementSpecMap.put(id, userSpec.get(id));
            }
        } else if (defaultElementSpecMap != null) {
            elementSpecMap = new HashMap<>(defaultElementSpecMap);
        }
    }

    public static ElementSpec getElementSpec(int id) {
        ElementSpec spec = null;
        if (elementSpecMap != null)
            spec = elementSpecMap.get(id);
        if (spec == null) {
            spec = new ElementSpec();
            spec.id = id;
            spec.groupId = (id >>> 8) & 0xff;
            spec.groupElementId = id & 0xff;
            spec.groupName = String.format("Group (%#04x)", spec.groupId);
            spec.name = String.format("Element (%#06x)", spec.id);
            spec.type = Type.array;
        }
        return spec;
    }
}
