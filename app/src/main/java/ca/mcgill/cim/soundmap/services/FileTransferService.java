package ca.mcgill.cim.soundmap.services;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.io.IOException;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FileTransferService extends AsyncTask<Void, Integer, String> {

    private static final String TAG = "FileTransferService";

    private static final String FILE_UPLOAD_URL =
            "http://sandeepmanjanna.dlinkddns.com:5000/upload";

    private String mSampleFile;
    private String mUser;
    private String mLocation;

    public FileTransferService(String sampleFile, String user, LatLng location) {
        //Log.d(TAG, "FileTransferService: Starting the file transfer service");
        
        mSampleFile = sampleFile;
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

        // Get the  Sample as a java File
        File sample = new File(mSampleFile);

        // Set up the OkHttp Client
        OkHttpClient client = new OkHttpClient();
        Request.Builder reqBuilder = new Request.Builder();
        reqBuilder.url(FILE_UPLOAD_URL);

        // Request Body
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder();
        bodyBuilder.setType(MultipartBody.FORM);
        bodyBuilder.addFormDataPart("username", mUser);
        bodyBuilder.addFormDataPart("location", mLocation);
        bodyBuilder.addFormDataPart("audio", sample.getName(), RequestBody.create(null, sample));

        // Create the Request
        MultipartBody body = bodyBuilder.build();
        Request request = reqBuilder.post(body).build();

        // Post the Request using the OkHttp Client
        try {
            //Log.d(TAG, "uploadFile: Attempting to post the request to the server");
            Response response = client.newCall(request).execute();
            return response.body().string();
        } catch (IOException e) {
            //Log.e(TAG, "uploadFile: Error - " + e.getMessage());
            return "IO Error";
        }
    }
}