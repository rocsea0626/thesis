package fi.hiit.complesense.core;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.provider.MediaStore;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.audio.AudioStreamClient;
import fi.hiit.complesense.audio.SendAudioThread;
import fi.hiit.complesense.connection.AsyncStreamClient;
import fi.hiit.complesense.connection.ConnectorUDP;
import fi.hiit.complesense.connection.UdpConnectionRunnable;
import fi.hiit.complesense.img.ImageWebSocketClient;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SensorUtil;
import fi.hiit.complesense.util.SystemUtil;

import static fi.hiit.complesense.json.JsonSSI.COMMAND;

/**
 * Created by rocsea0626 on 31.8.2014.
 */
public class ClientServiceHandler extends ServiceHandler
{
    private static final String TAG = "ClientServiceHandler";
    private final InetAddress ownerAddr;
    private int serverWebSocketPort;
    private LocationManager locationManager = null;
    private LocationListener mLocationDataListener = null;


    public ClientServiceHandler(Messenger serviceMessenger,
                                String name, Context context,
                                InetAddress ownerAddr, int delay)

    {
        super(serviceMessenger, name, context, false, ownerAddr, delay);
        this.ownerAddr = ownerAddr;
    }

    @Override
    public boolean handleMessage(Message msg)
    {
        if(!super.handleMessage(msg))
        {
            if(msg.what == JSON_RESPONSE_BYTES)
            {
                JSONObject jsonObject = (JSONObject)msg.obj;
                try
                {
                    SocketChannel socketChannel = (SocketChannel)jsonObject.get(JsonSSI.SOCKET_CHANNEL);
                    Socket socket = socketChannel.socket();

                    switch(jsonObject.getInt(COMMAND))
                    {
                        case JsonSSI.C:
                            absAsyncIO.send(socketChannel,JsonSSI.makeSensorDiscvoeryRep(SensorUtil.getLocalSensorTypeList(context)));
                            return true;
                        case JsonSSI.R:
                            JSONArray sensorConfigJson = jsonObject.getJSONArray(JsonSSI.SENSOR_TYPES);
                            int streamServerPort = jsonObject.getInt(JsonSSI.STREAM_PORT);

                            Log.i(TAG, "sensorConfigJson:" + sensorConfigJson.toString());
                            startStreaming(sensorConfigJson, streamServerPort);
                            return true;
                        default:
                            Log.i(TAG, "Unknown command...");
                            break;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    Log.i(TAG, e.toString());
                }
            }
        }


        return false;
    }

    private void startStreaming(JSONArray sensorConfigJson, int port) throws IOException, JSONException
    {
        updateStatusTxt("Start Streaming client");
        int startSignal = 1;
        CountDownLatch latch = new CountDownLatch(startSignal);
        AsyncStreamClient asyncStreamClient = new AsyncStreamClient(this, ownerAddr, port, latch);
        workerThreads.put(AsyncStreamClient.TAG, asyncStreamClient);
        asyncStreamClient.start();

        Set<Integer> requiredSensors = SystemConfig.getSensorTypesFromJson(sensorConfigJson);
        updateStatusTxt("Required sensors: "+ requiredSensors.toString());

        if(requiredSensors.remove(SensorUtil.SENSOR_MIC)){
            AudioStreamClient audioStreamClient = new AudioStreamClient(
                    this, asyncStreamClient, latch);
            workerThreads.put(AudioStreamClient.TAG,audioStreamClient);
            audioStreamClient.start();
        }

        if(requiredSensors.remove(SensorUtil.SENSOR_CAMERA))
        {

        }

        if(requiredSensors.remove(SensorUtil.SENSOR_GPS))
        {
            locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            mLocationDataListener = new LocationDataListener(this, context, asyncStreamClient);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationDataListener);
        }

        if(requiredSensors.size()>0){
            SensorDataCollectionThread sensorDataCollectionThread = new SensorDataCollectionThread(
                    this, context, requiredSensors, asyncStreamClient, latch);
            workerThreads.put(SensorDataCollectionThread.TAG,sensorDataCollectionThread);
            sensorDataCollectionThread.start();
        }
    }

    public void sendImg2Server(File imgFile)
    {
        Log.i(TAG, "sendImg2Server(imgFile: "+ imgFile +") @ thread id: " + Thread.currentThread().getId() );
        SocketAddress serverSocketAddr = new InetSocketAddress(ownerAddr.getHostAddress(), serverWebSocketPort);
        ImageWebSocketClient socketClient = new ImageWebSocketClient(imgFile, serverSocketAddr, this);
        socketClient.connect();
    }
}
