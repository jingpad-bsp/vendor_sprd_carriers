package com.sprd.dm.mbselfreg;

import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class HttpHelper {
    private static final String TAG = "DmService";

    public static final String POST_EXCEPTION_RESPONSE = "post_response_exception";
    private static final int BUFFER_SIZE = 1024 * 4;

    private static final String DEFAULT_CHARSET = "UTF-8";

    private int mConnectTimeout;
    private int mReadTimeout;

    public HttpHelper() {
    }

    public String get(GetRequest request) throws IOException, HttpException {
        return get(request.getUrl(), request.getHeaders());
    }

    public String get(String url, Map<String,String> requestHeaders)
            throws IOException, HttpException {
        HttpURLConnection c = null;
        try {
            c = createConnection(url, requestHeaders);
            c.setRequestMethod("GET");
            c.connect();
            return getResponseFrom(c);
        } finally {
            if (c != null) {
                c.disconnect();
            }
            return null;
        }
    }

    public String post(PostRequest request) throws HttpException {
        return post(request.getUrl(), request.getHeaders(), request.getContent());
    }

    public String post(String url, Map<String,String> requestHeaders, String content)
            throws HttpException {
        String response = POST_EXCEPTION_RESPONSE;
        HttpURLConnection c = null;
        OutputStreamWriter writer = null;
        try {
            if (requestHeaders == null) {
                requestHeaders = new HashMap<String, String>();
            }
            requestHeaders.put("Content-Length",
                    Integer.toString(content == null ? 0 : content.length()));
            c = createConnection(url, requestHeaders);
            c.setDoOutput(content != null);
            c.setRequestMethod("POST");
            c.connect();
            if (content != null) {
                writer = new OutputStreamWriter(c.getOutputStream());
                writer.write(content);
            }
        } catch (HttpException e) {
            //just throw the HttpException caus of response not ok.
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "Exception occurred in HttpHelper post:"+e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                    response = getResponseFrom(c);
                    Log.d(TAG, "finally response is"+ response);
                } catch (IOException e) {
                    Log.e(TAG, "Exception occurred in HttpHelper post:",e);
                }

            }
            if (c != null) {
                c.disconnect();
            }

        }
        return response;
    }

    private HttpURLConnection createConnection(String url, Map<String, String> headers)
            throws IOException, HttpException {
        URL u = new URL(url);
        Log.d(TAG, "URL=" + url);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        if (headers != null) {
            for (Map.Entry<String,String> e : headers.entrySet()) {
                String name = e.getKey();
                String value = e.getValue();
                Log.d(TAG, "  " + name + ": " + value);
                c.addRequestProperty(name, value);
            }
        }

        if (mConnectTimeout != 0) {
            c.setConnectTimeout(mConnectTimeout);
        }
        if (mReadTimeout != 0) {
            c.setReadTimeout(mReadTimeout);
        }
        return c;
    }

    private String getResponseFrom(HttpURLConnection c) throws IOException, HttpException {
        if (c.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new HttpException(c.getResponseCode(), c.getResponseMessage());
        }

        Log.d(TAG, "Content-Type: " + c.getContentType() + " (assuming " +
                    DEFAULT_CHARSET + ")");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(c.getInputStream(), DEFAULT_CHARSET));
        StringBuilder string = new StringBuilder();
        char[] chars = new char[BUFFER_SIZE];
        int bytes;
        while ((bytes = reader.read(chars)) != -1) {
            string.append(chars, 0, bytes);
        }
        return string.toString();
    }

    public void setConnectTimeout(int timeoutMillis) {
        mConnectTimeout = timeoutMillis;
    }

    public void setReadTimeout(int timeoutMillis) {
        mReadTimeout = timeoutMillis;
    }

    public static class GetRequest {
        private String mUrl;
        private Map<String,String> mHeaders;

        public GetRequest() {
        }

        public GetRequest(String url) {
            mUrl = url;
        }

        public String getUrl() {
            return mUrl;
        }

        public void setUrl(String url) {
            mUrl = url;
        }

        public Map<String, String> getHeaders() {
            return mHeaders;
        }

        public void setHeader(String name, String value) {
            if (mHeaders == null) {
                mHeaders = new HashMap<String,String>();
            }
            mHeaders.put(name, value);
        }

        public void setAuthToken(String login, String psw) {
            if (mHeaders == null) {
                mHeaders = new HashMap<String,String>();
            }
            final String cs = login + ":" + psw;
            String authString = "Basic " + Base64.encodeToString(cs.getBytes(), Base64.NO_WRAP);
            mHeaders.put(DmTransaction.HTTP_HEADER_AUTHORIZATION, authString);
        }
    }

    public static class PostRequest extends GetRequest {

        private String mContent;

        public PostRequest() {
        }

        public PostRequest(String url) {
            super(url);
        }

        public void setContent(String content, boolean encrypt) {
            if (encrypt) {
                mContent = Base64.encodeToString(content.getBytes(), Base64.DEFAULT);
            } else {
                mContent = content;
            }
        }

        public String getContent() {
            return mContent;
        }
    }

    public static class HttpException extends IOException {
        private final int mStatusCode;
        private final String mReasonPhrase;

        public HttpException(int statusCode, String reasonPhrase) {
            super(statusCode + " " + reasonPhrase);
            mStatusCode = statusCode;
            mReasonPhrase = reasonPhrase;
        }

        public int getStatusCode() {
            return mStatusCode;
        }

        public String getReasonPhrase() {
            return mReasonPhrase;
        }
    }

}
