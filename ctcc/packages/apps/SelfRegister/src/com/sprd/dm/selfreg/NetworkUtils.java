package com.sprd.dm.mbselfreg;

import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import com.dmyk.android.telephony.DmykAbsTelephonyManager;
import android.telephony.ServiceState;

public class NetworkUtils {
    private static final String TAG = "DmService";

    private static final long CDMA_BASEID_MAX = 65535L;
    private static final String OPERATOR_CTCC_4G = "46011";
    private static final String OPERATOR_CTCC = "46005";
    private static final String OPERATOR_CTCC_CDMA = "46003";
    private static final int SLOT_ID_0 = 0;
    private static final int SLOT_ID_1 = 1;
    private static final int SLOT_ID_INVALID = -1;
    private static final int SINGLE_SIM_COUNT = 1;
    private static final int DUAL_SIM_COUNT = 2;

    private Context mContext;
    private ConnectivityManager mConnMgr;
    private IntentFilter mNetStateFilter;
    private DmykAbsTelephonyManager mDmykAbsTelephonyManager;

    private BroadcastReceiver mNetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                Log.d(TAG, "NetworkUtils,mNetReceiver, intent = " + intent);
                DmUtil.printIntent(intent);

                boolean noConnectivity =
                    intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

                if (noConnectivity) {
                    Log.i(TAG, "NetworkUtils,mNetReceiver, not connected ");
                    return;
                }

                if (mContext instanceof DmService) {
                    if (DmService.getInstance().is_start_regist) {
                        Log.i(TAG, "NetworkUtils,mNetReceiver, DmService already start regist ");
                        return;
                    }

                    int masterSlotId = mDmykAbsTelephonyManager.getMasterPhoneId();
                    if (DmykAbsTelephonyManager.SIM_STATE_ABSENT == mDmykAbsTelephonyManager.getSimState(masterSlotId)){
                        Log.i(TAG, "NetworkUtils,mNetReceiver, Sim not insert ");
                        return;
                    }

                    if (masterSlotId != SLOT_ID_0 && masterSlotId != SLOT_ID_1) {
                        Log.i(TAG, "NetworkUtils,mNetReceiver, invalid masterSlotId = " + masterSlotId);
                        return;
                    }

                    if (isPhoneInternationalNetworkRoaming()) {
                        Log.i(TAG, "NetworkUtils,mNetReceiver, International NetworkRoaming!");
                        DmService.getInstance().stopSelfReg();
                        return;
                    }

                    int count =  mDmykAbsTelephonyManager.getPhoneCount();
                    Log.i(TAG, "NetworkUtils,mNetReceiver, getPhoneCount: count = " + count);
                    if (DUAL_SIM_COUNT == count && !isWifiAvailable()) {
                        int slaveSlotId = (masterSlotId == SLOT_ID_0 ? SLOT_ID_1 : SLOT_ID_0);
                        if (!isSlotCtccSimCard(masterSlotId)) {
                            Log.i(TAG, "NetworkUtils,mNetReceiver, Non-CTCC sim card, Wifi not Available!");
                            return;
                        } else if (isSimDataConnected(slaveSlotId)) {
                            Log.i(TAG, "NetworkUtils,mNetReceiver, ignore slave sim card data connected!");
                            return;
                        }
                    }

                    DmUtil.startDmService(mContext, DmReceiver.DM_SERVER_ACTION_CONNECT);
                }

            }
        }
    };

    public NetworkUtils(Context context) {
        Log.d(TAG, "NetworkUtils,NetworkUtils");
        mContext = context;
        mConnMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mDmykAbsTelephonyManager = DmykAbsTelephonyManager.getDefault(context);
    }

    public void registeReceiver() {
        Log.d(TAG, "NetworkUtils,registeReceiver");
        if (mNetStateFilter == null) {
            mNetStateFilter = new IntentFilter();
            mNetStateFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

            mContext.registerReceiver(mNetReceiver, mNetStateFilter);
        }
    }

    public void unRegisteReceiver() {
        if (mNetStateFilter != null) {
            mContext.unregisterReceiver(mNetReceiver);
            mNetReceiver = null;
            mNetStateFilter = null;
        }
    }

    private boolean isSimInfoReady(int slotId) {
        String imsi = mDmykAbsTelephonyManager.getSubscriberId(slotId);
        Log.d(TAG,"isSimInfoReady, slotId = " + slotId + ", imsi = " + imsi);
        if (TextUtils.isEmpty(imsi)) {
            return false;
        }

        String iccid = mDmykAbsTelephonyManager.getIccId(slotId);
        Log.d(TAG,"isSimInfoReady, iccid = " + iccid);
        if (TextUtils.isEmpty(iccid)) {
            return false;
        }

        int masterSlotId = mDmykAbsTelephonyManager.getMasterPhoneId();
        if (masterSlotId == SLOT_ID_INVALID) {
            return false;
        }

        return true;
    }

    private boolean isSlotCtccSimCard(int slotId) {
        String imsi = mDmykAbsTelephonyManager.getSubscriberId(slotId);
        Log.d(TAG,"isSlotCtccSimCard, slotId = " + slotId + ", imsi = " + imsi);
        if (imsi != null
            && (imsi.startsWith(OPERATOR_CTCC_CDMA)
                || imsi.startsWith(OPERATOR_CTCC)
                || imsi.startsWith(OPERATOR_CTCC_4G))) {
            return true;
        }
        return false;
    }

    public boolean isSimDataConnected(int slotId) {
        int dataState = mDmykAbsTelephonyManager.getDataState(slotId);
        Log.d(TAG,"isMasterSimDataConnected, slotId = " + slotId + ", dataState = " + dataState);
        if (DmykAbsTelephonyManager.DATA_CONNECTED == dataState) {
            return true;
        }
        return false;
    }

    private boolean isInternationalNetworkRoaming(int slotId) {
        boolean bRet =false;
        int simState = mDmykAbsTelephonyManager.getSimState(slotId);
        Log.d(TAG,"isInternationalNetworkRoaming, slotId = " + slotId + ", simState = " + simState);
        if (DmykAbsTelephonyManager.SIM_STATE_ABSENT != simState
         && DmykAbsTelephonyManager.SIM_STATE_UNKNOWN != simState) {
            bRet = mDmykAbsTelephonyManager.isInternationalNetworkRoaming(slotId);
        }
        Log.d(TAG,"isInternationalNetworkRoaming, bRet = " + bRet);
        return bRet;
    }

    public boolean isPhoneInternationalNetworkRoaming() {
        int masterSlotId = mDmykAbsTelephonyManager.getMasterPhoneId();
        if (isSlotCtccSimCard(masterSlotId) && isInternationalNetworkRoaming(masterSlotId)) {
            Log.d(TAG, "Ctcc master sim is International Network Roaming!");
            return true;
        }

        return false;
    }

    private boolean isVolteOpen(int slotId) {
        int state = mDmykAbsTelephonyManager.getVoLTEState(slotId);
        if (DmykAbsTelephonyManager.VOLTE_STATE_ON == state) {
            return true;
        }
        return false;
    }

    public boolean isPhoneInNrStatus(int slotId) {
        int subId = mDmykAbsTelephonyManager.getSubId(slotId);
        TelephonyManager tm = new TelephonyManager(mContext, subId);
        ServiceState serviceState = tm.getServiceStateForSubscriber(subId);
        if (serviceState != null && serviceState.getDataRegState() == ServiceState.STATE_IN_SERVICE
            &&  serviceState.getDataNetworkType() == TelephonyManager.NETWORK_TYPE_NR) {
            Log.d(TAG,"NetworkUtils is InNrService");
            return true;
        }
        return false;

    }
    private boolean isInService(int slotId) {
        int masterSlotId = mDmykAbsTelephonyManager.getMasterPhoneId();
        int simState = mDmykAbsTelephonyManager.getSimState(slotId);
        Log.d(TAG,"NetworkUtils, isInService, slotId = " + slotId + ", simState = " + simState);

        if (DmykAbsTelephonyManager.SIM_STATE_ABSENT != simState
            && DmykAbsTelephonyManager.SIM_STATE_UNKNOWN != simState) {

            if (!isSimInfoReady(slotId)) {
                return false;
            }

            if (isSlotCtccSimCard(slotId)) {
                Log.d(TAG,"NetworkUtils, isInService, sim card is ctcc sim.");
                int count =  mDmykAbsTelephonyManager.getPhoneCount();
                Log.i(TAG, "NetworkUtils,isInService, getPhoneCount: count = " + count);

                if (DUAL_SIM_COUNT == count && slotId != masterSlotId
                    && isSlotCtccSimCard(masterSlotId)
                    && !isVolteOpen(masterSlotId)
                    && !isVolteOpen(slotId)) {
                    Log.d(TAG,"NetworkUtils, isInService, dual ctcc sim card with volte off, slave sim needn't in service.");
                    return true;
                }

                long simcellid_nr = -1L;
                long simcellid_lte = -1L;

                if (isPhoneInNrStatus(slotId)) {
                    simcellid_nr = mDmykAbsTelephonyManager.getNrCellId(slotId);
                    Log.d(TAG,"NetworkUtils, isInNRService, simcellid_nr =  "+ simcellid_nr);
                } else {
                    simcellid_lte = mDmykAbsTelephonyManager.getCellId(slotId);
                    Log.d(TAG,"NetworkUtils, isInLTEService, simcellid_lte= " + simcellid_lte);
                }

                if (slotId == masterSlotId && simcellid_nr <= CDMA_BASEID_MAX && simcellid_lte <= CDMA_BASEID_MAX) {
                    return false;
                } else if ((simcellid_nr != -1) || (simcellid_lte != -1)) {
                    return true;
                }
            }

            boolean bRet = mDmykAbsTelephonyManager.isServiceStateInService(slotId);
            Log.d(TAG,"NetworkUtils, isInService, bRet = " + bRet);
            if (bRet) {
                return true;
            }
        } else {
            Log.d(TAG,"NetworkUtils, isSlotInService, sim absent or unknown, need not inservice");
            return true;
        }

        return false;
    }

    public boolean isPhoneInService() {
        int phoneCount = mDmykAbsTelephonyManager.getPhoneCount();
        Log.d(TAG, "NetworkUtils, isPhoneInService, phoneCount = " + phoneCount);

        if (SINGLE_SIM_COUNT == phoneCount) {
            return isInService(SLOT_ID_0);
        } else if (DUAL_SIM_COUNT == phoneCount) {
            return isInService(SLOT_ID_0) && isInService(SLOT_ID_1);
        }

        return false;
    }

    public boolean isWifiAvailable() {
        if (mConnMgr != null) {
            NetworkInfo wifi = mConnMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (wifi != null && wifi.isConnected()) {
                Log.d(TAG,"Wifi is available");
                return true;
            }
        }
        Log.d(TAG,"Wifi is unavailable");
        return false;
    }

    public boolean isNetworkAvailable() {
        if (mConnMgr != null) {
            if (isPhoneInternationalNetworkRoaming()) {
                Log.d(TAG, "NetworkUtils,isNetworkAvailable, Phone is International Network Roaming!");
                return false;
            }

            if (isWifiAvailable()) {
                Log.d(TAG, "NetworkUtils,isNetworkAvailable, Wifi Available!");
                return true;
            }

            int masterSlotId = mDmykAbsTelephonyManager.getMasterPhoneId();
            if (isSlotCtccSimCard(masterSlotId)
                && (isSimDataConnected(masterSlotId))) {
                Log.d(TAG, "NetworkUtils,isNetworkAvailable, Ctcc card and Network Available!");
                return true;
            }
        }
        return false;
    }

}
