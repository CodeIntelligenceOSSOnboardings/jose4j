/*
 * Copyright 2012-2017 Brian Campbell
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jose4j.http;

import org.jose4j.lang.StringUtil;
import org.jose4j.lang.UncheckedJoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class Get implements SimpleGet
{
    private static final long MAX_RETRY_WAIT = 8000;

    private static final Logger log = LoggerFactory.getLogger(Get.class);

    private int connectTimeout = 20000;
    private int readTimeout = 20000;
    private int retries = 3;
    private long initialRetryWaitTime = 180;
    private boolean progressiveRetryWait = true;
    private SSLSocketFactory sslSocketFactory;
    private HostnameVerifier hostnameVerifier;
    private int responseBodySizeLimit = 1024 * 512;
    private Proxy proxy;

    @Override
    public SimpleResponse get(String location) throws IOException
    {
        int attempts = 0;
        log.debug("HTTP GET of {}", location);
        URL url = new URL(location);
        while (true)
        {
            try
            {
                URLConnection urlConnection = (proxy == null) ? url.openConnection() : url.openConnection(proxy);
                urlConnection.setConnectTimeout(connectTimeout);
                urlConnection.setReadTimeout(readTimeout);

                setUpTls(urlConnection);

                HttpURLConnection httpUrlConnection = (HttpURLConnection) urlConnection;
                int code = httpUrlConnection.getResponseCode();
                String msg = httpUrlConnection.getResponseMessage();

                if (code != HttpURLConnection.HTTP_OK)
                {
                    throw new IOException("Non 200 status code ("+ code + " " + msg +") returned from " + url);
                }

                String charset = getCharset(urlConnection);

                String body = getBody(urlConnection, charset);

                Map<String,List<String>> headers = httpUrlConnection.getHeaderFields();
                SimpleResponse simpleResponse = new Response(code, msg, headers, body);
                log.debug("HTTP GET of {} returned {}", url, simpleResponse);
                return simpleResponse;
            }
            catch (SSLHandshakeException | SSLPeerUnverifiedException | FileNotFoundException | ResponseBodyTooLargeException e)
            {
                throw e;
            }
            catch (IOException e)
            {
                attempts++;
                if (attempts > retries)
                {
                    throw e;
                }
                long retryWaitTime = getRetryWaitTime(attempts);
                log.debug("Waiting {}ms before retrying ({} of {}) HTTP GET of {} after failed attempt: {}", retryWaitTime, attempts, retries, url, e);
                try { Thread.sleep(retryWaitTime); } catch (InterruptedException ie) { /* ignore */ }
            }
        }
    }

    private String getBody(URLConnection urlConnection, String charset) throws IOException
    {
        StringWriter writer = new StringWriter();
        try (InputStream is = urlConnection.getInputStream();
             InputStreamReader isr = new InputStreamReader(is, charset))
        {
            int charactersRead = 0;
            char[] buffer = new char[1024];
            int n;
            while (-1 != (n = isr.read(buffer)))
            {
                writer.write(buffer, 0, n);
                charactersRead += n;
                if (responseBodySizeLimit > 0 && charactersRead > responseBodySizeLimit)
                {
                    throw new ResponseBodyTooLargeException("More than " + responseBodySizeLimit + " characters have been read from the response body.");
                }
            }
            log.debug("read {} characters", charactersRead);
        }
        return writer.toString();
    }

    private void setUpTls(URLConnection urlConnection)
    {
        if (urlConnection instanceof HttpsURLConnection)
        {
            HttpsURLConnection httpsUrlConnection = (HttpsURLConnection) urlConnection;
            if (sslSocketFactory != null)
            {
                httpsUrlConnection.setSSLSocketFactory(sslSocketFactory);
            }

            if(hostnameVerifier != null)
            {
                httpsUrlConnection.setHostnameVerifier(hostnameVerifier);
            }
        }
    }

    private String getCharset(URLConnection urlConnection)
    {
        String contentType = urlConnection.getHeaderField("Content-Type");
        String charset = StringUtil.UTF_8;
        try
        {
            if (contentType != null)
            {
                for (String part : contentType.replace(" ", "").split(";")) {
                    String prefix = "charset=";
                    if (part.startsWith(prefix)) {
                        charset = part.substring(prefix.length());
                        break;
                    }
                }
                Charset.forName(charset);
            }
        }
        catch (Exception e)
        {
            log.debug("Unexpected problem attempted to determine the charset from the Content-Type ({}) so will default to using UTF8: {}", contentType, e);
            charset = StringUtil.UTF_8;
        }
        return charset;
    }

    private long getRetryWaitTime(int attempt)
    {
        if (progressiveRetryWait)
        {
            double pow = Math.pow(2, attempt - 1);
            long wait = (long) (pow * initialRetryWaitTime);
            return Math.min(wait, MAX_RETRY_WAIT);
        }
        else
        {
            return initialRetryWaitTime;
        }
    }

    /**
     *
     * @param connectTimeout in milliseconds
     */
    public void setConnectTimeout(int connectTimeout)
    {
        this.connectTimeout = connectTimeout;
    }

    /**
     *
     * @param readTimeout in milliseconds
     */
    public void setReadTimeout(int readTimeout)
    {
        this.readTimeout = readTimeout;
    }

    public void setHostnameVerifier(HostnameVerifier hostnameVerifier)
    {
        this.hostnameVerifier = hostnameVerifier;
    }

    public void setTrustedCertificates(X509Certificate... certificates)
    {
        setTrustedCertificates(Arrays.asList(certificates));
    }

    public void setRetries(int retries)
    {
        this.retries = retries;
    }

    public void setProgressiveRetryWait(boolean progressiveRetryWait)
    {
        this.progressiveRetryWait = progressiveRetryWait;
    }

    /**
     *
     * @param initialRetryWaitTime in milliseconds
     */
    public void setInitialRetryWaitTime(long initialRetryWaitTime)
    {

        this.initialRetryWaitTime = initialRetryWaitTime;
    }

    /**
     * in number of characters, -1 indicates no limit
     * @param responseBodySizeLimit
     */
    public void setResponseBodySizeLimit(int responseBodySizeLimit)
    {
        this.responseBodySizeLimit = responseBodySizeLimit;
    }

    public void setTrustedCertificates(Collection<X509Certificate> certificates)
    {
        try
        {
            TrustManagerFactory trustMgrFactory = TrustManagerFactory.getInstance("PKIX");
            KeyStore keyStore = KeyStore.getInstance("jks");
            keyStore.load(null, null);
            int i = 0;
            for (X509Certificate certificate : certificates)
            {
                keyStore.setCertificateEntry("alias" + i++, certificate);
            }
            trustMgrFactory.init(keyStore);
            TrustManager[] customTrustManagers = trustMgrFactory.getTrustManagers();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, customTrustManagers, null);
            sslSocketFactory = sslContext.getSocketFactory();
        }
        catch (NoSuchAlgorithmException | KeyManagementException | CertificateException | IOException | KeyStoreException e)
        {
            throw new UncheckedJoseException("Unable to initialize socket factory with custom trusted  certificates.", e);
        }
    }

    public void setHttpProxy(Proxy proxy)
    {
        this.proxy = proxy;
    }

    // todo -> need to give control over acceptable cipher suites? probably...

    private static class ResponseBodyTooLargeException extends IOException
    {
        public ResponseBodyTooLargeException(String message)
        {
            super(message);
        }
    }
}
