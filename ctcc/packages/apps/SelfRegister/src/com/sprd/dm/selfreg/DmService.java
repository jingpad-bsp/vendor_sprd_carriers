package com.sprd.dm.mbselfreg;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.Calendar;
import java.util.Properties;
import org.json.JSONException;
import org.json.JSONObject;

public class DmService extends Service {
    private static final String TAG = "DmService";

    private NetworkUtils mNetworkUtils;
    private DmMessage mDmMessage;
    private static DmService mInstance = null;
    private HandlerThread mSelfRegThread;
    private Handler mSelfRegHandler;

    private static final String RESPONSE_CODE_STRING = "resultCode";
    private static final String RESPONSE_DESC_STRING = "resultDesc";
    private static final String[] RESPONSE_CODE = new String[] {
      "0",    "1",    "2"
    };
    private static final String[] RESPONSE_DESC = new String[] {
      "Success",    "Decode error",    "Check error"
    };

    private static final String[] DM_REG_STATE_DESC = new String[] {
      "Normal",    "Retry",    "Resend",    "Volte changed", "Master sim changed"
    };

    private static final String DM_UEICCID_MASTER_SIM = "dm_ueiccid_master_sim";
    private static final String DM_UEICCID_SLAVE_SIM = "dm_ueiccid_slave_sim";

    private static final String DM_UEICCID_SIM1 = "dm_ueiccid_sim1";
    private static final String DM_UEICCID_SIM2 = "dm_ueiccid_sim2";
    private static final String DM_SWVER = "dm_swver";
    private static final String DM_SW_BUILD_DATE = "dm_last_sw_build_date";
    private static final String DM_RETRY_TIMES = "dm_selfReg_retry_times";
    private static final String DM_RESEND_TIMES = "dm_selfReg_resend_times";
    private static final String DM_RESEND_TIME_POINT = "dm_selfReg_resend_tp";
    private static final String EMPTY = "";

    public final int DM_REG_STATE_NORMAL = 0;
    public final int DM_REG_STATE_RETRY = 1;
    public final int DM_REG_STATE_RESEND = 2;
    public final int DM_REG_STATE_VOLTE = 3;
    public final int DM_REG_STATE_DATASIM_CHANGED = 4;

    public final int DM_FORM_PHONE_SINGLESIM = 1;
    public final int DM_FORM_PHONE_DUALSIM = 2;
    public final int DM_FORM_PAN_SMART_DEV = 3;

    private final int mCycle = 30*24; //30 days

    private static final int[] mDelayTime = {150,20,20,20,20,20,20,20}; //retry 8 times
    private static final int DM_WAIT_COUNT_MAX = mDelayTime.length;
    private static int mWaitCount = 0;

    private static final int DM_HALF_DAY_HOURS = 12; //hours
    private static final int DM_SECONDS_PER_HOUR = 60*60; //seconds
    private static final long DM_MS_PER_SECOND = 1000L;
    private int mRetryTimes = 0;
    private int mReSendTimes = DM_HALF_DAY_HOURS;
    private int mRegState = DM_REG_STATE_NORMAL;
    private boolean is_connect_trigger = false;

    public boolean is_start_regist = false;
    public boolean is_first_bootup = false;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        mNetworkUtils = new NetworkUtils(this);
        mNetworkUtils.registeReceiver();
        mDmMessage = new DmMessage(this);

        prepareProp();

        mSelfRegThread = new HandlerThread("dm-selfReg-message");
        mSelfRegThread.start();
        mSelfRegHandler = new SelfRegisterHandler(mSelfRegThread.getLooper());

        mInstance = this;

        //if sw version has changed,clear up ueiccid.
        if (isSwVerChanged()) {
            resetDataRegStatus();
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

    private void resetDataRegStatus() {
        resetUeIccid();
        initResendTimes();
    }

    private void resetUeIccid() {
        Settings.Global.putString(getContentResolver(), DM_UEICCID_SIM1, DmMessage.ICCID_DEFAULT_VALUE);
        Settings.Global.putString(getContentResolver(), DM_UEICCID_SIM2, DmMessage.ICCID_DEFAULT_VALUE);
    }

    private void startRetryAlarm() {
        String def = getResources().getString(R.string.service_interval);
        int interval = Integer.parseInt(ProperUtils.getProperties(this)
                    .getProperty(ProperUtils.PROPER_SERVICE_INTERVAL, def));
        Log.d(TAG, "startRetryAlarm, interval = " + interval);
        DmUtil.startBcAlarm(this, (int)(interval / DM_MS_PER_SECOND), DmReceiver.DM_SERVER_RETRY_ALARM);
    }

    private void cancelRetryAlarm() {
        DmUtil.cancelBcAlarm(this, DmReceiver.DM_SERVER_RETRY_ALARM);
    }

    private void startResendAlarms() {
        Log.d(TAG,"startResendAlarms, enter.");

        int resend_s = mCycle * (DM_SECONDS_PER_HOUR);

        int resend_tp = DmUtil.startBcAlarm(this, resend_s,
                                            DmReceiver.DM_SERVER_RESEND_ALARM);

        putResendTimePoint(resend_tp);
    }

    private void cancelResendAlarms() {
        DmUtil.cancelBcAlarm(this, DmReceiver.DM_SERVER_RESEND_ALARM);
    }

    private int calcValidTime(int resend_time) {
        Log.d(TAG,"calcValidTime, enter. resend_time = " + resend_time);

        int resend_period_s = mCycle * (DM_SECONDS_PER_HOUR);
        int current_s = (int)(System.currentTimeMillis() / DM_MS_PER_SECOND);

        Log.d(TAG,"calcValidTime, enter. current_s = " + current_s);

        if (resend_time < current_s) {
            long resend_time_millis = (long)resend_time;
            resend_time_millis = resend_time_millis * DM_MS_PER_SECOND;
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(resend_time_millis);

            while (resend_time < current_s) {
                calendar.add(Calendar.SECOND, resend_period_s);
                recordReSendTimes();
                resend_time = (int)(calendar.getTimeInMillis() / DM_MS_PER_SECOND);
            }

            putResendTimePoint(resend_time);
        }
        Log.d(TAG,"calcValidTime, left. resend_time = " + resend_time);
        return resend_time;
    }

    public void stopSelfReg() {
        if (null != mNetworkUtils) {
            mNetworkUtils.unRegisteReceiver();
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "DmService onDestroy");
        if (null != mSelfRegThread) {
            mSelfRegThread.quit();
            mSelfRegThread = null;
        }

        mInstance = null;
        Process.killProcess(Process.myPid());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        Log.d(TAG,"onStart, action = " + action);
        Log.d(TAG,"onStart, is_start_regist = " + is_start_regist);
        Log.d(TAG,"onStart, is_first_bootup = " + is_first_bootup);

        if (null == action) {
            return;
        }
        switch (action) {
            case DmReceiver.DM_SERVER_ACTION_BOOT:
                onBoot();
                break;
            case DmReceiver.DM_SERVER_ACTION_SIM_READY:
                if (!is_start_regist) {
                    if (mNetworkUtils.isNetworkAvailable()) {
                        is_connect_trigger = true;
                        resetWaitCount();
                        waitSendNextTime();
                    }
                    //Waiting for network.
                }
                break;
            case DmReceiver.DM_SERVER_ACTION_RETRY:
                mRegState = DM_REG_STATE_RETRY;
                sendWhileInService(true);
                break;
            case DmReceiver.DM_SERVER_ACTION_CONNECT:
                if (!is_connect_trigger) {
                    sendWhileInService(true);
                }
                break;
            case DmReceiver.DM_SERVER_ACTION_RESEND:
                mRegState = DM_REG_STATE_RESEND;
                doResend();
                break;
            case DmReceiver.DM_SERVER_ACTION_DATASIM_CHANGE:
                if (!is_start_regist) {
                    if (isMasterSimChanged()) {
                        mRegState = DM_REG_STATE_DATASIM_CHANGED;
                        if (mNetworkUtils.isNetworkAvailable()) {
                            sendWhileInService(true);
                        }
                        //Waiting for network.
                    } else if (isNeedSelfReg()) {
                        mRegState = DM_REG_STATE_NORMAL;
                        if (mNetworkUtils.isNetworkAvailable()) {
                            sendWhileInService(true);
                        }
                        //Waiting for network.
                    }
                }
                break;
            case DmReceiver.DM_SERVER_ACTION_WAITFOR_SERVICE:
                is_connect_trigger = false;
                sendWhileInService(false);
                break;
            default:
                break;
        }
    }

    public boolean canWait() {
        if (mWaitCount < DM_WAIT_COUNT_MAX) {
            return true;
        }
        return false;
    }

    public void waitSendNextTime() {
        Log.d(TAG,"waitSendNextTime, mWaitCount = " + mWaitCount);

        if (mWaitCount < DM_WAIT_COUNT_MAX) {
            int delayTime = mDelayTime[mWaitCount];
            DmUtil.startBcAlarm(this, delayTime, DmReceiver.DM_SERVER_WAITFOR_SERVICE_ALARM);
            mWaitCount++;
        }
    }

    public void resetWaitCount() {
        mWaitCount = 0;
        DmUtil.cancelBcAlarm(this, DmReceiver.DM_SERVER_WAITFOR_SERVICE_ALARM);
    }

    public void sendWhileInService(boolean ingoreLastReg) {
        boolean needWait = false;

        if (ingoreLastReg) {
            resetWaitCount();
        }

        Log.d(TAG,"sendWhileInService, mWaitCount = " + mWaitCount);

        if (canWait()) {
            if ( (mWaitCount == 0 && mRegState != DM_REG_STATE_RESEND)
                || !getNetworkUtils().isPhoneInService()
                || !getNetworkUtils().isNetworkAvailable()) {
                needWait = true;
            }
        }

        if (!needWait) {
            doSend();
        } else {
            waitSendNextTime();
        }
    }

    private void resetRetryTimes() {
        String def = getResources().getString(R.string.retry_times);
        mRetryTimes = Integer.parseInt(ProperUtils.getProperties(this)
                .getProperty(ProperUtils.PROPER_RETRY_TIMES, def));
        Log.d(TAG,"resetRetryTimes, mRetryTimes = " + mRetryTimes);
        putRetryTimes(mRetryTimes);
    }

    private void onBoot() {
        resetRetryTimes();
        is_first_bootup = true;

        int resend_tp = getResendTimePoint();
        if (resend_tp != 0) {
            resend_tp = calcValidTime(resend_tp);
            DmUtil.startBcAlarmByTp(this, resend_tp, DmReceiver.DM_SERVER_RESEND_ALARM);
        }
    }

    private boolean isNeedSelfReg() {

        int simcount = mDmMessage.getSlotCount();
        Log.d(TAG,"isNeedSelfReg, simcount = "+simcount);
        if (simcount == mDmMessage.SINGLE_SIM_COUNT) {
            if (mDmMessage.isSimAbsent(DmMessage.SLOT_ID_0)) {
                return false;
            }
            String ueiccid_sim1 = Settings.Global.getString(getContentResolver(), DM_UEICCID_SIM1);
            if (DmMessage.ICCID_DEFAULT_VALUE.equals(ueiccid_sim1)) {
                return true;
            }

            String iccid = mDmMessage.getSimIccid(DmMessage.SLOT_ID_0);
            if ((!TextUtils.isEmpty(ueiccid_sim1)) && iccid != null && iccid.equals(ueiccid_sim1)) {
                Log.i(TAG,"isNeedSelfReg, Single Sim Has Registered! UEICCID_SIM1 = "+ueiccid_sim1);
                return false;
            }
        } else if (simcount == mDmMessage.DUAL_SIM_COUNT) {
            if (mDmMessage.isSimAbsent(DmMessage.SLOT_ID_0) &&
               mDmMessage.isSimAbsent(DmMessage.SLOT_ID_1)) {
                return false;
            }
            String ueiccid_sim1 = Settings.Global.getString(getContentResolver(), DM_UEICCID_SIM1);
            String ueiccid_sim2 = Settings.Global.getString(getContentResolver(), DM_UEICCID_SIM2);
            Log.d(TAG,"isNeedSelfReg, ueiccid_sim1 = " + ueiccid_sim1 +
                                   ", ueiccid_sim2 = " + ueiccid_sim2);

            if (DmMessage.ICCID_DEFAULT_VALUE.equals(ueiccid_sim1)
              && DmMessage.ICCID_DEFAULT_VALUE.equals(ueiccid_sim2)) {
                return true;
            }

            String iccid_sim1 = mDmMessage.getSimIccid(DmMessage.SLOT_ID_0);
            String iccid_sim2 = mDmMessage.getSimIccid(DmMessage.SLOT_ID_1);

            Log.d(TAG,"isNeedSelfReg, iccid_sim1 = " + iccid_sim1 +
                                   ", iccid_sim2 = " + iccid_sim2);
            boolean equals_state = (!TextUtils.isEmpty(ueiccid_sim1) && iccid_sim1 != null && iccid_sim1.equals(ueiccid_sim1))
                && (!TextUtils.isEmpty(ueiccid_sim2) && iccid_sim2 != null && iccid_sim2.equals(ueiccid_sim2));
            if (equals_state) {
                Log.i(TAG,"isNeedSelfReg, Dual Sim Has Registered! UEICCID_SIM1 = " + ueiccid_sim1 +
                                                                ", UEICCID_SIM2 = " + ueiccid_sim2);
                return false;
            }
        } else {
            Log.e(TAG,"isNeedSelfReg, not support selregister PhoneCount = "+ mDmMessage.getSlotCount());
            return false;
        }
        return true;
    }

    private void doRetry() {
        if (DmService.getInstance().getNetworkUtils().isPhoneInternationalNetworkRoaming()) {
            Log.i(TAG,"International Network Roaming, need not retry.");
            DmService.getInstance().stopSelfReg();
            return;
        }

        mRetryTimes = Settings.Global.getInt(getContentResolver(), DM_RETRY_TIMES, 0);
        Log.i(TAG,"doRetry, mRetryTimes = " + mRetryTimes);

        if (mRetryTimes > 0) {
            --mRetryTimes;
            putRetryTimes(mRetryTimes);
            startRetryAlarm();
        }
        DmService.getInstance().stopSelfReg();
    }

    public void initResendTimes() {
        Log.d(TAG,"initResendTimes(), enter.");
        String resend_times = getResources().getString(R.string.resend_times);
        mReSendTimes = Integer.parseInt(ProperUtils.getProperties(this)
                .getProperty(ProperUtils.PROPER_RESEND_TIMES, resend_times));
        putResendTimes(mReSendTimes);
        putResendTimePoint(0);
    }

    private void recordReSendTimes() {
        mReSendTimes = Settings.Global.getInt(getContentResolver(), DM_RESEND_TIMES, 0);
        Log.i(TAG,"recordReSendTimes(), mReSendTimes = " + mReSendTimes +
                                     ", mCycle = " + mCycle);
        if (mReSendTimes == DM_HALF_DAY_HOURS) {
            mReSendTimes = mCycle;
        } else {
            mReSendTimes += mCycle;
        }
        putResendTimes(mReSendTimes);
    }

    private void doResend() {
        recordReSendTimes();
        Log.i(TAG,"doResend(), mReSendTimes = " + mReSendTimes +
                            ", mCycle = " + mCycle);
        if ((mReSendTimes >= mCycle) && (mReSendTimes % mCycle == 0)) {
            Log.i(TAG,"doResend(), Send self Register ############### ");
            sendWhileInService(true);
        }
    }

    private void doSend() {

        if (!is_start_regist) {
            is_start_regist = true;
        } else {
            Log.d(TAG,"doSend(), Self Register is already started, mRegState = " + mRegState);
            return;
        }

        if (mRegState < DM_REG_STATE_NORMAL || mRegState > DM_REG_STATE_DATASIM_CHANGED) {
            Log.e(TAG,"doSend(), Self Register Status Error, mRegState = " + mRegState);
            stopSelfReg();
            return;
        }

        Log.i(TAG,"doSend(), Self Register Status is: " + DM_REG_STATE_DESC[mRegState]);

        if (mRegState == DM_REG_STATE_NORMAL && !isNeedSelfReg()) {
            Log.i(TAG,"Not Need selfregister ");
            stopSelfReg();
            return;
        }

        if (mRegState == DM_REG_STATE_NORMAL
         || mRegState == DM_REG_STATE_VOLTE
         || mRegState == DM_REG_STATE_DATASIM_CHANGED) {
            initResendTimes();
        }

        DmService.getInstance().mSelfRegHandler.sendEmptyMessage(SelfRegisterHandler.MESSAGE_GENERATE_DATA);

    }

    public static DmService getInstance() {
        if (null == mInstance) {
            mInstance = new DmService();
        }
        return mInstance;
    }

    public NetworkUtils getNetworkUtils() {
        return mNetworkUtils;
    }

    private void putUeIccid() {

        String sim1Iccid = mDmMessage.getSimIccid(DmMessage.SLOT_ID_0);
        Settings.Global.putString(getContentResolver(), DM_UEICCID_SIM1, sim1Iccid);

        Log.i(TAG,"putUeIccid iccid_sim1:" + sim1Iccid);
        if (mDmMessage.DUAL_SIM_COUNT == mDmMessage.getSlotCount()) {
            String sim2Iccid = mDmMessage.getSimIccid(DmMessage.SLOT_ID_1);
            Settings.Global.putString(getContentResolver(), DM_UEICCID_SIM2, sim2Iccid);
            Log.i(TAG,"putUeIccid iccid_sim2:" + sim2Iccid);

            int masterSimSlot = mDmMessage.getMasterSimSlot();
            if (mDmMessage.SLOT_ID_0 == masterSimSlot) {
                setMasterSimUEIccid(sim1Iccid);
                setSlaveSimUEIccid(sim2Iccid);
            } else {
                setMasterSimUEIccid(sim2Iccid);
                setSlaveSimUEIccid(sim1Iccid);
            }
        }

        //put sw version also, for check if need to clear up iccid.
        Settings.Global.putString(getContentResolver(),
                DM_SWVER, mDmMessage.getSwVer());
        Settings.Global.putString(getContentResolver(),
                DM_SW_BUILD_DATE, mDmMessage.getSwBuildDate());
    }

    private void putRetryTimes(int times) {
        Settings.Global.putInt(getContentResolver(),
                    DM_RETRY_TIMES, times);
    }

    private void putResendTimes(int times) {
         Settings.Global.putInt(getContentResolver(),
                    DM_RESEND_TIMES, times);
    }

    private void putResendTimePoint(int time_point) {
        Log.i(TAG,"Save resend time point: " + time_point + "s.");
        Settings.Global.putInt(getContentResolver(),
                    DM_RESEND_TIME_POINT, time_point);
    }

    private int getResendTimePoint() {
        int time_point = Settings.Global.getInt(getContentResolver(), DM_RESEND_TIME_POINT, 0);
        Log.i(TAG,"Get resend time point: " + time_point + "s.");
        return time_point;
    }

    public String generateData() {
        final String[] messageStr;
        String data = EMPTY;

        int devForm = mDmMessage.getDeviceForm();
        Log.d(TAG,"generateData, devForm = " + devForm);

        switch(devForm) {
            case DM_FORM_PAN_SMART_DEV:
                Log.d(TAG,"generateData, load pan smart dev self reg msg table.");
                messageStr = getResources().getStringArray(R.array.pan_smart_dev_self_reg_msg_str);
                break;
            case DM_FORM_PHONE_SINGLESIM:
                Log.d(TAG,"generateData, load singlesim dev self reg msg table.");
                messageStr = getResources().getStringArray(R.array.singlesim_self_register_message_string);
                break;
            case DM_FORM_PHONE_DUALSIM:
                Log.d(TAG,"generateData, load dualsim dev self reg msg table.");
                messageStr = getResources().getStringArray(R.array.dualsim_self_register_message_string);
                break;
            default:
                Log.w(TAG,"generateData, there isn't a suitable table.");
                return data;
        }

        try {
            final JSONObject json = new JSONObject();

            for (int i = 0; i < messageStr.length; i++) {
                String msg = mDmMessage.getDmMessage(messageStr[i]);
                Log.d(TAG," "+messageStr[i]+" = "+msg);
                json.put(messageStr[i], msg);
            }
            data = json.toString();
            return data;
        } catch (final JSONException e) {
            Log.e(TAG,"Exception while generating data",e);
        }
        return data;
    }

    private String checkResponse(String response) {
        if (null == response) {
            Log.d(TAG,"checkResponse, response is null!");
            return null;
        }

        try {
            final JSONObject json = new JSONObject(response);
            return json.getString(RESPONSE_CODE_STRING);
        } catch (final JSONException e) {
            Log.e(TAG,"Exception while checking response",e);
            return null;
        }
    }

    private boolean isSelfRegSuccess(String response) {
        String code = checkResponse(response);
        if (RESPONSE_CODE[0].equals(code)) {
            return true;
        }
        Log.e(TAG,"Self Register Fail! Result Code:" + code +
              ", mRetryTimes=" + mRetryTimes);
        return false;
    }

    private void handleSelfRegResult(boolean ret) {
        if (ret) {
            putUeIccid();
            Log.i(TAG,"Self Register Success!");
            cancelRetryAlarm();
            resetRetryTimes();
            startResendAlarms();
        } else {
            doRetry();
        }
        DmService.getInstance().stopSelfReg();
    }

    private void prepareProp() {
        ProperUtils.createPropFile(this);
        Properties prop = ProperUtils.getProperties(this);
        Log.d(TAG,"prepareProp size="+prop.size());

        int interval = Integer.parseInt(
            prop.getProperty(ProperUtils.PROPER_SERVICE_INTERVAL, "0"));

        if (interval == 0) {
            prop.setProperty(ProperUtils.PROPER_SERVICE_INTERVAL, getResources().getString(R.string.service_interval));
            prop.setProperty(ProperUtils.PROPER_SERVER_URL, getResources().getString(R.string.dm_server_url));
            prop.setProperty(ProperUtils.PROPER_RETRY_TIMES, getResources().getString(R.string.retry_times));
            prop.setProperty(ProperUtils.PROPER_MANU_ID, getResources().getString(R.string.manu_id));
            prop.setProperty(ProperUtils.PROPER_RESEND_TIMES, getResources().getString(R.string.resend_times));

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
                    if (DmService.getInstance().getNetworkUtils().isPhoneInternationalNetworkRoaming()) {
                        DmService.getInstance().stopSelfReg();
                        return;
                    }
                    Log.d(TAG,"SelfRegisterHandler:generate data");

                    String data = generateData();
                    DmTransaction dmTransaction = new DmTransaction(DmService.this, data);
                    boolean ret = false;

                    String response = dmTransaction.sendData();
                    ret = isSelfRegSuccess(response);
                    if (!ret && DmService.getInstance().canWait()) {
                        DmService.getInstance().is_start_regist = false;
                        DmService.getInstance().waitSendNextTime();
                        return;
                    }
                    DmService.getInstance().resetWaitCount();
                    handleSelfRegResult(ret);
                    break;
            }
        }
    }

    private void setMasterSimUEIccid(String simIccid) {
        String masterSimIccid = DmMessage.ICCID_DEFAULT_VALUE;
        if (!TextUtils.isEmpty(simIccid)) {
            masterSimIccid = simIccid;
        }

        Log.i(TAG,"Save master sim iccid: " + masterSimIccid);
        Settings.Global.putString(getContentResolver(), DM_UEICCID_MASTER_SIM, masterSimIccid);
    }

    private String getMasterSimUEIccid() {
        String simIccid = Settings.Global.getString(getContentResolver(), DM_UEICCID_MASTER_SIM);
        if (TextUtils.isEmpty(simIccid)) {
            simIccid = DmMessage.ICCID_DEFAULT_VALUE;
        }
        Log.i(TAG,"Get UE saved master sim iccid: " + simIccid);
        return simIccid;
    }

    private void setSlaveSimUEIccid(String simIccid) {
        String slaveSimIccid = DmMessage.ICCID_DEFAULT_VALUE;
        if (!TextUtils.isEmpty(simIccid)) {
            slaveSimIccid = simIccid;
        }

        Log.i(TAG,"Save slave sim iccid: " + slaveSimIccid);
        Settings.Global.putString(getContentResolver(), DM_UEICCID_SLAVE_SIM, slaveSimIccid);
    }

    private String getSlaveSimUEIccid() {
        String simIccid = Settings.Global.getString(getContentResolver(), DM_UEICCID_SLAVE_SIM);
        if (TextUtils.isEmpty(simIccid)) {
            simIccid = DmMessage.ICCID_DEFAULT_VALUE;
        }
        Log.i(TAG,"Get UE saved slave sim iccid: " + simIccid);
        return simIccid;
    }

    private boolean isMasterSimChanged() {
        int simcount = mDmMessage.getSlotCount();
        if (simcount != mDmMessage.DUAL_SIM_COUNT) {
            Log.d(TAG,"isMasterSimChanged, sim slot count = " + simcount);
            return false;
        }

        int masterSimSlot = mDmMessage.getMasterSimSlot();
        int slaveSimSlot = mDmMessage.SLOT_ID_1;
        if (masterSimSlot == mDmMessage.SLOT_ID_1) {
            slaveSimSlot = mDmMessage.SLOT_ID_0;
        }

        String masterSimIccid = mDmMessage.getSimIccid(masterSimSlot);
        String slaveSimIccid = mDmMessage.getSimIccid(slaveSimSlot);
        Log.d(TAG,"isMasterSimChanged, master sim iccid = " + masterSimIccid +
                                    ", slave sim iccid = " + slaveSimIccid);

        String masterSimUEIccid = getMasterSimUEIccid();
        String slaveSimUEIccid = getSlaveSimUEIccid();

        if (slaveSimUEIccid.equals(DmMessage.ICCID_DEFAULT_VALUE)
         || masterSimUEIccid.equals(DmMessage.ICCID_DEFAULT_VALUE)
         || masterSimUEIccid.equals(masterSimIccid)) {
            Log.d(TAG,"isMasterSimChanged, master sim has not changed by user, ignored.");
            setMasterSimUEIccid(masterSimIccid);
            setSlaveSimUEIccid(slaveSimIccid);
            return false;
        }

        if (masterSimIccid != null && masterSimIccid.equals(slaveSimUEIccid)) {
            Log.d(TAG,"isMasterSimChanged, master sim has changed.");
            setMasterSimUEIccid(masterSimIccid);
            setSlaveSimUEIccid(slaveSimIccid);
            return true;
        }

        return false;
    }

}
