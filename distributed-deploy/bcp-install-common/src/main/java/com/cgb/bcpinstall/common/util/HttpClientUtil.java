package com.cgb.bcpinstall.common.util;

import com.cgb.bcpinstall.common.exception.DownloadFileException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * class_name: HttpClientUtil
 * package: com.cgb.dp.common.util
 * describe: Http工具类
 *
 * @author: zhun.xiao
 * @date: 2017/11/25
 * @time: 13:23
 **/
@Slf4j
@Component("httpClient")
public class HttpClientUtil {
    public static final String CONFIG_KEY_HTTP = "httpClient";
    public static final int HTTP_SUCCESS = 200;
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientUtil.class);
    private static final int RETRY_TIME = 3;

    // private static HttpClient client = null;
    private static CloseableHttpClient client = null;
    private RequestConfig config;

    @Autowired
    private Environment env;

    @Value("${httpClient.poolSize}")
    private String poolSize;

    @Value("${httpClient.socketTimeout}")
    private String socketTimeout;

    @Value("${httpClient.connectTimeout}")
    private String connectTimeout;

    @Value("${httpClient.connectionRequestTimeout}")
    private String connectionRequestTimeout;

    @Value("${httpClient.defaultMaxPerRoute}")
    private String defaultMaxPerRoute;

    /*static class MyTM implements TrustManager, X509TrustManager {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public boolean isServerTrusted(java.security.cert.X509Certificate[] certs) {
            return true;
        }

        public boolean isClientTrusted(java.security.cert.X509Certificate[] certs) {
            return true;
        }

        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
        }

        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
        }
    }*/

    @PostConstruct
    private void init() {
        try {
            int poolsize = Integer.parseInt(this.poolSize);
            int socketTimeout = Integer.parseInt(this.socketTimeout);
            int connectTimeout = Integer.parseInt(this.connectTimeout);
            int connectionRequestTimeout = Integer.parseInt(this.connectionRequestTimeout);
            int defaultMaxPerRoute = Integer.parseInt(this.defaultMaxPerRoute);

            LOGGER.info("HttpClientUtil init. poolsize:{}, socketTimeout:{}, connectTimeout:{}, connectionRequestTimeout:{}, defaultMaxPerRoute:{}",
                    poolsize, socketTimeout, connectTimeout,
                    connectionRequestTimeout, defaultMaxPerRoute);

            //TrustManager[] trustAllCerts = { new MyTM() };

            //SSLContext sc  = SSLContext.getInstance("SSL");
            //  sc.init(null, trustAllCerts, null);

            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.INSTANCE)
                    //.register("https", new SSLConnectionSocketFactory(sc))
                    .build();

           /* HostnameVerifier hv = (urlHostName, session) -> {
                LOGGER.info("Warning: URL Host: " + urlHostName + " vs. " + session.getPeerHost());
                return true;
            };*/

            config = RequestConfig.custom()
                    .setSocketTimeout(socketTimeout)
                    .setConnectTimeout(connectTimeout)
                    .setConnectionRequestTimeout(connectionRequestTimeout)
                    .build();

            PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            cm.setMaxTotal(poolsize);
            cm.setDefaultMaxPerRoute(defaultMaxPerRoute);

            client = HttpClients.custom()
                    .setConnectionManager(cm)
                    .setDefaultRequestConfig(config)
                    //.setSSLHostnameVerifier(hv)
                    .build();
        } catch (Exception e) {
            log.error("初始化http client异常", e);
            e.printStackTrace();
        }
    }

    /**
     * GET提交方式
     *
     * @param url
     * @param params
     * @return
     */
    public String doGet(String url, Map<String, Object> params) {
        String apiUrl = url;
        StringBuffer param = new StringBuffer();
        CloseableHttpResponse response = null;
        int i = 0;
        for (String key : params.keySet()) {
            if (i == 0) {
                param.append("?");
            } else {
                param.append("&");
            }

            param.append(key).append("=").append(params.get(key));
            i++;
        }
        apiUrl += param;
        String result = "";
        HttpGet httpPost = new HttpGet(apiUrl);
        try {
            response = client.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            if (HTTP_SUCCESS != statusCode) {
                return result;
            }
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                result = EntityUtils.toString(entity);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            httpPost.abort();
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                log.error(e.toString());
            }
        }
        return result;
    }

    /**
     * post提交表单,http参数是表单Form参数
     *
     * @param url
     * @param map
     * @param retry 是否重试，true时候，发送失败后重试，
     * @return
     * @throws ClientProtocolException
     * @throws IOException
     */
    public String postForm(String url, Map<String, Object> map, boolean retry)
            throws ClientProtocolException, IOException {
        Assert.notNull(url, "Error posting data, url is null.");
        return postInner(retry, parseHttpPost(url, getPostParams(map)));
    }

    /**
     * post提交表单,http参数是 Json参数
     *
     * @param url
     * @param json
     * @param retry
     * @return
     * @throws ClientProtocolException
     * @throws IOException
     */
    public String postJson(String url, String json, boolean retry)
            throws ClientProtocolException, IOException {
        Assert.notNull(url, "Error posting data, url is null.");
        HttpPost parsedHttpPost = parseHttpPost(url, getPostParams(json));
        parsedHttpPost.addHeader("Content-Type", "application/json");
        return postInner(retry, parsedHttpPost);
    }

    /**
     * 直接返回CloseableHttpResponse
     *
     * @param url
     * @param json
     * @return
     * @throws IOException
     */
    public CloseableHttpResponse post(String url, String json) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        if (json != null) {
            httpPost.setEntity(getPostParams(json));
        }

        return client.execute(httpPost);
    }

    /**
     * 直接返回CloseableHttpResponse
     *
     * @param url
     * @param params
     * @return
     * @throws IOException
     */
    public CloseableHttpResponse get(String url, Map<String, Object> params) throws IOException {
        String apiUrl = url;
        StringBuffer param = new StringBuffer();
        CloseableHttpResponse response = null;
        int i = 0;
        for (String key : params.keySet()) {
            if (i == 0) {
                param.append("?");
            } else {
                param.append("&");
            }

            param.append(key).append("=").append(params.get(key));
            i++;
        }
        apiUrl += param;

        HttpGet httpPost = new HttpGet(apiUrl);
        return client.execute(httpPost);
    }

    private String postInner(boolean retry, HttpPost httpPost) throws IOException {
        // HttpResponse response = null;
        CloseableHttpResponse response = null;
        try {
            // CloseableHttpClient httpClient = getHttpClient(retry);
            response = client.execute(httpPost);
            if (response.getStatusLine().getStatusCode() != HTTP_SUCCESS) {
//				httpPost.abort();
                return null;
            }
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity, Consts.UTF_8);
            EntityUtils.consume(entity);
            return result;
        } finally {
            httpPost.abort();
            // httpPost.releaseConnection();
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                log.error(e.toString());
            }
        }
    }

    private HttpPost parseHttpPost(String url, HttpEntity postParams) {
        HttpPost httpPost = new HttpPost(url);
        // httpPost.setConfig(getConfig());
        httpPost.setEntity(postParams);
        return httpPost;
    }

    private HttpEntity getPostParams(Map<String, Object> params) {
        if (params == null || params.size() == 0) {
            return null;
        }
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        Set<String> keySet = params.keySet();
        for (String key : keySet) {
            nvps.add(new BasicNameValuePair(key, params.get(key).toString()));
        }
        return new UrlEncodedFormEntity(nvps, Consts.UTF_8);
    }

    public HttpEntity getPostParams(String params) {
        return new StringEntity(params, Consts.UTF_8);
    }

    public void downloadFile(String url, String localFilePath) throws DownloadFileException {
        OutputStream out = null;
        InputStream in = null;

        try {
            HttpGet httpGet = new HttpGet(url);
            HttpResponse httpResponse = client.execute(httpGet);
            HttpEntity entity = httpResponse.getEntity();

            in = entity.getContent();

            if (!entity.getContentType().getValue().equalsIgnoreCase("application/octet-stream")) {
                out = new ByteArrayOutputStream();
                IOUtils.copy(in, out);

                throw new DownloadFileException(out.toString(), "下载文件失败");
            }

            File file = new File(localFilePath);
            out = new FileOutputStream(file, false);
            IOUtils.copy(in, out);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    public String uploadFile(String url, String filePath) {
        try {
            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
            File file = new File(filePath);

            multipartEntityBuilder.addBinaryBody("file", file);

            HttpEntity httpEntity = multipartEntityBuilder.build();

            HttpPost httpPost = new HttpPost(url);
            httpPost.setEntity(httpEntity);

            HttpResponse httpResponse = client.execute(httpPost);
            HttpEntity responseEntity = httpResponse.getEntity();
            int statusCode= httpResponse.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                String result = EntityUtils.toString(responseEntity, Consts.UTF_8);
                EntityUtils.consume(responseEntity);

                return result;
            }
        } catch (IOException e) {
            log.error(String.format("上传文件 %s 到 %s 异常", filePath, url));
            e.printStackTrace();
        }

        return "";
    }

    public String sendFileAndJson(String url, String filePath, String jsonContent) {
        try {
            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();

            if (!StringUtils.isEmpty(filePath)) {
                File file = new File(filePath);
                if (file.exists() && file.isFile()) {
                    multipartEntityBuilder.addBinaryBody("file", file);
                }
            }

            if (!StringUtils.isEmpty(jsonContent)) {
                multipartEntityBuilder.addTextBody("content", jsonContent, ContentType.APPLICATION_JSON);
            }

            HttpEntity httpEntity = multipartEntityBuilder.build();

            HttpPost httpPost = new HttpPost(url);
            httpPost.setEntity(httpEntity);

            HttpResponse httpResponse = client.execute(httpPost);
            HttpEntity responseEntity = httpResponse.getEntity();
            int statusCode= httpResponse.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                String result = EntityUtils.toString(responseEntity, Consts.UTF_8);
                EntityUtils.consume(responseEntity);

                return result;
            }
        } catch (Exception e) {
            log.error(String.format("发送文件 %s 到 %s 异常", filePath, url));
            e.printStackTrace();
        }

        return "";
    }

    class RetryHandler implements HttpRequestRetryHandler {
        @Override
        public boolean retryRequest(IOException exception, int executionCount,
                                    HttpContext context) {
            System.out.println(executionCount);
            if (executionCount >= RETRY_TIME) {
                // Do not retry if over max retry count

                return false;
            }
            if (exception instanceof InterruptedIOException) {
                // Timeout
                return false;
            }
            if (exception instanceof UnknownHostException) {
                // Unknown host
                return false;
            }
            if (exception instanceof ConnectTimeoutException) {
                // Connection refused
                return false;
            }
           /* if (exception instanceof SSLException) {
                // SSL handshake exception
                return false;
            }*/

            HttpClientContext clientContext = HttpClientContext.adapt(context);
            HttpRequest request = clientContext.getRequest();

            // 增加日志，便于确认是否重试


            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(" retryCount = " + executionCount);
            }

            boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
            if (idempotent) {
                // Retry if the request is considered idempotent
                return true;
            }
            return false;
        }

    }

}