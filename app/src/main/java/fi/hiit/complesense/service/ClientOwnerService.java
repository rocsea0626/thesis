package fi.hiit.complesense.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.util.Map;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.ClientServiceHandler;
import fi.hiit.complesense.core.ComleSenseDevice;
import fi.hiit.complesense.core.GroupOwnerServiceHandler;
import fi.hiit.complesense.core.WifiConnectionManager;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 7/28/14.
 */
public class ClientOwnerService extends AbstractGroupService
{
    private static final String TAG = "ClientOwnerService";

    private boolean retryChannel = false;
    private Context context;

    WifiP2pManager.DnsSdTxtRecordListener txtListener =
            new WifiP2pManager.DnsSdTxtRecordListener()
            {
                @Override
                public void onDnsSdTxtRecordAvailable(
                        String fullDomain, Map record, WifiP2pDevice device)
                {
                    Log.i(TAG, "DnsSdTxtRecord available -" + record.toString());
                    //Log.i(TAG, device.deviceName + " is "+ record.get(TXTRECORD_PROP_AVAILABLE));
                    SystemUtil.sendStatusTextUpdate(uiMessenger, (String)record.get(
                            WifiConnectionManager.TXTRECORD_SENSOR_TYPE_LIST));
                    SystemUtil.sendStatusTextUpdate(uiMessenger, (String)record.get(
                            WifiConnectionManager.TXTRECORD_NETWORK_INFO));
                    //Log.i(TAG,"available sensors: " + record.get(TXTRECORD_SENSOR_TYPE_LIST));
                    //Log.i(TAG, "available networks: " + record.get(TXTRECORD_NETWORK_INFO));

                    nearbyDevices.put(device.deviceAddress, new ComleSenseDevice(device, record) );
                    Log.i(TAG,"compleSenseDevices.size():" + nearbyDevices.size() );

                    if(nearbyDevices.size()>1)
                    {
                        mWifiConnManager.stopFindingService();

                        groupOwner = mWifiConnManager.decideGroupOnwer(nearbyDevices);
                        if(groupOwner == null)
                        {
                            Log.i(TAG,"groupOwner is null");
                            return;
                        }
                        Log.i(TAG,"Group Owner Addr: " + groupOwner.deviceAddress );

                        if(groupOwner.deviceAddress.equals(mDevice.deviceAddress) )
                            mWifiConnManager.connectP2p(device, 14);
                        else
                        {
                            mWifiConnManager.connectP2p(groupOwner, 1);
                            mWifiConnManager.clearServiceAdvertisement();
                        }
                    }
                }
            };

    WifiP2pManager.DnsSdServiceResponseListener servListener
            = new WifiP2pManager.DnsSdServiceResponseListener()
    {
        @Override
        public void onDnsSdServiceAvailable(String instanceName, String registrationType,
                                            WifiP2pDevice srcDevice)
        {
            // A service has been discovered. Is this our app?
            if (instanceName.equalsIgnoreCase(WifiConnectionManager.SERVICE_INSTANCE))
            {
                Log.i(TAG, "onDnsSdServiceAvailable()");
                // update the UI and add the item the discovered device.
                if(uiMessenger!=null)
                    SystemUtil.sendDnsFoundUpdate(uiMessenger, srcDevice,
                            instanceName);
            }
        }
    };

    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case Constants.SERVICE_MSG_INIT_SERVICE:
                    if(!isInitialized)
                    {
                        uiMessenger = msg.replyTo;
                        SystemUtil.sendSelfInfoUpdate(uiMessenger, mDevice);
                        mWifiConnManager.setUiMessenger(uiMessenger);

                        //clientManager = new ClientManager(mMessenger, context,false);
                        isInitialized = true;
                        sendServiceInitComplete();
                    }
                    break;
                case Constants.SERVICE_MSG_START:
                    start();
                    break;
                case Constants.SERVICE_MSG_STOP:
                    stopSelf();
                    break;
                case Constants.SERVICE_MSG_CONNECT:
                    mWifiConnManager.connectP2p((WifiP2pDevice)msg.obj, 1);
                    break;
                case Constants.SERVICE_MSG_FIND_SERVICE:
                    mWifiConnManager.findService(servListener, txtListener);
                    break;
                case Constants.SERVICE_MSG_STOP_CLIENT_SERVICE:
                    mWifiConnManager.stopFindingService();
                    stopSelf();
                    break;
                case Constants.SERVICE_MSG_CANCEL_CONNECT:
                    mWifiConnManager.cancelConnect();
                    stopSelf();
                    break;
                case Constants.SERVICE_MSG_STATUS_TXT_UPDATE:
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            (String) msg.obj);
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    @Override
    public void onCreate()
    {
        Log.i(TAG, "onCreate()");
        super.onCreate();
        mMessenger = new Messenger(new IncomingHandler());
        receiver = new GroupBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver,intentFilter);
        context = getApplicationContext();

        mWifiConnManager = new WifiConnectionManager(ClientOwnerService.this,
                manager, channel);
    }

    @Override
    public void onDestroy()
    {
        Log.i(TAG, "onDestroy()");
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override
    protected void start()
    {
        Log.i(TAG,"start()");
        if(mWifiConnManager!=null)
            mWifiConnManager.startRegistrationAndDiscovery(servListener, txtListener);
        else
            Log.e(TAG,"mWifiConnManager is null");

    }


    @Override
    protected void stop()
    {
        Log.i(TAG, "stop()");
        if(serviceHandler!=null)
            serviceHandler.stopServiceHandler();

        if (manager != null && channel != null)
        {
            mWifiConnManager.stopGroupOwner();
            mWifiConnManager.clearServiceAdvertisement();
        }

    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo)
    {
        Log.i(TAG,"onConnectionInfoAvailable()");
        if(mMessenger==null)
            Log.e(TAG,"mMessenger is null");
        if (p2pInfo.isGroupOwner)
        {
            if(groupOwner == null)
                groupOwner = mDevice;

            if(groupOwner.deviceAddress.equals(mDevice.deviceAddress) )
            {
                Log.i(TAG, "Device is connected as Group Owner in master mode");
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Device connected as Group Owner");
                if(serviceHandler == null)
                {
                    //serviceHandler = new GroupOwnerManager(mMessenger, context, true);
                    serviceHandler = new GroupOwnerServiceHandler(mMessenger,
                            "GroupOwnerServiceHandler", context);
                    serviceHandler.startServiveHandler();
                }

            }
            else
            {
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Error - Device cannot be launched as Group Owner in client mode");
                Log.e(TAG, "Device cannot be launched as Group Owner in client mode");
            }
            //manager.requestGroupInfo(channel,this);
        }
        else
        {
            Log.i(TAG, "Device is connected as Group Client in client mode");
            SystemUtil.sendStatusTextUpdate(uiMessenger,
                    "Device connected as Client");
            if(serviceHandler == null)
            {
                mWifiConnManager.clearServiceAdvertisement();

                //serviceHandler = new ClientManager(mMessenger, context, false);
                serviceHandler = new ClientServiceHandler(mMessenger,
                        "ClientServiceHandler", context, p2pInfo.groupOwnerAddress, 0);

                Log.i(TAG,"GroupOwner InetAddress: " + p2pInfo.groupOwnerAddress.toString());
                serviceHandler.startServiveHandler();
            }

        }

    }

    /**
     *
     * This BroadcastReceiver intercepts the android.net.ConnectivityManager.CONNECTIVITY_ACTION,
     * which indicates a connection change. It checks whether the type is TYPE_WIFI.
     * If it is, it checks whether Wi-Fi is connected and sets the wifiConnected flag in the
     * main activity accordingly.
     *
     */
    class NetworkReceiver extends BroadcastReceiver
    {

        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.i("NetworkReceiver", "onReceive()");
            ConnectivityManager connMgr =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo[] networkInfos = connMgr.getAllNetworkInfo();
            for(NetworkInfo ni:networkInfos)
            {
                Log.i("NetworkReceiver", ni.getTypeName());
                //if(ni.isAvailable())
                //    availableConns.add(ni.getType() );
            }

        }
    }
}
