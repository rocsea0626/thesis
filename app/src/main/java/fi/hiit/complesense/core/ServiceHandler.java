package fi.hiit.complesense.core;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AbstractUdpConnectionRunnable;
import fi.hiit.complesense.connection.AcceptorUDP;
import fi.hiit.complesense.connection.ConnectorCloud;
import fi.hiit.complesense.connection.ConnectorUDP;
import fi.hiit.complesense.connection.UdpConnectionRunnable;
import fi.hiit.complesense.util.SensorUtil;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 20.8.2014.
 */
public class ServiceHandler extends HandlerThread
        implements Handler.Callback,AliveConnection.AliveConnectionListener, UdpConnectionRunnable.UdpConnectionListner
{
    private static final String TAG = "ServiceHandler";

    public final String startTime = Long.toString(System.currentTimeMillis());

    protected final Messenger serviceMessenger;
    private final Context context;
    private Handler handler;
    protected Map<String, AbstractSystemThread> eventHandlingThreads;
    protected Map<String, AliveConnection> peerList; // Store all the alive connections

    public final SensorUtil sensorUtil;
    public final long delay;
    private boolean isGroupOwner;
    private long rttMeasurement;

    public ServiceHandler(Messenger serviceMessenger, String name,
                          Context context, boolean isGroupOwner, InetAddress ownerAddr,
                          long delay)
    {
        super(name);
        this.serviceMessenger = serviceMessenger;
        this.context = context;
        sensorUtil = new SensorUtil(context);
        eventHandlingThreads =  new TreeMap<String, AbstractSystemThread>();
        peerList = new TreeMap<String, AliveConnection>();
        this.isGroupOwner = isGroupOwner;

        this.delay = delay;
        init(ownerAddr);
    }

    protected void init(InetAddress ownerAddr)
    {
        if(isGroupOwner)
        {
            AcceptorUDP acceptor = null;
            ConnectorCloud connectorCloud = new ConnectorCloud(this);
            try {
                acceptor = new AcceptorUDP(serviceMessenger, this);
                eventHandlingThreads.put(AcceptorUDP.TAG, acceptor);
                eventHandlingThreads.put(ConnectorCloud.TAG, connectorCloud);

            } catch (IOException e) {
                Log.e(TAG, e.toString());

            }
        }
        else
        {
            ConnectorUDP connector = null;
            try {
                connector = new ConnectorUDP(serviceMessenger, this, ownerAddr);
                eventHandlingThreads.put(ConnectorUDP.TAG, connector);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    @Override
    protected void onLooperPrepared() {
        handler = new Handler(getLooper(), this);
    }

    @Override
    public boolean handleMessage(Message msg)
    {
        if(msg.what == UdpConnectionRunnable.ID_DATAGRAM_PACKET)
        {
            DatagramPacket packet = (DatagramPacket)msg.obj;
            SocketAddress fromAddr = packet.getSocketAddress();
            Log.i(TAG,"recv UDP packet from " +  fromAddr );

            if(msg.arg1 == SystemMessage.ID_SYSTEM_MESSAGE)
            {
                // received a SystemMessage
                SystemMessage sm = SystemMessage.getFromBytes(packet.getData());
                handleSystemMessage(sm, fromAddr);
            }
        }

        return false;
    }

    protected void handleSystemMessage(SystemMessage sm, SocketAddress fromAddr)
    {
        Log.i(TAG, "recv: " + sm.toString() + " from " + fromAddr);
        //reply(msg.replyTo);
    }


    protected void reply(Messenger messenger)
    {
        Message msg = new Message();
        msg.obj = SystemMessage.makeRelayListenerReply();
        try {
            messenger.send(msg);
        } catch (RemoteException e) {
            Log.i(TAG,e.toString());
        }
    }


    public Handler getHandler(){
        if(handler==null)
            Log.i(TAG,"handler is null");
        return handler;
    }

    public void startServiveHandler()
    {
        Log.i(TAG,"startServiceHandler(delay: " + delay +")");
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }
        this.start();
        Iterator<Map.Entry<String, AbstractSystemThread>> iterator
                = eventHandlingThreads.entrySet().iterator();

        while(iterator.hasNext())
        {
            iterator.next().getValue().start();
        }
    }

    public void stopServiceHandler()
    {
        Log.i(TAG,"stopServiceHandler()");

        Iterator<Map.Entry<String, AbstractSystemThread>> iterator
                = eventHandlingThreads.entrySet().iterator();
        while(iterator.hasNext())
            iterator.next().getValue().stopThread();

        quit();
    }
    public void send2Cloud(String srcAddr, float[] values)
    {
        ConnectorCloud cloudConnector = (ConnectorCloud)eventHandlingThreads.get(ConnectorCloud.TAG);
        if(cloudConnector!=null)
        {
            if(cloudConnector.getConnectionRunnable()!=null)
            {
                String str = srcAddr + "->";
                for(float f:values)
                {
                    str += Float.toString(f);
                    str +=", ";
                }
                //cloudConnector.getConnectionRunnable().write(str, cloudConnector.getCloudSocketAddr() );
                //cloudConnector.getConnectionRunnable().write(str.getBytes());
            }
        }
    }

    protected void addNewConnection(SocketAddress socketAddress)
    {
        String str = "addNewConnection("+ socketAddress +")";
        Log.i(TAG,str);
        updateStatusTxt(str);
        AliveConnection aliveConnection = new AliveConnection(socketAddress, this);
        if(!peerList.containsKey(socketAddress.toString()))
            this.peerList.put(socketAddress.toString(), aliveConnection);
    }


    protected void removeFromPeerList(String socketAddrStr)
    {
        String str = "removeFromPeerList("+ socketAddrStr +")";
        Log.i(TAG,str);
        updateStatusTxt(str);
        peerList.remove(socketAddrStr);
    }


    protected void renewPeerList(String socketAddrStr)
    {
        String str = "renewPeerList("+ socketAddrStr +")";
        Log.i(TAG,str);
        updateStatusTxt(str);
        ((AliveConnection)(peerList.get(socketAddrStr)) ).resetCheckTime();
    }

    public void updateStatusTxt(String str)
    {
        //Log.i(TAG,"updateStatusTxt()");
        Message msg = Message.obtain();
        msg.what = Constants.SERVICE_MSG_STATUS_TXT_UPDATE;
        msg.obj = str;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onValidTimeExpires(SocketAddress socketAddress)
    {
        Log.i(TAG, "onValidTimeExpires(" + socketAddress.toString() + ")");
        removeFromPeerList(socketAddress.toString());
    }
/*
    public void forwardRttQuery(byte[] payload, SocketAddress remoteSocketAddr)
    {
        ByteBuffer bb = ByteBuffer.wrap(payload);
        long timeStamp = bb.getLong();

        ArrayDeque<String> hops = SystemMessage.parseRttQuery(payload);
        Log.i(TAG, "RTT hops: " + hops.toString());

        if(socket.getLocalSocketAddress().toString().contains("/:::"))
        {
            //must be the group owner socket
            String nextHost = SystemUtil.getHost(hops.peek());
            int nextPort = SystemUtil.getPort(hops.peek());

            Log.i(TAG,"nextSocketAddr: " + nextHost+":"+nextPort);
            write(SystemMessage.makeRttQuery(timeStamp, hops), new InetSocketAddress(nextHost, nextPort));

        }
        else
        {
            String socketAddrStr = hops.poll();
            Log.i(TAG,"poll socketAddr: " + socketAddrStr);
            if(!socketAddrStr.equals(socket.getLocalSocketAddress().toString()))
            {
                Log.e(TAG,"packet wrong host: " + socketAddrStr + "/" +
                        socket.getLocalSocketAddress().toString());
                return;
            }
            if(hops.size()==0)
            {
                //Log.i(TAG,"RTT: " + Long.toString(System.currentTimeMillis()- timeStamp));
                long rtt = System.currentTimeMillis()-timeStamp;
                Log.i(TAG,"pacekt has reached its destination: " + rtt);

                rttMeasurements.add(rtt);
                Iterator<Long> iter = rttMeasurements.iterator();
                long sum = 0;
                while(iter.hasNext())
                    sum += (Long)iter.next();

                serviceHandler.updateStatusTxt("RTT: "
                        + Float.toString((float) (sum / rttMeasurements.size())));
                return;
            }

            if(timeStamp==0)
                timeStamp = System.currentTimeMillis();
            write(SystemMessage.makeRttQuery(timeStamp, hops), remoteSocketAddr);
        }

    }
*/
    public void replyRttQuery(byte[] payload, SocketAddress remoteSocketAddr, UdpConnectionRunnable runnable,
                              UdpConnectionRunnable.UdpConnectionListner listener)
    {
        ByteBuffer bb = ByteBuffer.wrap(payload);
        long startTime = bb.getLong();
        int rounds = bb.getInt();
        if(rounds <= 0)
        {
            listener.onReceiveRttReply(startTime);
        }
        else{
            if(runnable!=null)
                runnable.write(SystemMessage.makeRttQuery(startTime, rounds--), remoteSocketAddr);
        }
    }

    @Override
    public void onReceiveRttReply(long startTimeMillis) {
        this.rttMeasurement = (System.currentTimeMillis() - startTimeMillis) / Constants.RTT_ROUNDS;
    }
}
