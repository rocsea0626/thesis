package fi.hiit.complesense.connection;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 27.11.2014.
 */
public class ConnectorStreaming extends AbsSystemThread
{
    public static final String TAG = ConnectorStreaming.class.getSimpleName();

    private final URI jsonStreamUri, wavStreamUri;
    private final CountDownLatch latch;
    private WebSocket mJsonWebSocket = null, mWavWebSocket = null;
    private WebSocket.StringCallback mStringCallback;
    private DataCallback mDataCallback;


    public ConnectorStreaming(ServiceHandler serviceHandler, InetAddress ownerInetAddr, int streamPort, CountDownLatch latch) {
        super(TAG, serviceHandler);
        //uri = URI.create(Constants.WEB_PROTOCOL +"://"+ ownerInetAddr.getHostAddress()+":"+ streamPort + "/streaming_json");
        jsonStreamUri = URI.create(Constants.WEB_PROTOCOL +"://"+ ownerInetAddr.getHostAddress()+":"+ streamPort + "/streaming_json");
        wavStreamUri = URI.create(Constants.WEB_PROTOCOL +"://"+ ownerInetAddr.getHostAddress()+":"+ streamPort + "/streaming_wav");
        this.latch = latch;

        mStringCallback = new WebSocket.StringCallback(){
            @Override
            public void onStringAvailable(String s) {
                Log.e(TAG, "Streaming connect should not recv String: " + s);
            }
        };

        mDataCallback = new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                Log.i(TAG, "Streaming connect should not recv binary data: " + byteBufferList.getAll().array().length + " bytes");
            }
        };



    }

    @Override
    public void run() {
        String txt = "Starts ConnectorStreaming at thread id: " + Thread.currentThread().getId();
        Log.e(TAG, txt);
        serviceHandler.updateStatusTxt(txt);
        serviceHandler.workerThreads.put(TAG, this);

        AsyncHttpClient.getDefaultInstance().websocket(jsonStreamUri.toString(),
                Constants.WEB_PROTOCOL, new AsyncHttpClient.WebSocketConnectCallback() {

                    @Override
                    public void onCompleted(Exception e, WebSocket webSocket) {
                        Log.i(TAG, "onCompleted(" + jsonStreamUri.toString() + ")");
                        if (e != null) {
                            Log.e(TAG, e.toString());
                            return;
                        }
                        serviceHandler.updateStatusTxt("Connection with " + jsonStreamUri.toString() + " is established");

                        mJsonWebSocket = webSocket;
                        latch.countDown();

                        mJsonWebSocket.setStringCallback(mStringCallback);
                        mJsonWebSocket.setDataCallback(mDataCallback);
                        mJsonWebSocket.setEndCallback(new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception e) {
                                Log.e(TAG, e.toString());
                                if (mJsonWebSocket != null)
                                    mJsonWebSocket.close();
                            }
                        });
                    }
                });
        AsyncHttpClient.getDefaultInstance().websocket(wavStreamUri.toString(),
                Constants.WEB_PROTOCOL, new AsyncHttpClient.WebSocketConnectCallback() {

                    @Override
                    public void onCompleted(Exception e, WebSocket webSocket) {
                        Log.i(TAG, "onCompleted(" + wavStreamUri.toString() + ")");
                        if (e != null) {
                            Log.e(TAG, e.toString());
                            return;
                        }
                        serviceHandler.updateStatusTxt("Connection with " + wavStreamUri.toString() + " is established");

                        mWavWebSocket = webSocket;
                        latch.countDown();

                        mWavWebSocket.setStringCallback(mStringCallback);
                        mWavWebSocket.setDataCallback(mDataCallback);
                        mWavWebSocket.setEndCallback(new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception e) {
                                Log.e(TAG, e.toString());
                                if (mWavWebSocket != null)
                                    mWavWebSocket.close();
                            }
                        });
                    }
                });
    }

    public WebSocket getJsonWebSocket() {
        return mJsonWebSocket;
    }

    public WebSocket getWavWebSocket() {
        return mWavWebSocket;
    }

    @Override
    public void stopThread() {
        //String txt = "Stopping ConnectorStreaming at thread id: " + Thread.currentThread().getId();
        //Log.e(TAG, txt);
        //serviceHandler.updateStatusTxt(txt);
        Log.i(TAG, "stopThread()");
        if(mJsonWebSocket!=null)
            mJsonWebSocket.close();
        if(mWavWebSocket!=null)
            mWavWebSocket.close();
    }
}
