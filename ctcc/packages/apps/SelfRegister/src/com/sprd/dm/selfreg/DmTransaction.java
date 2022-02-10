package com.sprd.dm.mbselfreg;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.util.Properties;

public class DmTransaction {
    private static final String TAG = "DmService";

    public static final String HTTP_HEADER_USER_AGENT = "User-Agent";
    public static final String HTTP_HEADER_Content_Type = "Content-Type";
    public static final String HTTP_HEADER_AUTHORIZATION = "Authorization";

    public static final String VALUE_Content_Type = "application/encrypted-json";

    private static final int DEFAULT_TIMEOUT = 1000 * 60; //60s

    private Context mContext;
    private String mData;

    public DmTransaction(Context context, String data) {
        mContext = context;
        mData = data;
    }

    public String sendData() {
        Properties prop = ProperUtils.getProperties(DmService.getInstance());
        String def = DmService.getInstance().getResources().getString(R.string.dm_server_url);
        String url = prop.getProperty(ProperUtils.PROPER_SERVER_URL, def);
        final HttpHelper.PostRequest request = new HttpHelper.PostRequest(url);

        request.setHeader(HTTP_HEADER_Content_Type, VALUE_Content_Type);

        HttpHelper httpHelper = new HttpHelper();
        httpHelper.setConnectTimeout(DEFAULT_TIMEOUT);
        httpHelper.setReadTimeout(DEFAULT_TIMEOUT);

        String response = null;
        try {

            Log.d(TAG, "DmTransaction post data, mData : " + mData);
            request.setContent(mData,true);
            String content = request.getContent();
            Log.d(TAG, "DmTransaction post data, content : " + content);

            if (!TextUtils.isEmpty(content)) {
                if (DmService.getInstance().getNetworkUtils().isNetworkAvailable()) {
                    response = httpHelper.post(request);
                    if (HttpHelper.POST_EXCEPTION_RESPONSE.equals(response)) {
                        Log.d(TAG, "POST_EXCEPTION_RESPONSE, response invalid");
                    }
                } else {
                    Log.d(TAG, "DmTransaction, Network Unavailable!");
                }
                Log.d(TAG, "DmTransaction post data, response : " + response);
            }
        } catch (HttpHelper.HttpException e) {
            Log.e(TAG, "DmTransaction post data.....response : " + response, e);
        } catch (Exception e) {
            Log.e(TAG, "Post to DM Server failed......response : " + response, e);
        }
        return response;
    }
}
