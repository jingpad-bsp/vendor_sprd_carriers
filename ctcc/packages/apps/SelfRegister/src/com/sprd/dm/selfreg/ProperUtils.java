package com.sprd.dm.mbselfreg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import android.content.Context;
import android.util.Log;

public class ProperUtils {
    private static final String TAG = "DmService";

    private static final String PROPER_PATH = "appConfig.properties";

    public static final String PROPER_RESEND_TIMES = "resend_times";
    public static final String PROPER_RETRY_TIMES = "retry_times";
    public static final String PROPER_SERVICE_INTERVAL = "service_interval";
    public static final String PROPER_SERVER_URL = "dm_server_url";
    public static final String PROPER_MANU_ID = "manu_id";

    public static final String PROPER_SMS_SERVER = "dm_sms_server";
    public static final String PROPER_SMS_RETRY_TIMES = "sms_retry_times";
    public static final String PROPER_SMSSERVICE_INTERVAL = "sms_service_interval";
    public static final String PROPER_IMS_SMS = "dm_ims_sms";

    public static Properties getProperties(Context context) {
        Properties prop = new Properties();
        FileInputStream fis = null;
        try {
            fis = context.openFileInput(PROPER_PATH);
            prop.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "ProperUtils, getProperties exception!");
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "ProperUtils, getProperties  FileInputStream close exception!");
            }
        }
        return prop;
    }

    public static void saveConfig(Context context, Properties prop) {
        FileOutputStream fos = null;
        try {
            fos = context.openFileOutput(PROPER_PATH, Context.MODE_PRIVATE);
            prop.store(fos, "");
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "ProperUtils, saveConfig exception!");
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "getProperties FileOutputStream close exception!");
            }
        }
    }

    public static void createPropFile(Context context) {
        try {
            String fileDir = context.getApplicationContext().getFilesDir().getAbsolutePath();
            File propFile = new File(fileDir+"/"+PROPER_PATH);
            if (!propFile.getParentFile().exists()) {
                propFile.getParentFile().mkdirs();
            }
            if (!propFile.exists()) {
                propFile.createNewFile();
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}
