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

import android.app.Service;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.IBinder;
import android.util.Log;
import android.view.InputDevice;

public class XiaomiKeyboardService extends Service implements XiaomiKeyboardManager.KeyboardStateListener {

    private static final String TAG = "XiaomiKeyboardService";

    private InputManager mInputManager;
    private XiaomiKeyboardManager mXiaomiKeyboardManager;

    public XiaomiKeyboardService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mInputManager = getSystemService(InputManager.class);
        setKeyboardEnabled(false);
        mXiaomiKeyboardManager = new XiaomiKeyboardManager(this);
        mXiaomiKeyboardManager.addKeyboardStateListener(this);
        mXiaomiKeyboardManager.start();
    }

    @Override
    public void onDestroy() {
        mXiaomiKeyboardManager.removeKeyboardStateListener(this);
        mXiaomiKeyboardManager.stop();
        super.onDestroy();
    }

    private void setKeyboardEnabled(boolean enabled) {
        int id = getKeyboardDeviceId();
        if (id >= 0) {
            if (enabled) mInputManager.enableInputDevice(id);
            else mInputManager.disableInputDevice(id);
        }
    }

    private int getKeyboardDeviceId() {
        for (int id : mInputManager.getInputDeviceIds()) {
            InputDevice inputDevice = mInputManager.getInputDevice(id);
            if (XiaomiKeyboardUtils.deviceIsXiaomiKeyboard(
                    inputDevice.getVendorId(), inputDevice.getProductId())) {
                return id;
            }
        }
        return -1;
    }

    @Override
    public void onKeyboardStateChanged(int state) {
        Log.d(TAG, "keyboard state = " + state);
        setKeyboardEnabled(state == XiaomiKeyboardManager.KEYBOARD_STATE_CONNECTED);
    }
}
