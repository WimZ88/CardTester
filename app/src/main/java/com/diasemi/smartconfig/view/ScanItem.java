/*
 *******************************************************************************
 *
 * Copyright (C) 2020 Dialog Semiconductor.
 * This computer program includes Confidential, Proprietary Information
 * of Dialog Semiconductor. All Rights Reserved.
 *
 *******************************************************************************
 */

package com.diasemi.smartconfig.view;

/**
 * ScanItem object
 */
public class ScanItem {
    public int icon;
    public String name;
    public String address;
    public String description;
    public int signal;

    public ScanItem(int icon, String name, String address, String description, int signal) {
        this.icon = icon;
        this.name = name;
        this.address = address;
        this.description = description;
        this.signal = signal;
    }
}
