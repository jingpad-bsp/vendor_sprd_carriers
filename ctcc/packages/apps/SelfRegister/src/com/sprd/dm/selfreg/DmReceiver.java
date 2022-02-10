package com.sprd.dm.mbselfreg;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.net.Uri;
import android.text.TextUtils;
import java.util.HashMap;
import android.telephony.ServiceState;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import java.util.Properties;
import android.provider.Settings;

public class DmReceiver extends BroadcastReceiver {
    private static final String TAG = "DmService";

    public static final String ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED =
                                "android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED";
    public static final String ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED";

    public static final String DM_SERVER_RETRY_ALARM = "com.sprd.dm.DmRetryAlarm";
    public static final String DM_SERVER_RESEND_ALARM = "com.sprd.dm.DmResendAlarm";
    public static final String DM_SERVER_WAITFOR_SERVICE_ALARM = "com.sprd.dm.DmWaitForServiceAlarm";

    public static final String DM_SERVER_IMS_REGISTED = "com.spreadtrum.ims.VOLTE_REGISTED";
    public static final String DM_SERVER_CDMA_SMS_RETRY_ALARM = "com.sprd.dm.DmCmdaSmsRetryAlarm";
    public static final String DM_SERVER_IMS_SMS_RETRY_ALARM = "com.sprd.dm.DmImsSmsRetryAlarm";

    public static final String MESSAGE_SENT_ACTION =
            "com.sprd.dm.DmSmsService.SendStatus.MESSAGE_SENT";
    public static final String MESSAGE_DELIVERED_ACTION =
            "com.sprd.dm.DmSmsService.SendStatus.MESSAGE_DELIVERED";


    public static final String DM_SERVER_ACTION_BOOT = "com.sprd.dm.DmService.Boot";
    public static final String DM_SERVER_ACTION_SIM_READY = "com.sprd.dm.DmService.SimReady";
    public static final String DM_SERVER_ACTION_RETRY = "com.sprd.dm.DmService.Retry";
    public static final String DM_SERVER_ACTION_CONNECT = "com.sprd.dm.DmService.Connect";
    public static final String DM_SERVER_ACTION_RESEND = "com.sprd.dm.DmService.Resend";
    public static final String DM_SERVER_ACTION_VOLTE_CHANGE = "com.sprd.dm.DmService.VoLteStateChange";
    public static final String DM_SERVER_ACTION_DATASIM_CHANGE = "com.sprd.dm.DmService.DataSimChange";
    public static final String DM_SERVER_ACTION_WAITFOR_SERVICE = "com.sprd.dm.DmService.WaitForService";

    public static final String DM_SMS_SERVER_ACTION_BOOT = "com.sprd.dm.DmSmsService.Boot";
    public static final String DM_SMS_SERVER_ACTION_SIM_READY = "com.sprd.dm.DmSmsService.SimReady";
    public static final String DM_SMS_SERVER_ACTION_SIM_ABSENT = "com.sprd.dm.DmSmsService.SimAbsent";
    public static final String DM_SMS_SERVER_ACTION_CDMA_REG = "com.sprd.dm.DmSmsService.CdmaReg";
    public static final String DM_SMS_SERVER_ACTION_IMS_REG = "com.sprd.dm.DmSmsService.ImsReg";
    public static final String DM_SMS_SERVER_ACTION_CDMA_RETRY = "com.sprd.dm.DmSmsService.CdmaRetry";
    public static final String DM_SMS_SERVER_ACTION_IMS_RETRY = "com.sprd.dm.DmSmsService.ImsRetry";

    public static final String DM_SMS_SERVER_ACTION_SEND_STATUS = "com.sprd.dm.DmSmsService.Message.Send";

    private static final String DM_SERVER_SWITCH= "dm_switch";

    public static final String EXTRA_ERROR_CODE = "errorCode";
    public static final String EXTRA_PART_ID = "partId";
    public static final String EXTRA_SUB_ID = "subId";

    public static final int NO_ERROR_CODE = 0;
    public static final int NO_PART_ID = -1;
    public static final int DEFAULT_SUB_ID = -1;
    public static final int DM_SWITCH_CLOSE = 0;


    private static HashMap<String, String> sDmActionMap = new HashMap<String, String>();

    static {
        sDmActionMap.put(DM_SERVER_RETRY_ALARM, DM_SERVER_ACTION_RETRY);
        sDmActionMap.put(DM_SERVER_RESEND_ALARM, DM_SERVER_ACTION_RESEND);
        sDmActionMap.put(ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED,
                         DM_SERVER_ACTION_DATASIM_CHANGE);
        sDmActionMap.put(DM_SERVER_WAITFOR_SERVICE_ALARM, DM_SERVER_ACTION_WAITFOR_SERVICE);
        sDmActionMap.put(DM_SERVER_CDMA_SMS_RETRY_ALARM, DM_SMS_SERVER_ACTION_CDMA_RETRY);
        sDmActionMap.put(DM_SERVER_IMS_SMS_RETRY_ALARM, DM_SMS_SERVER_ACTION_IMS_RETRY);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        int dmSwitch = Settings.Global.getInt(
                            context.getContentResolver(), DM_SERVER_SWITCH, 1);
        if (DM_SWITCH_CLOSE == dmSwitch) {
             Log.d(TAG, "onReceive,dmSwitch = " + dmSwitch +",dm service exit!");
             return;
        }


        String action = intent.getAction();
        Log.d(TAG, "onReceive, action is " + action);


        switch(action) {
            case Intent.ACTION_BOOT_COMPLETED:
                DmUtil.startDmService(context, DM_SERVER_ACTION_BOOT);
                DmUtil.startDmService(context, DM_SMS_SERVER_ACTION_BOOT);
                break;
            case Intent.ACTION_SERVICE_STATE:
                Log.d(TAG, "onReceive, Service state changed....................");
                handleServiceStateChanged(context, intent);
                break;
            case DM_SERVER_IMS_REGISTED:
                {
                    Properties prop = ProperUtils.getProperties(context);
                    String def = context.getResources().getString(R.string.dm_ims_sms);
                    String ims_sms_switch = prop.getProperty(ProperUtils.PROPER_IMS_SMS, def);

                    int slot = intent.getIntExtra("android:phone_id", -1);
                    Log.d(TAG, "onReceive, slot: " + slot);
                    Log.d(TAG, "onReceive, ims sms switch: " + ims_sms_switch);
                    if ("true".equals(ims_sms_switch)) {
                        DmUtil.startDmService(context, DM_SMS_SERVER_ACTION_IMS_REG, slot);
                    } else {
                        Log.d(TAG,"onReceive,ims sms switch off!");
                    }
                }
                break;
            case DM_SERVER_CDMA_SMS_RETRY_ALARM:
                {
                    int slot = intent.getIntExtra("slot", -1);
                    Log.d(TAG, "onReceive, slot: " + slot);
                    DmUtil.startDmService(context, DM_SMS_SERVER_ACTION_CDMA_RETRY, slot);
                }
                break;
            case DM_SERVER_IMS_SMS_RETRY_ALARM:
                {
                    int slot = intent.getIntExtra("slot", -1);
                    Log.d(TAG, "onReceive, slot: " + slot);
                    DmUtil.startDmService(context, DM_SMS_SERVER_ACTION_IMS_RETRY, slot);
                }
                break;
            case ACTION_SIM_STATE_CHANGED:
                handleSimStateChanged(context, intent);
                break;
            case MESSAGE_SENT_ACTION:
                int resultCode = getResultCode();
                Log.d(TAG, "onReceive, resultCode = " + resultCode);

                final Uri requestId = intent.getData();
                Log.d(TAG, "onReceive, requestId = " + requestId);

                if (intent.getIntExtra("resultCode", -1) > 0) {
                    resultCode = intent.getIntExtra("resultCode", -1);
                }

                int errorCode = intent.getIntExtra(EXTRA_ERROR_CODE, NO_ERROR_CODE);
                int partId = intent.getIntExtra(EXTRA_PART_ID, NO_PART_ID);
                int subId = intent.getIntExtra(EXTRA_SUB_ID, DEFAULT_SUB_ID);

                Log.d(TAG, "onReceive, EXTRA_ERROR_CODE: " + errorCode);
                Log.d(TAG, "onReceive, EXTRA_PART_ID: " + partId);
                Log.d(TAG, "onReceive, EXTRA_ERROR_CODE: " + subId);

                Intent sendStatusIntend = new Intent();
                sendStatusIntend.setAction(DM_SMS_SERVER_ACTION_SEND_STATUS);
                sendStatusIntend.setPackage(context.getPackageName());
                sendStatusIntend.putExtra(EXTRA_ERROR_CODE, errorCode);
                sendStatusIntend.putExtra(EXTRA_PART_ID, partId);
                sendStatusIntend.putExtra(EXTRA_SUB_ID, subId);
                sendStatusIntend.putExtra("resultCode", resultCode);
                sendStatusIntend.putExtra("Uri", requestId.toString());

                DmUtil.printIntent(sendStatusIntend);
                context.startService(sendStatusIntend);
                break;
            case MESSAGE_DELIVERED_ACTION:
                final Uri smsMessageUri = intent.getData();
                Log.d(TAG, "onReceive, requestId = " + smsMessageUri);
                break;
            default:
                String dm_action = sDmActionMap.get(action);
                Log.d(TAG, "onReceive, dm action is " + dm_action);

                if (!TextUtils.isEmpty(dm_action)) {
                    DmUtil.startDmService(context, dm_action);
                }
                break;
        }
    }

    /**
    * This refers to com.android.internal.telehpony.IccCardConstants.INTENT_KEY_ICC_STATE.
    * It seems not possible to refer it through a builtin class like TelephonyManager, so we
    * define it here manually.
    */
    private static final String EXTRA_SIM_STATE = "ss";
    private static final String EXTRA_SLOT_KEY = "slot";
    private static final String SIM_STATE_READY = "READY";
    private static final String SIM_STATE_LOADED = "LOADED";
    private static final String SIM_STATE_ABSENT = "ABSENT";

    private void handleSimStateChanged(Context context, Intent intent) {
        DmUtil.printIntent(intent);

        int slotId = intent.getIntExtra(EXTRA_SLOT_KEY, -1);
        String state = intent.getExtras().getString(EXTRA_SIM_STATE);
        Log.d(TAG, "handleSimStateChanged, slot_int: " + slotId + ", state : " + state);
        if (slotId == -1) {
            return;
        }

        if (SIM_STATE_LOADED.equals(state)) {
            DmUtil.startDmService(context, DM_SERVER_ACTION_SIM_READY, slotId);
            DmUtil.startDmService(context, DM_SMS_SERVER_ACTION_SIM_READY, slotId);
        } else if (SIM_STATE_ABSENT.equals(state)) {
            DmUtil.startDmService(context, DM_SMS_SERVER_ACTION_SIM_ABSENT, slotId);
        }
    }

    private void handleServiceStateChanged(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        ServiceState serviceState = ServiceState.newFromBundle(bundle);
        int rt = serviceState.getRilVoiceRadioTechnology();
        int ant = ServiceState.rilRadioTechnologyToAccessNetworkType(rt);
        int state = serviceState.getState();
        Log.d(TAG, "handleServiceStateChanged, ant: " + ant + ", state: " + state);
        if (ant == AccessNetworkType.CDMA2000 && state == ServiceState.STATE_IN_SERVICE) {
            // CDMA registed
            Log.d(TAG, "handleServiceStateChanged, CDMA registed.");
            int slot_int = bundle.getInt("slot");
            Log.d(TAG, "onReceive, slot_int: " + slot_int);
            DmUtil.startDmService(context, DM_SMS_SERVER_ACTION_CDMA_REG, slot_int);
        }
    }

}
