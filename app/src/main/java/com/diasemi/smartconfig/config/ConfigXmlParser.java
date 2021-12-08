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

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class ConfigXmlParser {
    private static final String TAG = "ConfigXmlParser";

    private static final String NS = null;
    private static final String TAG_CONFIGURATION = "config-spec";
    private static final String TAG_GROUPS = "parameterGroups";
    private static final String TAG_GROUP = "group";
    private static final String TAG_ELEMENT = "element";
    private static final String TAG_NAME = "name";
    private static final String TAG_DESCRIPTION = "description";
    private static final String TAG_TYPE = "type";
    private static final String TAG_UNIT = "units";
    private static final String TAG_SIZE = "max_size";
    private static final String TAG_MIN = "min";
    private static final String TAG_MAX = "max";
    private static final String TAG_ENUMS = "enumTypes";
    private static final String TAG_ENUM = "enum";
    private static final String TAG_ITEM = "item";
    private static final String ATTR_ID = "id";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";
    private static final String ATTR_TYPE = "type";
    private static final String ATTR_SIZE = "size";

    private static class EnumType {
        String type;
        int size;
        ArrayList<Integer> values = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
    }

    public ConfigXmlParser() {
    }

    public HashMap<Integer, ConfigSpec.ElementSpec> loadConfig(Context context) {
        try {
            InputStream inputStream = context.getAssets().open("config-spec.xml");
            return parse(new BufferedInputStream(inputStream));
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Could not load XML asset", e);
        }
        return null;
    }

    public HashMap<Integer, ConfigSpec.ElementSpec> readConfigXml(File file) {
        try {
            return parse(new BufferedInputStream(new FileInputStream(file)));
        } catch (FileNotFoundException | SecurityException e) {
            Log.e(TAG, "Could not open XML file", e);
        }
        return null;
    }

    private HashMap<Integer, ConfigSpec.ElementSpec> parse(InputStream xml) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(xml, null);
            parser.nextTag();
            return readConfiguration(parser);
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "XML parsing error", e);
        } finally {
            try {
                xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private HashMap<Integer, ConfigSpec.ElementSpec> readConfiguration(XmlPullParser parser) throws IOException, XmlPullParserException {
        HashMap<Integer, ConfigSpec.ElementSpec> specMap = new HashMap<>();
        HashMap<String, EnumType> enums = new HashMap<>();
        HashMap<Integer, String> customTypes = new HashMap<>();

        parser.require(XmlPullParser.START_TAG, NS, TAG_CONFIGURATION);
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;
            switch (parser.getName()) {
                case TAG_GROUPS:
                    readGroups(parser, specMap, customTypes);
                    break;
                case TAG_ENUMS:
                    readEnums(parser, enums);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }

        for (Integer id : customTypes.keySet()) {
            String name = customTypes.get(id);
            EnumType enumType = enums.get(name);
            ConfigSpec.ElementSpec spec = specMap.get(id);
            if (enumType == null) {
                Log.e(TAG, "Invalid custom type: " + name);
                spec.type = ConfigSpec.Type.array;
                continue;
            }
            if (enumType.size != 0)
                spec.size = enumType.size;
            switch (spec.size) {
                case 2:
                    spec.type = ConfigSpec.Type.uint16;
                    break;
                case 4:
                    spec.type = ConfigSpec.Type.uint32;
                    break;
                default:
                    spec.type = ConfigSpec.Type.uint8;
                    break;
            }
            spec.enumNames = enumType.names;
            spec.enumValues = enumType.values;
        }

        return specMap;
    }

    private void readGroups(XmlPullParser parser, HashMap<Integer, ConfigSpec.ElementSpec> specMap, HashMap<Integer, String> customTypes) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, NS, TAG_GROUPS);
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;
            switch (parser.getName()) {
                case TAG_GROUP:
                    readGroup(parser, specMap, customTypes);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        parser.require(XmlPullParser.END_TAG, NS, TAG_GROUPS);
    }

    private void readGroup(XmlPullParser parser, HashMap<Integer, ConfigSpec.ElementSpec> specMap, HashMap<Integer, String> customTypes) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, NS, TAG_GROUP);
        String value;

        int groupId = -1;
        value = parser.getAttributeValue(NS, ATTR_ID);
        if (!TextUtils.isEmpty(value))
            groupId = decodeNumber(value);
        if (groupId < 0 || groupId > 255)
            throw new XmlPullParserException("Invalid group ID: " + groupId);

        value = parser.getAttributeValue(NS, ATTR_NAME);
        String groupName = !TextUtils.isEmpty(value) ? value : "N/A";

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;
            switch (parser.getName()) {
                case TAG_ELEMENT:
                    readElement(parser, specMap, groupId, groupName, customTypes);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        parser.require(XmlPullParser.END_TAG, NS, TAG_GROUP);
    }

    private void readElement(XmlPullParser parser, HashMap<Integer, ConfigSpec.ElementSpec> specMap, int groupId, String groupName, HashMap<Integer, String> customTypes) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, NS, TAG_ELEMENT);
        String value;

        int id = -1;
        value = parser.getAttributeValue(NS, ATTR_ID);
        if (!TextUtils.isEmpty(value))
            id = decodeNumber(value);
        if (id < 0 || id > 255)
            throw new XmlPullParserException("Invalid element ID: " + id);

        ConfigSpec.ElementSpec spec = new ConfigSpec.ElementSpec();
        spec.groupId = groupId;
        spec.groupName = groupName;
        spec.groupElementId = id;
        spec.id = ConfigSpec.elementId(groupId, id);

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;
            switch (parser.getName()) {
                case TAG_NAME:
                    spec.name = readText(parser, TAG_NAME);
                    if (TextUtils.isEmpty(spec.name))
                        throw new XmlPullParserException("Empty element name");
                    break;
                case TAG_DESCRIPTION:
                    value = readText(parser, TAG_DESCRIPTION);
                    if (!TextUtils.isEmpty(value))
                        spec.description = value;
                    break;
                case TAG_TYPE:
                    value = readText(parser, TAG_TYPE);
                    if (TextUtils.isEmpty(value))
                        throw new XmlPullParserException("Empty element type");
                    spec.type = decodeType(value);
                    if (spec.type == ConfigSpec.Type.custom)
                        customTypes.put(spec.id, value);
                    break;
                case TAG_UNIT:
                    value = readText(parser, TAG_UNIT);
                    if (!TextUtils.isEmpty(value))
                        spec.unit = value;
                    break;
                case TAG_SIZE:
                    spec.size = decodeNumber(readText(parser, TAG_SIZE));
                    break;
                case TAG_MIN:
                    spec.min = decodeNumber(readText(parser, TAG_MIN));
                    break;
                case TAG_MAX:
                    spec.max = decodeNumber(readText(parser, TAG_MAX));
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        parser.require(XmlPullParser.END_TAG, NS, TAG_ELEMENT);

        if (spec.name == null)
            throw new XmlPullParserException("No element name");
        if (spec.type == null)
            throw new XmlPullParserException("No element type");

        specMap.put(spec.id, spec);
    }

    private void readEnums(XmlPullParser parser, HashMap<String, EnumType> enums) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, NS, TAG_ENUMS);
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;
            switch (parser.getName()) {
                case TAG_ENUM:
                    readEnum(parser, enums);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        parser.require(XmlPullParser.END_TAG, NS, TAG_ENUMS);
    }

    private void readEnum(XmlPullParser parser, HashMap<String, EnumType> enums) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, NS, TAG_ENUM);
        String value;

        EnumType enumType = new EnumType();
        value = parser.getAttributeValue(NS, ATTR_TYPE);
        if (!TextUtils.isEmpty(value))
            enumType.type = value;
        else
            throw new XmlPullParserException("Empty enum type");

        value = parser.getAttributeValue(NS, ATTR_SIZE);
        if (!TextUtils.isEmpty(value))
            enumType.size = decodeNumber(value);

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;
            switch (parser.getName()) {
                case TAG_ITEM:
                    readEnumItem(parser, enumType);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        parser.require(XmlPullParser.END_TAG, NS, TAG_ENUM);

        if (enumType.values.isEmpty())
            throw new XmlPullParserException("No items in enum: " + enumType.type);

        enums.put(enumType.type, enumType);
    }

    private void readEnumItem(XmlPullParser parser, EnumType enumType) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, NS, TAG_ITEM);
        String value;

        int enumValue = 0;
        value = parser.getAttributeValue(NS, ATTR_VALUE);
        if (!TextUtils.isEmpty(value))
            enumValue = decodeNumber(value);

        String enumName = readText(parser, TAG_ITEM);
        if (TextUtils.isEmpty(enumName))
            throw new XmlPullParserException("Empty enum item name");

        enumType.names.add(enumName);
        enumType.values.add(enumValue);
    }

    private String readText(XmlPullParser parser, String tag) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, NS, tag);
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        parser.require(XmlPullParser.END_TAG, NS, tag);
        return result;
    }

    private int decodeNumber(String value) throws XmlPullParserException {
        try {
            return Integer.decode(value);
        } catch (NumberFormatException e) {
            throw new XmlPullParserException("Invalid number");
        }
    }

    private ConfigSpec.Type decodeType(String value) {
        switch (value) {
            case "string":
                return ConfigSpec.Type.string;
            case "uint8_t":
                return ConfigSpec.Type.uint8;
            case "uint16_t":
                return ConfigSpec.Type.uint16;
            case "uint32_t":
                return ConfigSpec.Type.uint32;
            case "uint64_t":
                return ConfigSpec.Type.uint64;
            case "int8_t":
                return ConfigSpec.Type.int8;
            case "int16_t":
                return ConfigSpec.Type.int16;
            case "int32_t":
                return ConfigSpec.Type.int32;
            case "int64_t":
                return ConfigSpec.Type.int64;
            case "array":
                return ConfigSpec.Type.array;
            case "bd_address_t":
                return ConfigSpec.Type.address;
            case "gpio_pin_t":
                return ConfigSpec.Type.gpio;
            default:
                return ConfigSpec.Type.custom;
        }
    }

    private void skip(XmlPullParser parser) throws IOException, XmlPullParserException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }
}
