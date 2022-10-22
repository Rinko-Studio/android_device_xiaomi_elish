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
import android.content.Intent;
import android.util.Log;
import android.os.UserHandle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class XiaomiKeyboardUtils {

    private static final String TAG = "XiaomiKeyboardUtils";

    public static final String KEYBOARD_FILE_PATH = "/sys/bus/platform/drivers/xiaomi-keyboard/soc:xiaomi_keyboard/xiaomi_keyboard_conn_status";
    public static final File KEYBOARD_FILE = new File(KEYBOARD_FILE_PATH);

    public static void enableKeyboardDevice() {
        writeKeyboardDevice("enable_keyboard");
    }

    public static void resetKeyboardDevice() {
        writeKeyboardDevice("reset");
    }

    public static void resetKeyboardHost() {
        writeKeyboardDevice("host_reset");
    }

    public static boolean deviceIsXiaomiKeyboard(int vendorId, int productId) {
        return vendorId == 0x3206 && productId == 0x3ffc;
    }

    public static void writeKeyboardDevice(String command) {
        if (KEYBOARD_FILE.exists()) {
            try {
                FileOutputStream out = new FileOutputStream(KEYBOARD_FILE);
                out.write(command.getBytes());
                out.flush();
                out.close();
                return;
            } catch (IOException e) {
                Log.e(TAG, "write xiaomi_keyboard_conn_status error", e);
                return;
            }
        }
        Log.e(TAG, "xiaomi_keyboard_conn_status not exists");
    }

    public static byte[] commandGetConnectState() {
        byte[] bytes = {78, 49, Byte.MIN_VALUE, 56, -95, 1, 1, 0};
        bytes[7] = getSum(bytes, 0, 7);
        return bytes;
    }

    public static synchronized byte getSum(byte[] data, int start, int length) {
        byte sum;
        synchronized (XiaomiKeyboardUtils.class) {
            sum = 0;
            for (int i = start; i < start + length; i++) {
                sum = (byte) (data[i] + sum);
            }
        }
        return sum;
    }

    public static void startService(Context context) {
        context.startServiceAsUser(new Intent(context, XiaomiKeyboardService.class), UserHandle.CURRENT);
    }
}
