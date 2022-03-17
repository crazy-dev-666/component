package cn.dev666.component.error.notice.utils;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;

public class HttpClientUtils {

    public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded;charset=utf-8";

    private static final int TIMEOUT = (int)Duration.ofMinutes(1).toMillis();

    public static String postJson(String url, String json) throws IOException {
        return doRequest("POST", url, json, CONTENT_TYPE_JSON, null);
    }

    private static String doRequest(String method, String url, String requestContent, String contentType,
                             Map<String, String> headerMap) throws IOException {
        HttpURLConnection conn = null;
        OutputStream out = null;
        try {
            conn = getConnection(new URL(url), method, contentType, headerMap);
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);

            if(requestContent != null && requestContent.trim().length() >0){
                out = conn.getOutputStream();
                out.write(requestContent.getBytes(StandardCharsets.UTF_8));
            }

            return getResponseAsString(conn);
        } finally {
            if (out != null) {
                out.close();
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static HttpURLConnection getConnection(URL url, String method,
                                            String contentType, Map<String, String> headerMap) throws IOException {
        HttpURLConnection conn;
        if ("https".equals(url.getProtocol())) {
            HttpsURLConnection connHttps = (HttpsURLConnection) url.openConnection();
            connHttps.setSSLSocketFactory(createSSLSocketFactory());
            connHttps.setHostnameVerifier((hostname, session) -> true);
            conn = connHttps;
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }
        conn.setRequestMethod(method);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setRequestProperty("Accept",
                "text/xml,text/javascript,text/html,application/json");
        conn.setRequestProperty("Content-Type", contentType);
        if (headerMap != null) {
            for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return conn;
    }

    private static String getResponseAsString(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        if (responseCode < 200 || responseCode >= 300){
            InputStream es = conn.getErrorStream();
            String msg;
            if (es != null) {
                msg = getStreamAsString(conn.getErrorStream());
            }else {
                msg = getStreamAsString(conn.getInputStream());
            }
            throw new RuntimeException(conn.getResponseCode() + " " + conn.getResponseMessage() + ", cause: " + msg);
        }
        return getStreamAsString(conn.getInputStream());
    }

    private static String getStreamAsString(InputStream stream) throws IOException {
        try {
            Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);

            StringBuilder response = new StringBuilder();
            final char[] buff = new char[1024];
            int read;
            while ((read = reader.read(buff)) > 0) {
                response.append(buff, 0, read);
            }

            return response.toString();
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    private static SSLSocketFactory createSSLSocketFactory() {
        SSLSocketFactory sSLSocketFactory = null;
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new TrustAllManager()},
                    new SecureRandom());
            sSLSocketFactory = sc.getSocketFactory();
        } catch (Exception ignored) {
        }
        return sSLSocketFactory;
    }

    private static class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType){
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType){
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
