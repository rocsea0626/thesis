package fi.hiit.complesense.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AsyncStreamClient;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 29.10.2014.
 */
public class AudioStreamClient extends AbsSystemThread
{
    public static final String TAG = AudioStreamClient.class.getSimpleName();

    private final AsyncStreamClient streamClient;
    private final CountDownLatch startSignal;

    private WavFileWriter wavFileWriter;
    private long threadID;
    private final short isJSON = 0;

    public AudioStreamClient(ServiceHandler serviceHandler,
                             AsyncStreamClient streamClient, CountDownLatch latch)
    {
        super(TAG, serviceHandler);
        this.streamClient = streamClient;
        this.startSignal = latch;
    }

    @Override
    public void run()
    {
        threadID = Thread.currentThread().getId();
        Log.i(TAG, "AudioStreamClient running at thread: " + threadID);
        FileChannel fileChannel = null;
        AudioRecord audio_recorder = null;
        try
        {
            startSignal.await();
            wavFileWriter= WavFileWriter.getInstance(Constants.ROOT_DIR + threadID + ".wav");
            if(wavFileWriter==null)
                throw new IOException("wavFileWrite is null");

            fileChannel = wavFileWriter.getFileChannel();
            audio_recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                    Constants.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    AudioRecord.getMinBufferSize(
                            Constants.SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT) * 10);

            int bytes_read = 0, payloadSize = 0, bytes_count = 0;

            byte[] buf = new byte[Constants.BUF_SIZE];
            ByteBuffer bb = ByteBuffer.allocate(Constants.BYTES_SHORT + 2*Constants.BYTES_INT +  Constants.BUF_SIZE);

            keepRunning = true;
            audio_recorder.startRecording();
            while(keepRunning)
            {
                bytes_read = audio_recorder.read(buf, 0, Constants.BUF_SIZE);
                //buffer = ByteBuffer.wrap(buf);
                wavFileWriter.write(buf);
                //fileChannel.write(buffer);
                bb.clear();

                payloadSize = Constants.BYTES_SHORT + Constants.BYTES_INT + bytes_read;
                bb.putInt(payloadSize);
                bb.putShort(isJSON);
                bb.putInt(SensorUtil.SENSOR_MIC);
                bb.put(buf);
                bytes_count += bytes_read;
                streamClient.send(bb.array());
                Thread.sleep(Constants.SAMPLE_INTERVAL);
            }

        } catch (IOException e) {
            Log.i(TAG, e.toString());
        } catch (InterruptedException e) {
            Log.i(TAG, e.toString());

        }
        finally
        {
            Log.i(TAG, "Recording thread stops");
            if(audio_recorder!=null)
                audio_recorder.stop();
            if(wavFileWriter!=null)
                wavFileWriter.close();
            if(fileChannel!=null)
            {
                try {
                    fileChannel.close();
                } catch (IOException e) {
                    Log.i(TAG, e.toString());
                }
            }
        }
    }

    @Override
    public void stopThread() {
        keepRunning = false;
    }
}
