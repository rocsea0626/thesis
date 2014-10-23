package fi.hiit.complesense.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Serializable;
import java.net.SocketAddress;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.service.TestingService;
import fi.hiit.complesense.util.SystemUtil;


public class DemoActivity extends AbstractGroupActivity
{

    public static final String TAG = "DemoActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.v(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        /*
        * Activity specific settings
        */
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(serviceMessenger!=null)
                {
                    try
                    {
                        Message msg = Message.obtain(null,
                                TestingService.STOP_TESTING);
                        msg.replyTo = uiMessenger;
                        serviceMessenger.send(msg);
                    }
                    catch (RemoteException e)
                    {
                    }
                    finish();
                }
            }
        });
        uiMessenger = new Messenger(new IncomingHandler());
        /**
         * Class for interacting with the main interface of the service.
         */
        mConnection = new ServiceConnection()
        {
            public void onServiceConnected(ComponentName className,
                                           IBinder service)
            {
                serviceMessenger = new Messenger(service);
                Log.i(TAG, "onServiceConnected()");
                try
                {
                    Message msg = Message.obtain(null,
                            TestingService.START_TESTING);
                    msg.replyTo = uiMessenger;
                    serviceMessenger.send(msg);
                    if(hasImage)
                    {
                        msg = Message.obtain(null, Constants.SERVICE_MSG_SEND_IMG);
                        msg.replyTo = uiMessenger;
                        msg.obj = imageUri;
                        serviceMessenger.send(msg);
                    }
                }
                catch (RemoteException e)
                {
                    // In this case the service has crashed before we could even
                    // do anything with it; we can count on soon being
                    // disconnected (and then reconnected if it can be restarted)
                    // so there is no need to do anything here.
                }
            }

            public void onServiceDisconnected(ComponentName className)
            {
                // This is called when the connection with the service has been
                // unexpectedly disconnected -- that is, its process crashed.
                serviceMessenger = null;
                mIsBound = false;
                appendStatus("Disconnected from GroupClientService");

                /**
                 // As part of the sample, tell the user what happened.
                 Toast.makeText(GroupClientActivity.this, R.string.remote_service_disconnected,
                 Toast.LENGTH_SHORT).show();
                 */
            }
        };

        Intent intent = new Intent(this, TestingService.class);
        String serviceName = TestingService.class.getCanonicalName();
        if(!SystemUtil.isServiceRunning(serviceName, this))
        {
            Log.d(TAG,"service is not running");
            startService(intent);
        }
        else
        {
            Log.d(TAG,"service is running");
        }

    }

    @Override
    protected void doBindService()
    {
        Log.v(TAG, "doBindService()");
        bindService(new Intent(getApplicationContext(),
                TestingService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        //appendStatus("Binding to GroupOwnerService");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    class IncomingHandler extends Handler implements Serializable
    {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what)
            {
                case Constants.MSG_UPDATE_STATUS_TXT:
                    appendStatus((String)msg.obj);
                    break;
                case Constants.MSG_TAKE_IMAGE:
                    SocketAddress socketAddress = (SocketAddress)msg.obj;
                    appendStatus("Receive image taking request from " + socketAddress.toString() );
                    imageUri = SystemUtil.getOutputMediaFileUri(Constants.MEDIA_TYPE_IMAGE);
                    Log.i(TAG, "imageUri: " + imageUri.toString() );
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri); // set the image file name

                    Message msg2Service = Message.obtain(null, Constants.SERVICE_MSG_SEND_IMG);
                    msg2Service.replyTo = uiMessenger;
                    msg2Service.obj = null;
                    try {
                        serviceMessenger.send(msg2Service);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    //startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        Log.i(TAG, "onActivityResult(requestCode: "+ requestCode + ")");
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE)
        {
            if (resultCode == RESULT_OK) {
                // Image captured and saved to fileUri specified in the Intent
                //Toast.makeText(this, "Image saved to: " + data.getData().toString(), Toast.LENGTH_SHORT).show();
                appendStatus("Image saved to: " + imageUri.toString() );
                hasImage = true;

            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the image capture
                appendStatus("Image Capture canceled");
            } else {
                // Image capture failed, advise user
                appendStatus("Image Capture failed");
            }
        }

        if (requestCode == CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Video captured and saved to fileUri specified in the Intent
                appendStatus("Video saved to: " + data.getData().toString());
            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the video capture
            } else {
                // Video capture failed, advise user
            }
        }
    }
}
