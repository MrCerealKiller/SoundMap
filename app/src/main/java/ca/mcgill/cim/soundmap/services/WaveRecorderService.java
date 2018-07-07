package ca.mcgill.cim.soundmap.services;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import ca.mcgill.cim.soundmap.activities.MappingActivity;

public class WaveRecorderService extends AsyncTask<Void, Void, Void> {

    private static final String TAG = "WaveRecorderService";

    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_STEREO;
    private static final int SAMPLE_RATE = 44100;      // Hz
    private static final int MAX_FILE_SIZE = 31457280; // 30 Mb

    private static final int BUFFER_SIZE = 2 * AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AUDIO_CHANNEL, AUDIO_ENCODING);

    private MappingActivity mCalledFrom;
    private String mFilename;
    private File mFile;
    private boolean mIsStopped = false;

    private WaveRecorderService(MappingActivity calledFrom, String filename) {
        mCalledFrom = calledFrom;
        mFilename = filename;
        mFile = new File(mFilename);
    }

    @Override
    protected Void doInBackground(Void... params) {
        AudioRecord audioRecord = null;
        FileOutputStream audioStream = null;

        try {
            // Open our two resources
            audioRecord = new AudioRecord(AUDIO_SOURCE,
                                          SAMPLE_RATE,
                                          AUDIO_CHANNEL,
                                          AUDIO_ENCODING,
                                          BUFFER_SIZE);

            audioStream = new FileOutputStream(mFilename);

            // Write Header -----------------------------------------------------------------------
            writeWavHeader(audioStream);

            byte[] buffer = new byte[BUFFER_SIZE];

            int in;
            long total = 0;

            // Recording --------------------------------------------------------------------------
            boolean isRunning = true;
            audioRecord.startRecording();
            while (isRunning && !mIsStopped && !isCancelled()) {
                in = audioRecord.read(buffer, 0, buffer.length);

                // Assuming Small max file size, this is fine
                if (total + in > MAX_FILE_SIZE) {
                    audioStream.write(buffer, 0, in);
                    isRunning = false;
                } else {
                    audioStream.write(buffer, 0, in);
                    total += in;
                }
            }
        } catch (IOException e) {
            //Log.e(TAG, "doInBackground: Error -" + e.toString());
        } finally {
            // Stop Recording ---------------------------------------------------------------------
            if (audioRecord != null) {
                try {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                    }
                } catch (IllegalStateException e) {
                    //Log.e(TAG, "doInBackground: Error - " + e.toString());
                }
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.release();
                }
            }
            if (audioStream != null) {
                try {
                    audioStream.close();
                } catch (IOException e) {
                    //Log.e(TAG, "doInBackground: Error - " + e.toString());
                }
            }
        }

        // Update Header --------------------------------------------------------------------------
        try {
            updateWavHeader(mFile);
        } catch (IOException e) {
            //Log.e(TAG, "doInBackground: Error - " + e.toString());
        }

        return null;
    }

    private static void writeWavHeader(OutputStream out) throws IOException {
        short channels = 2;
        short bitDepth = 16;

        // Convert the multi-byte integers to raw bytes in little endian format
        byte[] littleBytes = ByteBuffer
                .allocate(14)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(channels)
                .putInt(SAMPLE_RATE)
                .putInt(SAMPLE_RATE * channels * (bitDepth / 8))
                .putShort((short) (channels * (bitDepth / 8)))
                .putShort(bitDepth)
                .array();

        out.write(new byte[] {
                'R', 'I', 'F', 'F',
                0, 0, 0, 0, // Updated later
                'W', 'A', 'V', 'E',
                'f', 'm', 't', ' ',
                16, 0, 0, 0,
                1, 0,
                littleBytes[0], littleBytes[1],                                 // Channels
                littleBytes[2], littleBytes[3], littleBytes[4], littleBytes[5], // SampleRate
                littleBytes[6], littleBytes[7], littleBytes[8], littleBytes[9], // ByteRate
                littleBytes[10], littleBytes[11],                               // BlockAlign
                littleBytes[12], littleBytes[13],                               // BitsPerSample
                'd', 'a', 't', 'a',
                0, 0, 0, 0, // Updated later
        });
    }

    private static void updateWavHeader(File file) throws IOException {
        byte[] sizes = ByteBuffer
                .allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt((int) (file.length() - 8))  // Bit shift to ChunkSize
                .putInt((int) (file.length() - 44)) // Bit shift to Subchunk2Size
                .array();

        RandomAccessFile accessWave = null;

        //noinspection CaughtExceptionImmediatelyRethrown
        try {
            accessWave = new RandomAccessFile(file, "rw");
            // ChunkSize
            accessWave.seek(4);
            accessWave.write(sizes, 0, 4);

            // Subchunk2Size
            accessWave.seek(40);
            accessWave.write(sizes, 4, 4);
        } catch (IOException e) {
            throw e;
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
    protected void onCancelled(Void results) {
        onPostExecute(results);
    }

    public void stop() {
        mIsStopped = true; // Breaks the doInBackground record loop
    }

    @Override
    protected void onPostExecute(Void results) {
        // If cancelled delete the file
        if (!isCancelled()) {
            try {
                if (!mFile.delete()) {
                    throw new Exception("Could not delete sample file on cancel");
                }
            } catch (Exception e) {
                //Log.e(TAG, "onPostExecute: Error - " + e.toString());
            }
        }
    }
}