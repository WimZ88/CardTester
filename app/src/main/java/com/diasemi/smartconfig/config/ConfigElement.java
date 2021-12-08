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

import android.bluetooth.BluetoothAdapter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import androidx.annotation.NonNull;

public class ConfigElement {

    private int id;
    private byte[] data = new byte[0];
    private byte[] writeData = new byte[0];
    private ConfigSpec.ElementSpec spec;
    private ConfigUi.ElementUi ui;

    public ConfigElement(int id) {
        this.id = id;
        spec = ConfigSpec.getElementSpec(id);
        ui = ConfigUi.getElementUi(this);
    }

    public ConfigElement(int id, byte[] data) {
        this(id);
        this.data = data;
        this.writeData = data;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setId(int group, int id) {
        this.id = ConfigSpec.elementId(group, id);
    }

    public int getGroupId() {
        return (id >> 8) & 0xff;
    }

    public int getGroupElementId() {
        return id & 0xff;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] getWriteData() {
        return writeData;
    }

    public void setWriteData(byte[] writeData) {
        this.writeData = writeData;
    }

    public void resetWriteData() {
        this.writeData = data;
    }

    public boolean modified() {
        return !Arrays.equals(data, writeData);
    }

    public byte[] getWriteConfigData() {
        return ByteBuffer.allocate(3 + writeData.length).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) id)
                .put((byte) writeData.length)
                .put(writeData)
                .array();
    }

    public byte[] getWriteConfigData(byte[] writeData) {
        return ByteBuffer.allocate(3 + writeData.length).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) id)
                .put((byte) writeData.length)
                .put(writeData)
                .array();
    }

    public ConfigSpec.ElementSpec getSpec() {
        return spec;
    }

    public ConfigUi.ElementUi getUi() {
        return ui;
    }

    public long getIntValue() {
        byte[] data = modified() ? writeData : this.data;
        if (data.length < spec.size)
            return 0;
        switch (spec.type) {
            case uint8:
                return data[0] & 0xff;
            case uint16:
                return (data[0] & 0xff) | ((data[1] & 0xff) << 8);
            case uint32:
                return (data[0] & 0xff) | ((data[1] & 0xff) << 8) | ((data[2] & 0xff) << 16) | ((data[3] & 0xff) << 24);
            case uint64:
                return (data[0] & 0xff) | ((data[1] & 0xff) << 8) | ((data[2] & 0xff) << 16) | ((data[3] & 0xff) << 24) |
                        ((long)(data[4] & 0xff) << 32) | ((long)(data[5] & 0xff) << 40) | ((long)(data[6] & 0xff) << 48) | ((long)(data[7] & 0xff) << 56);
            case int8:
                return data[0];
            case int16:
                return (data[0] & 0xff) | (data[1] << 8);
            case int32:
                return (data[0] & 0xff) | ((data[1] & 0xff) << 8) | ((data[2] & 0xff) << 16) | (data[3] << 24);
            case int64:
                return (data[0] & 0xff) | ((data[1] & 0xff) << 8) | ((data[2] & 0xff) << 16) | ((data[3] & 0xff) << 24) |
                        ((long)(data[4] & 0xff) << 32) | ((long)(data[5] & 0xff) << 40) | ((long)(data[6] & 0xff) << 48) | ((long)data[7] << 56);
            default:
                return 0;
        }
    }

    public void setValue(long v) {
        data = numberBytes(v);
    }

    public void setWriteValue(long v) {
        writeData = numberBytes(v);
    }

    public byte[] numberBytes(long v) {
        switch (spec.type) {
            case uint8:
            case int8:
                return new byte[] { (byte) v };
            case uint16:
            case int16:
                return new byte[] { (byte) v, (byte)(v >>> 8) };
            case uint32:
            case int32:
                return new byte[] { (byte) v, (byte)(v >>> 8), (byte)(v >>> 16), (byte)(v >>> 24) };
            case uint64:
            case int64:
                return new byte[] { (byte) v, (byte)(v >>> 8), (byte)(v >>> 16), (byte)(v >>> 24), (byte)(v >>> 32), (byte)(v >>> 40), (byte)(v >>> 48) };
            default:
                return new byte[0];
        }
    }

    public String getStringValue() {
        return new String(modified() ? writeData : data, StandardCharsets.UTF_8);
    }

    public void setValue(String v) {
        data = v.getBytes(StandardCharsets.UTF_8);
    }

    public void setWriteValue(String v) {
        writeData = v.getBytes(StandardCharsets.UTF_8);
    }

    public String getArrayValue() {
        return ConfigUtil.hexArray(modified() ? writeData : data);
    }

    public String getAddressValue() {
        return ConfigUtil.hexArray(ConfigUtil.reverse(modified() ? writeData : data)).replace(" ", ":");
    }

    public String getGpioValue() {
        byte[] data = modified() ? writeData : this.data;
        return data.length >= 2 ? String.format(Locale.US, "Port %d, Pin %d", data[1] & 0xff, data[0] & 0xff) : "N/A";
    }

    public int getGpioPort() {
        byte[] data = modified() ? writeData : this.data;
        return data.length >= 2 ? data[1] & 0xff : 0;
    }

    public int getGpioPin() {
        byte[] data = modified() ? writeData : this.data;
        return data.length > 0 ? data[0] & 0xff : 0;
    }

    public void setValue(int port, int pin) {
        data = new byte[] { (byte) pin, (byte) port };
    }

    public void setWriteValue(int port, int pin) {
        writeData = new byte[] { (byte) pin, (byte) port };
    }

    public String getDisplayValue() {
        if (!modified() ? data.length == 0 : writeData.length == 0)
            return null;
        if (!spec.isEnum()) {
            if (spec.type.isInteger()) {
                return valueWithUnit(Long.toString(getIntValue()));
            } else if (spec.type.isText()) {
                return getStringValue();
            } else if (spec.type.isArray()) {
                return getArrayValue();
            } else if (spec.type.isAddress()) {
                return getAddressValue();
            } else if (spec.type.isGpio()) {
                return getGpioValue();
            }
        } else {
            long value = getIntValue();
            for (int i = 0; i < spec.enumValues.size(); ++i) {
                if (spec.enumValues.get(i) == value) {
                    return valueWithUnit(spec.enumNames.get(i));
                }
            }
        }
        return null;
    }

    public String valueWithUnit(String value) {
        return spec.unit == null ? value : String.format("%s %s", value, spec.unit);
    }

    public boolean validateValue(long value) {
        return spec.type.isInteger() && value >= spec.min && value <= spec.max;
    }

    public boolean validateValue(String value) {
        return spec.type.isText() && (spec.size == 0 || value.getBytes(StandardCharsets.UTF_8).length <= spec.size);
    }

    public boolean validateValue(byte[] value) {
        return spec.type.isArray() && value != null && (spec.size == 0 || value.length <= spec.size);
    }

    public boolean validateAddressValue(String address) {
        return spec.type.isAddress() && BluetoothAdapter.checkBluetoothAddress(address);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("%04x", id);
    }
}
