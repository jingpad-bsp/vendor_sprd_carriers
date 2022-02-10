package com.sprd.dm.mbselfreg;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemProperties;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.os.StatFs;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import com.dmyk.android.telephony.DmykAbsTelephonyManager;

import java.io.IOException;

public class DmMessage {
    private static final String TAG = "DmService";

    private static final String MSG_REGVER = "REGVER";
    private static final String MSG_MEID = "MEID";
    private static final String MSG_MODEL = "MODEL";
    private static final String MSG_SWVER = "SWVER";
    private static final String MSG_SIM1CDMAIMSI = "SIM1CDMAIMSI";
    private static final String MSG_UETYPE = "UETYPE";
    private static final String MSG_SIM1ICCID = "SIM1ICCID";
    private static final String MSG_SIM1LTEIMSI = "SIM1LTEIMSI";
    private static final String MSG_SIM2CDMAIMSI = "SIM2CDMAIMSI";
    private static final String MSG_SIM2ICCID = "SIM2ICCID";
    private static final String MSG_SIM2LTEIMSI = "SIM2LTEIMSI";
    private static final String MSG_IMEI1 = "IMEI1";
    private static final String MSG_IMEI2 = "IMEI2";
    private static final String MSG_SIM1TYPE = "SIM1TYPE";
    private static final String MSG_SIM2TYPE = "SIM2TYPE";
    private static final String MSG_MACID = "MACID";
    private static final String MSG_OSVER = "OSVER";
    private static final String MSG_RAM = "RAM";
    private static final String MSG_ROM = "ROM";
    private static final String MSG_SIM1CELLID = "SIM1CELLID";
    private static final String MSG_SIM2CELLID = "SIM2CELLID";
    private static final String MSG_SIM1VOLTESW = "SIM1VoLTESW";
    private static final String MSG_SIM2VOLTESW = "SIM2VoLTESW";
    private static final String MSG_ACCESSTYPE = "ACCESSTYPE";
    private static final String MSG_DATASIM = "DATASIM";
    private static final String MSG_REGDATE = "REGDATE";
    private static final String MSG_NETWORKTYPE = "NETWORKTYPE";

    private static final String ACCESS_TYPE_DATA = "1";
    private static final String ACCESS_TYPE_WIFI = "2";

    //VoLTE status: "1": open; "2": close; "3": not support
    private static final String VOLTE_STATUS_OPEN = "1";
    private static final String VOLTE_STATUS_CLOSE = "2";
    private static final String VOLTE_STATUS_UNSUPPORT = "3";

    private static final String DATA_SIM1 = "1";
    private static final String DATA_SIM2 = "2";

    private static final int SIM_TYPE_ICC = 1;
    private static final int SIM_TYPE_UICC = 2;

    private static final int SW_VERSION_LENGTH = 60;
    private static final int MODEL_LENGTH = 20;

    //  <!-- 1:phone, 2:pad, 3:DataCard, 4:CPE, 5:5G Phone, 6:Reserve, 7:IoT CM, 8:IoT terminal -->
    private static final int DM_DEV_TYPE_PHONE = 1;
    private static final int DM_DEV_TYPE_PAD = 2;
    private static final int DM_DEV_TYPE_DATACARD = 3;
    private static final int DM_DEV_TYPE_CPE = 4;
    private static final int DM_DEV_TYPE_5G_PHONE = 5;
    private static final int DM_DEV_TYPE_RESERVE2 = 6;
    private static final int DM_DEV_TYPE_IOT_CM = 7;
    private static final int DM_DEV_TYPE_IOT_TERMINAL = 8;
    private static final int DM_DEV_TYPE_4G_LAPTOP = 9;
    private static final int DM_DEV_TYPE_4G_TABLET = 10;
    private static final int DM_DEV_TYPE_KIDS_WATCH = 11;
    private static final int DM_DEV_TYPE_SMART_WATCH = 12;
    private static final int DM_DEV_TYPE_VR_DISPLAY = 13;
    private static final int DM_DEV_TYPE_AR_DISPLAY = 14;
    private static final int DM_DEV_TYPE_LOCATOR = 15;
    private static final int DM_DEV_TYPE_E_READER = 16;
    private static final int DM_DEV_TYPE_MIFI = 17;
    private static final int DM_DEV_TYPE_TRANSLATION = 18;
    private static final int DM_DEV_TYPE_CARFI = 19;
    private static final int DM_DEV_TYPE_SMART_REARVIEW_MIRROR = 20;
    private static final int DM_DEV_TYPE_AUTOMOBILE_RECORDER = 21;

    public static final int DM_FORM_PHONE_SINGLESIM = 1;
    public static final int DM_FORM_PHONE_DUALSIM = 2;
    public static final int DM_FORM_PAN_SMART_DEV = 3;

    private static final String OPERATOR_CTCC_4G = "46011";
    private static final String OPERATOR_CTCC = "46005";
    private static final String OPERATOR_CTCC_CDMA = "46003";

    private static final String DM_RAM_SIZE_PROP = "ro.selfreg.ram.size";
    private static final String DM_ROM_SIZE_PROP = "ro.selfreg.flash.size";
    private static final String DM_MANUFACTURER_PROP = "ro.product.manufacturer";
    private static final String DM_SW_VERSION_PROP = "ro.version.software";
    private static final String DM_SW_BUILD_DATE_PROP = "ro.build.date.utc";
    private static final String DM_SMS_BOOT_PROP = "sys.dm.sms.selfreg.boot";

    private static final String MACID_FILE_PATH = "/mnt/vendor/wifimac.txt";

    private static final String EMPTY = "";
    private static final int NETWORK_TYPE_UNKNOWN = 0;

    private Context mContext;

    private HashMap<String, String> mCellInfo = new HashMap<String, String>();

    private DmykAbsTelephonyManager mDmykAbsTelephonyManager;
    private TelephonyManager mTelephonyManager;

    public static final String ICCID_DEFAULT_VALUE = "00000000000000000000";
    public static final String IMSI_DEFAULT_VALUE = "000000000000000";
    public static final String IMEI_DEFAULT_VALUE = "000000000000000";
    public static final String MEID_DEFAULT_VALUE = "00000000000000";

    public static final int SLOT_ID_0 = 0;
    public static final int SLOT_ID_1 = 1;
    public static final int SLOT_ID_INVALID = -1;

    public static final int SINGLE_SIM_COUNT = 1;
    public static final int DUAL_SIM_COUNT = 2;

    public DmMessage(Context context) {
        mContext = context;
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mDmykAbsTelephonyManager = DmykAbsTelephonyManager.getDefault(context);
    }

    public String getDmMessage(String msg) {
        switch (msg) {
            case MSG_REGVER:
                return mContext.getResources().getString(R.string.register_version);
            case MSG_MEID:
                return getMeid();
            case MSG_MODEL:
                return getModel();
            case MSG_SWVER:
                return getSwVer();
            case MSG_UETYPE:
                return getUEType();
            case MSG_SIM1ICCID:
                String sim1Iccid = getSimIccid(SLOT_ID_0);
                return sim1Iccid == ICCID_DEFAULT_VALUE ? EMPTY : sim1Iccid;
            case MSG_SIM1LTEIMSI:
                return getSimLteImsi(SLOT_ID_0);
            case MSG_SIM1CDMAIMSI:
                return getSimCdmaImsi(SLOT_ID_0);
            case MSG_SIM1CELLID:
                return getSimCellId(SLOT_ID_0);
            case MSG_SIM2CELLID:
                return getSimCellId(SLOT_ID_1);
            case MSG_SIM2ICCID:
                String sim2Iccid = getSimIccid(SLOT_ID_1);
                return sim2Iccid == ICCID_DEFAULT_VALUE ? EMPTY : sim2Iccid;
            case MSG_SIM2LTEIMSI:
                return getSimLteImsi(SLOT_ID_1);
            case MSG_SIM2CDMAIMSI:
                return getSimCdmaImsi(SLOT_ID_1);
            case MSG_IMEI1:
                return getImei(SLOT_ID_0);
            case MSG_IMEI2:
                return getImei(SLOT_ID_1);
            case MSG_SIM1TYPE:
                return getSimType(SLOT_ID_0);
            case MSG_SIM2TYPE:
                return getSimType(SLOT_ID_1);
            case MSG_SIM1VOLTESW:
                return getSimVoLTEState(SLOT_ID_0);
            case MSG_SIM2VOLTESW:
                return getSimVoLTEState(SLOT_ID_1);
            case MSG_MACID:
                return getWifiMac();
            case MSG_OSVER:
                return getOsVer();
            case MSG_ROM:
                return getROM();
            case MSG_RAM:
                return getRAM();
            case MSG_DATASIM:
                return getDataSim();
            case MSG_ACCESSTYPE:
                return getAccessType();
            case MSG_REGDATE:
                return getTimeStamp();
            case MSG_NETWORKTYPE:
                return getNetworkType();
            default:
                String value = mCellInfo.get(msg);
                if (TextUtils.isEmpty(value)) {
                    value = EMPTY;
                }
                return value;
        }
    }

    protected String getSimCellId(int slotId) {
        Log.d(TAG,"getSimCellId, slotId = " + slotId);
        long simcellid_nr = -1L;
        long simcellid_lte = -1L;
        if (mDmykAbsTelephonyManager != null) {
           if (DmService.getInstance().getNetworkUtils().isPhoneInNrStatus(slotId)) {
               simcellid_nr = mDmykAbsTelephonyManager.getNrCellId(slotId);
               Log.d(TAG,"getSimCellId, simcellid_nr = " + simcellid_nr);
           } else {
               simcellid_lte = mDmykAbsTelephonyManager.getCellId(slotId);
               Log.d(TAG,"getSimCellId, simcellid_lte = " + simcellid_lte);
           }

        }
        if (simcellid_nr != -1) {
            return String.valueOf(simcellid_nr);
        }
        if (simcellid_lte != -1) {
            return String.valueOf(simcellid_lte);
        }
        return EMPTY;
    }

    protected String getModel() {
        Properties prop = ProperUtils.getProperties(mContext);
        String def = mContext.getResources().getString(R.string.manu_id);
        String manuId = prop.getProperty(ProperUtils.PROPER_MANU_ID, def);

        String modelInfo = manuId + "-" + Build.MODEL;

        int endIndex = modelInfo.length();

        if (endIndex > MODEL_LENGTH) {
            Log.d(TAG,"getModel, the length of modelInfo is more than 20, modelInfo = " + modelInfo);
            endIndex = MODEL_LENGTH;
        }

        modelInfo = modelInfo.substring(0, endIndex);
        Log.d(TAG,"getModel, modelInfo = "+modelInfo);

        return modelInfo;
    }

    protected String getSwVer() {
        String swVer = SystemProperties.get(DM_SW_VERSION_PROP, EMPTY);
        if (TextUtils.isEmpty(swVer)) {
            swVer = Build.DISPLAY;
            Log.d(TAG,"getSwVer, Build.DISPLAY = " + Build.DISPLAY);
        }

        int endIndex = swVer.length();

        if (endIndex > SW_VERSION_LENGTH) {
            Log.d(TAG,"getSwVer, the length of swVer is more than " + SW_VERSION_LENGTH
                     + ", swVer = " + swVer);
            endIndex = SW_VERSION_LENGTH;
        }

        swVer = swVer.substring(0, endIndex);
        Log.d(TAG,"getSwVer, swVer = " + swVer);

        return swVer;
    }

    protected String getSwBuildDate() {
        String buildDate = SystemProperties.get(DM_SW_BUILD_DATE_PROP, EMPTY);
        Log.d(TAG,"getSwBuildDate, buildDate = " + buildDate);
        return buildDate;
    }

    protected String getMeid() {
        return getMeid(SLOT_ID_0);
    }

    protected String getMeid(int slotId) {
        if (mTelephonyManager != null) {
            String meid = mTelephonyManager.getMeid(SLOT_ID_0);
            if (!TextUtils.isEmpty(meid)) {
                Log.d(TAG,"getMeid, meid = " + meid);
                return meid;
            }
        }
        return getImei(SLOT_ID_0);
    }

    protected String getSimLteImsi(int slotId) {
        if (mDmykAbsTelephonyManager != null) {
            String lteImsi = mDmykAbsTelephonyManager.getSubscriberId(slotId);
            if (!TextUtils.isEmpty(lteImsi)) {
                Log.d(TAG,"getSimLteImsi, lteImsi = " + lteImsi);
                return lteImsi;
            }
        }
        return EMPTY;
    }

    protected String getSimCdmaImsi(int slotId) {
        if (mDmykAbsTelephonyManager != null) {
            String cdmaImsi = mDmykAbsTelephonyManager.getCdmaImsi(slotId);
            if (!TextUtils.isEmpty(cdmaImsi)) {
                Log.d(TAG,"getSimCdmaImsi, cdmaImsi = " + cdmaImsi);
                return cdmaImsi;
            }
        }
        return EMPTY;
    }

    protected String getSimIccid(int slotId) {
        if (mDmykAbsTelephonyManager != null ) {
            if (DmykAbsTelephonyManager.SIM_STATE_ABSENT == mDmykAbsTelephonyManager.getSimState(slotId)) {
                return ICCID_DEFAULT_VALUE;
            }

            String simIccid = mDmykAbsTelephonyManager.getIccId(slotId);
            if (!TextUtils.isEmpty(simIccid)) {
               return simIccid;
            }
        }
        return ICCID_DEFAULT_VALUE;
    }

    protected String getImei(int slotId) {
        if((SLOT_ID_0 != slotId) && (SLOT_ID_1 != slotId)) {
            return IMEI_DEFAULT_VALUE;
        }

        if((SINGLE_SIM_COUNT == getSlotCount()) && (SLOT_ID_0 != slotId)) {
            return IMEI_DEFAULT_VALUE;
        }

        if (mDmykAbsTelephonyManager != null) {
            String imei = mDmykAbsTelephonyManager.getGsmDeviceId(slotId);
            if (!TextUtils.isEmpty(imei)) {
                Log.d(TAG,"getImei, imei = " + imei);
                return imei;
            }
        }

        return IMEI_DEFAULT_VALUE;
    }

    private String getSimType(int slotId) {
        if (mDmykAbsTelephonyManager != null) {
            int type = mDmykAbsTelephonyManager.queryLteCtccSimType(slotId);
            if (type == SIM_TYPE_ICC || type == SIM_TYPE_UICC)
                return String.valueOf(type);
        }
        return EMPTY;
    }

    private String getSimVoLTEState(int slotId) {
        //VoLTE status: "1": open; "2": close; "3": not support
        if (mDmykAbsTelephonyManager != null ) {
            int state = mDmykAbsTelephonyManager.getVoLTEState(slotId);
            switch(state) {
                case DmykAbsTelephonyManager.VOLTE_STATE_ON:
                    return VOLTE_STATUS_OPEN;
                case DmykAbsTelephonyManager.VOLTE_STATE_OFF:
                    return VOLTE_STATUS_CLOSE;
                case DmykAbsTelephonyManager.VOLTE_STATE_UNKNOWN:
                    return VOLTE_STATUS_UNSUPPORT;
                default:
                    return EMPTY;
            }
        }
        return EMPTY;
    }

    private String getDataSim() {
        int masterSlotId = mDmykAbsTelephonyManager.getMasterPhoneId();

        if (SLOT_ID_0 == masterSlotId) {
            return DATA_SIM1;
        }

        if (SLOT_ID_1 == masterSlotId) {
            return DATA_SIM2;
        }
        return EMPTY;
    }

    public String ReadWifiMacFromFile(String strFilePath) {
        Log.d(TAG, "ReadWifiMacFromFile: strFilePath = " + strFilePath);
        StringBuffer sb = new StringBuffer("");
        BufferedReader br = null;
        try {
            String line = EMPTY;
            br = new BufferedReader(new FileReader(strFilePath));
            if (br != null) {
                while ((line = br.readLine())!= null) {
                    sb.append(line);
                };
            }
        } catch (java.io.FileNotFoundException e) {
            Log.d(TAG, "ReadWifiMacFromFile: The File does not exist.");
        } catch (IOException e) {
            Log.e(TAG, "ReadWifiMacFromFile: IOException:",e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    Log.e(TAG, "ReadWifiMacFromFile: BufferedReader close:",e);
                }
            }
            return sb.toString();
        }
    }

    private String getWifiMac() {
        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        /* UNISOC: Add for bug1499157{@ */
        boolean isRandomMacAddr = mContext.getResources().getBoolean(R.bool.random_mac_address);
        String macAddress = null;

        if (isRandomMacAddr) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        } else {
            final String[] macAddresses = wifiManager.getFactoryMacAddresses();
            if (macAddresses != null && macAddresses.length > 0) {
                macAddress = macAddresses[0];
            }
        }
        /* @} */

        Log.d(TAG, "getWifiMac,isRandomMacAddr = " + isRandomMacAddr + " macAddress = " + macAddress);

        if (TextUtils.isEmpty(macAddress) || WifiInfo.DEFAULT_MAC_ADDRESS.equals(macAddress)) {
            macAddress = ReadWifiMacFromFile(MACID_FILE_PATH);
            Log.d(TAG, "getWifiMac from wifimac.txt, macAddress = " + macAddress);
        }

        return !TextUtils.isEmpty(macAddress) ? macAddress : EMPTY;
    }

    private String getOsVer() {
        return "android"+Build.VERSION.RELEASE;
    }

    private String getRAM() {
        String ram = SystemProperties.get(DM_RAM_SIZE_PROP, EMPTY);
        if (!TextUtils.isEmpty(ram)) {
            return ram;
        }
        return EMPTY;
    }

    private String getROM() {
        String flash = SystemProperties.get(DM_ROM_SIZE_PROP, EMPTY);
        if (!TextUtils.isEmpty(flash)) {
            return flash;
        }
        return EMPTY;
    }

    private String getManufacturer() {
        return SystemProperties.get(DM_MANUFACTURER_PROP, EMPTY);
    }

    private String getAccessType() {
        if(mContext instanceof DmService) {
            if(DmService.getInstance().getNetworkUtils().isWifiAvailable()){
                return ACCESS_TYPE_WIFI;
            }
            return ACCESS_TYPE_DATA;
        }
        return EMPTY;
    }

    public String getTimeStamp() {
        long currentMillis = System.currentTimeMillis();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(currentMillis);
        String timeStamp = format.format(date);
        return timeStamp;
    }

    public String getNetworkType() {
        int type = NETWORK_TYPE_UNKNOWN;
        if (mDmykAbsTelephonyManager != null) {
            int masterSlotId = mDmykAbsTelephonyManager.getMasterPhoneId();
            type =  mDmykAbsTelephonyManager.getNetworkClass(masterSlotId);
            Log.d(TAG, "getNetworkType, type = " + type);
        }
        return String.valueOf(type);
    }

    public int getSlotCount() {
        int count = SINGLE_SIM_COUNT;

        if (mDmykAbsTelephonyManager != null) {
            count =  mDmykAbsTelephonyManager.getPhoneCount();
            Log.d(TAG, "getSlotCount, count = " + count);
        }
        return count;
    }

    public int getInsertSimCount() {
        int count = 0;

        boolean is_slot_0_insert = (mDmykAbsTelephonyManager != null
            && (DmykAbsTelephonyManager.SIM_STATE_ABSENT != mDmykAbsTelephonyManager.getSimState(SLOT_ID_0)));
        boolean is_slot_1_insert = (mDmykAbsTelephonyManager != null
            && (DmykAbsTelephonyManager.SIM_STATE_ABSENT != mDmykAbsTelephonyManager.getSimState(SLOT_ID_1)));

        if (is_slot_0_insert) {
            count++;
        }

        if (is_slot_1_insert) {
            count++;
        }

        return count;
    }

    public boolean isSimAbsent(int slotId) {
        if (DmykAbsTelephonyManager.SIM_STATE_ABSENT == mDmykAbsTelephonyManager.getSimState(slotId)){
            Log.w(TAG, "isSimAbsent, Sim "+ slotId +" not insert ");
            return true;
        }
        Log.w(TAG, "isSimAbsent, Sim "+ slotId +" insert ");
        return false;
    }

    public boolean isSimLoaded(int slotId) {
        int simState = SubscriptionManager.getSimStateForSlotIndex(slotId);
        Log.d(TAG, "isSimLoaded, simState = "+ simState);
        int simStateTele = mDmykAbsTelephonyManager.getSimState(slotId);
        Log.d(TAG, "isSimLoaded, simStateTele = "+ simStateTele);
        if (TelephonyManager.SIM_STATE_LOADED == simState
         && DmykAbsTelephonyManager.SIM_STATE_READY == simStateTele) {
            return true;
        }
        return false;
    }

    public String getUEType() {
        return mContext.getResources().getString(R.string.ue_type);
    }

    public int getDeviceForm() {
        int deviceForm = DM_FORM_PAN_SMART_DEV;
        int ueType = Integer.parseInt(getUEType());

        switch(ueType) {
            case DM_DEV_TYPE_RESERVE2:
            case DM_DEV_TYPE_IOT_CM:
            case DM_DEV_TYPE_IOT_TERMINAL:
            case DM_DEV_TYPE_4G_LAPTOP:
            case DM_DEV_TYPE_4G_TABLET:
            case DM_DEV_TYPE_KIDS_WATCH:
            case DM_DEV_TYPE_SMART_WATCH:
            case DM_DEV_TYPE_VR_DISPLAY:
            case DM_DEV_TYPE_AR_DISPLAY:
            case DM_DEV_TYPE_LOCATOR:
            case DM_DEV_TYPE_E_READER:
            case DM_DEV_TYPE_MIFI:
            case DM_DEV_TYPE_TRANSLATION:
            case DM_DEV_TYPE_CARFI:
            case DM_DEV_TYPE_SMART_REARVIEW_MIRROR:
            case DM_DEV_TYPE_AUTOMOBILE_RECORDER:
                deviceForm = DM_FORM_PAN_SMART_DEV;
                break;
            case DM_DEV_TYPE_5G_PHONE:
            case DM_DEV_TYPE_PHONE:
                int slotCount = getSlotCount();
                if (SINGLE_SIM_COUNT == slotCount) {
                    deviceForm = DM_FORM_PHONE_SINGLESIM;
                } else if (DUAL_SIM_COUNT == slotCount) {
                    deviceForm = DM_FORM_PHONE_DUALSIM;
                }
                break;
            default:
                break;
        }

        return deviceForm;
    }

    public int getCtccSimSlot() {
        if (isCtccSimCard(SLOT_ID_0)) {
            if (isCtccSimCard(SLOT_ID_1)) {
                int masterSlotId = mDmykAbsTelephonyManager.getMasterPhoneId();
                return masterSlotId;
            } else {
                return SLOT_ID_0;
            }
        } else if (isCtccSimCard(SLOT_ID_1)) {
            return SLOT_ID_1;
        } else {
            return SLOT_ID_INVALID;
        }
    }

    public int getSubId(int slotId) {
        if (mDmykAbsTelephonyManager != null) {
            int subId = mDmykAbsTelephonyManager.getSubId(slotId);
            Log.d(TAG, "getSubId, subId = " + subId);
            if (subId != -1) {
                return subId;
            }
        }
        return -1;
    }

    public boolean isCtccSimCard(int slotId) {
        if (mDmykAbsTelephonyManager != null) {
            String imsi = mDmykAbsTelephonyManager.getSubscriberId(slotId);
            Log.i(TAG,"isCtccSimCard, imsi = "+imsi);
            if (imsi != null
                && (imsi.startsWith(OPERATOR_CTCC_CDMA)
                || imsi.startsWith(OPERATOR_CTCC)
                || imsi.startsWith(OPERATOR_CTCC_4G))) {
                return true;
            }
        }
        return false;
    }

    public int getMasterSimSlot() {
        if (mDmykAbsTelephonyManager != null) {
            int masterSlotId = mDmykAbsTelephonyManager.getMasterPhoneId();
            Log.i(TAG, "getMasterSimSlot: masterSlotId = " + masterSlotId);
            return masterSlotId;
        }
        return SLOT_ID_INVALID;
    }

    public int getSlotId(int subId) {
        SubscriptionManager subManager = SubscriptionManager.from(mContext);
        if (subManager != null) {
            int slotId = subManager.getSlotIndex(subId);
            Log.i(TAG, "getSlotId: slotId = " + slotId);

            return slotId;
        }
        return SLOT_ID_INVALID;
    }

    public String getMeidInSim(int slotId) {
        String meidInSim = mDmykAbsTelephonyManager.readMeidFromCsim(slotId);
        Log.i(TAG, "getMeidInSim: meidInSim = " + meidInSim);
        return meidInSim;
    }

    public void setMeidToSim(int slotId, String meid) {
        getMeidInSim(slotId);
        mDmykAbsTelephonyManager.writeMeidToCsim(slotId, meid);
        Log.i(TAG, "setMeidToSim: meid = " + meid);

        return;
    }

    public boolean isServiceStateInService(int slotId) {
        if (mDmykAbsTelephonyManager != null) {
            if (isSimLoaded(slotId)) {
                return mDmykAbsTelephonyManager.isServiceStateInService(slotId);
            }
        }
        return false;
    }

    public boolean isCdmaNetwork(int slotId) {
        int subId = getSubId(slotId);
        Log.i(TAG, "isCdmaNetwork: slotId = " + slotId + ", subId = " + subId);
        if (mTelephonyManager != null && subId != -1) {
            int networkType = mTelephonyManager.getVoiceNetworkType(subId);
            Log.i(TAG, "isCdmaNetwork: networkType = " + networkType);
            if (TelephonyManager.NETWORK_TYPE_CDMA == networkType
            || TelephonyManager.NETWORK_TYPE_1xRTT == networkType) {
                return true;
            }
        }
        return false;
    }

    public boolean isInternationalNetworkRoaming(int slotId) {
        Log.d(TAG,"isInternationalNetworkRoaming, slotId = " + slotId);
        boolean bRet = false;
        int simState = mDmykAbsTelephonyManager.getSimState(slotId);
        Log.d(TAG,"isInternationalNetworkRoaming, simState = " + simState);
        if (DmykAbsTelephonyManager.SIM_STATE_ABSENT != simState
         && DmykAbsTelephonyManager.SIM_STATE_UNKNOWN != simState) {
            bRet = mDmykAbsTelephonyManager.isInternationalNetworkRoaming(slotId);
        }
        Log.d(TAG,"isInternationalNetworkRoaming, bRet = " + bRet);
        return bRet;
    }

    public boolean isDmSmsFirstBoot() {
        String firstBoot = SystemProperties.get(DM_SMS_BOOT_PROP, EMPTY);
        Log.d(TAG,"isDmSmsFirstBoot, firstBoot = " + firstBoot);
        if (TextUtils.isEmpty(firstBoot)) {
            try {
                SystemProperties.set(DM_SMS_BOOT_PROP, "1");
                return true;
            } catch (Exception e) {
                Log.e(TAG, "SystemProperties: set:", e);
                return false;
            }
        }
        return false;
    }
}
