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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.diasemi.smartconfig.R;

import org.greenrobot.eventbus.EventBus;

import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

public class ConfigUi {

    public static void initializeSettingsList(final ConfigurationManager manager, final LinearLayout settingsList, final LayoutInflater layoutInflater, final FragmentManager fragmentManager) {
        settingsList.removeAllViews();
        int count = 0;
        for (final ConfigElement element : manager.getElements()) {
            ElementUi ui = element.getUi();
            final View view = layoutInflater.inflate(ui.getViewLayout(), settingsList, false);
            view.setTag(element);
            view.setTag(R.id.element, element);
            view.setEnabled(false);
            ui.initializeView(manager, view, layoutInflater);
            ui.updateView(manager, view);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!view.isEnabled())
                        return;
                    SettingDialog dialog = new SettingDialog();
                    dialog.setElement(element);
                    fragmentManager.beginTransaction().add(dialog, "SettingDialog").commit();
                }
            });

            settingsList.addView(view);
            if (++count < manager.getElements().size())
                settingsList.addView(layoutInflater.inflate(R.layout.settings_separator, settingsList, false));
        }

    }

    public static abstract class ElementUi {

        public int getViewLayout() {
            return -1;
        }

        public int getDialogLayout() {
            return -1;
        }

        public void initializeView(ConfigurationManager manager, View view, LayoutInflater layoutInflater) {
        }

        public void updateView(ConfigurationManager manager, View view) {
        }

        public void initializeDialog(ConfigurationManager manager, Dialog dialog, View view, LayoutInflater layoutInflater) {
        }

        public void onDialogConfirm(ConfigurationManager manager, View view) {
        }
    }

    public static class SimpleElementUi extends ElementUi {

        private static class Views {
            TextView name;
            TextView value;
        }

        private static class DialogViews {
            TextView valueLabel;
            EditText value;
            RadioGroup valueList;
            View gpioView;
            Spinner gpioPort;
            Spinner gpioPin;
        }

        @Override
        public int getViewLayout() {
            return R.layout.setting_simple;
        }

        @Override
        public void initializeView(ConfigurationManager manager, View view, LayoutInflater layoutInflater) {
            Views views = new Views();
            views.name = view.findViewById(R.id.name);
            views.value = view.findViewById(R.id.value);
            view.setTag(R.id.views, views);
        }

        @Override
        public void updateView(ConfigurationManager manager, View view) {
            ConfigElement element = (ConfigElement) view.getTag(R.id.element);
            ConfigSpec.ElementSpec spec = element.getSpec();
            Views views = (Views) view.getTag(R.id.views);

            views.name.setText(spec.name);
            views.name.setEnabled(view.isEnabled());
            // Make name bold if modified
            if (views.name.getTag(R.id.typeface) == null)
                views.name.setTag(R.id.typeface, views.name.getTypeface());
            if (element.modified())
                views.name.setTypeface((Typeface) views.name.getTag(R.id.typeface), Typeface.BOLD);
            else
                views.name.setTypeface((Typeface) views.name.getTag(R.id.typeface));

            String value = element.getDisplayValue();
            if (value != null)
                views.value.setText(value);
            else
                views.value.setText(R.string.not_available);
        }

        @Override
        public int getDialogLayout() {
            return R.layout.setting_simple_dialog;
        }

        @Override
        public void initializeDialog(ConfigurationManager manager, Dialog dialog, View view, LayoutInflater layoutInflater) {
            ConfigElement element = (ConfigElement) view.getTag(R.id.element);
            ConfigSpec.ElementSpec spec = element.getSpec();

            DialogViews views = (DialogViews) view.getTag(R.id.views);
            if (views == null) {
                views = new DialogViews();
                views.valueLabel = view.findViewById(R.id.valueLabel);
                views.value = view.findViewById(R.id.value);
                views.valueList = view.findViewById(R.id.valueList);
                views.gpioView = view.findViewById(R.id.gpioView);
                views.gpioPort = view.findViewById(R.id.gpioPort);
                views.gpioPin = view.findViewById(R.id.gpioPin);
                view.setTag(R.id.views, views);
            }
            if (!spec.isEnum() && !spec.type.isGpio()) {
                views.value.setVisibility(View.VISIBLE);
                if (spec.type.isInteger()) {
                    views.value.setText(Long.toString(element.getIntValue()));
                    views.value.setInputType(InputType.TYPE_CLASS_NUMBER);
                    new NumberValidationInputFilter(manager, element, dialog, views.value);
                } else if (spec.type.isText()) {
                    views.value.setText(element.getStringValue());
                    new StringValidationInputFilter(manager, element, dialog, views.value);
                } else if (spec.type.isArray()) {
                    views.valueLabel.setText(R.string.dialog_array_value_label);
                    views.value.setText(element.getArrayValue());
                    views.value.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    new ArrayValidationInputFilter(manager, element, dialog, views.value);
                } else if (spec.type.isAddress()) {
                    views.valueLabel.setText(R.string.dialog_address_value_label);
                    views.value.setText(element.getAddressValue());
                    views.value.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    new AddressValidationInputFilter(manager, element, dialog, views.value);
                }
            } else if (spec.type.isGpio()) {
                views.gpioView.setVisibility(View.VISIBLE);
                views.valueLabel.setText(R.string.dialog_gpio_value_label);
                ArrayAdapter gpioPortAdapter = ArrayAdapter.createFromResource(manager.getContext(), R.array.gpio_ports, R.layout.gpio_spinner_item);
                gpioPortAdapter.setDropDownViewResource(R.layout.gpio_spinner_item);
                views.gpioPort.setAdapter(gpioPortAdapter);
                views.gpioPort.setSelection(element.getGpioPort() < manager.getContext().getResources().getStringArray(R.array.gpio_ports).length ? element.getGpioPort() : 0);
                ArrayAdapter gpioPinAdapter = ArrayAdapter.createFromResource(manager.getContext(), R.array.gpio_pins, R.layout.gpio_spinner_item);
                gpioPinAdapter.setDropDownViewResource(R.layout.gpio_spinner_item);
                views.gpioPin.setAdapter(gpioPinAdapter);
                views.gpioPin.setSelection(element.getGpioPin() < manager.getContext().getResources().getStringArray(R.array.gpio_pins).length ? element.getGpioPin() : 0);
            } else {
                views.valueList.setVisibility(View.VISIBLE);
                views.valueLabel.setText(R.string.dialog_value_list_label);
                for (int i = 0; i < spec.enumNames.size(); ++i) {
                    String name = spec.enumNames.get(i);
                    RadioButton radioButton = (RadioButton) layoutInflater.inflate(R.layout.setting_simple_enum_item, views.valueList, false);
                    radioButton.setText(element.valueWithUnit(name));
                    Integer value = spec.enumValues.get(i);
                    radioButton.setTag(R.id.value, value);
                    radioButton.setChecked(value == element.getIntValue());
                    radioButton.setId(i + 1);
                    views.valueList.addView(radioButton);
                }
            }
        }

        @Override
        public void onDialogConfirm(ConfigurationManager manager, View view) {
            ConfigElement element = (ConfigElement) view.getTag(R.id.element);
            ConfigSpec.ElementSpec spec = element.getSpec();
            DialogViews views = (DialogViews) view.getTag(R.id.views);

            if (!spec.isEnum() && !spec.type.isGpio()) {
                String value = views.value.getText().toString();
                if (spec.type.isInteger()) {
                    try {
                        long number = Long.parseLong(value);
                        if (element.validateValue(number)) {
                            element.setWriteValue(number);
                        } else {
                            Toast.makeText(manager.getContext(), R.string.invalid_value_message, Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(manager.getContext(), R.string.invalid_value_message, Toast.LENGTH_SHORT).show();
                    }
                } else if (spec.type.isText()) {
                    if (element.validateValue(value)) {
                        element.setWriteValue(value);
                    } else {
                        Toast.makeText(manager.getContext(), R.string.invalid_value_message, Toast.LENGTH_SHORT).show();
                    }
                } else if (spec.type.isArray()) {
                    byte[] array = ConfigUtil.hex2bytes(value);
                    if (element.validateValue(array)) {
                        element.setWriteData(array);
                    } else {
                        Toast.makeText(manager.getContext(), R.string.invalid_value_message, Toast.LENGTH_SHORT).show();
                    }
                } else if (spec.type.isAddress()) {
                    if (element.validateAddressValue(value)) {
                        element.setWriteData(ConfigUtil.reverse(ConfigUtil.hex2bytes(value)));
                    } else {
                        Toast.makeText(manager.getContext(), R.string.invalid_value_message, Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (spec.type.isGpio()) {
                int port = views.gpioPort.getSelectedItemPosition();
                int pin = views.gpioPin.getSelectedItemPosition();
                element.setWriteValue(port, pin);
            } else {
                int selected = views.valueList.getCheckedRadioButtonId();
                if (selected == -1)
                    return;
                selected--;
                Integer value = spec.enumValues.get(selected);
                element.setWriteValue(value);
            }
            EventBus.getDefault().post(new DialogConfirm(manager, element));
        }
    }

    public static final SimpleElementUi SIMPLE_ELEMENT_UI = new SimpleElementUi();

    public static ElementUi getElementUi(ConfigElement element) {
        switch (element.getSpec().type) {
            default:
                return SIMPLE_ELEMENT_UI;
        }
    }

    public static class DialogConfirm {
        public ConfigurationManager manager;
        public ConfigElement element;

        public DialogConfirm(ConfigurationManager manager, ConfigElement element) {
            this.manager = manager;
            this.element = element;
        }
    }

    public static class SettingDialog extends DialogFragment {

        private ConfigurationManager manager;
        private ConfigElement element;
        private AlertDialog dialog;

        public ConfigElement getElement() {
            return element;
        }

        public void setElement(ConfigElement element) {
            this.element = element;
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            outState.putInt(getClass().getName() + "#element", element.getId());
            super.onSaveInstanceState(outState);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            super.onCreateDialog(savedInstanceState);
            manager = ((ConfigurationManager.Holder) getActivity()).getConfigurationManager();
            if (savedInstanceState != null) {
                element = manager.getElement(savedInstanceState.getInt(getClass().getName() + "#element"));
            }
            final ConfigSpec.ElementSpec spec = element.getSpec();
            final ElementUi ui = element.getUi();

            LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            final View view = layoutInflater.inflate(ui.getDialogLayout(), null);
            view.setTag(R.id.element, element);

            dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(spec.name)
                    .setView(view)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ui.onDialogConfirm(manager, view);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setCancelable(true)
                    .create();
            ui.initializeDialog(manager, dialog, view, layoutInflater);
            return dialog;
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
        }
    }

    public static abstract class ConfigInputFilter implements InputFilter {

        protected ConfigurationManager manager;
        protected ConfigElement element;
        protected Dialog dialog;
        protected EditText editText;
        protected ColorStateList colors;
        protected int errorColor;

        public ConfigInputFilter(ConfigurationManager manager, ConfigElement element, Dialog dialog, EditText editText) {
            this.manager = manager;
            this.element = element;
            this.dialog = dialog;
            this.editText = editText;
            colors = editText.getTextColors();
            errorColor = Build.VERSION.SDK_INT >= 23 ? manager.getContext().getColor(R.color.invalid_input) : manager.getContext().getResources().getColor(R.color.invalid_input);
            InputFilter[] previous = editText.getFilters();
            InputFilter[] filters = Arrays.copyOf(previous, previous.length + 1);
            filters[previous.length] = this;
            editText.setFilters(filters);
        }

        protected void updateUi(boolean valid) {
            if (valid) {
                editText.setTextColor(colors);
                Button button = dialog instanceof AlertDialog ? ((AlertDialog)dialog).getButton(DialogInterface.BUTTON_POSITIVE) : null;
                if (button != null)
                    button.setEnabled(true);
            } else {
                editText.setTextColor(errorColor);
                Button button = dialog instanceof AlertDialog ? ((AlertDialog)dialog).getButton(DialogInterface.BUTTON_POSITIVE) : null;
                if (button != null)
                    button.setEnabled(false);
            }
        }

        protected String getText(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            return dest.subSequence(0, dstart).toString() + source.subSequence(start, end).toString() + dest.subSequence(dend, dest.length()).toString();
        }
    }

    public static class StringValidationInputFilter extends ConfigInputFilter {

        public StringValidationInputFilter(ConfigurationManager manager, ConfigElement element, Dialog dialog, EditText editText) {
            super(manager, element, dialog, editText);
            check(editText.getText().toString());
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            check(getText(source, start, end, dest, dstart, dend));
            return null;
        }

        private void check(String input) {
            updateUi(element.validateValue(input));
        }
    }

    public static class NumberValidationInputFilter extends ConfigInputFilter {

        public NumberValidationInputFilter(ConfigurationManager manager, ConfigElement element, Dialog dialog, EditText editText) {
            super(manager, element, dialog, editText);
            check(Long.parseLong(editText.getText().toString()));
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            try {
                String text = getText(source, start, end, dest, dstart, dend);
                if (text.isEmpty())
                    updateUi(false);
                else
                    check(Long.parseLong(text));
                return null;
            } catch (NumberFormatException nfe) {
                return "";
            }
        }

        private void check(long input) {
            updateUi(element.validateValue(input));
        }
    }

    public static class ArrayValidationInputFilter extends ConfigInputFilter {

        public ArrayValidationInputFilter(ConfigurationManager manager, ConfigElement element, Dialog dialog, EditText editText) {
            super(manager, element, dialog, editText);
            check(ConfigUtil.hex2bytes(editText.getText().toString()));
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            String text = getText(source, start, end, dest, dstart, dend);
            text = text.replace("0x", "");
            if (text.matches(".*[^a-fA-F0-9 \\[\\]].*"))
                return "";
            check(ConfigUtil.hex2bytes(text));
            return null;
        }

        private void check(byte[] input) {
            updateUi(element.validateValue(input));
        }
    }

    public static class AddressValidationInputFilter extends ConfigInputFilter {

        public AddressValidationInputFilter(ConfigurationManager manager, ConfigElement element, Dialog dialog, EditText editText) {
            super(manager, element, dialog, editText);
            check(editText.getText().toString());
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            String text = getText(source, start, end, dest, dstart, dend);
            if (text.matches(".*[^a-fA-F0-9: ].*"))
                return "";
            check(text.toUpperCase().replace(" ", ":"));
            return source.subSequence(start, end).toString().toUpperCase().replace(" ", ":");
        }

        private void check(String input) {
            updateUi(element.validateAddressValue(input));
        }
    }
}
