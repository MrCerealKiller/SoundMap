package ca.mcgill.cim.soundmap.services;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

public class WaveRecorderService extends AsyncTask<Void, Void, Object[]> {

    private static final String TAG = "WaveRecorderService";

    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_STEREO;
    private static final int SAMPLE_RATE = 44100; // Hz
    private static final int MAX_FILE_SIZE = 31457280; // Bytes

    private static final int BUFFER_SIZE = 2 * AudioRecord.getMinBufferSize(SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_ENCODING);

    private Context mContext;
    private String mFilename;
    private File mFile;

    private WaveRecorderService(Context context, String filename) {
        mContext = context;
        mFilename = filename;
        mFile = new File(mFilename);
    }

    @Override
    protected Object[] doInBackground(Void... params) {
        AudioRecord audioRecord = null;
        FileOutputStream audioStream = null;

        long startTime = 0;
        long endTime = 0;

        try {
            // Open our two resources
            audioRecord = new AudioRecord(AUDIO_SOURCE,
                                          SAMPLE_RATE,
                                          AUDIO_CHANNEL,
                                          AUDIO_ENCODING,
                                          BUFFER_SIZE);

            audioStream = new FileOutputStream(mFilename);

            // Write out the wav file header
            writeWavHeader(audioStream);

            // Avoiding loop allocations
            byte[] buffer = new byte[BUFFER_SIZE];
            boolean run = true;
            int read;
            long total = 0;

            startTime = SystemClock.elapsedRealtime();
            audioRecord.startRecording();
            while (run && !isCancelled()) {
                read = audioRecord.read(buffer, 0, buffer.length);

                if (total + read > MAX_FILE_SIZE) {
                    for (int i = 0; i < read && total <= MAX_FILE_SIZE; i++, total++) {
                        audioStream.write(buffer[i]);
                    }
                    run = false;
                } else {
                    audioStream.write(buffer, 0, read);
                    total += read;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "doInBackground: Error -" + e.toString());
        } finally {
            if (audioRecord != null) {
                try {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                        endTime = SystemClock.elapsedRealtime();
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, "doInBackground: Error - " + e.toString());
                }
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.release();
                }
            }
            if (audioStream != null) {
                try {
                    audioStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "doInBackground: Error - " + e.toString());
                }
            }
        }

        try {
            // This is not put in the try/catch/finally above since it needs to run
            // after we close the FileOutputStream
            updateWavHeader(mFile);
        } catch (IOException ex) {
            return new Object[] { ex };
        }
    }

    private static void writeWavHeader(OutputStream out) throws IOException {
        short channels = 2;
        short bitDepth = 16;

        // Convert the multi-byte integers to raw bytes in little endian format as required by the spec
        byte[] littleBytes = ByteBuffer
                .allocate(14)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(channels)
                .putInt(SAMPLE_RATE)
                .putInt(SAMPLE_RATE * channels * (bitDepth / 8))
                .putShort((short) (channels * (bitDepth / 8)))
                .putShort(bitDepth)
                .array();

        out.write(new byte[]{
                // RIFF header
                'R', 'I', 'F', 'F', // ChunkID
                0, 0, 0, 0, // ChunkSize (must be updated later)
                'W', 'A', 'V', 'E', // Format
                // fmt subchunk
                'f', 'm', 't', ' ', // Subchunk1ID
                16, 0, 0, 0, // Subchunk1Size
                1, 0, // AudioFormat
                littleBytes[0], littleBytes[1], // NumChannels
                littleBytes[2], littleBytes[3], littleBytes[4], littleBytes[5], // SampleRate
                littleBytes[6], littleBytes[7], littleBytes[8], littleBytes[9], // ByteRate
                littleBytes[10], littleBytes[11], // BlockAlign
                littleBytes[12], littleBytes[13], // BitsPerSample
                // data subchunk
                'd', 'a', 't', 'a', // Subchunk2ID
                0, 0, 0, 0, // Subchunk2Size (must be updated later)
        });
    }

    private static void updateWavHeader(File wav) throws IOException {
        byte[] sizes = ByteBuffer
                .allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt((int) (wav.length() - 8)) // ChunkSize
                .putInt((int) (wav.length() - 44)) // Subchunk2Size
                .array();

        RandomAccessFile accessWave = null;

        //noinspection CaughtExceptionImmediatelyRethrown
        try {
            accessWave = new RandomAccessFile(wav, "rw");
            // ChunkSize
            accessWave.seek(4);
            accessWave.write(sizes, 0, 4);

            // Subchunk2Size
            accessWave.seek(40);
            accessWave.write(sizes, 4, 4);
        } catch (IOException ex) {
            // Rethrow but we still close accessWave in our finally
            throw ex;
        } finally {
            if (accessWave != null) {
                try {
                    accessWave.close();
                } catch (IOException ex) {
                    //
                }
            }
        }
    }

    @Override
    protected void onCancelled(Object[] results) {
        // Handling cancellations and successful runs in the same way
        mIsCancelled = true;
        onPostExecute(results);
    }

    @Override
    protected void onPostExecute(Object[] results) {
        if (mContext != null) {
            if (!(results[0] instanceof Throwable)) {
                double size = (long) results[0] / 1000000.00;
                long time = (long) results[1] / 1000;
                Toast.makeText(mContext, String.format(Locale.getDefault(), "%.2f MB / %d seconds",
                        size, time), Toast.LENGTH_LONG).show();
            } else {
                // Error
                Toast.makeText(ctx, throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
}