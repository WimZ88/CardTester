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

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.diasemi.smartconfig.R;

import java.util.ArrayList;

/**
 * ScanItem adapter for the device list on the main activity
 */
public class ScanAdapter extends ArrayAdapter<ScanItem> {

    private Context context;
    private int layoutResourceId;
    private ArrayList<ScanItem> data;

    public ScanAdapter(Context context, int layoutResourceId, ArrayList<ScanItem> data) {
        super(context, layoutResourceId, data);
        this.layoutResourceId = layoutResourceId;
        this.context = context;
        this.data = data;
    }

    private static class Views {
        ImageView icon;
        TextView name;
        TextView address;
        TextView description;
        TextView rssi;
        SignalBar signalBar;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Create view
        Views views;
        if (convertView == null) {
            convertView = ((Activity) context).getLayoutInflater().inflate(layoutResourceId, parent, false);
            views = new Views();
            views.icon = convertView.findViewById(R.id.scanItemIcon);
            views.name = convertView.findViewById(R.id.scanItemName);
            views.address = convertView.findViewById(R.id.scanItemAddress);
            views.description = convertView.findViewById(R.id.scanItemDescription);
            views.rssi = convertView.findViewById(R.id.scanItemRssi);
            views.signalBar = convertView.findViewById(R.id.signalBar);
            convertView.setTag(views);
        } else {
            views = (Views) convertView.getTag();
        }

        // Update view
        ScanItem scanitem = data.get(position);
        views.icon.setImageResource(scanitem.icon != 0 ? scanitem.icon : R.drawable.icon_dialog);
        views.name.setText(scanitem.name != null ? scanitem.name : context.getString(R.string.not_available));
        views.address.setText(scanitem.address);
        views.description.setText(!scanitem.description.isEmpty() ? scanitem.description : context.getString(R.string.scan_unknown));
        views.rssi.setText(context.getString(R.string.scan_signal,scanitem.signal));
        views.signalBar.setRssi(scanitem.signal);
        return convertView;
    }
}
