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
import java.util.List;
import java.util.UUID;

public class ConfigEvent {

    private static class Event {
        public ConfigurationManager manager;

        public Event(ConfigurationManager manager) {
            this.manager = manager;
        }
    }

    public static class Connection extends Event {
        public Connection(ConfigurationManager manager) {
            super(manager);
        }
    }

    public static class ServiceDiscovery extends Event {
        public boolean complete;

        public ServiceDiscovery(ConfigurationManager manager, boolean complete) {
            super(manager);
            this.complete = complete;
        }
    }

    public static class ElementDiscovery extends Event {
        public boolean complete;

        public ElementDiscovery(ConfigurationManager manager, boolean complete) {
            super(manager);
            this.complete = complete;
        }
    }

    public static class NotSupported extends Event {
        public NotSupported(ConfigurationManager manager) {
            super(manager);
        }
    }

    public static class Version extends Event {
        public Version(ConfigurationManager manager) {
            super(manager);
        }
    }

    public static class Ready extends Event {
        public Ready(ConfigurationManager manager) {
            super(manager);
        }
    }

    public static class Read extends Event {
        public ConfigElement element;
        public int status;

        public Read(ConfigurationManager manager, ConfigElement element, int status) {
            super(manager);
            this.element = element;
            this.status = status;
        }

        public boolean failed() {
            return status != ConfigSpec.STATUS_SUCCESS;
        }
    }

    public static class Write extends Event {
        public List<ConfigElement> elements;
        public HashMap<ConfigElement, byte[]> data;
        public int status;

        public Write(ConfigurationManager manager, List<ConfigElement> elements, HashMap<ConfigElement, byte[]> data, int status) {
            super(manager);
            this.elements = elements;
            this.data = data;
            this.status = status;
        }

        public boolean failed() {
            return status != ConfigSpec.STATUS_SUCCESS;
        }
    }

    // Errors
    public static final int ERROR_NOT_READY = 0;
    public static final int ERROR_INIT_CONFIGURATION_SERVICE = 1;
    public static final int ERROR_GATT_OPERATION = 2;
    public static final int ERROR_INIT_READ = 3;
    public static final int ERROR_INIT_WRITE = 4;

    public static class Error extends Event {
        public int error;

        public Error(ConfigurationManager manager, int error) {
            super(manager);
            this.error = error;
        }
    }

    public static class DeviceInfo extends Event {
        public UUID uuid;
        public byte[] value;
        public String info;

        public DeviceInfo(ConfigurationManager manager, UUID uuid, byte[] value, String info) {
            super(manager);
            this.uuid = uuid;
            this.value = value;
            this.info = info;
        }
    }
}
