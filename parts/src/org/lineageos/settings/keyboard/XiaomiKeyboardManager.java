/*
 * Copyright (C) 2022 Project Kaleidoscope
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.lineageos.settings.keyboard;

import android.content.Context;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class XiaomiKeyboardManager {

    private static final String TAG = "XiaomiKeyboardManager";

    public static final int KEYBOARD_STATE_CONNECTED = 1;
    public static final int KEYBOARD_STATE_DISCONNECTED = 0;

    private UsbDeviceConnection mUsbConnection;
    private UsbDevice mUsbDevice;
    private UsbInterface mUsbInterface;
    private UsbInterface mReportInterface;
    private UsbEndpoint mInUsbEndpoint;
    private UsbEndpoint mOutUsbEndpoint;
    private UsbEndpoint mReportInUsbEndpoint;

    private KeyboardHandler mHandler;
    private HandlerThread mHandlerThread;

    private int mKeyboardState = 0;

    private final Object mUsbDeviceLock = new Object();
    private final byte[] mSendBuf = new byte[64];
    private final byte[] mRecBuf = new byte[64];
    private final UsbManager mUsbManager;
    private final ArrayList<KeyboardStateListener> mKeyboardStateListeners = new ArrayList<>();

    private final FileObserver mFileObserver = new FileObserver(XiaomiKeyboardUtils.KEYBOARD_FILE) {
        @Override
        public void onEvent(int event, @Nullable String path) {
            mHandler.sendEmptyMessage(KeyboardHandler.MSG_GET_REPORT_DATA);
        }
    };

    public XiaomiKeyboardManager(Context context) {
        mUsbManager = context.getSystemService(UsbManager.class);
    }

    public void start() {
        if (mHandlerThread != null && mHandlerThread.isAlive()) return;
        mHandlerThread = new HandlerThread("keyboard_handler");
        mHandlerThread.start();
        mHandler = new KeyboardHandler(mHandlerThread.getLooper());
        mHandler.sendEmptyMessage(KeyboardHandler.MSG_READ_CONNECT_STATE);
        mFileObserver.startWatching();
    }

    public void stop() {
        if (mHandlerThread != null && mHandlerThread.isAlive()) {
            mHandlerThread.quitSafely();
        }
        mFileObserver.stopWatching();
    }

    private boolean getTransferEndpoint(UsbDevice device) {
        if (device == null) {
            return false;
        }
        UsbConfiguration configuration = device.getConfiguration(0);
        for (int i = 0; i < configuration.getInterfaceCount(); i++) {
            UsbInterface usbInterface = configuration.getInterface(i);
            if (usbInterface != null && usbInterface.getEndpointCount() >= 2) {
                for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
                    UsbEndpoint endpoint = usbInterface.getEndpoint(j);
                    if (endpoint != null) {
                        int direction = endpoint.getDirection();
                        if (direction == 128) {
                            mInUsbEndpoint = endpoint;
                        } else if (direction == 0) {
                            mOutUsbEndpoint = endpoint;
                        }
                    }
                }
                if (mInUsbEndpoint != null && mOutUsbEndpoint != null) {
                    mUsbInterface = usbInterface;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean getDeviceReadyForTransfer() {
        synchronized (mUsbDeviceLock) {
            if (mUsbDevice != null && mUsbConnection != null && mUsbInterface != null &&
                    mOutUsbEndpoint != null && mInUsbEndpoint != null) {
                mUsbConnection.claimInterface(mUsbInterface, true);
                return true;
            } else if (mUsbDevice == null && !getUsbDevice()) {
                return false;
            } else {
                if (!getTransferEndpoint(mUsbDevice)) {
                    Log.i(TAG, "get transfer endpoint failed");
                    return false;
                }
                if (mUsbConnection == null) {
                    mUsbConnection = mUsbManager.openDevice(mUsbDevice);
                }
                if (mUsbConnection != null && mOutUsbEndpoint != null && mInUsbEndpoint != null) {
                    mUsbConnection.claimInterface(mUsbInterface, true);
                    return true;
                }
                Log.i(TAG, "get usb transfer connection failed");
                return false;
            }
        }
    }

    private boolean getUsbDevice() {
        synchronized (mUsbDeviceLock) {
            HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
            for (UsbDevice device : deviceList.values()) {
                if (mUsbManager.hasPermission(device) &&
                        XiaomiKeyboardUtils.deviceIsXiaomiKeyboard(
                                device.getVendorId(), device.getProductId())) {
                    Log.i(TAG, "getUsbDevice: " + device.getDeviceName());
                    mUsbDevice = device;
                    break;
                }
            }
        }
        if (mUsbDevice == null) {
            Log.i(TAG, "get usb device failed");
            if (!mHandler.hasMessages(12)) {
                mHandler.sendEmptyMessageDelayed(KeyboardHandler.MSG_GET_DEVICE_TIME_OUT, 20000);
            }
        } else {
            mHandler.removeMessages(12);
        }
        return mUsbDevice != null;
    }

    private boolean sendUsbData(UsbDeviceConnection connection, UsbEndpoint endpoint, byte[] data) {
        return connection != null && endpoint != null && data != null
                && connection.bulkTransfer(endpoint, data, data.length, 500) != -1;
    }

    private void doCheckConnectionState() {
        if (!getDeviceReadyForTransfer()) {
            return;
        }
        Arrays.fill(mSendBuf, (byte) 0);
        byte[] command = XiaomiKeyboardUtils.commandGetConnectState();
        System.arraycopy(command, 0, mSendBuf, 0, command.length);
        for (int i = 0; i < 2; i++) {
            if (sendUsbData(mUsbConnection, mOutUsbEndpoint, mSendBuf)) {
                Arrays.fill(mRecBuf, (byte) 0);
                while (sendUsbData(mUsbConnection, mInUsbEndpoint, mRecBuf)) {
                    if (parseConnectState(mRecBuf)) {
                        return;
                    }
                }
            } else {
                Log.i(TAG, "send connect failed");
            }
        }
    }

    private boolean parseConnectState(byte[] recBuf) {
        if (recBuf[4] != -94) {
            Log.i(TAG, "receive connect state error:" + String.format("%02x", recBuf[4]));
            return false;
        }
        if (recBuf[18] == 1) {
            Log.i(TAG, "keyboard is over charged");
        } else if ((recBuf[9] & 3) == 1) {
            Log.i(TAG, "TRX check failed");
        } else if ((recBuf[9] & 99) == 67) {
            Log.i(TAG, "pin connect failed");
        } else if ((recBuf[9] & 3) == 0) {
            notifyKeyboardStateListeners(KEYBOARD_STATE_DISCONNECTED);
            return true;
        } else if ((recBuf[9] & 99) == 35) {
            notifyKeyboardStateListeners(KEYBOARD_STATE_CONNECTED);
            return true;
        } else {
            Log.i(TAG, "unhandled connect state:" + String.format("%02x", recBuf[9]));
        }
        return false;
    }

    private boolean getReportEndpoint(UsbDevice device) {
        if (device == null) {
            return false;
        }
        UsbConfiguration configuration = device.getConfiguration(0);
        for (int i = 0; i < configuration.getInterfaceCount(); i++) {
            UsbInterface anInterface = configuration.getInterface(i);
            if (anInterface.getId() == 3 && anInterface.getEndpointCount() == 1) {
                UsbEndpoint endpoint = anInterface.getEndpoint(0);
                mReportInUsbEndpoint = endpoint;
                if (endpoint != null) {
                    mReportInterface = anInterface;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean getDeviceReadyForReport() {
        synchronized (mUsbDeviceLock) {
            if (mUsbDevice != null && mUsbConnection != null && mReportInterface != null && mReportInUsbEndpoint != null) {
                mUsbConnection.claimInterface(mReportInterface, true);
                return true;
            } else if (mUsbDevice == null && !getUsbDevice()) {
                return false;
            } else {
                if (!getReportEndpoint(mUsbDevice)) {
                    Log.e(TAG, "get usb report endpoint fail");
                    return false;
                }
                if (mUsbConnection == null) {
                    mUsbConnection = mUsbManager.openDevice(mUsbDevice);
                }
                if (mUsbConnection != null && mReportInterface != null && mReportInUsbEndpoint != null) {
                    mUsbConnection.claimInterface(mReportInterface, true);
                    return true;
                }
                Log.e(TAG, "get usb report connection fail");
                return false;
            }
        }
    }

    private void doGetReportData() {
        if (!getDeviceReadyForReport()) {
            return;
        }
        Arrays.fill(mRecBuf, (byte) 0);
        long startTime = System.currentTimeMillis();
        while (true) {
            boolean hasReport = sendUsbData(mUsbConnection, mReportInUsbEndpoint, mRecBuf);
            if (hasReport || System.currentTimeMillis() - startTime < 20) {
                if (hasReport) {
                    parseReportData(mRecBuf);
                }
            } else {
                return;
            }
        }
    }

    private void parseReportData(byte[] recBuf) {
        if (recBuf[0] == 38 && recBuf[2] == 56 && recBuf[4] == -94) {
            parseConnectState(recBuf);
        }
    }

    private void notifyKeyboardStateListeners(int state) {
        if (mKeyboardState == state) return;
        synchronized (mKeyboardStateListeners) {
            for (KeyboardStateListener listener : mKeyboardStateListeners) {
                listener.onKeyboardStateChanged(state);
            }
        }
        mKeyboardState = state;
    }

    public void addKeyboardStateListener(KeyboardStateListener listener) {
        synchronized (mKeyboardStateListeners) {
            mKeyboardStateListeners.add(listener);
        }
    }

    public void removeKeyboardStateListener(KeyboardStateListener listener) {
        synchronized (mKeyboardStateListeners) {
            mKeyboardStateListeners.remove(listener);
        }
    }

    private class KeyboardHandler extends Handler {
        private static final int MSG_READ_CONNECT_STATE = 1;
        private static final int MSG_GET_REPORT_DATA = 2;
        private static final int MSG_GET_DEVICE_TIME_OUT = 3;

        public KeyboardHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_READ_CONNECT_STATE:
                    doCheckConnectionState();
                    break;
                case MSG_GET_REPORT_DATA:
                    doGetReportData();
                    break;
                case MSG_GET_DEVICE_TIME_OUT:
                    XiaomiKeyboardUtils.resetKeyboardHost();
                    break;
            }
        }
    }

    public interface KeyboardStateListener {
        void onKeyboardStateChanged(int state);
    }
}
