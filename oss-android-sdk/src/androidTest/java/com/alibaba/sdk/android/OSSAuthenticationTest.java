package com.alibaba.sdk.android;

import android.test.AndroidTestCase;

import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.common.HttpMethod;
import com.alibaba.sdk.android.oss.common.OSSConstants;
import com.alibaba.sdk.android.oss.common.OSSLog;
import com.alibaba.sdk.android.oss.common.RequestParameters;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSCustomSignerCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationToken;
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider;
import com.alibaba.sdk.android.oss.common.utils.DateUtil;
import com.alibaba.sdk.android.oss.common.utils.IOUtils;
import com.alibaba.sdk.android.oss.common.utils.OSSUtils;
import com.alibaba.sdk.android.oss.internal.OSSAsyncTask;
import com.alibaba.sdk.android.oss.model.GeneratePresignedUrlRequest;
import com.alibaba.sdk.android.oss.model.GetObjectRequest;
import com.alibaba.sdk.android.oss.model.GetObjectResult;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by LK on 15/12/2.
 */
public class OSSAuthenticationTest extends AndroidTestCase {
    private OSS oss;
    @Override
    protected void setUp() throws Exception {
        OSSTestConfig.instance(getContext());
        super.setUp();
        if (oss == null) {
            OSSLog.enableLog();
            Thread.sleep(5 * 1000); // for logcat initialization
        }
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, OSSTestConfig.credentialProvider);
    }

    public void testNullCredentialProvider() throws Exception {
        boolean thrown = false;
        try {
            oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, null);
        } catch (Exception e) {
            thrown = true;
            assertTrue(e instanceof IllegalArgumentException);
        }
        assertTrue(thrown);
    }

    public void testValidCustomSignCredentialProvider() throws Exception {
        final OSSCredentialProvider credentialProvider = new OSSCustomSignerCredentialProvider() {
            @Override
            public String signContent(String content) {
                String signature = "";
                try {
                    signature = OSSUtils.sign("wrong-AK", "wrong-SK", content);
                    assertNotNull(signature);
                    OSSLog.logDebug(signature);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                return signature;
            }
        };
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, credentialProvider);
        assertNotNull(oss);
        PutObjectRequest put = new PutObjectRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m",
                OSSTestConfig.FILE_DIR + "file1m");
        OSSTestConfig.TestPutCallback putCallback = new OSSTestConfig.TestPutCallback();
        OSSAsyncTask putTask = oss.asyncPutObject(put, putCallback);
        putTask.waitUntilFinished();
        assertNotNull(putCallback.serviceException);
        assertEquals(403, putCallback.serviceException.getStatusCode());
    }

    public void testValidCustomSignCredentialProviderKeyError() throws Exception {
        final OSSCredentialProvider credentialProvider = new OSSCustomSignerCredentialProvider() {
            @Override
            public String signContent(String content) {
                String signature = "";
                try {
                    signature = OSSUtils.sign("wrong-AK", null, content);
                    assertNotNull(signature);
                    OSSLog.logDebug(signature);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                return signature;
            }
        };
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, credentialProvider);
        assertNotNull(oss);
        PutObjectRequest put = new PutObjectRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m",
                OSSTestConfig.FILE_DIR + "file1m");
        OSSTestConfig.TestPutCallback putCallback = new OSSTestConfig.TestPutCallback();
        OSSAsyncTask putTask = oss.asyncPutObject(put, putCallback);
        putTask.waitUntilFinished();
        assertNull(putCallback.serviceException);
    }

    public void testPutObjectWithNullFederationCredentialProvider() throws Exception {
        final OSSCredentialProvider credentialProvider = new OSSFederationCredentialProvider() {
        @Override
        public OSSFederationToken getFederationToken() {
            return null;
        }
    };
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, credentialProvider);
        assertNotNull(oss);
        PutObjectRequest put = new PutObjectRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m",
                OSSTestConfig.FILE_DIR + "file1m");
        OSSTestConfig.TestPutCallback putCallback = new OSSTestConfig.TestPutCallback();
        OSSAsyncTask putTask = oss.asyncPutObject(put, putCallback);
        putTask.waitUntilFinished();
        assertNull(putCallback.result);
        assertNotNull(putCallback.clientException);
        assertTrue(putCallback.clientException.getMessage().contains("Can't get a federation token"));
    }

    public void testPresignObjectURL() throws Exception {
        String url = oss.presignConstrainedObjectURL(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m", 15 * 60);

        OSSLog.logDebug("[testPresignConstrainedObjectURL] - " + url);
        Request request = new Request.Builder().url(url).build();
        Response resp = new OkHttpClient().newCall(request).execute();

        assertEquals(200, resp.code());
        assertEquals(1024 * 1000, resp.body().contentLength());
    }

    public void testPresignObjectURLWithProcess() throws Exception {
        GeneratePresignedUrlRequest signrequest = new GeneratePresignedUrlRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "shilan.jpg", 15 * 60);
        signrequest.setExpiration(15 * 60);
        signrequest.setProcess("image/resize,m_lfit,w_100,h_100");

        String url = oss.presignConstrainedObjectURL(signrequest);

        Request request = new Request.Builder().url(url).build();
        Response resp = new OkHttpClient().newCall(request).execute();
        OSSLog.logDebug("[testPresignConstrainedObjectURL] - " + url);
        assertEquals(200, resp.code());
    }

    public void testPresignObjectURLWithGeneratePresignedUrlRequest() throws Exception {
        GeneratePresignedUrlRequest signrequest = new GeneratePresignedUrlRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m", 15 * 60);
        signrequest.setQueryParameter(new HashMap<String, String>(){
            {
                put("queryKey1","value1");
            }
        });
        signrequest.setMethod(HttpMethod.GET);
        signrequest.addQueryParameter("queryKey2","value2");

        String rawUrl = oss.presignConstrainedObjectURL(signrequest);

        signrequest.setContentMD5("80ec129d645c70cf0de45b1a5a682235");
        signrequest.setContentType("application/octet-stream");

        String url = oss.presignConstrainedObjectURL(signrequest);

        assertTrue(!url.equals(rawUrl));

        signrequest.setQueryParameter(new HashMap<String, String>(){
            {
                put("queryKey11","value11");
            }
        });
        String url2 = oss.presignConstrainedObjectURL(signrequest);

        assertTrue(!url2.equals(rawUrl));
    }

    public void testPresignObjectURLWithErrorParams() {
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m", 15 * 60, HttpMethod.POST);
            request.setQueryParameter(null);
            assertTrue(false);
        }catch (Exception e){
            assertTrue(true);
        }
    }

    public void testPresignObjectURLWithOldAkSk() throws Exception {
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, new OSSPlainTextAKSKCredentialProvider(OSSTestConfig.AK, OSSTestConfig.SK));
        String url = oss.presignConstrainedObjectURL(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m", 15 * 60);

        OSSLog.logDebug("[testPresignObjectURLWithOldAkSk] - " + url);
        Request request = new Request.Builder().url(url).build();
        Response resp = new OkHttpClient().newCall(request).execute();

        assertEquals(200, resp.code());
        assertEquals(1024 * 1000, resp.body().contentLength());
    }

    public void testPresignObjectURLWithCustomSignCredentialProvider() throws Exception {
        final OSSCredentialProvider credentialProvider = new OSSCustomSignerCredentialProvider() {
            @Override
            public String signContent(String content) {
                String signature = "";
                try {
                    signature = OSSUtils.sign(OSSTestConfig.AK.trim(), OSSTestConfig.SK.trim(), content);
                    assertNotNull(signature);
                    OSSLog.logDebug(signature);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                return signature;
            }
        };
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, credentialProvider);
        String url = oss.presignConstrainedObjectURL(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m", 15 * 60);

        OSSLog.logDebug("[testCustomSignCredentialProvider] - " + url);
        Request request = new Request.Builder().url(url).build();
        Response resp = new OkHttpClient().newCall(request).execute();

        assertEquals(200, resp.code());
        assertEquals(1024 * 1000, resp.body().contentLength());
    }

    public void testPresignPublicObjectURL() throws Exception {
        String url = oss.presignPublicObjectURL(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m");
        OSSLog.logDebug("[testPresignPublicObjectURL] - " + url);

        Request request = new Request.Builder().url(url).build();
        Response resp = new OkHttpClient().newCall(request).execute();

        assertEquals(200, resp.code());
        assertEquals(1024 * 1000, resp.body().contentLength());
    }

    public void testPresignObjectURLWithWrongBucket() throws Exception {
        try {
            String url = oss.presignConstrainedObjectURL("wrong-bucket", "file1m", 15 * 60);
            Request request = new Request.Builder().url(url).build();
            Response response = new OkHttpClient().newCall(request).execute();
            assertEquals(404, response.code());
            assertEquals("Not Found", response.message());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

//    public void testPresignObjectURLWithWrongObjectKey() throws Exception {
//        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT,
//                new OSSPlainTextAKSKCredentialProvider(OSSTestConfig.AK, OSSTestConfig.SK));
//        try {
//            String url = oss.presignConstrainedObjectURL(OSSTestConfig.ANDROID_TEST_BUCKET, "wrong-key", 15 * 60);
//            Request request = new Request.Builder().url(url).build();
//            Response response = new OkHttpClient().newCall(request).execute();
//            OSSLog.logError(response.body().string());
//            assertEquals(404, response.code());
//            assertEquals("Not Found", response.message());
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

//    public void testPresignObjectURLWithExpiredTime() throws Exception {
//        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT,
//                new OSSPlainTextAKSKCredentialProvider(OSSTestConfig.AK, OSSTestConfig.SK));
//        final CountDownLatch latch1 = new CountDownLatch(1);
//        final CountDownLatch latch2 = new CountDownLatch(1);
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    String url = oss.presignConstrainedObjectURL(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m", 1);
//                    latch1.await();
//                    Request request = new Request.Builder().url(url).build();
//                    Response response = new OkHttpClient().newCall(request).execute();
//                    assertEquals(403, response.code());
//                    assertEquals("Forbidden", response.message());
//                    latch2.countDown();
//                }
//                catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }).start();
//        latch2.await(20, TimeUnit.SECONDS);
//        latch1.countDown();
//        latch2.await();
//        OSSLog.logDebug("testPresignObjectURLWithExpiredTime success.");
//    }

    public void testTimeTooSkewedAndAutoFix() throws Exception {

        DateUtil.setCurrentServerTime(0);

        assertTrue(DateUtil.getFixedSkewedTimeMillis() < 1000);

        GetObjectRequest get = new GetObjectRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m");
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, OSSTestConfig.credentialProvider);

        GetObjectResult getResult = oss.getObject(get);
        assertEquals(200, getResult.getStatusCode());

        assertTrue(Math.abs(DateUtil.getFixedSkewedTimeMillis() - System.currentTimeMillis()) < 5 * 60 * 1000);
    }

    public void testOSSPlainTextAKSKCredentialProvider() throws Exception{
        GetObjectRequest get = new GetObjectRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m");
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, OSSTestConfig.plainTextAKSKcredentialProvider);

        GetObjectResult getResult = oss.getObject(get);
        assertEquals(200, getResult.getStatusCode());
    }

    public void testOSSFederationToken() throws Exception{
        GetObjectRequest get = new GetObjectRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m");
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, OSSTestConfig.fadercredentialProvider);
        GetObjectResult getResult = oss.getObject(get);
        assertEquals(200, getResult.getStatusCode());
    }

    public void testOSSFederationTokenWithWrongExpiration() throws Exception{
        GetObjectRequest get = new GetObjectRequest(OSSTestConfig.ANDROID_TEST_BUCKET, "file1m");
        oss = new OSSClient(getContext(), OSSTestConfig.ENDPOINT, OSSTestConfig.fadercredentialProviderWrong);
        GetObjectResult getResult = oss.getObject(get);
        assertEquals(200, getResult.getStatusCode());
    }

    //测试token失效重刷新回调
    public void testFederationTokenExpiration() throws Exception{
        long firstTime = System.currentTimeMillis()/1000;
        OSSFederationCredentialProvider federationCredentialProvider = new OSSFederationCredentialProvider() {
            @Override
            public OSSFederationToken getFederationToken() {
                OSSLog.logError("[getFederationToken] -------------------- ");
                try {
                    InputStream input = getContext().getAssets().open("test_sts.json");
                    String jsonText = IOUtils.readStreamAsString(input, OSSConstants.DEFAULT_CHARSET_NAME);
                    JSONObject jsonObjs = new JSONObject(jsonText);
                    String ak = jsonObjs.getString("AccessKeyId");
                    String sk = jsonObjs.getString("AccessKeySecret");
                    String token = jsonObjs.getString("SecurityToken");
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    Date date = new Date(System.currentTimeMillis()-(8*60*60*1000));//测试超时
                    String expiration = sdf.format(date);
                    return new OSSFederationToken(ak, sk, token, expiration);
                } catch (Exception e) {
                    OSSLog.logError(e.toString());
                    e.printStackTrace();
                }
                return null;
            }
        };
        federationCredentialProvider.getValidFederationToken();
        Thread.sleep(2000l);
        federationCredentialProvider.getValidFederationToken();
        federationCredentialProvider.getCachedToken().toString();
        assertEquals(true,firstTime < federationCredentialProvider.getCachedToken().getExpiration());
    }
}
