package com.sprd.opm;

import android.app.Application;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import cn.richinfo.dm.DMSDK;
import cn.richinfo.dm.util.DMLog;

public class OPApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DMLog.i("OPApplication", "OPApplication onCreate");
        if (UserHandle.myUserId() != UserHandle.USER_OWNER) {
            DMLog.i("OPApplication", "not owner, disable opm");
            PackageManager pm = getApplicationContext().getPackageManager();
            pm.setApplicationEnabledSetting(getPackageName(),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
        }
        DMSDK.init(this);
        DMSDK.setDebugMode(true);
    }
}
