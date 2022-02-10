package com.sprd.dm.mbselfreg;

import android.annotation.NonNull;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;

import android.provider.Settings;
import android.service.carrier.CarrierMessagingService;
import android.service.carrier.MessagePdu;
import android.text.TextUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.util.Log;


import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.telephony.TelephonyManager;


public class DmSmsService extends CarrierMessagingService { //add priviledged|signature
    private static final String TAG = "DmServiceSms";

    private DmMessage mDmMessage;
    private static DmSmsService mInstance = null;
    private HandlerThread mSelfRegThread;
    private Handler mSelfRegHandler;


    private static String mDmSmsServerNum = "10659401";

    private static final int DM_SMS_TELESERVISE_ID = 65005;

    private static final String DM_SWVER = "dm_sms_swver";
    private static final String DM_SW_BUILD_DATE = "dm_sms_last_sw_build_date";
    private static final int PHONE_COUNT = 2;

    private static final String DM_UE_MEID_IN_SIM1 = "dm_ue_meid_sim1";
    private static final String DM_UE_MEID_IN_SIM2 = "dm_ue_meid_sim2";
    private static final String DM_CDMA_SMS_SELF_REG1 = "dm_cdma_sms_self_reg_tag_1";//check selfreg flag
    private static final String DM_CDMA_SMS_SELF_REG2 = "dm_cdma_sms_self_reg_tag_2";//check selfreg flag
    private static final String DM_UE_CDMA_IMSI_SIM1 = "dm_ue_cdma_imsi_sim1";
    private static final String DM_UE_CDMA_IMSI_SIM2 = "dm_ue_cdma_imsi_sim2";
    private static final String DM_CDMA_SMS_RETRY_TIMES = "dm_cdma_sms_selfReg_retry_times";//change to 3
    private int mCdmaRetryTimes = 0;
    public boolean[] is_start_cdma_regist = new boolean[PHONE_COUNT];

    public static final int DM_CDMA_SMS_REG_STAT_INIT = 0;
    public static final int DM_CDMA_SMS_REG_STAT_SENDING = 1;
    public static final int DM_CDMA_SMS_REG_STAT_WAIT_REPLY = 2;

    private int[] mCdmaRegState = new int[PHONE_COUNT];

    public static final int DM_IMS_SMS_REG_STAT_INIT = 0;
    public static final int DM_IMS_SMS_REG_STAT_SENDING = 1;
    public static final int DM_IMS_SMS_REG_STAT_WAIT_REPLY = 2;

    private static final int DM_CDMA_SMS_REG_RESPONSE_SUCCESS = 1;
    private static final int DM_IMS_SMS_REG_RESPONSE_SUCCESS = 2;
    private static final int DM_SMS_REG_RESPONSE_FAIL = 0;
    private static final int DM_CDMA_REG_DELAY_TIME = 60; //60S
    private static final String[] DM_IMS_REG_STATE_KEY = new String[] {
      "dm_ims_sms_reg_state_sim1",    "dm_ims_sms_reg_state_sim2"
    };

    private static final String DM_UE_LTE_IMSI_SIM1 = "dm_ue_lte_imsi_sim1";
    private static final String DM_UE_LTE_IMSI_SIM2 = "dm_ue_lte_imsi_sim2";
    private static final String DM_IMS_SMS_RETRY_TIMES = "dm_ims_sms_selfReg_retry_times";
    private int mImsRetryTimes = 0;

    private boolean[] is_start_ims_regist = new boolean[PHONE_COUNT];

    private static final String DM_CDMA_SMS_REG_INIT = "0";
    private static final String DM_CDMA_SMS_REG_SUCCESS = "1";

    private static final int DM_CDMA_SMS_PROTOCOL = 0x02;
    private static final int DM_IMS_SMS_PROTOCOL = 0x03;

    private static final int SMS_LENGTH_MAX = 140;
    private static final int LENGTH_MESSAGE_HEAD = 4;
    private static final int LENGTH_MESSAGE_CRC = 8;
    private static final int LENGTH_CDMA_SMS_MAX = SMS_LENGTH_MAX - LENGTH_MESSAGE_HEAD - LENGTH_MESSAGE_CRC;
    private static final int CDMA_SMS_END_TAG_LEN = 10;

    private static final int LENGTH_IMS_SMS_MAX = SMS_LENGTH_MAX - LENGTH_MESSAGE_HEAD;
    private static final int IMS_SMS_END_TAG_LEN = 5;

    private static final int SMS_WAIT_SEND_RESULT_TIME = 3000; //ms

    /*
     * A map for pending sms messages. The key is the random request UUID.
     */
    private static ConcurrentHashMap<Uri, SendResult> mPendingMessageMap =
                                new ConcurrentHashMap<Uri, SendResult>();


    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        mDmMessage = new DmMessage(this);

        prepareProp();

        mDmSmsServerNum = getDmSmsServerNum();

        mSelfRegThread = new HandlerThread("dm_sms-selfReg-message");
        mSelfRegThread.start();
        mSelfRegHandler = new SelfRegisterHandler(mSelfRegThread.getLooper());

        mInstance = this;

        if (mDmMessage.isDmSmsFirstBoot()) {
            onBoot();
            resetUESavedSimMeid();
        }

        if (isSwVerChanged()) {
            resetSmsRegStatus();
        }
    }

    private boolean isSwVerChanged() {
        String dmSwver = Settings.Global.getString(getContentResolver(), DM_SWVER);
        Log.d(TAG, "isSwVerChanged, ue saved dmSwver:" + dmSwver);

        String swver = mDmMessage.getSwVer();
        Log.d(TAG, "isSwVerChanged, swver:" + swver);
        if (swver != null && !swver.equals(dmSwver)) {
            return true;
        }

        String savedBuidDate = Settings.Global.getString(getContentResolver(), DM_SW_BUILD_DATE);
        Log.d(TAG, "isSwVerChanged, ue saved buid date:" + savedBuidDate);
        String buiddate = mDmMessage.getSwBuildDate();
        Log.d(TAG, "isSwVerChanged, sw buid date:" + buiddate);
        if (buiddate != null && !buiddate.equals(savedBuidDate)) {
            return true;
        }

        return false;
    }

    public void setCdmaSmsSelfRegStat(int slot, int stat) {
        Log.d(TAG, "setCdmaSmsSelfRegStat, slot: " + slot
                     +" ,stat: " + stat);
        if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
            mCdmaRegState[slot] = stat;
        }
    }

    public int getCdmaSmsSelfRegStat(int slot) {
        if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
            Log.d(TAG, "getCdmaSmsSelfRegStat, slot: " + slot
                     +" ,mCdmaRegState: " + mCdmaRegState[slot]);
            return mCdmaRegState[slot];
        }
        return DM_CDMA_SMS_REG_STAT_INIT;
    }

    public void setImsSmsSelfRegStat(int slot, int stat) {
        Log.d(TAG, "setImsSmsSelfRegStat, slot: " + slot
                     +" ,stat: " + stat);
        if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
            Settings.Global.putInt(getContentResolver(),
                    DM_IMS_REG_STATE_KEY[slot], stat);
        }
    }

    public int getImsSmsSelfRegStat(int slot) {
        if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
            int regState = Settings.Global.getInt(getContentResolver(),
                                    DM_IMS_REG_STATE_KEY[slot],
                                    DM_IMS_SMS_REG_STAT_INIT);
            Log.d(TAG, "getImsSmsSelfRegStat, slot: " + slot
                     +" ,mImsRegState: " + regState);
            return regState;
        }
        return DM_IMS_SMS_REG_STAT_INIT;
    }


    private String getDmSmsServerNum() {
        String def = getResources().getString(R.string.dm_sms_server);
        String dmSmsServer = ProperUtils.getProperties(this).getProperty(ProperUtils.PROPER_SMS_SERVER, def);
        Log.d(TAG, "getDmSmsServerNum dmSmsServer: " + dmSmsServer);
        return dmSmsServer;
    }

    private void resetSmsRegStatus() {
        //Reset CDMA sms self register status
        Settings.Global.putString(getContentResolver(), DM_CDMA_SMS_SELF_REG1, DM_CDMA_SMS_REG_INIT);
        Settings.Global.putString(getContentResolver(), DM_CDMA_SMS_SELF_REG2, DM_CDMA_SMS_REG_INIT);
        Settings.Global.putString(getContentResolver(), DM_UE_CDMA_IMSI_SIM1, DmMessage.IMSI_DEFAULT_VALUE);
        Settings.Global.putString(getContentResolver(), DM_UE_CDMA_IMSI_SIM2, DmMessage.IMSI_DEFAULT_VALUE);
        resetCdmaSelfRegStartStatus();

        //Reset IMS sms self register status
        Settings.Global.putString(getContentResolver(), DM_UE_LTE_IMSI_SIM1, DmMessage.IMSI_DEFAULT_VALUE);
        Settings.Global.putString(getContentResolver(), DM_UE_LTE_IMSI_SIM2, DmMessage.IMSI_DEFAULT_VALUE);
        resetImsSelfRegStartStatus(DmMessage.SLOT_ID_0);
        resetImsSelfRegStartStatus(DmMessage.SLOT_ID_1);
        setImsSmsSelfRegStat(DmMessage.SLOT_ID_0, DM_IMS_SMS_REG_STAT_INIT);
        setImsSmsSelfRegStat(DmMessage.SLOT_ID_1, DM_IMS_SMS_REG_STAT_INIT);

        resetUESavedSimMeid();

        Settings.Global.putString(getContentResolver(), DM_SWVER, mDmMessage.getSwVer());
        Settings.Global.putString(getContentResolver(), DM_SW_BUILD_DATE, mDmMessage.getSwBuildDate());
    }

    private void delayStartCdmaSmsReg(int slot) {
        DmUtil.startBcAlarm(this, DM_CDMA_REG_DELAY_TIME,
                            DmReceiver.DM_SERVER_CDMA_SMS_RETRY_ALARM,
                            slot);
    }

    private void startCdmaSmsRetryAlarm(int slot) {
        String def = getResources().getString(R.string.smsservice_interval);
        int interval = Integer.parseInt(ProperUtils.getProperties(this)
                    .getProperty(ProperUtils.PROPER_SMSSERVICE_INTERVAL, def));

        DmUtil.startBcAlarm(this, interval/1000,
                            DmReceiver.DM_SERVER_CDMA_SMS_RETRY_ALARM,
                            slot);
    }

    private void cancelCdmaSmsRetryAlarm() {
        DmUtil.cancelBcAlarm(this, DmReceiver.DM_SERVER_CDMA_SMS_RETRY_ALARM);
    }

    private void startImsSmsRetryAlarm(int slot) {
        String def = getResources().getString(R.string.smsservice_interval);
        int interval = Integer.parseInt(ProperUtils.getProperties(this)
                    .getProperty(ProperUtils.PROPER_SMSSERVICE_INTERVAL, def));

        DmUtil.startBcAlarm(this,
                            interval/1000,
                            DmReceiver.DM_SERVER_IMS_SMS_RETRY_ALARM,
                            slot);
    }

    private void cancelImsSmsRetryAlarm() {
        DmUtil.cancelBcAlarm(this, DmReceiver.DM_SERVER_IMS_SMS_RETRY_ALARM);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "DmSmsService onDestroy");
        mSelfRegThread.quit();
        mInstance = null;
        Process.killProcess(Process.myPid());
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.d(TAG, "DmSmsService onBind");
        return super.onBind(intent);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        Log.d(TAG,"onStart, action = " + action);

        if (null == action) {
            return;
        }

        switch (action) {
            case DmReceiver.DM_SMS_SERVER_ACTION_BOOT:
                onBoot();
                break;
            case DmReceiver.DM_SMS_SERVER_ACTION_SIM_READY:
                {
                    int slot = intent.getIntExtra("slot", -1);
                    Log.d(TAG, "onStart, slot: " + slot);

                    if (mDmMessage.isCdmaNetwork(slot)) {
                        doCdmaSmsSend(slot);
                    }
                }
                break;
            case DmReceiver.DM_SMS_SERVER_ACTION_SIM_ABSENT:
                {
                    int slot = intent.getIntExtra("slot", -1);
                    Log.d(TAG, "onStart, slot: " + slot);

                    setUESavedSimMeid(slot, DmMessage.MEID_DEFAULT_VALUE);
                }
                break;
            case DmReceiver.DM_SMS_SERVER_ACTION_CDMA_REG:
                {
                    int slot = intent.getIntExtra("slot", -1);
                    Log.d(TAG, "onStart, slot: " + slot);

                    if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
                        if (mDmMessage.isServiceStateInService(slot)) {
                            delayStartCdmaSmsReg(slot);
                        } else {
                            Log.d(TAG, "onStart, sim not in service, ignore... ");
                        }
                    }
                }
                break;
            case DmReceiver.DM_SMS_SERVER_ACTION_IMS_REG:
                {
                    int slot = intent.getIntExtra("slot", -1);
                    Log.d(TAG, "onStart, slot: " + slot);

                    if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
                        if (mDmMessage.isServiceStateInService(slot)) {
                            doImsSmsSend(slot);
                        } else {
                            Log.d(TAG, "onStart, sim not in service, ignore... ");
                        }
                    }
                }
                break;
            case DmReceiver.DM_SMS_SERVER_ACTION_IMS_RETRY:
                {
                    int slot = intent.getIntExtra("slot", -1);
                    Log.d(TAG, "onStart, slot: " + slot);

                    if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
                        doImsSmsSend(slot);
                    }
                }
                break;
            case DmReceiver.DM_SMS_SERVER_ACTION_CDMA_RETRY:
                int slot = intent.getIntExtra("slot", -1);
                Log.d(TAG, "onStart, slot: " + slot);

                if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
                    doCdmaSmsSend(slot);
                }
                break;
            case DmReceiver.DM_SMS_SERVER_ACTION_SEND_STATUS:
                handleSendStatus(intent);
                break;
            default:

                break;
        }
    }

    public void stopSmsSelfReg() {
        if (isCdmaSmsSelfRegStart(DmMessage.SLOT_ID_0)
         || isCdmaSmsSelfRegStart(DmMessage.SLOT_ID_1)) {
            Log.d(TAG,"stopSmsSelfReg, cdma sms is starting register, can not stop.");
            return;
        }

        if (isImsSmsSelfRegStart(DmMessage.SLOT_ID_0)
         || isImsSmsSelfRegStart(DmMessage.SLOT_ID_1)) {
            Log.d(TAG,"stopSmsSelfReg, ims sms is starting register, can not stop.");
            return;
        }
        stopSelf();
    }

    private void onBoot() {
        String def = getResources().getString(R.string.sms_retry_times);
        int smsRetryTimes = Integer.parseInt(ProperUtils.getProperties(this)
                .getProperty(ProperUtils.PROPER_SMS_RETRY_TIMES, def));
        Log.d(TAG,"onBoot, smsRetryTimes = " + smsRetryTimes);
        putCdmaSmsRetryTimes(smsRetryTimes);
        putImsSmsRetryTimes(smsRetryTimes);
    }

    private String getRegTag(int slot) {
        String reg_tag = DM_CDMA_SMS_SELF_REG1;
        if (DmMessage.SLOT_ID_1 == slot) {
            reg_tag = DM_CDMA_SMS_SELF_REG2;
        }
        Log.d(TAG,"getRegTag, reg_tag = " + reg_tag);
        return reg_tag;
    }

    private String getUESavedMeidTag(int slot) {
        String dm_ue_meid_sim = DM_UE_MEID_IN_SIM1;
        if (DmMessage.SLOT_ID_1 == slot) {
            dm_ue_meid_sim = DM_UE_MEID_IN_SIM2;
        }
        Log.d(TAG,"getUESavedMeidTag, dm_ue_meid_sim = " + dm_ue_meid_sim);
        return dm_ue_meid_sim;
    }

    private String getUESavedSimMeid(int slot) {
        String ue_saved_sim_meid = Settings.Global.getString(getContentResolver(), getUESavedMeidTag(slot));
        Log.d(TAG,"getUESavedSimMeid, ue_saved_sim_meid = " + ue_saved_sim_meid);

        if (DmMessage.MEID_DEFAULT_VALUE.equals(ue_saved_sim_meid)) {
            return mDmMessage.getMeidInSim(slot);
        } else {
            return ue_saved_sim_meid;
        }
    }

    private void setUESavedSimMeid(int slot, String meid) {
        Log.d(TAG,"setUESavedSimMeid, slot = " + slot + ", meid = " + meid);

        if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
            Settings.Global.putString(getContentResolver(),
                                      getUESavedMeidTag(slot),
                                      meid);
        }
    }

    private void resetUESavedSimMeid() {
        setUESavedSimMeid(DmMessage.SLOT_ID_0, DmMessage.MEID_DEFAULT_VALUE);
        setUESavedSimMeid(DmMessage.SLOT_ID_1, DmMessage.MEID_DEFAULT_VALUE);
    }

    private boolean isNeedCdmaSelfReg(int slot) {
        Log.d(TAG,"isNeedCdmaSelfReg, Begin, slot = " + slot);

        String cdma_imsi = mDmMessage.getSimCdmaImsi(slot);
        Log.d(TAG,"isNeedCdmaSelfReg, cdma_imsi = " + cdma_imsi);
        if (TextUtils.isEmpty(cdma_imsi)) {
            Log.d(TAG,"isNeedCdmaSelfReg, cdma imsi is null, need not register");
            return false;
        }

        boolean bRet = mDmMessage.isInternationalNetworkRoaming(slot);
        if (bRet) {
            Log.d(TAG,"isNeedCdmaSelfReg, International Network Roaming, need not register");
            return false;
        }

        String self_reg = Settings.Global.getString(getContentResolver(), getRegTag(slot));
        if (DM_CDMA_SMS_REG_INIT.equals(self_reg)) {
            Log.d(TAG,"isNeedCdmaSelfReg, self reg flag is 0, need register");
            return true;
        }

        String ue_cdma_imsi1 = Settings.Global.getString(getContentResolver(),
                                                        DM_UE_CDMA_IMSI_SIM1);
        Log.d(TAG,"isNeedCdmaSelfReg, ue_cdma_imsi1 = " + ue_cdma_imsi1);
        String ue_cdma_imsi2 = Settings.Global.getString(getContentResolver(),
                                                        DM_UE_CDMA_IMSI_SIM2);
        Log.d(TAG,"isNeedCdmaSelfReg, ue_cdma_imsi2 = " + ue_cdma_imsi2);

        if (DmMessage.SLOT_ID_0 == slot) {
            String cdma_imsi1 = mDmMessage.getSimCdmaImsi(slot);
            String cdma_imsi2 = getAnotherSimImsi(DmMessage.SLOT_ID_1);
            Log.d(TAG,"isNeedCdmaSelfReg, cdma_imsi1 = " + cdma_imsi1);
            Log.d(TAG,"isNeedCdmaSelfReg, cdma_imsi2 = " + cdma_imsi2);
            if (!cdma_imsi1.equals(ue_cdma_imsi1) || !cdma_imsi2.equals(ue_cdma_imsi2)) {
                return true;
            }
        } else if (DmMessage.SLOT_ID_1 == slot) {
            String cdma_imsi1 = getAnotherSimImsi(DmMessage.SLOT_ID_0);
            String cdma_imsi2 = mDmMessage.getSimCdmaImsi(slot);
            Log.d(TAG,"isNeedCdmaSelfReg, cdma_imsi1 = " + cdma_imsi1);
            Log.d(TAG,"isNeedCdmaSelfReg, cdma_imsi2 = " + cdma_imsi2);
            if (!cdma_imsi1.equals(ue_cdma_imsi1) || !cdma_imsi2.equals(ue_cdma_imsi2)) {
                return true;
            }
        } else {
            Log.d(TAG,"isNeedCdmaSelfReg, invalidate slot id, need not register");
            return false;
        }

        String meid = mDmMessage.getMeid(slot);
        String meid_sim = getUESavedSimMeid(slot); //mDmMessage.getMeidInSim(slot);
        Log.d(TAG,"isNeedCdmaSelfReg, meid = " + meid);
        Log.d(TAG,"isNeedCdmaSelfReg, meid_sim = " + meid_sim);
        if (!meid.equals(meid_sim)) {
            return true;
        }

        return false;
    }

    private boolean isNeedImsSelfReg(int slot) {
        Log.d(TAG,"isNeedImsSelfReg, Begin, slot = " + slot);

        if (!mDmMessage.isCtccSimCard(slot)) {
            Log.d(TAG,"isNeedImsSelfReg, slot not ctcc sim, need not register");
            return false;
        }

        boolean bRet = mDmMessage.isInternationalNetworkRoaming(slot);
        if (bRet) {
            Log.d(TAG,"isNeedCdmaSelfReg, International Network Roaming, need not register");
            return false;
        }

        if (DmMessage.SLOT_ID_0 == slot) {
            String imsi1= mDmMessage.getSimLteImsi(slot);
            String ueimsi_sim1 = Settings.Global.getString(getContentResolver(),
                                                           DM_UE_LTE_IMSI_SIM1);
            Log.d(TAG,"isNeedImsSelfReg, imsi1 = " + imsi1);
            Log.d(TAG,"isNeedImsSelfReg, ueimsi_sim1 = " + ueimsi_sim1);
            if (null != imsi1 && !(imsi1.equals(ueimsi_sim1))) {
                return true;
            }
        } else if (DmMessage.SLOT_ID_1 == slot) {
            String imsi2= mDmMessage.getSimLteImsi(slot);
            String ueimsi_sim2 = Settings.Global.getString(getContentResolver(),
                                                           DM_UE_LTE_IMSI_SIM2);
            Log.d(TAG,"isNeedImsSelfReg, imsi2 = " + imsi2);
            Log.d(TAG,"isNeedImsSelfReg, ueimsi_sim2 = " + ueimsi_sim2);
            if (null != imsi2 && !(imsi2.equals(ueimsi_sim2))) {
                return true;
            }
        } else {
            Log.d(TAG,"isNeedImsSelfReg, invalidate slot id, need not register");
        }

        return false;
    }


    private void doCdmaSmsRetry(int slot) {
        mCdmaRetryTimes = Settings.Global.getInt(getContentResolver(), DM_CDMA_SMS_RETRY_TIMES, 0);
        Log.i(TAG,"doCdmaSmsRetry, mCdmaRetryTimes = " + mCdmaRetryTimes);

        if (mCdmaRetryTimes > 0) {
            --mCdmaRetryTimes;
            putCdmaSmsRetryTimes(mCdmaRetryTimes);
            startCdmaSmsRetryAlarm(slot);
        }
    }

    private void doCdmaSmsSend(int slot) {
        if (!isCdmaSmsSelfRegStart(slot)) {
            setCdmaSmsSelfRegStarted(slot, true);
        } else {
            Log.d(TAG,"CDMA self register is already started. ");
            return;
        }

        if (!isNeedCdmaSelfReg(slot)) {
            Log.d(TAG,"No need CDMA selfregister ");
            setCdmaSmsSelfRegStarted(slot, false);
            stopSmsSelfReg();
            return;
        }

        cancelCdmaSmsRetryAlarm();
        setCdmaSmsSelfRegStat(slot, DM_CDMA_SMS_REG_STAT_INIT);

        Message message = mSelfRegHandler.obtainMessage(
                                SelfRegisterHandler.MESSAGE_GENERATE_DATA,
                                DM_CDMA_SMS_PROTOCOL,
                                slot);
        mSelfRegHandler.sendMessage(message);

    }

    private void putImsSmsRetryTimes(int times) {
         Settings.Global.putInt(getContentResolver(),
                    DM_IMS_SMS_RETRY_TIMES, times);
    }

    private void doImsSmsRetry(int slot) {
        mImsRetryTimes = Settings.Global.getInt(getContentResolver(), DM_IMS_SMS_RETRY_TIMES, 0);
        Log.i(TAG,"doImsSmsRetry, mImsRetryTimes = " + mImsRetryTimes);

        if (mImsRetryTimes > 0) {
            --mImsRetryTimes;
            putImsSmsRetryTimes(mImsRetryTimes);
            startImsSmsRetryAlarm(slot);
        }
    }

    private void doImsSmsSend(int slot) {
        if (!isImsSmsSelfRegStart(slot)) {
            setImsSmsSelfRegStarted(slot);
        } else {
            Log.d(TAG,"Ims self register is already started. slot: " + slot);
            return;
        }

        if (!isNeedImsSelfReg(slot)) {
            Log.d(TAG,"Not Need selfregister ");
            resetImsSelfRegStartStatus(slot);
            return;
        }

        cancelImsSmsRetryAlarm();
        setImsSmsSelfRegStat(slot, DM_IMS_SMS_REG_STAT_INIT);

        Message message = mSelfRegHandler.obtainMessage(
                                SelfRegisterHandler.MESSAGE_GENERATE_DATA,
                                DM_IMS_SMS_PROTOCOL,
                                slot);
        mSelfRegHandler.sendMessage(message);
    }

    public static DmSmsService getInstance() {
        if (null == mInstance) {
            mInstance = new DmSmsService();
        }
        return mInstance;
    }

    private boolean isCdmaSmsSelfRegStart(int slot) {

        if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
            return is_start_cdma_regist[slot];
        }

        return false;
    }

    private void setCdmaSmsSelfRegStarted(int slot, boolean is_start) {
        if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
            is_start_cdma_regist[slot] = is_start;
        }
    }

    private void resetCdmaSelfRegStartStatus() {
        is_start_cdma_regist[DmMessage.SLOT_ID_0] = false;
        is_start_cdma_regist[DmMessage.SLOT_ID_1] = false;
        setCdmaSmsSelfRegStat(DmMessage.SLOT_ID_0, DM_CDMA_SMS_REG_STAT_INIT);
        setCdmaSmsSelfRegStat(DmMessage.SLOT_ID_1, DM_CDMA_SMS_REG_STAT_INIT);
    }

    private boolean isImsSmsSelfRegStart(int slot) {
        if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
            return is_start_ims_regist[slot];
        }
        return false;
    }

    private void setImsSmsSelfRegStarted(int slot) {
        if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
            is_start_ims_regist[slot] = true;
        }
    }

    private void resetImsSelfRegStartStatus(int slot) {
        if ((DmMessage.SLOT_ID_0 == slot) || (DmMessage.SLOT_ID_1 == slot)) {
            is_start_ims_regist[slot] = false;
            setImsSmsSelfRegStat(slot, DM_IMS_SMS_REG_STAT_INIT);
        }
    }

    private String getAnotherSimImsi(int slot) {

        if (mDmMessage.isCtccSimCard(slot)) {
            return mDmMessage.getSimCdmaImsi(slot);
        } else {
            return mDmMessage.getSimLteImsi(slot);
        }
    }

    private void saveCdmaSelfRegInfo(int slot) {
        /*
            set sefreg flag =0;
            save UEMEID to sim;
            save CDMAIMSI to UE
        */
        Settings.Global.putString(getContentResolver(), getRegTag(slot), DM_CDMA_SMS_REG_INIT);

        Log.d(TAG,"saveCdmaSelfRegInfo, cmda_slot_id = " + slot);

        if (DmMessage.SLOT_ID_0 == slot) {
            String imsi1 = mDmMessage.getSimCdmaImsi(slot);;
            Settings.Global.putString(getContentResolver(), DM_UE_CDMA_IMSI_SIM1, imsi1);//put CDMA IMSI to UE
            Log.d(TAG,"putUeImsi imsi_sim1:" + imsi1);
            String imsi2 = getAnotherSimImsi(DmMessage.SLOT_ID_1);
            Settings.Global.putString(getContentResolver(), DM_UE_CDMA_IMSI_SIM2, imsi2);//save sim2 imsi
            Log.d(TAG,"putUeImsi imsi_sim2:" + imsi2);
        } else if (DmMessage.SLOT_ID_1 == slot) {
            String imsi1 = getAnotherSimImsi(DmMessage.SLOT_ID_0);
            Settings.Global.putString(getContentResolver(), DM_UE_CDMA_IMSI_SIM1, imsi1);//save sim2 imsi
            Log.d(TAG,"putUeImsi imsi_sim1:" + imsi1);

            String imsi2 = mDmMessage.getSimCdmaImsi(slot);;
            Settings.Global.putString(getContentResolver(), DM_UE_CDMA_IMSI_SIM2, imsi2);//put CDMA IMSI to UE
            Log.d(TAG,"putUeImsi imsi_sim2:" + imsi2);
        }
        String meid = mDmMessage.getMeid(slot);
        mDmMessage.setMeidToSim(slot, meid);
        setUESavedSimMeid(slot, meid);
    }

    private void putCdmaSmsRetryTimes(int times) {
         Settings.Global.putInt(getContentResolver(),
                    DM_CDMA_SMS_RETRY_TIMES, times);
    }

    private void saveImsSelfRegInfo(int slot) {
        Log.d(TAG,"saveImsSelfRegInfo, slot = " + slot);

        String lteImsi = mDmMessage.getSimLteImsi(slot);

        if (DmMessage.SLOT_ID_0 == slot) {
            Settings.Global.putString(getContentResolver(), DM_UE_LTE_IMSI_SIM1, lteImsi);//put LTE IMSI to UE
            Log.d(TAG,"Save sim1 LTE imsi:" + lteImsi);
        } else if (DmMessage.SLOT_ID_1 == slot) {
            Settings.Global.putString(getContentResolver(), DM_UE_LTE_IMSI_SIM2, lteImsi);//put LTE IMSI to UE
            Log.d(TAG,"Save sim2 LTE imsi:" + lteImsi);
        }
    }

    private byte[] byteMerger(byte[] msgHead, byte[] mstDate){
        if ((msgHead == null) || (msgHead.length == 0)) {
            return mstDate;
        } else if ((mstDate == null) || (mstDate.length == 0)) {
            return msgHead;
        } else {
            byte[] result = new byte[msgHead.length  + mstDate.length];
            System.arraycopy(msgHead, 0, result, 0, msgHead.length);
            System.arraycopy(mstDate, 0, result, msgHead.length, mstDate.length);
            return result;
        }
    }


    public byte[] generateData(int protocol, int slot) {
        String xmlData = "";
        if (protocol == DM_CDMA_SMS_PROTOCOL) {
            xmlData = generateCdmaRegData(slot);
        } else if (protocol == DM_IMS_SMS_PROTOCOL) {
            xmlData = generateImsRegData(slot);
        }
        int xmlDataLen = xmlData.length();
        Log.d(TAG,"generateData xmlDataLen = :" + xmlDataLen);

        byte[] msgHead = new byte[LENGTH_MESSAGE_HEAD];
        //Set sms head
        msgHead[0] = (byte)protocol;
        msgHead[1] = (byte)0x03;
        msgHead[2] = (byte)(xmlDataLen);
        msgHead[3] = (byte)0x00;
        //merge head and msg
        byte[] dataXml = xmlData.getBytes(StandardCharsets.US_ASCII);
        byte[] data = byteMerger(msgHead,dataXml);

        Log.d(TAG,"protocol = " + protocol + "DmUtil.dumpDataInHex ====>msgData :");

        if (protocol == DM_CDMA_SMS_PROTOCOL) {
            byte[] msgCrc32 = DmUtil.int32ToByteArray(DmUtil.getCRC32(data));
            data = byteMerger(data,msgCrc32);
        }
        DmUtil.dumpDataInHex(data);
        Log.d(TAG,"generateData, result length: " + data.length);

        return data;
    }

    /*
        protocol version: 1 byte: "2" --"02" CDMA SMS; "03" IMS SMS
        cmd type: 1 byte,reg cmd type=3 --"03"
        data length : 1 byte of DATA    --"FF"
        fill bit: 1 byte 00             --"00"
        DATA: less than 128 byte;string ASCII
        <a1>
            <b1>Device Model</b1>
            <b2>MEID number</b2>  ----14 charactor
            <b3>CDMA IMSI</b3>    ----15 charactor
            <b4>Software Version</b4> ----40 charactor
        </a1>
        CRC32 verify: 8 byte;string  ASCII
    */
    public String generateCdmaRegData(int slot) {
        //The max charactor of model and version is 54 = 128 - 14 - 15 - 45(xml tag)
        final int MODEL_LEGTH_MAX = 14;
        final int VERSION_LEGTH_MAX = 39;

        final String beginTag = "<a1>";
        final String endTag = "</a1>";
        final String modelBeginTag = "<b1>";
        final String modelEndTag = "</b1>";
        final String meidBeginTag = "<b2>";
        final String meidEndTag = "</b2>";
        final String imsiBeginTag = "<b3>";
        final String imsiEndTag = "</b3>";
        final String versionBeginTag = "<b4>";
        final String versionEndTag = "</b4>";

        String devMod = mDmMessage.getModel();
        String meid = mDmMessage.getMeid(slot);
        String imsi = mDmMessage.getSimCdmaImsi(slot);
        String swVer = mDmMessage.getSwVer();

        int endIndex = devMod.length();
        if (endIndex > MODEL_LEGTH_MAX) {
            endIndex = MODEL_LEGTH_MAX;
        }
        devMod = devMod.substring(0, endIndex);
        Log.d(TAG,"generateCdmaRegData, devMod = " + devMod);

        int swEndIndex = swVer.length();
        if (swEndIndex > VERSION_LEGTH_MAX) {
            swEndIndex = VERSION_LEGTH_MAX;
        }
        swVer = swVer.substring(0, swEndIndex);
        Log.d(TAG,"generateCdmaRegData, swVer = " + swVer);

        StringBuffer data = new StringBuffer();
        data.append(beginTag);
        data.append(modelBeginTag).append(devMod).append(modelEndTag);
        data.append(meidBeginTag).append(meid).append(meidEndTag);
        data.append(imsiBeginTag).append(imsi).append(imsiEndTag);
        data.append(versionBeginTag).append(swVer).append(versionEndTag);
        data.append(endTag);

        if (data.length() > LENGTH_CDMA_SMS_MAX) {
            Log.w(TAG, "generateCdmaRegData Message length > " + LENGTH_CDMA_SMS_MAX + ", cut it!");
            int exceedLength = data.length() - LENGTH_CDMA_SMS_MAX;
            data = data.delete(data.length() - CDMA_SMS_END_TAG_LEN - exceedLength,
                               data.length() - CDMA_SMS_END_TAG_LEN);
        }

        Log.d(TAG, "generateImsRegData, message: " + data.toString());
        return data.toString();
    }

    /*
        protocol version: 1 byte: "2" --"02" CDMA SMS; "03" IMS SMS
        cmd type: 1 byte,reg cmd type=3 --"03"
        data length : 1 byte of DATA    --"FF"
        fill bit: 1 byte 00             --"00"
        DATA: less than 136 byte;string ASCII
        <a1>
            <b1>Device Model（length <= 20）
            <b2>IMEI1 Number（Dual card terminal - current IMS sms registered card slot）
            <b3>IMEI2 Number（Dual card terminal - another slot, The single card terminal - filled with 15 0's）
            <b4>IMSI Number （LTE IMSI for terminal user card）
            <b5>Software Version（length <= 40）
        </a1>
    */
    public String generateImsRegData(int slot) {
        final String beginTag = "<a1>";
        final String endTag = "</a1>";
        final String modelBeginTag = "<b1>";
        final String modelEndTag = "</b1>";
        final String imei1BeginTag = "<b2>";
        final String imei1EndTag = "</b2>";
        final String imei2BeginTag = "<b3>";
        final String imei2EndTag = "</b3>";
        final String imsiBeginTag = "<b4>";
        final String imsiEndTag = "</b4>";
        final String versionBeginTag = "<b5>";
        final String versionEndTag = "</b5>";

        String devMod = mDmMessage.getModel();
        String swVer = mDmMessage.getSwVer();
        String imei1 = mDmMessage.getImei(slot);
        int anotherSlot = (DmMessage.SLOT_ID_0 == slot ? DmMessage.SLOT_ID_1 : DmMessage.SLOT_ID_0);
        String imei2 = mDmMessage.getImei(anotherSlot);
        String imsi = mDmMessage.getSimLteImsi(slot);

        Log.d(TAG,"generateImsRegData getModel, devMod = " + devMod);
        Log.d(TAG,"generateImsRegData getSwVer, swVer = " + swVer);

        StringBuffer data = new StringBuffer();
        data.append(beginTag);
        data.append(modelBeginTag).append(devMod);//.append(modelEndTag);
        data.append(imei1BeginTag).append(imei1);//.append(imei1EndTag);
        data.append(imei2BeginTag).append(imei2);//.append(imei2EndTag);
        data.append(imsiBeginTag).append(imsi);//.append(imsiEndTag);
        data.append(versionBeginTag).append(swVer);//.append(versionEndTag);
        data.append(endTag);

        if (data.length() > LENGTH_IMS_SMS_MAX) {
            Log.w(TAG, "generateImsRegData Message length > " + LENGTH_IMS_SMS_MAX + ", cut it!");
            int exceedLength = data.length() - LENGTH_IMS_SMS_MAX;
            data = data.delete(data.length() - IMS_SMS_END_TAG_LEN - exceedLength,
                               data.length() - IMS_SMS_END_TAG_LEN);
        }

        Log.d(TAG, "generateImsRegData, message: " + data.toString());
        return data.toString();
    }

    private String getImsiInCdmaSmsResp(final String response) {
        Log.d(TAG,"getImsiInCdmaSmsResp, response = " + response);

        final String startTag = "<b3>";
        final String endTag = "</b3>";
        final int startIndex = response.indexOf(startTag);
        final int endIndex = response.indexOf(endTag);

        Log.d(TAG,"getImsiInCdmaSmsResp, startIndex = " + startIndex);
        Log.d(TAG,"getImsiInCdmaSmsResp, endIndex = " + endIndex);

        if (startIndex == -1 || endIndex == -1) {
            return null;
        }

        String imsiInSmsResp = response.substring(startIndex + startTag.length(), endIndex);

        Log.d(TAG,"getImsiInCdmaSmsResp, imsiInSmsResp = " + imsiInSmsResp);

        return imsiInSmsResp;
    }

    private int checkSmsRegResponse (final String response, final int slot) {

        byte [] responseData = response.getBytes(StandardCharsets.US_ASCII);
        Log.d(TAG,"checkCdmaResponse dumpDataInHex responseData====>");
        DmUtil.dumpDataInHex(responseData);

        if (responseData[0] == 0x02
                && responseData[1] == 0x04) {
            return DM_CDMA_SMS_REG_RESPONSE_SUCCESS;
        } else if (responseData[0] == 0x01
                && responseData[1] == 0x04) {
            return DM_CDMA_SMS_REG_RESPONSE_SUCCESS;
        } else if (responseData[0] == 0x02
                && responseData[1] == 0x03) {
            String savedSimImsi = DM_UE_CDMA_IMSI_SIM1;
            if (DmMessage.SLOT_ID_1 == slot) {
                savedSimImsi = DM_UE_CDMA_IMSI_SIM2;
            }
            String ueSavedCdmaImsi = Settings.Global.getString(getContentResolver(), savedSimImsi);
            String imsiInSms = getImsiInCdmaSmsResp(response);
            Log.d(TAG,"checkCdmaResponse, ueSavedCdmaImsi = " + ueSavedCdmaImsi);
            Log.d(TAG,"checkCdmaResponse, imsiInSms = " + imsiInSms);
            if (ueSavedCdmaImsi.equals(imsiInSms)) {
                return DM_CDMA_SMS_REG_RESPONSE_SUCCESS;
            }
        } else if (responseData[0] == 0x03
                && responseData[1] == 0x04) {
            Log.d(TAG,"checkImsResponse register success");
            return DM_IMS_SMS_REG_RESPONSE_SUCCESS;
        }

        return DM_SMS_REG_RESPONSE_FAIL;
    }

    private Boolean checkCdmaResponse(final String response, final int slot) {
        /*check object sms
            if return "byte0,byte0";  "0x02,0x04" ---sucess;
            else return 5-1 format,then check IMSI in message with IMSI-IN-UE;and MEID with MEID-UE
        */
        byte [] responseData = response.getBytes(StandardCharsets.US_ASCII);
        Log.d(TAG,"checkCdmaResponse dumpDataInHex responseData====>");
        DmUtil.dumpDataInHex(responseData);

        if (responseData[0] == 0x02
        && responseData[1] == 0x04) {
            return true;
        } else if (responseData[0] == 0x01
               && responseData[1] == 0x04) {
            return true;
        } else if (responseData[0] == 0x02
               && responseData[1] == 0x03) {
            String savedSimImsi = DM_UE_CDMA_IMSI_SIM1;
            if (DmMessage.SLOT_ID_1 == slot) {
              savedSimImsi = DM_UE_CDMA_IMSI_SIM2;
            }
            String ueSavedCdmaImsi = Settings.Global.getString(getContentResolver(), savedSimImsi);
            String imsiInSms = getImsiInCdmaSmsResp(response);
            Log.d(TAG,"checkCdmaResponse, ueSavedCdmaImsi = " + ueSavedCdmaImsi);
            Log.d(TAG,"checkCdmaResponse, imsiInSms = " + imsiInSms);
            if (ueSavedCdmaImsi.equals(imsiInSms)) {
                return true;
            }
        }

        return false;
    }

    private void handleCdmaSelfRegSuccess(final String response, final int slot) {

        int regStat = getCdmaSmsSelfRegStat(slot);
        Log.d(TAG,"Cdma Sms self regStat: " + regStat);
        if (DM_CDMA_SMS_REG_STAT_WAIT_REPLY != regStat) {
            Log.d(TAG,"Cdma Sms self reg ignore server sms reply");
            return;
        }


        Settings.Global.putString(getContentResolver(),
                    getRegTag(slot), DM_CDMA_SMS_REG_SUCCESS);
        Log.d(TAG,"Cdma Sms self register success!");


        setCdmaSmsSelfRegStarted(slot, false);
        stopSmsSelfReg();
    }

    private void handleImsSelfRegSuccess(final String response, final int slot) {

        saveImsSelfRegInfo(slot);
        Log.d(TAG,"Ims sms self register success!");

        resetImsSelfRegStartStatus(slot);
        stopSmsSelfReg();
    }

    private Boolean checkImsResponse(final String response, final int slot) {
        /*
            check object sms
            if return "byte0,byte1";  "0x03,0x04" ---sucess;
         */
        byte [] responseData = response.getBytes(StandardCharsets.US_ASCII);
        Log.d(TAG,"checkImsResponse dumpDataInHex responseData====>");
        DmUtil.dumpDataInHex(responseData);

        if (responseData[0] == 0x03
         && responseData[1] == 0x04) {
            Log.d(TAG,"checkImsResponse register success");
            return true;
        }

        return false;
    }

    private void prepareProp() {
        ProperUtils.createPropFile(this);
        Properties prop = ProperUtils.getProperties(this);
        Log.d(TAG,"prepareProp size="+prop.size());

        int sms_interval = Integer.parseInt(
            prop.getProperty(ProperUtils.PROPER_SMSSERVICE_INTERVAL, "0"));
        if (sms_interval == 0) {
            prop.setProperty(ProperUtils.PROPER_SMSSERVICE_INTERVAL, getResources().getString(R.string.smsservice_interval));
            prop.setProperty(ProperUtils.PROPER_SMS_RETRY_TIMES, getResources().getString(R.string.sms_retry_times));
            prop.setProperty(ProperUtils.PROPER_SMS_SERVER, getResources().getString(R.string.dm_sms_server));
            prop.setProperty(ProperUtils.PROPER_IMS_SMS,getResources().getString(R.string.dm_ims_sms));
            ProperUtils.saveConfig(this, prop);
        }
    }

    private class SelfRegisterHandler extends Handler {
        public static final int MESSAGE_GENERATE_DATA = 1;

        public SelfRegisterHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case MESSAGE_GENERATE_DATA:
                    Log.d(TAG,"SelfRegisterHandler:generate data");
                    int protocol = message.arg1;
                    int slot = message.arg2;
                    boolean bRet = sendSmsRegDataMessage(protocol, slot);
                    if (protocol == DM_CDMA_SMS_PROTOCOL) {
                        handleCdmaSmsSendResult(slot, bRet);
                    } else if (protocol == DM_IMS_SMS_PROTOCOL) {
                        handleImsSmsSendResult(slot, bRet);
                    }
                    break;
            }
        }
    }

    private void handleCdmaSmsSendResult(int slot, boolean bRet) {
        if (bRet) {
            saveCdmaSelfRegInfo(slot);
            setCdmaSmsSelfRegStat(slot, DM_CDMA_SMS_REG_STAT_WAIT_REPLY);
        } else {
            doCdmaSmsRetry(slot);
            setCdmaSmsSelfRegStarted(slot, false);
        }
    }

    private void handleImsSmsSendResult(int slot, boolean bRet) {
        if (bRet) {
            // Wait for response from CTCC IMS DMserver
            setImsSmsSelfRegStat(slot, DM_IMS_SMS_REG_STAT_WAIT_REPLY);
        } else {
            doImsSmsRetry(slot);
            resetImsSelfRegStartStatus(slot);
        }
    }

    private static Intent getSendStatusIntent(final Context context, final String action,
        final Uri requestUri, final int partId, final int subId) {
        // Encode requestId in intent data
        final Intent intent = new Intent(action, requestUri, context, DmReceiver.class);
        intent.putExtra(DmReceiver.EXTRA_PART_ID, partId);
        intent.putExtra(DmReceiver.EXTRA_SUB_ID, subId);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return intent;
    }

    private boolean sendSmsRegDataMessage(int protocol, int slot) {
        Log.d(TAG,"sendSmsRegDataMessage: slot = " + slot + ",protocol = " + protocol);

        byte[] data = generateData(protocol, slot);
        if (data == null) {
            return false;
        }

        int dataLen = data.length;

        Log.d(TAG,"sendSmsRegDataMessage, dataLen = " + dataLen);

        Log.d(TAG,"sendSmsRegDataMessage ====>data :");
        DmUtil.dumpDataInHex(data);

        if (dataLen > SMS_LENGTH_MAX) {
            Log.d(TAG,"sendSmsRegDataMessage, Data length over 140");
            return false;
        }

        final int subId = mDmMessage.getSubId(slot);
        final SmsManager smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);

        Log.d(TAG,"sendSmsRegDataMessage: slot = " + slot + ", subId = " + subId);

        final Uri messageUri = Uri.parse(mDmMessage.getTimeStamp());
        final int partId = 0;

        PendingIntent deliveryIntent = PendingIntent.getBroadcast(
                                            this,
                                            partId,
                                            getSendStatusIntent(this,
                                                    DmReceiver.MESSAGE_DELIVERED_ACTION,
                                                    messageUri, partId, subId),
                                            0/*flag*/);
        PendingIntent sentIntent = PendingIntent.getBroadcast(
                                        this,
                                        partId,
                                        getSendStatusIntent(this,
                                                DmReceiver.MESSAGE_SENT_ACTION,
                                                messageUri, partId, subId),
                                        0/*flag*/);

        // Prepare the send result, which collects the send status for each part
        final SendResult pendingResult = new SendResult(1);
        mPendingMessageMap.put(messageUri, pendingResult);
        Log.d(TAG,"sendSmsRegDataMessage: messageUri = " + messageUri);
        Log.d(TAG,"sendSmsRegDataMessage: mPendingMessageMap = " + mPendingMessageMap);

        final short port = 0;

        Log.d(TAG,"sendSmsRegDataMessage: port = " + port);

        if (DM_CDMA_SMS_PROTOCOL == protocol) {
            setCdmaSmsSelfRegStat(slot, DM_CDMA_SMS_REG_STAT_SENDING);
        } else if (DM_IMS_SMS_PROTOCOL == protocol) {
            setImsSmsSelfRegStat(slot, DM_IMS_SMS_REG_STAT_SENDING);
        } else {
            Log.d(TAG, "sendSmsRegDataMessage: invalidate sms registrator protocol:" + protocol);
        }

        smsManager.sendDataMessage(mDmSmsServerNum,
                                null,port,
                                data,
                                sentIntent,
                                deliveryIntent);

        synchronized (pendingResult) {

            // we have the send results of all parts.
            while (pendingResult.hasPending()) {
                try {
                    pendingResult.wait(SMS_WAIT_SEND_RESULT_TIME);
                    Log.d(TAG,"sendSmsRegDataMessage: waiting for message send result... ");
                } catch (final InterruptedException e) {
                    Log.e(TAG, "sendSmsRegDataMessage: sending wait interrupted", e);
                }
            }
        }

        Log.d(TAG, "sendSmsRegDataMessage: sending completed. " +
                    "dest=" + mDmSmsServerNum +
                    " result=" + pendingResult);

        mPendingMessageMap.remove(messageUri);

        if (SendResult.FAILURE_LEVEL_NONE == pendingResult.getHighestFailureLevel()) {
            switch(protocol) {
                case DM_CDMA_SMS_PROTOCOL:
                    Log.d(TAG, "sendSmsRegDataMessage : send selfregister CDMA sms success.");
                    break;
                case DM_IMS_SMS_PROTOCOL:
                    Log.d(TAG, "sendSmsRegDataMessage : send selfregister IMS sms success.");
                    break;
                default:
                    break;
            }
            return true;
        }

        Log.d(TAG, "sendSmsRegDataMessage: send selfregister sms failed. Protocol = " + protocol);
        return false;
    }

    @Override
    public void onReceiveTextSms(@NonNull MessagePdu pdu, @NonNull String format,
            int destPort, int subId, @NonNull final ResultCallback<Integer> callback) {

        Log.d(TAG,"onReceiveTextSms: format = " + format);
        Log.d(TAG,"onReceiveTextSms: destPort = " + destPort);
        Log.d(TAG,"onReceiveTextSms: subId = " + subId);
        Log.d(TAG,"onReceiveTextSms: pdu = " + pdu);

        onFilterSms(pdu, format, destPort, subId, new ResultCallback<Boolean>() {
            @Override
            public void onReceiveResult(Boolean result) throws RemoteException {
                Log.d(TAG,"onFilterSms, onReceiveResult: result = " + result);
                boolean needSave = true; //(true: The user will see this message)
                String response = parseTextSms(pdu, format);

                int slotId = mDmMessage.getSlotId(subId);
                Log.d(TAG,"onFilterSms: slotId = " + slotId);

                if (response != null) {
                    needSave = false;
                }

                callback.onReceiveResult(needSave ? RECEIVE_OPTIONS_DEFAULT : RECEIVE_OPTIONS_DROP
                    | RECEIVE_OPTIONS_SKIP_NOTIFY_WHEN_CREDENTIAL_PROTECTED_STORAGE_UNAVAILABLE);

                if (response != null && (slotId == 0 || slotId == 1)) {
                    int iRet =  checkSmsRegResponse(response, slotId);
                    Log.d(TAG, "sms self register response is "+iRet);
                    switch (iRet) {
                        case DM_CDMA_SMS_REG_RESPONSE_SUCCESS:
                            handleCdmaSelfRegSuccess(response, slotId);
                            break;
                        case DM_IMS_SMS_REG_RESPONSE_SUCCESS:
                            handleImsSelfRegSuccess(response, slotId);
                            break;
                        default:
                            Log.d(TAG, "sms self register fail! response = " + iRet);
                            break;
                    }
                }
            }
        });
    }

    private static String parseTextSms(final MessagePdu msgPdu, final String format) {

        Log.d(TAG,"onReceiveTextSms: format = " + format);

        List<byte[]> pduList = msgPdu.getPdus();

        String fromAddress = "";
        String fullMsgBody;
        StringBuilder stringBuilder = new StringBuilder();
        int teleServiceID = -1;

        for(int j = 0; j < pduList.size(); j++) {
            Log.d(TAG,"DmUtil.dumpDataInHex ====>" + "pduList[" + j + "]:");
            DmUtil.dumpDataInHex(pduList.get(j));

            SmsMessage message = SmsMessage.createFromPdu(pduList.get(j), format);

            if (message == null) {
                Log.d(TAG,"onReceiveTextSms: createFromPdu failed. ");
                continue;
            }

            fromAddress = message.getOriginatingAddress();
            Log.d(TAG,"onReceiveTextSms: fromAddress = " + fromAddress);

            byte[] userData = message.getUserData();
            int dataLen = userData.length;

            stringBuilder.append(new String(userData, 0, dataLen, StandardCharsets.US_ASCII));

            if (SmsMessage.FORMAT_3GPP2.equals(format)) {
                com.android.internal.telephony.cdma.SmsMessage cdmaSmsMsg =
                    (com.android.internal.telephony.cdma.SmsMessage)message.mWrappedSmsMessage;
                teleServiceID = cdmaSmsMsg.getTeleService();
                Log.d(TAG,"onReceiveTextSms: teleServiceID = " + teleServiceID);
            }
        }

        fullMsgBody = stringBuilder.toString();


        if (mDmSmsServerNum.equals(fromAddress)) {
            Log.d(TAG,"onReceiveTextSms: fullMsgBody = " + fullMsgBody);
            return fullMsgBody;
        }

        return null;
    }


    /**
     * Class that holds the sent status for all parts of a multipart message sending
     */
    public static class SendResult {
        // Failure levels, used by the caller of the sender.
        // For temporary failures, possibly we could retry the sending
        // For permanent failures, we probably won't retry
        public static final int FAILURE_LEVEL_NONE = 0;
        public static final int FAILURE_LEVEL_TEMPORARY = 1;
        public static final int FAILURE_LEVEL_PERMANENT = 2;

        // Tracking the remaining pending parts in sending
        private int mPendingParts;
        // Tracking the highest level of failure among all parts
        private int mHighestFailureLevel;

        public SendResult(final int numOfParts) {
            //Assert.isTrue(numOfParts > 0);
            mPendingParts = numOfParts;
            mHighestFailureLevel = FAILURE_LEVEL_NONE;
        }

        // Update the sent status of one part
        public void setPartResult(final int resultCode) {
            mPendingParts--;
            setHighestFailureLevel(resultCode);
        }

        public boolean hasPending() {
            return mPendingParts > 0;
        }

        public int getHighestFailureLevel() {
            return mHighestFailureLevel;
        }

        private int getFailureLevel(final int resultCode) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    return FAILURE_LEVEL_NONE;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    return FAILURE_LEVEL_TEMPORARY;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    return FAILURE_LEVEL_PERMANENT;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    return FAILURE_LEVEL_PERMANENT;
                default: {
                    Log.e("DmSmsService", "SendResult: Unexpected sent intent resultCode = " + resultCode);
                    return FAILURE_LEVEL_PERMANENT;
                }
            }
        }

        private void setHighestFailureLevel(final int resultCode) {
            final int level = getFailureLevel(resultCode);
            if (level > mHighestFailureLevel) {
                mHighestFailureLevel = level;
            }
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("SendResult:");
            sb.append("Pending=").append(mPendingParts).append(",");
            sb.append("HighestFailureLevel=").append(mHighestFailureLevel);
            return sb.toString();
        }
    }

    private void setResult(final Uri requestId, final int resultCode,
        final int errorCode, final int partId, int subId) {

        SendResult result = null;
        if (requestId != null) {
            Log.d(TAG,"setResult: requestId = " + requestId);
            Log.d(TAG,"setResult: mPendingMessageMap = " + mPendingMessageMap);
            result = mPendingMessageMap.get(requestId);
            if (result != null) {
                synchronized (result) {
                    result.setPartResult(resultCode);
                    if (!result.hasPending()) {
                        result.notifyAll();
                    }
                }
            } else {
                Log.e(TAG, "Ignoring sent result. " + " requestId=" + requestId
                        + " partId=" + partId + " resultCode=" + resultCode);
            }
        }
    }

    private void handleSendStatus(Intent intent) {
        final String requestIdStr = intent.getStringExtra("Uri");
        Uri requestId = Uri.parse(requestIdStr);
        Log.d(TAG, "handleSendStatus, requestId = " + requestId);

        int resultCode = intent.getIntExtra("resultCode", -1);

        Log.d(TAG, "handleSendStatus, resultCode = " + resultCode);

        Log.d(TAG, "handleSendStatus, EXTRA_ERROR_CODE: "
                 + intent.getIntExtra(DmReceiver.EXTRA_ERROR_CODE, DmReceiver.NO_ERROR_CODE));
        Log.d(TAG, "handleSendStatus, EXTRA_PART_ID: "
                 + intent.getIntExtra(DmReceiver.EXTRA_PART_ID, DmReceiver.NO_PART_ID));
        Log.d(TAG, "handleSendStatus, EXTRA_ERROR_CODE: "
                 + intent.getIntExtra(DmReceiver.EXTRA_SUB_ID, DmReceiver.DEFAULT_SUB_ID));

        setResult(
                requestId,
                resultCode,
                intent.getIntExtra(DmReceiver.EXTRA_ERROR_CODE, DmReceiver.NO_ERROR_CODE),
                intent.getIntExtra(DmReceiver.EXTRA_PART_ID, DmReceiver.NO_PART_ID),
                intent.getIntExtra(DmReceiver.EXTRA_SUB_ID, DmReceiver.DEFAULT_SUB_ID));
    }

}
