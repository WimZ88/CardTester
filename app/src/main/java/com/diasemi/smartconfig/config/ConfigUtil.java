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

import java.util.HashMap;

public class ConfigUtil {

    private static final String HEX_DIGITS_LC = "0123456789abcdef";
    private static final String HEX_DIGITS_UC = "0123456789ABCDEF";
    private static HashMap<Character, Integer> HEX_DIGITS_MAP = new HashMap<>(32);
    static {
        for (int i = 0; i < 16; ++i) {
            HEX_DIGITS_MAP.put(HEX_DIGITS_LC.charAt(i), i);
            HEX_DIGITS_MAP.put(HEX_DIGITS_UC.charAt(i), i);
        }
    }

    public static String hex(byte[] v, boolean uppercase) {
        if (v == null)
            return "<null>";
        String hexDigits = uppercase ? HEX_DIGITS_UC : HEX_DIGITS_LC;
        StringBuilder buffer = new StringBuilder(v.length * 2);
        for (byte b : v) {
            buffer.append(hexDigits.charAt((b >> 4) & 0x0f)).append(hexDigits.charAt(b & 0x0f));
        }
        return buffer.toString();
    }

    public static String hex(byte[] v) {
        return hex(v, true);
    }

    public static String hexArray(byte[] v, boolean uppercase, boolean brackets) {
        if (v == null)
            return "[]";
        String hexDigits = uppercase ? HEX_DIGITS_UC : HEX_DIGITS_LC;
        StringBuilder buffer = new StringBuilder(v.length * 3 + 3);
        if (brackets)
            buffer.append("[ ");
        for (byte b : v) {
            buffer.append(hexDigits.charAt((b >> 4) & 0x0f)).append(hexDigits.charAt(b & 0x0f)).append(" ");
        }
        if (brackets)
            buffer.append("]");
        else if (buffer.length() > 0)
            buffer.setLength(buffer.length() - 1);
        return buffer.toString();
    }

    public static String hexArray(byte[] v) {
        return hexArray(v, true, false);
    }

    public static String hexArrayLog(byte[] v) {
        return hexArray(v, false, true);
    }

    public static byte[] hex2bytes(String s) {
        s = s.replace("0x", "");
        s = s.replaceAll("[^a-fA-F0-9]", "");
        if (s.length() % 2 != 0)
            return null;
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < s.length(); ++i) {
            Integer d = HEX_DIGITS_MAP.get(s.charAt(i));
            if (d == null)
                return null;
            b[i / 2] |= i % 2 == 0 ? d << 4 : d;
        }
        return b;
    }

    public static byte[] reverse(byte[] v) {
        if (v == null)
            return null;
        byte[] r = new byte[v.length];
        for (int i = 0; i < v.length; ++i) {
            r[i] = v[v.length - i - 1];
        }
        return r;
    }
}
