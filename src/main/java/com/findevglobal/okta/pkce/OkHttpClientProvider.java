package com.findevglobal.okta.pkce;

import okhttp3.OkHttpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.util.concurrent.TimeUnit;

public class OkHttpClientProvider {

    public static OkHttpClient createHttpClient() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        OkHttpClient.Builder newBuilder = new OkHttpClient.Builder();
        newBuilder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
        newBuilder.hostnameVerifier((hostname, session) -> true);
        newBuilder.connectTimeout(600, TimeUnit.SECONDS);
        newBuilder.readTimeout(600, TimeUnit.SECONDS);
        newBuilder.writeTimeout(600, TimeUnit.SECONDS);
        return newBuilder.build();
    }

}

