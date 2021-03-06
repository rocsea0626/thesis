package fi.hiit.complesense.json;

import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 24.10.2014.
 */
public class JsonSSI
{
    public static final short R = 0x52; //Request sensor data
    public static final short V = 0x56; //Sensor data response
    public static final short C = 0x43; //Discover sensors
    public static final short N = 0x4E; //Discover reply
    public static final short Q = 0x51; //Query
    public static final short A = 0x41;
    public static final short S = 0x53;

    public static final short NEW_STREAM_CONNECTION = 0x08;
    public static final short NEW_CONNECTION = 0x09; // Accept new connection at server
    public static final short RTT_LAST = 0x10; // rtt query
    public static final short SEND_DATA = 0x30; //Send data

    public static final int DISCONNECT = 0x80;
    public static final int WAV_STREAM_DISCONNECT = 0x81;
    public static final int JSON_STREAM_DISCONNECT = 0x82;

    public static final String COMMAND = "command";
    public static final String DESC = "description";
    public static final String SENSOR_TYPES = "sensor_types";
    public static final String SOCKET_CHANNEL = "socket_channel";
    public static final String TIMESTAMP = "timestamp";
    public static final String ROUNDS = "rrt_rounds";
    public static final String ORIGIN_HOST = "origin_host";
    public static final String ORIGIN_PORT = "origin_port";
    public static final String TIME_DIFF = "time_diff";
    public static final String SAMPLES_PER_SECOND = "samples_per_second";
    public static final String STREAM_PORT = "stream_port";
    public static final String SENSOR_TYPE = "sensor_type";
    public static final String SENSOR_VALUES = "sv";
    public static final String STREAM_SERVER_THREAD_ID = "stream_server_thread_id";
    public static final String WEB_SOCKET_KEY = "web_socket_key";
    public static final String DATA_TO_SEND = "data_to_send";
    public static final String LOCAL_TIME = "local_time";
    public static final String SENSOR_PACKET = "sp";
    public static final String OK_TO_SEND = "ok_to_send";
    public static final String IMAGE_COMMAND = "image_command";
    public static final String START_SEND_IMG = "start_image_send";
    public static final String COMPLETE_SEND_IMG = "complete_image_send";
    public static final String IMAGE_ORIENTATIONS = "image_orientations";
    public static final String IMAGE_NAME = "image_name";
    public static final String SYSTEM_STATUS = "system_status";
    public static final String BATTERY_LEVEL = "battery_level";
    public static final String QUERY_CONTENT = "query_content";
    public static final String CONF_CONTENT = "configuration_content";
    public static final String SECONDARY_MASTER = "secondary_master";
    public static final String IS_CLIENT_LOCAL = "is_client_local";
    public static final String CONF_VAL = "conf_val";


    public static JSONObject makeSensorDiscvoeryReq() throws JSONException
    {
        JSONObject req = new JSONObject();
        req.put(COMMAND, C);
        req.put(DESC, "Discover sensors request");
        return req;
    }

    public static JSONObject makeSensorDiscvoeryRep(List<Integer> sensorTypes) throws JSONException
    {
        JSONObject rep = new JSONObject();
        rep.put(COMMAND, N);
        JSONArray jsonArray = new JSONArray(sensorTypes);
        rep.put(SENSOR_TYPES, jsonArray);
        rep.put(LOCAL_TIME, System.currentTimeMillis());
        rep.put(DESC, "Discover sensors reply");
        return rep;
    }

    public static JSONObject makeRttQuery(long timeStamp,
                                          int rounds) throws JSONException
    {
        JSONObject query = new JSONObject();
        //query.put(COMMAND, RTT_QUERY);
        query.put(TIMESTAMP, timeStamp);
        query.put(ROUNDS, rounds);

        return query;
    }

    public static JSONObject makeStartStreamReq(JSONArray sensorTypes, long timeDiff, int streamPort) throws JSONException
    {
        //Log.i(TAG, "makeStartStreamReq(): " + sensorTypes.toString());
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(COMMAND, R);
        jsonObject.put(SENSOR_TYPES, sensorTypes);
        jsonObject.put(TIME_DIFF, timeDiff);
        jsonObject.put(STREAM_PORT, streamPort);
        jsonObject.put(DESC, "Start Streaming Request");
        return jsonObject;
    }

    public static JSONObject makeLastRttReceived(WebSocket webSocket) throws JSONException {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put(COMMAND, RTT_LAST);
        jsonObject.put(WEB_SOCKET_KEY, webSocket.toString());
        jsonObject.put(DESC, "Last RTT pong message has been received");
        return jsonObject;
    }

    public static JSONObject makeBatteryRep(double battery, boolean mIsLocal) throws JSONException {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put(COMMAND, A);
        jsonObject.put(BATTERY_LEVEL, battery);
        jsonObject.put(IS_CLIENT_LOCAL, mIsLocal);
        jsonObject.put(DESC, "Battery level");
        return jsonObject;
    }


    public static JSONObject makeJsonBatteryQuery() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(JsonSSI.COMMAND, JsonSSI.Q);
        jsonObject.put(JsonSSI.QUERY_CONTENT, JsonSSI.BATTERY_LEVEL);
        return jsonObject;
    }

    public static JSONObject makeJsonAssignSecondaryMaster(boolean isSecondaryMaster) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(JsonSSI.COMMAND, JsonSSI.S);
        jsonObject.put(JsonSSI.CONF_CONTENT, JsonSSI.SECONDARY_MASTER);
        jsonObject.put(JsonSSI.CONF_VAL, isSecondaryMaster);
        return jsonObject;
    }

    public static JSONObject makeJsonDisconnect(WebSocket webSocket) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(JsonSSI.SYSTEM_STATUS, JsonSSI.DISCONNECT);
        jsonObject.put(JsonSSI.WEB_SOCKET_KEY, webSocket.toString());
        return jsonObject;
    }

    public static JSONObject makeJsonWavStreamDisconnet(WebSocket webSocket) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(JsonSSI.SYSTEM_STATUS, JsonSSI.WAV_STREAM_DISCONNECT);
        jsonObject.put(JsonSSI.WEB_SOCKET_KEY, webSocket.toString());
        return jsonObject;
    }

    public static JSONObject makeJsonJsonStreamDisconnet(WebSocket webSocket) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(JsonSSI.SYSTEM_STATUS, JsonSSI.JSON_STREAM_DISCONNECT);
        jsonObject.put(JsonSSI.WEB_SOCKET_KEY, webSocket.toString());
        return jsonObject;
    }
}
