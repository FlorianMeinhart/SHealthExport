package com.example.shealthexport;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult;
import com.samsung.android.sdk.healthdata.HealthConstants;
import com.samsung.android.sdk.healthdata.HealthData;
import com.samsung.android.sdk.healthdata.HealthDataResolver;
import com.samsung.android.sdk.healthdata.HealthDataStore;
import com.samsung.android.sdk.healthdata.HealthDataUtil;
import com.samsung.android.sdk.healthdata.HealthPermissionManager;
import com.samsung.android.sdk.healthdata.HealthResultHolder;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    private FloatingActionButton btnLoadData;
    private FloatingActionButton btnSendData;
    private TextView tvSHealthInfo;

    private static final String APP_TAG = MainActivity.class.getSimpleName();
    private static MainActivity mInstance = null;
    private HealthDataStore mStore;
    private HealthConnectionErrorResult mConnError;
    private Set<HealthPermissionManager.PermissionKey> mKeySet;

    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:SSS");
    private StringBuilder sHealthData;

    private int nLastDays = 100;

    private static final long ONE_DAY_IN_MILLIS = 24 * 60 * 60 * 1000L;

    private static final String sHealthDataFile = "SHealthData.csv";

    private class StepCountData {

        int count = 0;
        String start_time = "";
        String end_time = "";

        public StepCountData(int count,
                             String start_time,
                             String end_time) {

            this.count = count;
            this.start_time = start_time;
            this.end_time = end_time;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getStart_time() {
            return start_time;
        }

        public void setStart_time(String start_time) {
            this.start_time = start_time;
        }

        public String getEnd_time() {
            return end_time;
        }

        public void setEnd_time(String end_time) {
            this.end_time = end_time;
        }
    }

    private class HeartRateData {

        float heart_rate = 0.f;
        float heart_rate_min = 0.f;
        float heart_rate_max = 0.f;
        String start_time = "";
        String end_time = "";

        public HeartRateData(float heart_rate,
                             float heart_rate_min,
                             float heart_rate_max,
                             String start_time,
                             String end_time) {

            this.heart_rate = heart_rate;
            this.heart_rate_min = heart_rate_min;
            this.heart_rate_max = heart_rate_max;
            this.start_time = start_time;
            this.end_time = end_time;
        }

        public float getHeart_rate() {
            return heart_rate;
        }

        public void setHeart_rate(float heart_rate) {
            this.heart_rate = heart_rate;
        }

        public float getHeart_rate_min() {
            return heart_rate_min;
        }

        public void setHeart_rate_min(float heart_rate_min) {
            this.heart_rate_min = heart_rate_min;
        }

        public float getHeart_rate_max() {
            return heart_rate_max;
        }

        public void setHeart_rate_max(float heart_rate_max) {
            this.heart_rate_max = heart_rate_max;
        }

        public String getStart_time() {
            return start_time;
        }

        public void setStart_time(String start_time) {
            this.start_time = start_time;
        }

        public String getEnd_time() {
            return end_time;
        }

        public void setEnd_time(String end_time) {
            this.end_time = end_time;
        }
    }

    ArrayList<HeartRateData> listHeartRateData;
    ArrayList<StepCountData> listStepCountData;

    // Predefined binning heart rate data structure by Samsung
    private class HeartRateBinningData {
        float heart_rate = 0.f;
        float heart_rate_min = 0.f;
        float heart_rate_max = 0.f;
        long start_time = 0L;
        long end_time = 0L;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mInstance = this;
        mKeySet = new HashSet<>();
        mKeySet.add(new HealthPermissionManager.PermissionKey(
                HealthConstants.StepCount.HEALTH_DATA_TYPE,
                HealthPermissionManager.PermissionType.READ));
        mKeySet.add(new HealthPermissionManager.PermissionKey(
                HealthConstants.HeartRate.HEALTH_DATA_TYPE,
                HealthPermissionManager.PermissionType.READ));
        // Create a HealthDataStore instance and set its listener
        mStore = new HealthDataStore(this, mConnectionListener);
        // Request the connection to the health data store
        mStore.connectService();


        tvSHealthInfo = findViewById(R.id.tv_info);

        btnSendData = findViewById(R.id.btn_load);
        btnSendData.setEnabled(false);
        btnSendData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listHeartRateData = new ArrayList<HeartRateData>();
                listStepCountData = new ArrayList<StepCountData>();
                checkPermissionAndReadData();
            }
        });

        btnLoadData = findViewById(R.id.btn_send);
        btnLoadData.setEnabled(false);
        btnLoadData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sHealthData = new StringBuilder();
                createFileAndSendData();
            }
        });

    }

    @Override
    public void onDestroy() {
        mStore.disconnectService();
        super.onDestroy();
    }


    private final HealthDataStore.ConnectionListener mConnectionListener = new HealthDataStore.ConnectionListener() {

        @Override
        public void onConnected() {
            Log.d(APP_TAG, "SHealth data service is connected.");
            tvSHealthInfo.setText("SHealth data service is connected.");

            // enable buttons if connected
            btnLoadData.setEnabled(true);
            btnSendData.setEnabled(true);
        }

        @Override
        public void onConnectionFailed(HealthConnectionErrorResult error) {
            Log.d(APP_TAG, "SHealth data service is not available.");
            tvSHealthInfo.setText("SHealth data service is not available.");
            showConnectionFailureDialog(error);
        }

        @Override
        public void onDisconnected() {
            Log.d(APP_TAG, "SHealth data service is disconnected.");
            tvSHealthInfo.setText("SHealth data service is disconnected.");
        }
    };


    private void showConnectionFailureDialog(HealthConnectionErrorResult error) {

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        mConnError = error;
        String message = "Connection with Samsung Health is not available";

        if (mConnError.hasResolution()) {
            switch(error.getErrorCode()) {
                case HealthConnectionErrorResult.PLATFORM_NOT_INSTALLED:
                    message = "Please install Samsung Health";
                    break;
                case HealthConnectionErrorResult.OLD_VERSION_PLATFORM:
                    message = "Please upgrade Samsung Health";
                    break;
                case HealthConnectionErrorResult.PLATFORM_DISABLED:
                    message = "Please enable Samsung Health";
                    break;
                case HealthConnectionErrorResult.USER_AGREEMENT_NEEDED:
                    message = "Please agree with Samsung Health policy";
                    break;
                default:
                    message = "Please make Samsung Health available";
                    break;
            }
        }

        alert.setMessage(message);

        alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                if (mConnError.hasResolution()) {
                    mConnError.resolve(mInstance);
                }
            }
        });

        if (error.hasResolution()) {
            alert.setNegativeButton("Cancel", null);
        }

        alert.show();
    }


    private void checkPermissionAndReadData() {
        HealthPermissionManager pmsManager = new HealthPermissionManager(mStore);

        try {
            // Check whether the permissions that this application needs are acquired
            Map<HealthPermissionManager.PermissionKey, Boolean> resultMap = pmsManager.isPermissionAcquired(mKeySet);

            if (resultMap.containsValue(Boolean.FALSE)) {
                // Request the permission for reading step counts if it is not acquired
                pmsManager.requestPermissions(mKeySet, MainActivity.this).setResultListener(mPermissionListener);
            } else {
                readStepCountAndHeartRate();
            }
        } catch (Exception e) {
            Log.e(APP_TAG, e.getClass().getName() + " - " + e.getMessage());
            Log.e(APP_TAG, "Permission setting fails.");
            tvSHealthInfo.setText("Permission setting fails.");
        }
    }


    private final HealthResultHolder.ResultListener<HealthPermissionManager.PermissionResult> mPermissionListener =
            new HealthResultHolder.ResultListener<HealthPermissionManager.PermissionResult>() {

                @Override
                public void onResult(HealthPermissionManager.PermissionResult result) {
                    Log.d(APP_TAG, "Permission callback is received.");
                    Map<HealthPermissionManager.PermissionKey, Boolean> resultMap = result.getResultMap();

                    if (resultMap.containsValue(Boolean.FALSE)) {
                        Log.i(APP_TAG, "Permission Request failed");
                    } else {
                        readStepCountAndHeartRate();
                    }
                }
            };


    private void readStepCountAndHeartRate() {
        tvSHealthInfo.setText("Loading data...");

        HealthDataResolver resolver = new HealthDataResolver(mStore, null);

        // Set time range from start time of today to the current time
        long startTime = getStartTimeOfToday();
        long endTime = startTime + ONE_DAY_IN_MILLIS;

        startTime -= ONE_DAY_IN_MILLIS * nLastDays;

        // Build request for step count
        HealthDataResolver.ReadRequest requestStepCount = new HealthDataResolver.ReadRequest.Builder()
                .setDataType(HealthConstants.StepCount.HEALTH_DATA_TYPE)
                .setProperties(new String[] {
                        HealthConstants.StepCount.COUNT,
                        HealthConstants.StepCount.START_TIME,
                        HealthConstants.StepCount.END_TIME
                })
                .setLocalTimeRange(
                        HealthConstants.StepCount.START_TIME,
                        HealthConstants.StepCount.TIME_OFFSET,
                        startTime,
                        endTime)
                .build();

        try {
            resolver.read(requestStepCount).setResultListener(mListenerSteps);
        } catch (Exception e) {
            Log.e(MainActivity.APP_TAG, "Getting step count fails.", e);
            tvSHealthInfo.setText("Getting step count fails.");
        }

        // Build request for heart rate
        HealthDataResolver.ReadRequest requestHeartRate = new HealthDataResolver.ReadRequest.Builder()
                .setDataType(HealthConstants.HeartRate.HEALTH_DATA_TYPE)
                .setProperties(new String[] {
                        HealthConstants.HeartRate.HEART_BEAT_COUNT,
                        HealthConstants.HeartRate.HEART_RATE,
                        HealthConstants.HeartRate.BINNING_DATA,
                        HealthConstants.HeartRate.START_TIME,
                        HealthConstants.HeartRate.END_TIME
                })
                .setLocalTimeRange(
                        HealthConstants.HeartRate.START_TIME,
                        HealthConstants.HeartRate.TIME_OFFSET,
                        startTime,
                        endTime)
                .build();

        try {
            resolver.read(requestHeartRate).setResultListener(mListenerHeartRate);
        } catch (Exception e) {
            Log.e(MainActivity.APP_TAG, "Getting heart rate fails.", e);
            tvSHealthInfo.setText("Getting heart rate fails.");
        }
    }


    private final HealthResultHolder.ResultListener<HealthDataResolver.ReadResult> mListenerSteps =
            new HealthResultHolder.ResultListener<HealthDataResolver.ReadResult>() {
                @Override
                public void onResult(HealthDataResolver.ReadResult result){
                    int ii = 0;
                    int count = 0;
                    String startTime;
                    String endTime;

                    try {
                        for (HealthData data : result) {
                            count = data.getInt(HealthConstants.StepCount.COUNT);
                            startTime = data.getString(HealthConstants.StepCount.START_TIME);
                            endTime = data.getString(HealthConstants.StepCount.END_TIME);
                            Log.i(APP_TAG, "Count: " + count
                                    + "\t\tStart time: " + ms2str(startTime)
                                    + "\t\tEnd time: " + ms2str(endTime));
                            listStepCountData.add(new StepCountData(count,
                                                                    ms2str(startTime),
                                                                    ms2str(endTime)));
                            ii++;
                        }
                    } finally {

                        Log.i(APP_TAG, "Number of loaded data points (steps): " + ii);
                        tvSHealthInfo.setText(tvSHealthInfo.getText() + "\nStep count data loaded (" + ii + ")");
                        result.close();
                    }
                }
            };


    private final HealthResultHolder.ResultListener<HealthDataResolver.ReadResult> mListenerHeartRate =
            new HealthResultHolder.ResultListener<HealthDataResolver.ReadResult>() {
                @Override
                public void onResult(HealthDataResolver.ReadResult result){
                    int ii = 0;
                    int hbc = 0;
                    long hr = 0;
                    byte[] binningHR;
                    String startTime;
                    String endTime;

                    try {
                        Log.w(APP_TAG, "Heart rate INFO: " + result.getCount());
                        for (HealthData data : result) {
                            hbc = data.getInt(HealthConstants.HeartRate.HEART_BEAT_COUNT);
                            hr = data.getLong(HealthConstants.HeartRate.HEART_RATE);
                            binningHR = data.getBlob(HealthConstants.HeartRate.BINNING_DATA);
                            startTime = data.getString(HealthConstants.StepCount.START_TIME);
                            endTime = data.getString(HealthConstants.StepCount.END_TIME);


                            Log.i(APP_TAG,"Heart rate: " + hr
                                    + "\t\tHeart Beat Count: " + hbc
                                    + "\t\tStart time: " + ms2str(startTime)
                                    + "\t\tEnd time: " + ms2str(endTime));


                            if (binningHR != null) {
                                //Log.i(APP_TAG, new String(binningHR));

                                List<HeartRateBinningData> hrBinningDataList = HealthDataUtil.getStructuredDataList(binningHR, HeartRateBinningData.class);
                                Log.i(APP_TAG, "liveDataList size: " + hrBinningDataList.size());

                                for (HeartRateBinningData hrBinningData : hrBinningDataList) {
                                    Log.i(" ", "-->\tHR: " + hrBinningData.heart_rate
                                            + "\t\tHR min: " + hrBinningData.heart_rate_min
                                            + "\t\tHR max: " + hrBinningData.heart_rate_max
                                            + "\t\tStart time: " + ms2str(hrBinningData.start_time)
                                            + "\t\tEnd time: " + ms2str(hrBinningData.end_time));

                                    listHeartRateData.add(new HeartRateData(
                                            hrBinningData.heart_rate,
                                            hrBinningData.heart_rate_min,
                                            hrBinningData.heart_rate_max,
                                            ms2str(hrBinningData.start_time),
                                            ms2str(hrBinningData.end_time)));
                                    ii++;
                                }
                            }
                        }
                    } finally {
                        Log.i(APP_TAG, "Number of loaded data points (heart rate): " + ii);
                        tvSHealthInfo.setText(tvSHealthInfo.getText() + "\nHeart rate data loaded (" + ii + ")");
                        result.close();
                    }
                }
            };



    private void createFileAndSendData() {
        class GetDataForExport extends AsyncTask<Void, Void, StringBuilder> {

            @Override
            protected StringBuilder doInBackground(Void... voids) {

                StringBuilder data = new StringBuilder();

                //generate step count data --> stringBuilder
                data.append("count,start_time,end_time");
                for (StepCountData stepData : listStepCountData) {
                    data.append("\n" + stepData.getCount() + ","
                            + stepData.getStart_time() + ","
                            + stepData.getEnd_time());
                }

                //generate step count data --> stringBuilder
                data.append("\n\nheart_rate,heart_rate_min,heart_rate_max,start_time,end_time");
                for (HeartRateData hrData : listHeartRateData) {
                    data.append("\n" + hrData.getHeart_rate() + ","
                            + hrData.getHeart_rate_min() + ","
                            + hrData.getHeart_rate_max() + ","
                            + hrData.getStart_time() + ","
                            + hrData.getEnd_time());
                }

                return data;
            }

            @Override
            protected void onPostExecute(StringBuilder data) {
                super.onPostExecute(data);

                sHealthData = data;

                // Export data
                try{
                    //saving the file into device
                    FileOutputStream out = openFileOutput(sHealthDataFile, Context.MODE_PRIVATE);
                    out.write((sHealthData.toString()).getBytes());
                    out.close();

                    Toast.makeText(getApplicationContext(), sHealthDataFile + " created", Toast.LENGTH_LONG).show();

                    //exporting
                    Context context = getApplicationContext();
                    File filelocation = new File(getFilesDir(), sHealthDataFile);
                    Uri path = FileProvider.getUriForFile(context, "com.example.shealthexport.fileprovider", filelocation);
                    Intent fileIntent = new Intent(Intent.ACTION_SEND);
                    fileIntent.setType("text/csv");
                    fileIntent.putExtra(Intent.EXTRA_SUBJECT, sHealthDataFile);
                    fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    fileIntent.putExtra(Intent.EXTRA_STREAM, path);

                    startActivity(Intent.createChooser(fileIntent, "Send " + sHealthDataFile));
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }
        }

        GetDataForExport gd = new GetDataForExport();
        gd.execute();
    }


    private long getStartTimeOfToday() {
        Calendar today = Calendar.getInstance(TimeZone.getDefault());

        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        return today.getTimeInMillis();
    }


    private String ms2str(long timeInMillis) {
        return(dateFormatter.format(new Date(timeInMillis)));
    }

    private String ms2str(String timeInMillis) {
        return(dateFormatter.format(new Date(Long.parseLong(timeInMillis))));
    }
}
