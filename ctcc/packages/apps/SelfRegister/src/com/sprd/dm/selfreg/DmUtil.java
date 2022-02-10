package com.sprd.dm.mbselfreg;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.zip.CRC32;

public class DmUtil {
    private static final String TAG = "DmService";

    public static final String SLOT_INDEX = "slot";
    public static final long  MS_PER_SECOND = 1000L;
    public static final int   BUFFER_LEN = 32;
    public static final int   MAX_BUFFER_LEN = 65536;
    private static final int SLOT_ID_0 = 0;
    private static final int SLOT_ID_1 = 1;

    /**
     * Get PendingIntent.
     *
     * @param context     Dm service context.
     * @param bc_alarm    The alarm manerger will send broadcast to dm service.
     *
     * @return The PendingIntent.
     */
    private static final PendingIntent getPendingIntent(Context context, final String bc_alarm) {
        return getPendingIntent(context, bc_alarm, -1);
    }

    /**
     * Get PendingIntent.
     *
     * @param context     Dm service context.
     * @param bc_alarm    The alarm manerger will send broadcast to dm service.
     * @param slot        The working sim slot.
     * @return The PendingIntent.
     */
    private static final PendingIntent getPendingIntent(Context context, final String bc_alarm, final int slot) {
        Intent dmIntent = new Intent(bc_alarm);
        dmIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        if (slot == SLOT_ID_0 || slot == SLOT_ID_1) {
            dmIntent.putExtra(SLOT_INDEX, slot);
        }
        dmIntent.setClassName(context,"com.sprd.dm.mbselfreg.DmReceiver");
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, dmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return sender;
    }

    /**
     * Start alarm.
     *
     * @param context     Dm service context.
     * @param start_sec   The alarm will start after start_sec seconds.
     * @param bc_alarm    The alarm manerger will send broadcast to dm service.
     *
     * @return The time point when the alarm is triggered.
     */
    public static final int startBcAlarm(Context context, final int start_sec, final String bc_alarm) {
        return startBcAlarm(context, start_sec, bc_alarm, -1);
    }

    /**
     * Start alarm.
     *
     * @param context     Dm service context.
     * @param start_sec   The alarm will start after start_sec seconds.
     * @param bc_alarm    The alarm manerger will send broadcast to dm service.
     * @param slot        The working sim slot.
     *
     * @return The time point when the alarm is triggered.
     */
    public static final int startBcAlarm(Context context,
                                         final int start_sec,
                                         final String bc_alarm,
                                         final int slot) {
        Log.d(TAG,"startBcAlarm, start_sec = " + start_sec + ", bc_alarm = " + bc_alarm);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.SECOND, start_sec);
        long time_point_millis = calendar.getTimeInMillis();
        Log.d(TAG,"startBcAlarm, time_point_millis = " + time_point_millis);

        int time_point = (int)(time_point_millis/1000);
        startBcAlarmByTp(context, time_point, bc_alarm, slot);
        return time_point;
    }

    /**
     * Start alarm.
     *
     * @param context     Dm service context.
     * @param tp_sec   The alarm will start at the timepiont: tp_sec seconds.
     * @param bc_alarm    The alarm manerger will send broadcast to dm service.
     *
     */
    public static final void startBcAlarmByTp(Context context, final int tp_sec, final String bc_alarm) {
        startBcAlarmByTp(context, tp_sec, bc_alarm, -1);
    }

    /**
     * Start alarm.
     *
     * @param context     Dm service context.
     * @param tp_sec   The alarm will start at the timepiont: tp_sec seconds.
     * @param bc_alarm    The alarm manerger will send broadcast to dm service.
     * @param slot        The working sim slot.
     *
     */
    public static final void startBcAlarmByTp(Context context, final int tp_sec, final String bc_alarm, final int slot) {
        cancelBcAlarm(context, bc_alarm);
        Log.d(TAG,"startBcAlarmByTp, tp_sec = " + tp_sec + ", bc_alarm = " + bc_alarm);

        PendingIntent sender = getPendingIntent(context, bc_alarm, slot);

        long time_point_millis = tp_sec * MS_PER_SECOND;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time_point_millis, sender);
        } else/* if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) */{
            am.setExact(AlarmManager.RTC_WAKEUP, time_point_millis, sender);
        }
    }

    /**
     * Cancel alarm.
     *
     * @param context     Dm service context.
     * @param bc_alarm    The alarm manerger cancel the broadcast alarm.
     *
    */
    public static final void cancelBcAlarm(Context context, final String bc_alarm) {
        Log.d(TAG,"cancelBcAlarm, bc_alarm = " + bc_alarm);
        PendingIntent sender = getPendingIntent(context, bc_alarm);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(sender);
        sender.cancel();
    }

    /**
     * Calculate the crc of the data
     *
     * @param byteArray    Source data.
     *
     * @return The result of crc32.
     */
    public static int getCRC32(byte[] byteArray) {
        CRC32 crc = new CRC32();
        crc.update(byteArray);
        int result = (int)crc.getValue();

        Log.d(TAG,"getCRC32, result = " + Integer.toHexString(result));
        return result;
    }
    /**
     * Convert a 32-bit integer to a byte array
     *
     * @param value   int number.
     *
     * @return  a byte array
     */
    public static byte[]  int32ToByteArray(int value) {
        final int BYTES_COUNT = 8;
        byte[] bytes = new byte[BYTES_COUNT];
        for (int i = 0; i < BYTES_COUNT; i++) {
            int offset = 32 - (i + 1) * 4;
            bytes[i] = (byte) ((value >> offset) & 0xf);
            if (bytes[i] < 0xa) {
                bytes[i] += '0' - 0x0;
            } else {
                bytes[i] += 'a' - 0xa;
            }
            Log.d(TAG,"int32ToByteArray, bytes[" + i + "] = " + bytes[i]);
        }

        return bytes;
    }

    /**
     * Convert a 32-bit integer to an ASCII string
     *
     * @param value    Long int number.
     *
     * @return    ASCII String. For example: 0x0ab1c2d3 will return "0ab1c2d3"
     */
    public static String int32ToASCII(int value) {
        final int BYTES_COUNT = 8;
        byte[] bytes = new byte[BYTES_COUNT];
        for (int i = 0; i < BYTES_COUNT; i++) {
            int offset = 32 - (i + 1) * 4;
            bytes[i] = (byte) ((value >> offset) & 0xf);
            if (bytes[i] < 0xa) {
                bytes[i] += '0' - 0x0;
            } else {
                bytes[i] += 'a' - 0xa;
            }
            Log.d(TAG,"longToASCII, bytes[" + i + "] = " + bytes[i]);
        }

        String result = new String(bytes, 0, BYTES_COUNT);
        Log.d(TAG,"longToASCII, result = " + result);
        return result;
    }

    /**
     * Start dm service by action intent
     *
     * @param context    Context.
     * @param dmAction   Start action.
     *
     */
    public static void startDmService(Context context, String dmAction) {
        Intent dmService = new Intent();
        dmService.setAction(dmAction);
        dmService.setPackage(context.getPackageName());
        context.startService(dmService);
    }

    /**
     * Start dm service by action intent include slot parameter
     *
     * @param context    Context.
     * @param dmAction   Start action.
     * @param slot       The slot need self-registration
     */
    public static void startDmService(Context context, String dmAction, int slot) {
        Intent dmService = new Intent();
        dmService.setAction(dmAction);
        dmService.putExtra("slot", slot);
        dmService.setPackage(context.getPackageName());
        context.startService(dmService);
    }


    /**
     * Dump the byte array in hex mode
     *
     * @param data  byte[]
     *
     */
    public static void dumpDataInHex(final byte[] data) {
        Log.d(TAG,"dumpDataInHex, =================================Begin============================== ");
        if (data == null) {
            Log.e(TAG,"dumpDataInHex, data is null. ");
            return;
        }

        final int bufLen = BUFFER_LEN;

        Log.d(TAG,"dumpDataInHex, dataLen = " + data.length);

        int pos = 0;
        int len = data.length;
        String hexStr = "";

        while(len > bufLen) {
            hexStr = byteArrayToHexStr(data, pos, bufLen);
            Log.d(TAG,"dumpDataInHex: " + hexStr);
            pos += bufLen;
            len -= bufLen;
        }

        hexStr = byteArrayToHexStr(data, pos, len);
        Log.d(TAG,"dumpDataInHex: " + hexStr);
        Log.d(TAG,"dumpDataInHex, =================================End================================ ");
    }

    public static String byteArrayToHexStr(final byte[] data, final int offset, final int len) {
        if (data == null || len <= 0 ) {
            Log.e(TAG,"byteArrayToHexStr, para null. ");
            return null;
        }

        if (offset + len > data.length) {
            Log.d(TAG,"byteArrayToHexStr, data array size = " + data.length);
            Log.d(TAG,"byteArrayToHexStr, offset = " + offset);
            Log.d(TAG,"byteArrayToHexStr, data len = " + len);

            Log.e(TAG,"byteArrayToHexStr, para error. ");
            return null;
        }

        final char[] DIGITS_LOWER = { '0', '1', '2', '3', '4', '5',
                '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

        if (len > MAX_BUFFER_LEN) {
            Log.d(TAG,"byteArrayToHexStr, data is too long");
            return null;
        }

        char[] buf = new char[len << 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; i < len; i++) {
            buf[j++] = DIGITS_LOWER[0x0F & (data[offset + i] >> 4)];
            buf[j++] = DIGITS_LOWER[0x0F & (data[offset + i])];
        }
        String result = new String(buf);

        return result;
    }

    public static byte[] hexStrToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static void printIntent(Intent intent) {
        Log.d(TAG,"printIntent, intent = " + intent);

        if (intent == null) {
            return;
        }

        Bundle bundle = intent.getExtras();

        if (bundle == null) {
            return;
        }

        for (String key : bundle.keySet()) {
            Log.d(TAG,"printIntent, key = " + key + ", value = " + bundle.get(key));
        }
    }

}
