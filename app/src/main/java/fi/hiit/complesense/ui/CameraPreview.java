package fi.hiit.complesense.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import fi.hiit.complesense.Constants;

/**
 * Created by hxguo on 26.11.2014.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback
{
    private static final String TAG = CameraPreview.class.getSimpleName();

    private File localDir;
    private SurfaceHolder mHolder;
    public Camera mCamera;

    public CameraPreview(Context context) {
        super(context);
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mCamera = openBackFacingCamera();
        if(mCamera!=null){
            mCamera.setDisplayOrientation(90);
            localDir = new File(Constants.ROOT_DIR, Constants.LOCAL_SENSOR_DATA_DIR);
            return;
        }

    }


    private Camera openBackFacingCamera() {
        int cameraCount = 0;
        Camera cam = null;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();
        for (int camIdx = 0; camIdx < cameraCount; camIdx++)
        {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                try {
                    cam = Camera.open(camIdx);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Camera failed to open: " + e.getLocalizedMessage());
                }
            }
        }

        return cam;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.e(TAG, "Error setting camera preview: " + e.toString());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }
        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
            /*
            mCamera.setPreviewCallback(new Camera.PreviewCallback() {

                public void onPreviewFrame(byte[] data, Camera camera) {
                    FileOutputStream outStream = null;
                    try {
                        String tempFile = String.format("%s/preview-%d.bmp", localDir.toString(), System.currentTimeMillis());
                        outStream = new FileOutputStream(tempFile);
                        outStream.write(data);
                        outStream.close();
                        Log.i(TAG, "onPreviewFrame - wrote bytes: " + data.length);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                    }
                    CameraPreview.this.invalidate();
                }
            });
            */
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mCamera.stopPreview();
        mCamera = null;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        Paint p= new Paint(Color.RED);
        Log.d(TAG,"draw");
        canvas.drawText("PREVIEW", canvas.getWidth()/2, canvas.getHeight()/2, p );
    }
}
