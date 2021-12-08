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

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.diasemi.smartconfig.R;

import org.greenrobot.eventbus.EventBus;

import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class ScanFilterActivity extends AppCompatActivity {
    private static final String TAG = "ScanFilterActivity";

    public static final String PREFERENCES_NAME = "scan-filter-preferences";

    public static class ScanFilterChanged {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_filter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.status_bar_background));
            getWindow().setNavigationBarColor(getResources().getColor(R.color.navigation_bar_background));
        }
        ColorDrawable colorDrawable = new ColorDrawable(Color.parseColor("#347ab8"));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setBackgroundDrawable(colorDrawable);
            actionBar.setTitle(R.string.app_name);
            actionBar.setSubtitle(R.string.activity_scan_filter);
        }

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, new ScanFilterFragment());
        fragmentTransaction.commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class ScanFilterFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        private PreferenceManager preferenceManager;
        private SharedPreferences preferences;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            preferences.registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onDestroy() {
            preferences.unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            preferenceManager = getPreferenceManager();
            preferenceManager.setSharedPreferencesName(PREFERENCES_NAME);
            preferences = preferenceManager.getSharedPreferences();
            setPreferencesFromResource(R.xml.scan_filter_preferences, rootKey);

            final EditTextPreference rssi = preferenceManager.findPreference("rssiLevel");
            rssi.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                }
            });
            rssi.setSummaryProvider(new Preference.SummaryProvider() {
                @Override
                public CharSequence provideSummary(Preference preference) {
                    return getString(R.string.pref_rssi_level_summary, rssi.getText());
                }
            });

            preferenceManager.findPreference("name").setOnPreferenceChangeListener(patternCheck);
            preferenceManager.findPreference("address").setOnPreferenceChangeListener(patternCheck);
            preferenceManager.findPreference("advData").setOnPreferenceChangeListener(patternCheck);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.d(TAG, "onSharedPreferenceChanged: " + key);
            EventBus.getDefault().post(new ScanFilterChanged());
        }

        private Preference.OnPreferenceChangeListener patternCheck = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                try {
                    String pattern = (String) newValue;
                    Pattern.compile(pattern);
                    return true;
                } catch (Exception e) {
                    Toast.makeText(getContext(), R.string.invalid_pattern_message, Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        };
    }
}
