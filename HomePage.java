package com.project.jean.project;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.provider.ContactsContract;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;

// IBM Watson SDK
import com.project.jean.speech_to_text.dto.SpeechConfiguration;
import com.project.jean.speech_to_text.ISpeechDelegate;
import com.project.jean.speech_to_text.SpeechToText;
import com.project.jean.text_to_speech.TextToSpeech;

/**
 *
 */
public class HomePage extends Activity implements ISpeechDelegate{

    private static final String TAG = "MainActivity";
    private static final String PREFS = "VoiceAppPrefs";
    private final int SETTINGS_REQ_CODE = 1;
    private final int REQUEST_ENABLE_BT = 10;

    static Queue<String> synthesizer_queue = new LinkedList<>();
    static Queue<String> unread_mail_queue = new LinkedList<>();

    static MailHandler mailSender = new MailHandler("jeanvdberg1994@gmail.com", "sometimes5567");

    int maximum_unread_emails = 2;

    private enum ApplicationState {
        IDLE, SST_CONVERSION, TTS_CONVERSION
    }

    ApplicationState aState = ApplicationState.IDLE;

    private static boolean read_emails = false;

    private static String recognition_results = "";

    private enum ConnectionState {
        IDLE, CONNECTING, CONNECTED
    }

    ConnectionState mState = ConnectionState.IDLE;

    private static Handler mHandler = null;

    private Context mContext = null;

    private boolean use_custom_playback_device = false;
    private boolean use_stt_api = false;
    //note: can not use stt api and custom device together.
    //if use_stt_api is true, use_custom_playback_device is not considered

    private Dictionary dictionary;
    private AudioRecord recorder = null;
    private static boolean recording = false;
    private static int recording_index = 0;
    private static String recording_word = "you";
    private static String stt_total_result = "";
    private static boolean save_recordings = false;

    int uart_counter = 0;
    int uart_counter2 = 0;

    private BluetoothAdapter mBluetoothAdapter;

//    private BluetoothHeadset mBluetoothHeadset;
//    private BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
//        public void onServiceConnected(int profile, BluetoothProfile proxy) {
//            if (profile == BluetoothProfile.HEADSET) {
//                mBluetoothHeadset = (BluetoothHeadset) proxy;
//            }
//        }
//        public void onServiceDisconnected(int profile) {
//            if (profile == BluetoothProfile.HEADSET) {
//                mBluetoothHeadset = null;
//            }
//        }
//    };

    String bluetooth_dev_name = "";
    String bluetooth_dev_mac = "";

    public static final int MESSAGE_READ = 2;

    private boolean bluetooth_enabled = false;
    private static boolean bluetooth_active_connection = false;

    private ConnectedThread mConnectedThread;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;

    private final UUID default_uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //apparently this UUID must be used for SPP
    //private final UUID default_uuid = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    int test_recording_index = 0;
    String base_file_name = "mic_array_recording";
    boolean recording_data = true;
    File pcm_file = null;
    BufferedWriter pcm_buffered_writer = null;
    double int_to_double_scaler = 0.000030517578125;
    double[] stt_buffer = new double[8192];
    int stt_buffer_index = 0;
    /**
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_page);
        mContext = getApplicationContext();

        // Strictmode needed to run the http/wss request for devices > Gingerbread
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.GINGERBREAD) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        SharedPreferences settings = getSharedPreferences(PREFS, 0);
        maximum_unread_emails = settings.getInt("unread_email_amount", 2);
        use_custom_playback_device = settings.getBoolean("use_custom_playback_device", false);
        use_stt_api = settings.getBoolean("use_stt_api", false);

        if(use_custom_playback_device) {
            mHandler = new Handler() {
                @Override
                public void handleMessage(android.os.Message msg) {
                    byte[] encodedMessage = (byte[]) msg.obj;
                    String decodedMessage = new String(encodedMessage, 0, msg.arg1);
                    //Log.d(TAG, "Receiving Message - mHandler - " + decodedMessage);
                    if (pcm_file == null) {
                        try {
                            pcm_file = new File(getExternalFilesDir(null), base_file_name + test_recording_index + ".txt");
                            pcm_buffered_writer = new BufferedWriter(new FileWriter(pcm_file));
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e(TAG, "IO exception when creating new file or opening file stream.");
                        }
                    }
                    try {
//                    for (int i = 0; i < encodedMessage.length; i++) {
//                        //Log.d(TAG, "Arr at " + i + " = " + buffer[i]);
//                        pcm_buffered_writer.write(encodedMessage[i] + "\n"); //convert byte to a signed int here
//                    }
                        for (byte temp : encodedMessage) //convert byte to a signed int here
                        {
                            pcm_buffered_writer.write(temp + "\n");
                            stt_buffer[stt_buffer_index] = temp * int_to_double_scaler;
                            stt_buffer_index++;
                        }
                        if (!recording_data) //todo check timing issues with this, might need to make buffer larger to avoid timing issues
                        {
                            pcm_buffered_writer.close();
                            test_recording_index++;
                            Intent intent = //update the directory of the phone
                                    new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            intent.setData(Uri.fromFile(pcm_file));
                            sendBroadcast(intent);

                            //// TODO: 2016/10/18 check if this works

                            final String stt_result = dictionary.recognize(stt_buffer, stt_buffer_index);
                            Log.d(TAG, "Audio recording identified as: " + stt_result);
                            //stt_total_result += Character.toUpperCase(stt_result.charAt(0)) + stt_result.substring(1) + " "; //capitalize first letter
                            stt_total_result += stt_result + " ";

                            final Runnable runnable_ui = new Runnable() {
                                @Override
                                public void run() {
                                    TextView textDisplay = (TextView) findViewById(R.id.textDisplay);
                                    textDisplay.setText(stt_total_result);
                                }
                            };

                            new Thread() {
                                public void run() {
                                    mHandler.post(runnable_ui);
                                }
                            }.start();

                            pcm_file = null;
                            stt_buffer_index = 0;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "IO exception when trying to write pcm data to a file.");
                    }
                    super.handleMessage(msg);
                }
            };

            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter != null) {
                // Device does not support Bluetooth
                //lol who has a phone that doesn't support bluetooth in 2016

                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    bluetooth_enabled = true;
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... none) {
                            startBluetoothClient();
                            return null;
                        }
                    }.execute();

                    ////if you want to use BluetoothHeadset:
                    // Establish connection to the proxy.
                    //mBluetoothAdapter.getProfileProxy(mContext, mProfileListener, BluetoothProfile.HEADSET);

                    // ... call functions on mBluetoothHeadset

                    // Close proxy connection after use.
                    //mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
                }
            }

            //bluetooth send and receive test:
//            Log.d(TAG, "1");
//            int hi = 0;
//            while (!bluetooth_active_connection) {
//                if (hi == 0) {
//                    Log.d(TAG, ".");
//                    hi = 1;
//                }
//            }
//            Log.d(TAG, "2");
//            String temp = "Test bluetooth";
//            byte[] bytes = new byte[0];
//            try {
//                bytes = temp.getBytes("UTF-8");
//            } catch (UnsupportedEncodingException e) {
//                Log.e(TAG, "UnsupportedEncodingException...");
//                e.printStackTrace();
//            }
//            Log.d(TAG, "Sending bytes");
//            BluetoothWrite(bytes);
        }
        else
        {
            mHandler = new Handler();
        }

//        new Thread(){
//            public void run(){
//                try{
//                    Message[] emails = mailSender.getUnreadMail();
//                    int unread_count = emails.length;
//
//                    int maximum = (unread_count > maximum_unread_emails) ? maximum_unread_emails : unread_count;
//                    int minimum = (emails.length > maximum_unread_emails) ? (unread_count - maximum_unread_emails) : 0;
//
//                    String unread_email_msg = "You have " + unread_count + " unread emails. ";
//                    unread_email_msg += "Would you like the latest " + maximum + " emails to be read out loud?";
//
//                    synthesizer_queue.add(unread_email_msg);
//
//                    Intent intent_email_init = new Intent("broadcast_email_init");
//                    sendBroadcast(intent_email_init);
//
//                    for (int i = unread_count - 1; i >= minimum; i--) {
//                        String message = getMailMessage(emails[i]);
//                        unread_mail_queue.add(message);
//                    }
//
//                    mailSender.setListener(new MailListener() {
//                        @Override
//                        public void callback(Message[] emails, int length) {
//                            for (int i = 0; i < length; i++) {
//                                String message = getMailMessage(emails[i]);
//                                synthesizer_queue.add(message);
//                                Log.d(TAG, "Adding email to queue");
//                            }
//                            Log.d(TAG, "Sending intent");
//                            Intent intent_trigger_tts = new Intent("broadcast_trigger_tts");
//                            sendBroadcast(intent_trigger_tts);
//                        }
//                    });
//                    mailSender.getIncomingMail();
//                }
//                catch(MessagingException e)
//                {
//                    Log.e(TAG, "Messaging Exception when fetching unread mail.");
//                    e.printStackTrace();
//                }
//            }
//        }.start();

        Log.d(TAG, "Initializing TTS");

        if (!initializeTTS()) {
            TextView textbox = (TextView) findViewById(R.id.textDisplay);
            textbox.setText(R.string.authenticationErrorTTS);
        } else {
            Log.d(TAG, "TTS API initialized");
        }

        Log.d(TAG, "Initializing STT");
        if(use_stt_api) {
            if (!initializeSTT()) {
                TextView textbox = (TextView) findViewById(R.id.textDisplay);
                textbox.setText(R.string.authenticationErrorSTT);
            }
            else
            {
                Log.d(TAG, "STT API initialized");
            }
        }
        else
        {
            dictionary = new Dictionary(getApplicationContext());
            Log.d(TAG, "STT Custom initialized");
//            double[] buffer = new double[2695];
//            Scanner scanner = new Scanner(getResources().openRawResource(R.raw.apple01));
//            scanner.useDelimiter(",|\\n");
//            scanner.useLocale(Locale.ENGLISH); //try this if having weird problem
//            int i = 0;
//            while(scanner.hasNextDouble())
//            {
//                buffer[i] = scanner.nextDouble();
//                i++;
//            }
//            Log.d(TAG, "Start recognition test");
//            String result = dictionary.recognize(buffer, i);
//            Log.d(TAG, "Audio signal recognized as " + result);
//
//            double[] buffer1 = new double[9000];
//            Scanner scanner1 = new Scanner(getResources().openRawResource(R.raw.text5));
//            scanner1.useDelimiter(",|\\n");
//            scanner1.useLocale(Locale.ENGLISH); //try this if having weird problem
//            int i1 = 0;
//            while(scanner1.hasNextDouble())
//            {
//                buffer1[i1] = scanner1.nextDouble();
//                i1++;
//            }
//            Log.d(TAG, "Start recognition test");
//            String result1 = dictionary.recognize(buffer1, i1);
//            Log.d(TAG, "Audio signal recognized as " + result1);
//
//            double[] buffer2 = new double[9000];
//            Scanner scanner2 = new Scanner(getResources().openRawResource(R.raw.this5));
//            scanner2.useDelimiter(",|\\n");
//            scanner2.useLocale(Locale.ENGLISH); //try this if having weird problem
//            int i2 = 0;
//            while(scanner2.hasNextDouble())
//            {
//                buffer2[i2] = scanner2.nextDouble();
//                i2++;
//            }
//            Log.d(TAG, "Start recognition test");
//            String result2 = dictionary.recognize(buffer2, i2);
//            Log.d(TAG, "Audio signal recognized as " + result2);

            double[] buffer3 = new double[7000];
            Scanner scanner3 = new Scanner(getResources().openRawResource(R.raw.you5));
            scanner3.useDelimiter(",|\\n");
            scanner3.useLocale(Locale.ENGLISH); //try this if having weird problem
            int i3 = 0;
            while(scanner3.hasNextDouble())
            {
                buffer3[i3] = scanner3.nextDouble();
                i3++;
            }
            Log.d(TAG, "Start recognition test");
            String result3 = dictionary.recognize(buffer3, i3);
            Log.d(TAG, "Audio signal recognized as " + result3);
        }

        IntentFilter filter_received_sms = new IntentFilter("broadcast_sms");
        registerReceiver(broadcastReceiverReceivedSms, filter_received_sms);

        IntentFilter filter_end_of_synthesis = new IntentFilter("broadcast_end_of_synthesis");
        registerReceiver(broadcastReceiverEndOfSynthesis, filter_end_of_synthesis);

        IntentFilter filter_email_init = new IntentFilter("broadcast_email_init");
        registerReceiver(broadcastReceiverEmailInitialized, filter_email_init);

        IntentFilter filter_trigger_tts = new IntentFilter("broadcast_trigger_tts");
        registerReceiver(broadcastReceiverTriggerTTS, filter_trigger_tts);

//        Button buttonRecord = (Button) findViewById(R.id.buttonRecord);
//        buttonRecord.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View arg0) {
//                if(use_stt_api) {
//                    if (mState == ConnectionState.IDLE) {
//                        mState = ConnectionState.CONNECTING;
//                        aState = ApplicationState.SST_CONVERSION;
//                        Log.d(TAG, "onClickRecord: IDLE -> CONNECTING");
//                        recognition_results = "";
//                        displayResult(recognition_results, false);
//                        // start recognition
//                        new AsyncTask<Void, Void, Void>() {
//                            @Override
//                            protected Void doInBackground(Void... none) {
//                                SpeechToText.sharedInstance().recognize(); //uses OnMessage() function to display results
//                                return null;
//                            }
//                        }.execute();
//                        setButtonLabel(R.id.buttonRecord, "Connecting...");
//                        setButtonState(true);
//                    } else if (mState == ConnectionState.CONNECTED) {
//                        mState = ConnectionState.IDLE;
//                        Log.d(TAG, "onClickRecord: CONNECTED -> IDLE");
//                        SpeechToText.sharedInstance().stopRecognition(); //uses OnMessage() function to display results
//                        setButtonState(false);
//                    }
//                }
//                else
//                {
//                    if(!recording) {
//                        recording = true;
//                        setButtonLabel(R.id.buttonRecord, "Stop Recording");
//                        Log.d(TAG, "Recording started");
//                        if(use_custom_playback_device)
//                        {
//                            recording_data = true;
//                            byte[] bytes = {0x72}; //lowercase r for 'record'
//                            BluetoothWrite(bytes);
//                            //todo implement shiz
//                        }
//                        else {
//                            recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, 8000,
//                                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, 8000);
//                            new AsyncTask<Void, Void, Void>() {
//                                @Override
//                                protected Void doInBackground(Void... none) {
//
//                                    recorder.startRecording();
//                                    float[] f_buffer = new float[1000];
//                                    double[] buffer = new double[24000];
//                                    int buffer_size = 0;
//                                    double max = 0;
//                                    boolean data_started = false;
//                                    do {
//                                        int data_read = recorder.read(f_buffer, 0, f_buffer.length, AudioRecord.READ_NON_BLOCKING);
//                                        if (data_read > 0) {
//                                            //Log.d("Dictionary", "Sample float = " + f_buffer[0]);
//                                            for (int i = 0; i < data_read; i++) {
//                                                //if (f_buffer[i] > 0.0004 || f_buffer[i] < -0.0004 || data_started) {
//                                                buffer[buffer_size] = (double) f_buffer[i];
//                                                if (buffer[buffer_size] > max)
//                                                    max = buffer[buffer_size];
//                                                buffer_size++;
//                                                data_started = true;
//                                                //}
//                                            }
//                                            //Log.d("Dictionary", "Sample double = " + buffer[buffer_size - data_read]);
//                                        } else if (data_read < 0) {
//                                            Log.e(TAG, "Error code " + data_read + " given by AudioRecord, consult documentation.");
//                                        }
//                                    }
//                                    while (recording);
//
//                                    if (save_recordings) {
//                                        try {
//                                            //Log.d(TAG, "Directory = " + getFilesDir()+File.separator+"test.txt");
//                                            File file = new File(getExternalFilesDir(null), recording_word + recording_index + ".txt");
//                                            recording_index++;
//                                            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
//                                            for (int i = 0; i < buffer_size; i++) {
//                                                //Log.d(TAG, "Arr at " + i + " = " + buffer[i]);
//                                                bufferedWriter.write(buffer[i] + "\n");
//                                            }
//                                            bufferedWriter.close();
//                                            Intent intent =
//                                                    new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//                                            intent.setData(Uri.fromFile(file));
//                                            sendBroadcast(intent);
//                                        } catch (IOException ex) {
//                                            ex.printStackTrace();
//                                        }
//                                    }
//
//                                    Log.d(TAG, "Max = " + max);
//                                    Log.d(TAG, "Recording stopped");
//                                    recorder.stop();
//                                    recorder.release();
//                                    final String stt_result = dictionary.recognize(buffer, buffer_size);
//                                    //// TODO: 2016/10/06 check why the log likelihoods are slightly different compared to matlab
//                                    Log.d(TAG, "Audio recording identified as: " + stt_result);
//                                    //stt_total_result += Character.toUpperCase(stt_result.charAt(0)) + stt_result.substring(1) + " "; //capitalize first letter
//                                    stt_total_result += stt_result + " ";
//
//                                    final Runnable runnable_ui = new Runnable() {
//                                        @Override
//                                        public void run() {
//                                            TextView textDisplay = (TextView) findViewById(R.id.textDisplay);
//                                            textDisplay.setText(stt_total_result);
//                                        }
//                                    };
//
//                                    new Thread() {
//                                        public void run() {
//                                            mHandler.post(runnable_ui);
//                                        }
//                                    }.start();
//
//                                    return null;
//                                }
//                            }.execute();
//                        }
//                    }
//                    else
//                    {
//                        recording = false;
//                        setButtonLabel(R.id.buttonRecord, "Record");
//                        if(use_custom_playback_device)
//                        {
//                            recording_data = false;
//                            byte[] bytes = {0x73}; //lowercase s for 'stop'
//                            BluetoothWrite(bytes);
//                        }
//                    }
//                }
//            }
//        });
    }

    /**
     * Unregisters the broadcast receivers when the application is paused.
     */
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiverTriggerTTS);
        unregisterReceiver(broadcastReceiverEndOfSynthesis);
        unregisterReceiver(broadcastReceiverEmailInitialized);
        unregisterReceiver(broadcastReceiverReceivedSms);
    }

    /**
     * Re-registers the broadcast receivers when the application is resumed.
     */
    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter_received_sms = new IntentFilter("broadcast_sms");
        registerReceiver(broadcastReceiverReceivedSms, filter_received_sms);

        IntentFilter filter_end_of_synthesis = new IntentFilter("broadcast_end_of_synthesis");
        registerReceiver(broadcastReceiverEndOfSynthesis, filter_end_of_synthesis);

        IntentFilter filter_emails_init = new IntentFilter("broadcast_email_init");
        registerReceiver(broadcastReceiverEmailInitialized, filter_emails_init);

        IntentFilter filter_trigger_tts = new IntentFilter("broadcast_trigger_tts");
        registerReceiver(broadcastReceiverTriggerTTS, filter_trigger_tts);
    }

    /**
     *
     * @param menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_home_page, menu);
        return true;
    }

    /**
     *
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Starts recording data using the microphone or custom hardware device and converts data to text.
     * @param view The current view.
     */
    public void recordButtonPressed(View view){
        if(use_stt_api) {
            if (mState == ConnectionState.IDLE) {
                mState = ConnectionState.CONNECTING;
                aState = ApplicationState.SST_CONVERSION;
                Log.d(TAG, "onClickRecord: IDLE -> CONNECTING");
                recognition_results = "";
                displayResult(recognition_results, false);
                // start recognition
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... none) {
                        SpeechToText.sharedInstance().recognize(); //uses OnMessage() function to display results
                        return null;
                    }
                }.execute();
                setButtonLabel(R.id.buttonRecord, "Connecting...");
                setButtonState(true);
            } else if (mState == ConnectionState.CONNECTED) {
                mState = ConnectionState.IDLE;
                Log.d(TAG, "onClickRecord: CONNECTED -> IDLE");
                SpeechToText.sharedInstance().stopRecognition(); //uses OnMessage() function to display results
                setButtonState(false);
            }
        }
        else
        {
            if(!recording) {
                recording = true;
                setButtonLabel(R.id.buttonRecord, "Stop Recording");
                Log.d(TAG, "Recording started");
                if(use_custom_playback_device)
                {
                    recording_data = true;
                    byte[] bytes = {0x72}; //lowercase r for 'record'
                    BluetoothWrite(bytes);
                    //todo implement shiz
                }
                else {
                    recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, 8000,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, 8000);
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... none) {

                            recorder.startRecording();
                            float[] f_buffer = new float[1000];
                            double[] buffer = new double[24000];
                            int buffer_size = 0;
                            double max = 0;
                            boolean data_started = false;
                            do {
                                int data_read = recorder.read(f_buffer, 0, f_buffer.length, AudioRecord.READ_NON_BLOCKING);
                                if (data_read > 0) {
                                    //Log.d("Dictionary", "Sample float = " + f_buffer[0]);
                                    for (int i = 0; i < data_read; i++) {
                                        if (f_buffer[i] > 0.0004 || f_buffer[i] < -0.0004 || data_started) {
                                            buffer[buffer_size] = (double) f_buffer[i];
                                            if (buffer[buffer_size] > max)
                                                max = buffer[buffer_size];
                                            buffer_size++;
                                            data_started = true;
                                        }
                                    }
                                    //Log.d("Dictionary", "Sample double = " + buffer[buffer_size - data_read]);
                                } else if (data_read < 0) {
                                    Log.e(TAG, "Error code " + data_read + " given by AudioRecord, consult documentation.");
                                }
                            }
                            while (recording);

                            if (save_recordings) {
                                try {
                                    //Log.d(TAG, "Directory = " + getFilesDir()+File.separator+"test.txt");
                                    File file = new File(getExternalFilesDir(null), recording_word + recording_index + ".txt");
                                    recording_index++;
                                    BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
                                    for (int i = 0; i < buffer_size; i++) {
                                        //Log.d(TAG, "Arr at " + i + " = " + buffer[i]);
                                        bufferedWriter.write(buffer[i] + "\n");
                                    }
                                    bufferedWriter.close();
                                    Log.d(TAG, "Recording saved as word " + recording_word);
                                    Intent intent =
                                            new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                    intent.setData(Uri.fromFile(file));
                                    sendBroadcast(intent);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                            }

                            Log.d(TAG, "Max = " + max);
                            Log.d(TAG, "Recording stopped");
                            recorder.stop();
                            recorder.release();
                            final String stt_result = dictionary.recognize(buffer, buffer_size);
                            //// TODO: 2016/10/06 check why the log likelihoods are slightly different compared to matlab
                            Log.d(TAG, "Audio recording identified as: " + stt_result);
                            //stt_total_result += Character.toUpperCase(stt_result.charAt(0)) + stt_result.substring(1) + " "; //capitalize first letter
                            stt_total_result += stt_result + " ";

                            final Runnable runnable_ui = new Runnable() {
                                @Override
                                public void run() {
                                    TextView textDisplay = (TextView) findViewById(R.id.textDisplay);
                                    textDisplay.setText(stt_total_result);
                                }
                            };

                            new Thread() {
                                public void run() {
                                    mHandler.post(runnable_ui);
                                }
                            }.start();

                            return null;
                        }
                    }.execute();
                }
            }
            else
            {
                recording = false;
                setButtonLabel(R.id.buttonRecord, "Record");
                if(use_custom_playback_device)
                {
                    recording_data = false;
                    byte[] bytes = {0x73}; //lowercase s for 'stop'
                    BluetoothWrite(bytes);
                }
            }
        }
    }

    /**
     * Starts the Display Help activity on press of help button.
     * @param view The current view.
     */
    public void instrucButtonPressed(View view) {
        Intent instrucIntent = new Intent(this, DisplayHelp.class); //Create the intent.
        startActivity(instrucIntent); //Start the help activity.
    }

    /**
     * Starts the Display Settings activity on press of settings button.
     * @param view The current view.
     */
    public void settingsButtonPressed(View view) {
        Intent settingsIntent = new Intent(this, DisplaySettings.class); //Create the intent.
        settingsIntent.putExtra("email_amount", maximum_unread_emails); //Add an extra containing the current unread email amount.
        settingsIntent.putExtra("stt_api_status", use_stt_api); //Add an extra containing the current unread email amount.
        settingsIntent.putExtra("custom_playback_device_status", use_custom_playback_device); //Add an extra containing the current unread email amount.
        startActivityForResult(settingsIntent, SETTINGS_REQ_CODE); //Start the settings activity and expect a result.
    }

    /**
     * Processes the string that represents the speech to text conversion currently displayed to
     * the user
     * @param view The current view.
     */
    public void processButtonPressed(View view) {
        if(!use_stt_api)
        {
            Log.d(TAG, "Process button pressed");
            processText(stt_total_result);
            stt_total_result = "";
        }
    }

    /**
     * Initializes the connection to the IBM Speech-to-text service.
     * @return Boolean indicating initialization success.
     */
    private boolean initializeSTT() {

        String username = getString(R.string.STTUsername); //Set username.
        String password = getString(R.string.STTPassword); //Set password.
        String serviceURL = getString(R.string.STTServiceURL); //Set service url.

        SpeechConfiguration config = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_OGGOPUS); //Set audio input format.
        //SpeechConfiguration config = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_DEFAULT);

        SpeechToText.sharedInstance().initWithContext(this.getHost(serviceURL), getApplicationContext(), config); //Initialize TextToSpeech object.
        SpeechToText.sharedInstance().setCredentials(username, password); //Set credentials using basic authentication.
        SpeechToText.sharedInstance().setModel(getString(R.string.modelDefault)); //Set language model to selected default.
        SpeechToText.sharedInstance().setDelegate(this); //Set delegate to the Home Page that extends ISpeechDelegate.

        return true;
    }

    /**
     * Function that is called when the STT service is opened.
     */
    public void onOpen() {
        Log.d(TAG, "onOpen");
        setButtonLabel(R.id.buttonRecord, "Stop recording");
        mState = ConnectionState.CONNECTED; //Set connection state to CONNECTED.
    }

    /**
     * Function that is called when an error occurs in the STT service.
     * @param error String containing the STT service error.
     */
    public void onError(String error) {

        Log.e(TAG, error);
        displayResult(error, false); //Output the error to the screen.
        mState = ConnectionState.IDLE; //Set connection state to IDLE.
    }

    /**
     * Function that is called when connection to STT service is closed.
     * @param code Integer indicating code for reason of closing.
     * @param reason String containing the reason for closing STT service.
     * @param remote
     */
    public void onClose(int code, String reason, boolean remote) {
        Log.d(TAG, "onClose, code: " + code + " reason: " + reason);
        setButtonLabel(R.id.buttonRecord, "Record");
        mState = ConnectionState.IDLE; //Set connection state to IDLE.
    }

    /**
     *
     * @param message
     */
    public void onMessage(String message) {
        try {
            JSONObject jObj = new JSONObject(message);
            if(jObj.has("state")) {
                Log.d(TAG, "Status message: " + jObj.getString("state"));
            }
            else if (jObj.has("results")) {
                Log.d(TAG, "Results message: ");
                JSONArray jArr = jObj.getJSONArray("results");
                for (int i=0; i < jArr.length(); i++) {
                    JSONObject obj = jArr.getJSONObject(i);
                    JSONArray jArr1 = obj.getJSONArray("alternatives");
                    String str = jArr1.getJSONObject(0).getString("transcript");
                    // remove whitespaces if the language requires it
                    String strFormatted = Character.toUpperCase(str.charAt(0)) + str.substring(1);
                    if (obj.getString("final").equals("true")) {
                        mState = ConnectionState.IDLE;
                        Log.d(TAG, "Shutting down recognition.");
                        Log.d(TAG, "onClickRecord: CONNECTED -> IDLE");
                        SpeechToText.sharedInstance().stopRecognition(); //uses OnMessage() function to display results
                        //setButtonState(false);
                        recognition_results += strFormatted.substring(0,strFormatted.length()-1) + ". ";

                        displayResult(recognition_results, true);
                    } else {
                        displayResult(recognition_results + strFormatted, false);
                    }
                    break;
                }
            }
            else {
                displayResult("Unexpected data coming from STT server: \n" + message, false);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON");
            e.printStackTrace();
        }
    }

    /**
     * Unused function that is required to implement ISpeechDelegate
     * @param amplitude Unused.
     * @param volume Unused.
     */
    public void onAmplitude(double amplitude, double volume) {
        //Log.d(TAG, "onAmplitude function called");
    }

    /**
     * Obtains the host URI given a URL.
     * @param url  String containing the URL from which to get the host URI.
     * @return Returns a new URI object from the url String.
     */
    public URI getHost(String url){
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     *
     * @param result
     * @param complete
     */
    public void displayResult(final String result, final boolean complete) {
        if(use_stt_api && complete) {
            //process string here to find keywords: Text, Email, Phone numbers (10 digits) and Names of Contacts
            processText(result);
        }

        final Runnable runnableUi = new Runnable(){
            @Override
            public void run() {
                TextView textDisplay = (TextView) findViewById(R.id.textDisplay);
                textDisplay.setText(result);
            }
        };

        new Thread(){
            public void run(){
                mHandler.post(runnableUi);
            }
        }.start();
    }

    public void processText(final String result) {
        if (!read_emails) {
            aState = ApplicationState.IDLE;
            if (result.toLowerCase().contains("yes")) {
                new Thread() {
                    public void run() {
                        try {
                            while (!unread_mail_queue.isEmpty()) {
                                if (aState.equals(ApplicationState.IDLE)) {
                                    aState = ApplicationState.TTS_CONVERSION;
                                    String message = unread_mail_queue.poll();
                                    Log.d(TAG, "Reading message: " + message);
                                    TextToSpeech.sharedInstance().synthesize(message, getApplicationContext());
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            }
            //todo: else{processing the string using other functionality}
            read_emails = true;
        } else {
            String textArray[] = result.split(" ");
            if (textArray[0].equalsIgnoreCase("text")) {
                String contact_name = textArray[1];

                Log.d(TAG, "Attempting to obtain number of contact");

                String phone_num = getContactNumber(getApplicationContext(), contact_name);

                Log.d(TAG, "Obtained number: " + phone_num);

                if (!phone_num.equals("")) {
                    String msg = "";
                    int length = textArray.length;

                    for (int i = 2; i < length - 1; i++) {
                        msg += textArray[i] + " ";
                    }
                    msg += textArray[length - 1];

                    Log.d(TAG, "Obtained message: " + msg);

                    SMSSender smsSender = new SMSSender();
                    smsSender.sendSMS(phone_num, msg);

                    aState = ApplicationState.IDLE;
                } else {
                    //unknown contact name or incorrectly translated
                    aState = ApplicationState.TTS_CONVERSION;
                    TextToSpeech.sharedInstance().synthesize("Unable to find cellphone number of specified contact.", getApplicationContext());
                }
            } else if (textArray[0].equalsIgnoreCase("email")) {
                String contact = textArray[1];

                Log.d(TAG, "Attempting to obtain email address of contact");

                String email_address = getContactEmail(getApplicationContext(), contact);

                Log.d(TAG, "Obtained email address: " + email_address);

                //TEXT <NAME> <CONTENTS>
                //EMAIL <NAME> <SUBJECT> MESSAGE <BODY>
                //assumes 1 word names

                if (!email_address.equals("")) {
                    String msg = "";
                    String subject = "";
                    int msg_start = 2;

                    int length = textArray.length;
                    for (int i = 2; i < length; i++) {
                        if (textArray[i].equalsIgnoreCase("message")) {
                            msg_start = i + 1;
                            for (int j = 2; j < msg_start - 1; j++) {
                                subject += textArray[j] + " ";
                            }
                            break;
                        }
                    }

                    for (int i = msg_start; i < length - 1; i++) {
                        msg += textArray[i] + " ";
                    }
                    msg += textArray[length - 1];

                    Log.d(TAG, "Obtained subject: " + subject);
                    Log.d(TAG, "Obtained message: " + msg);

                    try {
                        mailSender.sendMail(email_address, subject, msg);
                    } catch (MessagingException e) {
                        Log.e(TAG, "Messaging Exception when sending email.");
                        e.printStackTrace();
                    } catch (Exception e) {
                        Log.e(TAG, "Unknown Exception when sending email.");
                        e.printStackTrace();
                    }//todo add better checks for blank messages and so on

                    aState = ApplicationState.IDLE;
                } else {
                    //unknown contact name or incorrectly translated
                    aState = ApplicationState.TTS_CONVERSION;
                    TextToSpeech.sharedInstance().synthesize("Unable to find email address of specified contact.", getApplicationContext());
                }
            } else {
                //inform user that command was not understood
                aState = ApplicationState.TTS_CONVERSION;
                TextToSpeech.sharedInstance().synthesize("Command could not be understood.", getApplicationContext());
                //could also check if a number was spoken manually
            }

        }
    }

    /**
     * Function that sets the text of the recording button.
     * @param buttonId ID of the button.
     * @param label String that the text of the button should be changed to.
     */
    public void setButtonLabel(final int buttonId, final String label) {
        final Runnable runnableUi = new Runnable(){
            @Override
            public void run() {
                Button button = (Button) findViewById(buttonId); //Obtain the button.
                button.setText(label); //Set the button text.
            }
        };
        new Thread(){
            public void run(){
                mHandler.post(runnableUi); //Use the mHandler to change the UI.
            }
        }.start();
    }

    /**
     * Sets the state of the record button to indicate when the button is recording.
     * @param bRecording Boolean indicating if recording is occuring or not.
     */
    public void setButtonState(final boolean bRecording) {

        final Runnable runnableUi = new Runnable(){
            @Override
            public void run() {
                //int iDrawable = bRecording ? R.drawable.button_record_stop : R.drawable.button_record_start;
                Button btnRecord = (Button) findViewById(R.id.buttonRecord);
                //btnRecord.setBackground(getResources().getDrawable(iDrawable));
            }
        };
        new Thread(){
            public void run(){
                mHandler.post(runnableUi);
            }
        }.start();
    }

    /**
     * Initializes the connection to the IBM text-to-speech server.
     * @return Boolean indicating connection success.
     */
    private boolean initializeTTS() {
        String username = getString(R.string.TTSUsername); //Set username.
        String password = getString(R.string.TTSPassword); //Set password.
        String serviceURL = getString(R.string.TTSServiceURL); //Set service url.

        TextToSpeech.sharedInstance().initWithContext(this.getHost(serviceURL)); //Initialize TextToSpeech object.
        TextToSpeech.sharedInstance().setCredentials(username, password); //Set credentials using basic authentication.
        TextToSpeech.sharedInstance().setVoice(getString(R.string.voiceDefault)); //Set voice to selected default.
        return true;
    }

    /**
     * Function that is called to extract the contents of an email. Loops through all the content types
     * contained within the email and extracts the important information.
     * @param mail The email Message.
     * @return Returns a String containing the message contents.
     */
    public String getMailMessage(Message mail) {
        String full_message = "";
        try{
            String subject = mail.getSubject(); //Obtain email subject.
            String from = mail.getFrom()[0].toString(); //Obtain email sender
            String body = "";
            if(mail.isMimeType("text/plain")) //Test mime types
            {
                body = (String) mail.getContent(); //If text/plain, obtain content.
            }
            else if (mail.isMimeType("text/html")) { //If text/html
                String temp = (String) mail.getContent(); //Obtain content.
                body = Jsoup.parse(temp).text(); //Parse content using Jsoup.
            }
            else
            if(mail.getContentType().toLowerCase().contains("multipart/")) //If multipart/
            {
                MimeMultipart multipart = (MimeMultipart) mail.getContent(); //Get multipart content
                body += getTextFromMimeMultipart(multipart); //Call function to get data from multipart.
            }
            else
            {
                Log.d(TAG, "Unknown MimeType (Not from multipart): " + mail.getContentType()); //Unkown mime type.
            }
            //todo test SST with multiple sentences -> causes problem, final is triggered when end of sentence is reached. Will need to solve later.

            //Extract name of sender if email is in format: //Jean van den Berg <jeanvdberg1994@gmail.com>
            int index = from.indexOf('<');
            if(index > 1 && from.charAt(index - 1) == ' ')
                index--;
            if(index >= 1)
                from = from.substring(0, index);

            full_message = "Mail received from: " + from + ".\nThe subject is: " + subject + ".\nThe contents are as follows. " + body;

            Log.d(TAG, full_message);
        } catch(MessagingException e)
        {
            Log.e(TAG, "Messaging Exception when obtaining mail details.");
            e.printStackTrace();
        } catch(IOException e)
        {
            Log.e(TAG, "IO Exception");
            e.printStackTrace();
        } catch(Exception e)
        {
            e.printStackTrace();
        }

        return full_message;
    }

    /**
     * Obtains String containg important data from a MimeMultipart object. Function loops through all
     * the mime types and combines all the relevant data into a single string.
     * @param multipart MimeMultipart object.
     * @return Returns a String containg the relevant data.
     * @throws MessagingException Throws MessagingExceptions.
     * @throws IOException Throws IOExceptions.
     */
    public String getTextFromMimeMultipart(MimeMultipart multipart) throws MessagingException, IOException {
        String text = "";
        int j = 0;
        int multipart_length = multipart.getCount(); //Obtain amount of parts.
        if(multipart.getContentType().toLowerCase().contains("multipart/alternative"))
            j = multipart_length - 1;
        //explanation: a multipart/alternative mime type is alternative versions of the same body
        //according to RFC2046, the last bodypart is the most accurate to the original body
        for (; j < multipart_length; j++) { //Loop through multipart object.
            BodyPart bodypart = multipart.getBodyPart(j); //Obtain the current BodyPart of the multipart.
            if (bodypart.isMimeType("text/plain")) { //Start testing body part mime types.
                text += bodypart.getContent() + "\n"; //If text/plain get the content.
            }
            else if (bodypart.isMimeType("text/html")) { //If text/html
                String temp = (String) bodypart.getContent(); //Get the content.
                String parsedHtml = Jsoup.parse(temp).text(); //Parse the content using Jsoup.
                text += parsedHtml + "\n";
            }
            else if(bodypart.getContentType().toLowerCase().contains("multipart/")){ //If the multipart contains a multipart.
                text += getTextFromMimeMultipart((MimeMultipart) bodypart.getContent()); //Recursive call.
            }
            else if(bodypart.getContentType().toLowerCase().contains("image/")) //If the multipart contains an image.
            {
                text += ". Mail has an attached image.\n"; //Inform user about attachment.
            }
            else if(bodypart.getContentType().toLowerCase().contains("audio/")) //If the multipart contains an audio file.
            {
                text += ". Mail has an attached audio file.\n"; //Inform user about attachment.
            }
            else if(bodypart.getContentType().toLowerCase().contains("video/")) //If the multipart contains a video.
            {
                text += ". Mail has an attached video file.\n"; //Inform user about attachment.
            }
            else if(bodypart.getContentType().toLowerCase().contains("pdf")) //If the multipart contains a pdf.
            {
                text += ". Mail has an attached PDF.\n"; //Inform user about attachment.
            }else
            {
                Log.d(TAG, "Unknown MimeType: " + bodypart.getContentType());
            }
        }
        return text;
    }

    /**
     * Function that is called when the Display Settings activity is finished. The function edits and
     * saves the changed settings to the Shared Preferences of the application.
     * @param requestCode Code identifying the display settings activity.
     * @param resultCode Result code returned by the activity.
     * @param intent Intent containing the data from the activity.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch(requestCode) {
            case (SETTINGS_REQ_CODE) : {
                if (resultCode == RESULT_OK) { //Ensure the activity result was ok.
                    Log.d(TAG, "Received broadcast from Settings indicating changed settings.");
                    Bundle b = intent.getExtras(); //Obtain the extras from the intent.
                    int temp = b.getInt("Unread_email_amnt", 2); //Get the unread email amount extra.
                    boolean new_use_stt_api = b.getBoolean("Use_stt_api", false);
                    boolean new_use_custom_playback_device = b.getBoolean("Use_custom_playback_device", false);
                    if(maximum_unread_emails != temp && temp > 0) //Test if the setting was changed.
                    {
                        maximum_unread_emails = temp;
                        SharedPreferences settings = getSharedPreferences(PREFS, 0); //Get the Shared Preferences of the app.
                        SharedPreferences.Editor editor = settings.edit(); //Create a Shared Preferences editor.
                        editor.putInt("unread_email_amount", maximum_unread_emails); //Edit the Share Preferences.
                        editor.apply(); //Save the settings.
                    }
                    if(use_stt_api != new_use_stt_api)
                    {
                        use_stt_api = new_use_stt_api;
                        SharedPreferences settings = getSharedPreferences(PREFS, 0); //Get the Shared Preferences of the app.
                        SharedPreferences.Editor editor = settings.edit(); //Create a Shared Preferences editor.
                        editor.putBoolean("use_stt_api", use_stt_api); //Edit the Share Preferences.
                        editor.apply(); //Save the settings.
                    }
                    if(use_custom_playback_device != new_use_custom_playback_device)
                    {
                        use_custom_playback_device = new_use_custom_playback_device;
                        SharedPreferences settings = getSharedPreferences(PREFS, 0); //Get the Shared Preferences of the app.
                        SharedPreferences.Editor editor = settings.edit(); //Create a Shared Preferences editor.
                        editor.putBoolean("use_custom_playback_device", use_custom_playback_device); //Edit the Share Preferences.
                        editor.apply(); //Save the settings.
                    }
                }
                break;
            }
            case (REQUEST_ENABLE_BT) : {
                if (resultCode == RESULT_OK) {
                    bluetooth_enabled = true;
                    startBluetoothClient();
                }
                if(resultCode == RESULT_CANCELED) {
                    bluetooth_enabled = false;
                }
                break;
            }
        }
    }

    /**
     * Broadcast receiver that triggers when a general text to speech conversion is required. The function
     * extracts the messages from the synthesizer queue and synthesizes the messages.
     */
    BroadcastReceiver broadcastReceiverTriggerTTS =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received broadcast to trigger TTS conversion.");
            new Thread() {
                public void run() {
                    try {
                        while (!synthesizer_queue.isEmpty())
                        {
                            if (aState.equals(ApplicationState.IDLE)) { //Test if application is idle.
                                aState = ApplicationState.TTS_CONVERSION; //Set application state.
                                String message = synthesizer_queue.poll(); //Obtain the message.
                                Log.d(TAG, "Reading message:\n" + message);
                                TextToSpeech.sharedInstance().synthesize(message, getApplicationContext()); //Send message to synthesizer.
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    };

    /**
     * Broadcast receiver that triggers when speech synthesis is complete. Function sets the application
     * state to idle.
     */
    BroadcastReceiver broadcastReceiverEndOfSynthesis =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received broadcast from TTS indicating end of synthesis.");
            aState = ApplicationState.IDLE; //Set state to idle.
        }
    };

    /**
     * Broadcast Receiver that triggers when the Mail Handler is finished initializing and the message
     * asking the user if they would like the unread emails to be read out loud is in the queue. The
     * function synthesizes the message asking if the user would like these emails to be read once
     * the application is idle. This function does not wait for application state to go idle whereas the
     * 1st broadcast receiver does.
     */
    BroadcastReceiver broadcastReceiverEmailInitialized =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received broadcast indicating email initialization.");
            new Thread() { //Start new thread to poll when application state is idle.
                public void run() {
                    try {
                        if (!synthesizer_queue.isEmpty() && aState.equals(ApplicationState.IDLE)) { //Test if application is idle.
                            aState = ApplicationState.TTS_CONVERSION; //Set application state.
                            String message = synthesizer_queue.poll(); //Obtain the message.
                            Log.d(TAG, "Reading message: " + message);
                            TextToSpeech.sharedInstance().synthesize(message, getApplicationContext()); //Send message to synthesizer.
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    };

    /**
     * Broadcast Receiver that triggers when the SMS Receiver class sends a broadcast.
     * The function processes the received text and sends the data to the TTS converter.
     */
    BroadcastReceiver broadcastReceiverReceivedSms =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle b = intent.getExtras(); //Obtain the extras in the intent.

            String message = b.getString("message"); //Obtain the message.
            String contact_number = b.getString("contact"); //Obtain the sender's number.
            String contact_name = getContactName(getApplicationContext(), contact_number, true); //Obtain the name of the contact.
            String output = "Text received from ";

            if(!contact_name.equals("")) //Test if a name was returned.
            {
                output += contact_name; //Add the name to the output.
            }
            else
                output += contact_number; //Add the number to the output.

            output += " with the following message. " + message;
            Log.d(TAG, output);
            synthesizer_queue.add(output); //Add the entire message to the synthesizer queue.

            Intent intent_trigger_tts = new Intent("broadcast_trigger_tts");
            sendBroadcast(intent_trigger_tts); //Send a broadcast indicating a new email was received.
        }
    };

    /**
     * Obtains a contact name by doing a contact lookup on the local phone using the contact number
     * or email address of the contact.
     * @param context Current application context.
     * @param search_term The String containing a contact number or email address search term.
     * @param isNumber Boolean stating if search term is a number or email address.
     * @return String containg the email address of the contact, contains an empty string if none was found.
     */
    public static String getContactName(Context context, String search_term, boolean isNumber) {
        Uri uri = ContactsContract.Data.CONTENT_URI; //Set the URI to a phone content URI.
        String[] projection = {ContactsContract.Data.DISPLAY_NAME}; //Set the projection to obtain display names.
        String name = "";
        String selection;
        if(isNumber)
            selection = ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?"; //Set search selection to find a number containing the search term.
        else
            selection = ContactsContract.CommonDataKinds.Email.ADDRESS + " LIKE ?"; //Set search selection to find an email address containing the search term.
        String[] selectionArgs = {"%" + search_term + "%"}; //Set the selection argument.

        Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null); //Obtain a cursor returning the matched data.

        if(cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) //Check if the cursor returned a valid result.
        {
            name = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)); //Obtain the display name of the contact.
        }

        if(cursor != null && !cursor.isClosed()) {
            cursor.close(); //Close the cursor.
        }

        Log.d(TAG, "Obtained name: " + name);

        return name;
    }

    /**
     * Obtains a contact number by doing a contact lookup on the local phone using the name of
     * the contact.
     * @param context Current application context.
     * @param name Name of the contact to obtain the email address for.
     * @return String containg the email address of the contact, contains an empty string if none was found.
     */
    public static String getContactNumber(Context context, String name) {
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI; //Set the URI to a phone content URI.
        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER}; //Set the projection to obtain contact numbers.
        String contactNumber = "";
        String selection = ContactsContract.Data.DISPLAY_NAME + " LIKE ?"; //Set search selection to find a display name containing the name parameter.
        String[] selectionArgs = {"%" + name + "%"}; //Set the selection argument.

        Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null); //Obtain a cursor returning the matched data.

        if(cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) //Check if the cursor returned a valid result.
        {
            contactNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)); //Obtain the phone number of the contact.
        }

        if(cursor != null && !cursor.isClosed()) {
            cursor.close(); //Close the cursor.
        }

        Log.d(TAG, "Obtained number: " + contactNumber);

        return contactNumber;
    }

    /**
     * Obtains an email address by doing a contact lookup on the local phone using the name of
     * the contact.
     * @param context Current application context.
     * @param name Name of the contact to obtain the email address for.
     * @return String containg the email address of the contact, contains an empty string if none was found.
     */
    public static String getContactEmail(Context context, String name) {
        Uri uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI; //Set the URI to an email content URI.
        String[] projection = {ContactsContract.CommonDataKinds.Email.ADDRESS}; //Set the projection to obtain email addresses.
        String email = "";
        String selection = ContactsContract.Data.DISPLAY_NAME + " LIKE ?"; //Set search selection to find a display name containing the name parameter.
        String[] selectionArgs = {"%" + name + "%"}; //Set the selection argument.

        Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null); //Obtain a cursor returning the matched data.

        if(cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) //Check if the cursor returned a valid result.
        {
            email = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)); //Obtain the email address of the contact.
        }

        if(cursor != null && !cursor.isClosed()) {
            cursor.close(); //Close the cursor.
        }

        return email;
    }

    public void startBluetoothClient(){
        Log.d(TAG, "Starting bluetooth client");
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) { //check if there are paired devices
            for (BluetoothDevice device : pairedDevices) { // Loop through paired devices
                // Add the name and address to an array adapter to show in a ListView
                //mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                bluetooth_dev_name = device.getName();
                bluetooth_dev_mac = device.getAddress();
                break;
            }
        }
        BluetoothDevice bluetooth_device =  mBluetoothAdapter.getRemoteDevice(bluetooth_dev_mac);
        mConnectThread = new ConnectThread(bluetooth_device);
        mConnectThread.run();
    }

    public void startBluetoothServer(){
        mAcceptThread = new AcceptThread();
        mAcceptThread.run();
    }

    public void manageConnectedSocket(BluetoothSocket mmSocket){
        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        Log.d(TAG, "Managing bluetooth connection");
        mConnectedThread = new ConnectedThread(mmSocket);
        bluetooth_active_connection = true;
        mConnectedThread.run();
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("Server", default_uuid);
            }
            catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "IO Exception when instantiating accept thread.");
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "IO Exception when accepting a connection to the accept thread.");
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    manageConnectedSocket(socket);
                    try
                    {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "IO Exception when closing the accept thread.");
                    }
                    break;
                }
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "IO Exception when cancelling the accept thread.");
            }
        }
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createInsecureRfcommSocketToServiceRecord(default_uuid);

            }
            catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "IO Exception when instantiating connect thread.");
            }
//            catch(NoSuchMethodException e)
//            {
//                e.printStackTrace();
//                Log.e(TAG, "No such method exception occurred");
//            }
//            catch(IllegalAccessException e)
//            {
//                e.printStackTrace();
//                Log.e(TAG, "Illegal access exception occurred");
//            }
//            catch(InvocationTargetException e)
//            {
//                e.printStackTrace();
//                Log.e(TAG, "Invocation target exception occurred");
//            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
                Log.d(TAG, "Connected");
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                Log.e(TAG, connectException.getMessage());

                try {
                    mmSocket =(BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(mmDevice,1);
                    mmSocket.connect();
                }
                catch (IOException closeException) {
                    closeException.printStackTrace();
                    Log.e(TAG, "IO Exception when connecting to backup socket.");
                }
                catch(Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "You are fucked");
                }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            manageConnectedSocket(mmSocket);
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            }
            catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "IO Exception when cancelling connect thread.");
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private AudioTrack audio_track;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            //audio_track = new AudioTrack(AudioManager.STREAM_VOICE_CALL, 8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_8BIT, 256, AudioTrack.MODE_STREAM);

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "IO Exception when obtaining input and/or output stream.");
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[128];  // buffer store for the stream
            int bytes = 0;
            // Keep listening to the InputStream until an exception occurs
            while(true) {
                try {
                    // Read from the InputStream
//                        bytes = mmInStream.read(buffer);
//                        String readMessage = new String(buffer, 0, bytes);
//                        uart_counter2 = uart_counter2 + bytes;
//                        Log.d(TAG, "Total bytes2: " + uart_counter2);
//                        int avail = mmInStream.available();
//                        Log.d(TAG, "Bytes available: " + avail);
                    // Send the obtained bytes to the UI Activity
                    //mHandler.obtainMessage(MESSAGE_READ, bytes, -1, readMessage)
                    //        .sendToTarget();
                    //                   if (mmSocket.isConnected()) {
//                    int avail = mmInStream.available();
//                    if (avail > 0) {
//                        uart_counter = uart_counter + avail;
//                        Log.d(TAG, "Bytes available: " + avail);
//                        Log.d(TAG, "Total bytes: " + uart_counter);
//                        for(int i = 0; i < avail; i++)
//                        {
//                            buffer[i] = (byte)mmInStream.read();
//                        }
//                            // Read from the InputStream
//                            //buffer = new byte [1024];
////                                int dhs = mmInStream.read();
////                                Log.d(TAG, "Read byte: " + dhs); //1.312sec -> 10496 samples //received 6529 bytes
//                                                               //1.221sec -> 9768 samples //received 6338 bytes
//
//                                                               //2 seconds of sampling -> 16000 samples //received 8000 bytes
////                                bytes = mmInStream.read(buffer);
////                                Log.d(TAG, "Reading bytes: " + bytes);
////                                String temp = new String(buffer);
////                                Log.d(TAG, "Bytes read: " + temp);
//                            // Send the obtained bytes to the UI activity
//                        //mHandler.obtainMessage(MESSAGE_READ, avail, -1, buffer)
//                        //            .sendToTarget();
//                    }
//                    else SystemClock.sleep(20);


                    //if (mmInStream.available() > 0) {
                    int i = 0;
                    int temp = 0;
                    while (i < 128) {
                        temp = mmInStream.read();
                        buffer[i] = (byte) temp;
                        i++;
                    }
                    uart_counter2 = uart_counter2 + i;
                    Log.d(TAG, "Total bytes received: " + uart_counter2);

                    byte[] keep_alive = new byte[1];
                    keep_alive[0] = (byte) 105;
                    write(keep_alive);

                    mHandler.obtainMessage(MESSAGE_READ, 128, -1, buffer)
                            .sendToTarget();
//                    }
//                    else {
//                        Log.e(TAG, "Read failed as socket is currently closed.");
//                        this.cancel();
//                    }
                    //}
                }
                catch(IOException e){
                    e.printStackTrace();
                    Log.e(TAG, "IO Exception when obtaining messaging from mHandler in connected thread.");
                    break;
                }
            }
//            while (true) {
//                try {
//                    if(mmSocket.isConnected()) {
//                        // Read from the InputStream
//                        bytes = mmInStream.read(buffer);
//                        // Send the obtained bytes to the UI activity
//                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
//                                .sendToTarget();
//                    }
//                    else
//                    {
//                        Log.e(TAG, "Read failed as socket is currently closed.");
//                        this.cancel();
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                    Log.e(TAG, "IO Exception when obtaining messaging from mHandler in connected thread.");
//                    break;
//                }
//            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                if(mmSocket.isConnected())
                    mmOutStream.write(bytes);
                else {
                    Log.e(TAG, "Write failed as socket is currently closed.");
                    this.cancel();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "IO Exception when writing to IO stream in connected thread.");
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            bluetooth_active_connection = false;
            try {
                mmSocket.close();
                //audio_track.release();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "IO Exception when canceling connected thread.");
            }
        }
    }

    public void BluetoothWrite(byte[] out) {
        // Create temporary object
        ConnectedThread temp;
        // Synchronize a copy of the ConnectedThread
        //ensures there can only be one copy of the ConnectedThread
        synchronized (this) {
            if (mConnectedThread == null)
                return;
            temp = mConnectedThread;
        }
        // Perform the write unsynchronized
        temp.write(out);
    }
}

