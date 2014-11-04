package fi.hiit.complesense.core;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.connection.AsyncStreamClient;
import fi.hiit.complesense.json.JsonSSI;

/**
 * Created by hxguo on 29.10.2014.
 */
public class SensorDataCollectionThread extends AbsSystemThread
{
    public static final float[] dummies = new float[3];
    public static final String TAG = SensorDataCollectionThread.class.getSimpleName();

    private final SensorManager mSensorManager;
    private final AsyncStreamClient asyncStreamClient;
    private final Set<Integer> requiredSensors;
    private final CountDownLatch startSignal;
    private SensorEventListener mListener;

    public SensorDataCollectionThread(ServiceHandler serviceHandler,
                                      Context context,
                                      Set<Integer> requiredSensors,
                                      AsyncStreamClient asyncStreamClient, CountDownLatch latch)
    {
        super(TAG, serviceHandler);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.asyncStreamClient = asyncStreamClient;
        this.requiredSensors = requiredSensors;
        startSignal = latch;
    }

    @Override
    public void run()
    {
        Log.i(TAG, " starts running at thread: " + Thread.currentThread().getId());
        try {
            startSignal.await();
            mListener = new SensorEventListener()
            {
                ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE/8);
                @Override
                public void onSensorChanged(SensorEvent sensorEvent)
                {
                    //String formatedStr =  formatSensorValues(sensorEvent);

                    JSONObject jsonObject = new JSONObject();
                    try {
                        jsonObject.put(JsonSSI.COMMAND, JsonSSI.V);
                        jsonObject.put(JsonSSI.TIMESTAMP, System.currentTimeMillis());
                        jsonObject.put(JsonSSI.SENSOR_TYPE, sensorEvent.sensor.getType());
                        JSONArray jsonArray = new JSONArray();
                        for(float value:sensorEvent.values)
                            jsonArray.put(value);
                        jsonObject.put(JsonSSI.SENSOR_VALUES, jsonArray);
                        //Log.i(TAG, jsonObject.toString());
                        //bb.clear();
                        //ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE/8);
                        //bb.putInt(jsonObject.toString().length());
                        //asyncStreamClient.send(bb.array());
                        asyncStreamClient.send(jsonObject.toString().getBytes());
                    } catch (JSONException e) {
                        Log.i(TAG, e.toString());
                    }
                }
                @Override
                public void onAccuracyChanged(Sensor sensor, int i) {

                }
            };

            registerSensors();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private String formatSensorValues(SensorEvent sensorEvent)
    {
        String formatted = Integer.toString(sensorEvent.sensor.getType()) + "\t"
                + String.valueOf(System.currentTimeMillis()) + "\t"
                + "\t" + String.valueOf(sensorEvent.timestamp)
                + "\t" + String.valueOf(sensorEvent.values[0])
                + "\t" + String.valueOf(sensorEvent.values[1])
                + "\t" + String.valueOf(sensorEvent.values[2])
                + "\r\n";
        return formatted;
    }

    private void registerSensors()
    {
        for(int type : requiredSensors)
        {
            mSensorManager.registerListener(mListener,
                    mSensorManager.getDefaultSensor(type), SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void stopThread()
    {
        keepRunning = false;
        unRegisterSensors();
    }

    private void unRegisterSensors()
    {
        if(mSensorManager!=null){
            mSensorManager.unregisterListener(mListener);
        }
    }


}