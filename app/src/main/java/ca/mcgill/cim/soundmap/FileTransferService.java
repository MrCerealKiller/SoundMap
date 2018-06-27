package ca.mcgill.cim.soundmap;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;

import java.net.HttpURLConnection;
import java.net.URL;

public class FileTransferService extends AsyncTask<Void, Integer, String> {

    private static final String TAG = "FileTransferService";

    private static final String FILE_UPLOAD_URL =
            "http://sandeepmanjanna.dlinkddns.com:5000/upload";

    private static final String LINE_END = "\r\n";
    private static final String TWO_HYPHENS = "--";
    private static final String BOUNDARY = "*****";
    private static int MAX_BUFFER_SIZE = 1 * 1024 * 1024;

    private String mSourceFile;
    private String mUser;
    private String mLocation;

    public FileTransferService(String sourceFile, String user, LatLng location) {
        mSourceFile = sourceFile;
        mUser = user;
        mLocation = location.toString();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected String doInBackground(Void... params) {
        return uploadFile();
    }

    private String uploadFile() {

        HttpURLConnection conn;
        DataOutputStream dos;
        File sourceFile = new File(mSourceFile);

        int bytesRead;
        int bytesAvailable;
        int bufferSize;
        byte[] buffer;
        
        if (!sourceFile.isFile()) {
            Log.e(TAG, "uploadFile: Source file does not exist");
            return "Source file does not exist";
        } else {

            String result = "Failure";

            try {
                FileInputStream fileInputStream = new FileInputStream(sourceFile);
                URL url = new URL(FILE_UPLOAD_URL);

                conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Content-Type","multipart/form-data;BOUNDARY=" + BOUNDARY);
                conn.setRequestProperty("uploaded_file", mSourceFile);

                conn.connect();

                dos = new DataOutputStream(conn.getOutputStream());

                dos.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);

                // Send User Name ------------------------------------------------------------------
                dos.writeBytes("Content-Disposition: form-data; name=\"user\"" + LINE_END);
                dos.writeBytes("Content-Type: text/plain; charset=UTF-8" + LINE_END);
                dos.writeBytes("Content-Length: " + mUser.length() + LINE_END);
                dos.writeBytes(LINE_END);
                dos.writeBytes(mUser);
                dos.writeBytes(LINE_END);

                dos.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);

                // Send Location -------------------------------------------------------------------
                dos.writeBytes("Content-Disposition: form-data; name=\"location\"" + LINE_END);
                dos.writeBytes("Content-Type: text/plain; charset=UTF-8" + LINE_END);
                dos.writeBytes("Content-Length: " + mLocation.length() + LINE_END);
                dos.writeBytes(LINE_END);
                dos.writeBytes(mLocation);
                dos.writeBytes(LINE_END);

                dos.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);

                // Send File -----------------------------------------------------------------------
                dos.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\"" +
                        mSourceFile + "\"" + LINE_END);
                dos.writeBytes(LINE_END);

                // Create file buffer
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, MAX_BUFFER_SIZE);
                buffer = new byte[bufferSize];

                // Read the file and transfer it
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                while (bytesRead > 0)
                {
                    dos.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, MAX_BUFFER_SIZE);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                }

                // Terminate the Request -----------------------------------------------------------
                dos.writeBytes(LINE_END);
                dos.writeBytes(TWO_HYPHENS + BOUNDARY + TWO_HYPHENS + LINE_END);

                result = conn.getResponseMessage();

                Log.i("uploadFile", "HTTP Response is : "+ result);

                // Close the pipe and flush
                fileInputStream.close();
                dos.flush();
                dos.close();

            } catch (final Exception e) {
                Log.e(TAG, "uploadFile: Error - " + e.getMessage());
            }

            return result;
        }
    }
}