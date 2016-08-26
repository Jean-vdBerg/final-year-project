package com.project.jean.project;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.Queue;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;

// IBM Watson SDK
import com.project.jean.speech_to_text.dto.SpeechConfiguration;
import com.project.jean.speech_to_text.ISpeechDelegate;
import com.project.jean.speech_to_text.SpeechToText;
import com.project.jean.text_to_speech.TextToSpeech;
import com.project.jean.speech_common.TokenProvider;


public class HomePage extends Activity implements ISpeechDelegate{

    private static final String TAG = "MainActivity";
    private static final String PREFS = "VoiceAppPrefs";
    private final int SETTINGS_REQ_CODE = 1;

    static Queue<String> synthesizer_queue = new LinkedList<>();
    static Queue<String> unread_mail_queue = new LinkedList<>();

    static MailHandler mailSender = new MailHandler("jeanvdberg1994@gmail.com", "<password>");

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

    public Context mContext = null;
    private Handler mHandler = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_page);

        // Strictmode needed to run the http/wss request for devices > Gingerbread
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.GINGERBREAD) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        mContext = getApplicationContext();
        mHandler = new Handler();

        SharedPreferences settings = getSharedPreferences(PREFS, 0);
        maximum_unread_emails = settings.getInt("unread_email_amount", 2);

        new Thread(){
            public void run(){
                try{
                    Message[] emails = mailSender.getUnreadMail();
                    int unread_count = emails.length;

                    int maximum = (unread_count > maximum_unread_emails) ? maximum_unread_emails : unread_count;
                    int minimum = (emails.length > maximum_unread_emails) ? (unread_count - maximum_unread_emails) : 0;

                    String unread_email_msg = "You have " + unread_count + " unread emails. ";
                    unread_email_msg += "Would you like the latest " + maximum + " emails to be read out loud?";

                    synthesizer_queue.add(unread_email_msg);

                    Intent intent_email_init = new Intent("broadcast_email_init");
                    sendBroadcast(intent_email_init);

                    for (int i = unread_count - 1; i >= minimum; i--) {
                        String message = getMailMessage(emails[i]);
                        unread_mail_queue.add(message);
                    }

                    mailSender.setListener(new MailListener() {
                        @Override
                        public void callback(Message[] emails, int length) {
                            for (int i = 0; i < length; i++) {
                                String message = getMailMessage(emails[i]);
                                synthesizer_queue.add(message);
                                Log.d(TAG, "Adding email to queue");
                            }
                            Log.d(TAG, "Sending intent");
                            Intent intent_trigger_tts = new Intent("broadcast_trigger_tts");
                            sendBroadcast(intent_trigger_tts);
                        }
                    });
                    mailSender.getIncomingMail();
//            Iterator it = synthesizer_queue.iterator();
//            Log.d(TAG, "Queue contains " + synthesizer_queue.size() + " elements.");
//            while(it.hasNext())
//            {
//                String iteratorValue = (String) it.next();
//                Log.d(TAG, "Current msg:\n" + iteratorValue);
//            }
                }
                catch(MessagingException e)
                {
                    Log.e(TAG, "Messaging Exception when fetching unread mail.");
                    e.printStackTrace();
                }
            }
        }.start();

        Log.d(TAG, "Initializing TTS");
        if (!initializeTTS()) {
            TextView textbox = (TextView) findViewById(R.id.textDisplay);
            textbox.setText(R.string.authenticationErrorTTS);
        }
        else
        {
            Log.d(TAG, "TTS initialized");
        }

        Log.d(TAG, "Initializing STT");
        if (!initializeSTT()) {
            TextView textbox = (TextView) findViewById(R.id.textDisplay);
            textbox.setText(R.string.authenticationErrorSTT);
        }
        else
        {
            Log.d(TAG, "STT initialized");
        }

        Button buttonRecord = (Button) findViewById(R.id.buttonRecord);
        buttonRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (mState == ConnectionState.IDLE) {
                    mState = ConnectionState.CONNECTING;
                    aState = ApplicationState.SST_CONVERSION;
                    Log.d(TAG, "onClickRecord: IDLE -> CONNECTING");
                    recognition_results = "";
                    displayResult(recognition_results, false);
                    // start recognition
                    new AsyncTask<Void, Void, Void>(){
                        @Override
                        protected Void doInBackground(Void... none) {
                            SpeechToText.sharedInstance().recognize(); //uses OnMessage() function to display results
                            return null;
                        }
                    }.execute();
                    setButtonLabel(R.id.buttonRecord, "Connecting...");
                    setButtonState(true);
                }
                else if (mState == ConnectionState.CONNECTED) {
                    mState = ConnectionState.IDLE;
                    Log.d(TAG, "onClickRecord: CONNECTED -> IDLE");
                    SpeechToText.sharedInstance().stopRecognition(); //uses OnMessage() function to display results
                    setButtonState(false);
                }
            }
        });

        IntentFilter filter_received_sms = new IntentFilter("broadcast_sms");
        registerReceiver(broadcastReceiverReceivedSms, filter_received_sms);

        IntentFilter filter_end_of_synthesis = new IntentFilter("broadcast_end_of_synthesis");
        registerReceiver(broadcastReceiverEndOfSynthesis, filter_end_of_synthesis);

        IntentFilter filter_email_init = new IntentFilter("broadcast_email_init");
        registerReceiver(broadcastReceiverEmailInitialized, filter_email_init);

        IntentFilter filter_trigger_tts = new IntentFilter("broadcast_trigger_tts");
        registerReceiver(broadcastReceiverTriggerTTS, filter_trigger_tts);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiverTriggerTTS);
        unregisterReceiver(broadcastReceiverEndOfSynthesis);
        unregisterReceiver(broadcastReceiverEmailInitialized);
        unregisterReceiver(broadcastReceiverReceivedSms);
    }

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

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_home_page, menu);
        return true;
    }

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

    public void instrucButtonPressed(View view) {
        Intent instrucIntent = new Intent(this, DisplayHelp.class);
        startActivity(instrucIntent);
    }

    public void settingsButtonPressed(View view) {
        Intent settingsIntent = new Intent(this, DisplaySettings.class);
        settingsIntent.putExtra("email_amount", maximum_unread_emails);
        startActivityForResult(settingsIntent, SETTINGS_REQ_CODE);
    }

    private boolean initializeSTT() {

        String username = getString(R.string.STTUsername);
        String password = getString(R.string.STTPassword);

        String tokenFactoryURL = getString(R.string.defaultTokenFactory);
        String serviceURL = "wss://stream.watsonplatform.net/speech-to-text/api";

        SpeechConfiguration sConfig = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_OGGOPUS);
        //SpeechConfiguration sConfig = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_DEFAULT);

        SpeechToText.sharedInstance().initWithContext(this.getHost(serviceURL), getApplicationContext(), sConfig);

        // token factory is the preferred authentication method (service credentials are not distributed in the client app)
        if (!tokenFactoryURL.equals(getString(R.string.defaultTokenFactory))) {
            SpeechToText.sharedInstance().setTokenProvider(new MyTokenProvider(tokenFactoryURL));
        }
        // Basic Authentication
        else if (!username.equals(getString(R.string.defaultUsername))) {
            SpeechToText.sharedInstance().setCredentials(username, password);
        } else {
            // no authentication method available
            return false;
        }

        SpeechToText.sharedInstance().setModel(getString(R.string.modelDefault));
        SpeechToText.sharedInstance().setDelegate(this);

        return true;
    }

    public void onOpen() {
        Log.d(TAG, "onOpen");
        setButtonLabel(R.id.buttonRecord, "Stop recording");
        mState = ConnectionState.CONNECTED;
    }

    public void onError(String error) {

        Log.e(TAG, error);
        displayResult(error, false);
        mState = ConnectionState.IDLE;
    }

    public void onClose(int code, String reason, boolean remote) {
        Log.d(TAG, "onClose, code: " + code + " reason: " + reason);
        setButtonLabel(R.id.buttonRecord, "Record");
        mState = ConnectionState.IDLE;
    }

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

    public void onAmplitude(double amplitude, double volume) {
        Log.d(TAG, "onAmplitude function called");
    }

    public URI getHost(String url){
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void displayResult(final String result, final boolean complete) {
        if(complete) {
            //process string here to find keywords: Text, Email, Phone numbers (10 digits) and Names of Contacts
            if(!read_emails)
            {
                aState = ApplicationState.IDLE;
                if(result.toLowerCase().contains("yes"))
                {
                    new Thread() {
                        public void run() {
                            try {
                                while (!unread_mail_queue.isEmpty())
                                {
                                    if (aState.equals(ApplicationState.IDLE)) {
                                        aState = ApplicationState.TTS_CONVERSION;
                                        String message = unread_mail_queue.poll();
                                        Log.d(TAG, "Reading message: " + message);
                                        TextToSpeech.sharedInstance().synthesize(message, mContext);
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                    //read_emails = true;
                    //Intent intent = new Intent("broadcast_trigger_tts");
                    //mContext.sendBroadcast(intent);
                }
                //todo: else{processing the string using other functionality}
                read_emails = true;
            }
            else {
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
                        TextToSpeech.sharedInstance().synthesize("Unable to find cellphone number of specified contact.", mContext);
                        //todo test this
                    }
                } else if (textArray[0].equalsIgnoreCase("email")) {
                    String contact = textArray[1];

                    Log.d(TAG, "Attempting to obtain email address of contact");

                    String email_address = getContactEmail(getApplicationContext(), contact);

                    Log.d(TAG, "Obtained email address: " + email_address);

                    //TEXT <NAME> <CONTENTS>
                    //EMAIL <NAME> <SUBJECT> MESSAGE <BODY>
                    //assumes 1 word names

                    if (email_address.equals("")) {
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
                        }

                        aState = ApplicationState.IDLE;
                    } else {
                        //unknown contact name or incorrectly translated
                        aState = ApplicationState.TTS_CONVERSION;
                        TextToSpeech.sharedInstance().synthesize("Unable to find email address of specified contact.", mContext);
                        //todo test this
                    }
                } else {
                    //inform user that command was not understood, ignore the queue if the queue is not empty
                    aState = ApplicationState.TTS_CONVERSION;
                    TextToSpeech.sharedInstance().synthesize("Command could not be understood.", mContext);
                    //todo test this
                    //could also check if a number was spoken manually
                }

            }
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

    public void setButtonLabel(final int buttonId, final String label) {
        final Runnable runnableUi = new Runnable(){
            @Override
            public void run() {
                Button button = (Button) findViewById(buttonId);
                button.setText(label);
            }
        };
        new Thread(){
            public void run(){
                mHandler.post(runnableUi);
            }
        }.start();
    }

    //todo edit this to change background colour of record button preferably
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

    private boolean initializeTTS() {
        String username = getString(R.string.TTSUsername);
        String password = getString(R.string.TTSPassword);
        String tokenFactoryURL = getString(R.string.defaultTokenFactory);
        String serviceURL = "https://stream.watsonplatform.net/text-to-speech/api";

        TextToSpeech.sharedInstance().initWithContext(this.getHost(serviceURL));

        // token factory is the preferred authentication method (service credentials are not distributed in the client app)
        if (!tokenFactoryURL.equals(getString(R.string.defaultTokenFactory))) {
            TextToSpeech.sharedInstance().setTokenProvider(new MyTokenProvider(tokenFactoryURL));
        }
        else if (!username.equals(getString(R.string.defaultUsername))) {
            TextToSpeech.sharedInstance().setCredentials(username, password); //basic authentication
        } else {
            return false; //authentication failure
        }
        TextToSpeech.sharedInstance().setVoice(getString(R.string.voiceDefault));
        return true;
    }

    static class MyTokenProvider implements TokenProvider {

        String m_strTokenFactoryURL = null;

        public MyTokenProvider(String strTokenFactoryURL) {
            m_strTokenFactoryURL = strTokenFactoryURL;
        }

        public String getToken() {

            Log.d(TAG, "attempting to get a token from: " + m_strTokenFactoryURL);
            try {
                // DISCLAIMER: the application developer should implement an authentication mechanism from the mobile app to the
                // server side app so the token factory in the server only provides tokens to authenticated clients
                HttpClient httpClient = new DefaultHttpClient();
                HttpGet httpGet = new HttpGet(m_strTokenFactoryURL);
                HttpResponse executed = httpClient.execute(httpGet);
                InputStream is = executed.getEntity().getContent();
                StringWriter writer = new StringWriter();
                IOUtils.copy(is, writer, "UTF-8");
                String strToken = writer.toString();
                Log.d(TAG, strToken);
                return strToken;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    public String getMailMessage(Message mail) {
        String full_message = "";
        try{
            String subject = mail.getSubject();
            String from = mail.getFrom()[0].toString();
            String body = "";
            if(mail.isMimeType("text/plain"))
            {
                body = (String) mail.getContent();
            }
            else if (mail.isMimeType("text/html")) {
                String temp = (String) mail.getContent();
                body = Jsoup.parse(temp).text();
            }
            else
            if(mail.getContentType().toLowerCase().contains("multipart/"))
            {
                MimeMultipart multipart = (MimeMultipart) mail.getContent();
                body += getTextFromMimeMultipart(multipart);
            }
            //                        else if(emails[i].getContentType().toLowerCase().contains("image/"))
            //                        {
            //                            body += "Mail has an attached image.\n";
            //                        }
            //                        else if(emails[i].getContentType().toLowerCase().contains("audio/"))
            //                        {
            //                            body += "Mail has an attached audio file.\n";
            //                        }
            //                        else if(emails[i].getContentType().toLowerCase().contains("video/"))
            //                        {
            //                            body += "Mail has an attached video file.\n";
            //                        }
            //                        else if(emails[i].getContentType().toLowerCase().contains("pdf"))
            //                        {
            //                            body += "Mail has an attached PDF.\n";
            //                        }
            else
            {
                Log.d(TAG, "Unknown MimeType (Not from multipart): " + mail.getContentType());
            }
            //todo add volume control
            //todo test SST with multiple sentences
            //todo check format of emails i send to myself

            int index = from.indexOf('<'); //Jean van den Berg <jeanvdberg1994@gmail.com>
            if(index > 1 && from.charAt(index - 1) == ' ')
                index--;
            if(index >= 1)
                from = from.substring(0, index);

            full_message = "Mail received from: " + from + ".\nThe subject is: " + subject + ".\nThe contents are as follows. " + body;
            //todo check what happens with synthesizer when the body does not end with a fullstop and there is an attachment that is announced.

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

    public String getTextFromMimeMultipart(MimeMultipart multipart) throws MessagingException, IOException {
        String text = "";
        int j = 0;
        int multipart_length = multipart.getCount();
        if(multipart.getContentType().toLowerCase().contains("multipart/alternative"))
            j = multipart_length - 1;
        //explanation: a multipart/alternative mime type is alternative versions of the same body
        //according to RFC2046, the last bodypart is the most accurate to the original body
        for (; j < multipart_length; j++) {
            BodyPart bodypart = multipart.getBodyPart(j);
            if (bodypart.isMimeType("text/plain")) {
                text += bodypart.getContent() + "\n";
            }
            else if (bodypart.isMimeType("text/html")) {
                String temp = (String) bodypart.getContent();
                String parsedHtml = Jsoup.parse(temp).text();
                text += parsedHtml + "\n";
            }
            else if(bodypart.getContentType().toLowerCase().contains("multipart/")){
                text += getTextFromMimeMultipart((MimeMultipart) bodypart.getContent());
            }
            else if(bodypart.getContentType().toLowerCase().contains("image/"))
            {
                text += "Mail has an attached image.\n";
            }
            else if(bodypart.getContentType().toLowerCase().contains("audio/"))
            {
                text += "Mail has an attached audio file.\n";
            }
            else if(bodypart.getContentType().toLowerCase().contains("video/"))
            {
                text += "Mail has an attached video file.\n";
            }
            else if(bodypart.getContentType().toLowerCase().contains("pdf"))
            {
                text += "Mail has an attached PDF.\n";
            }else
            {
                Log.d(TAG, "Unknown MimeType: " + bodypart.getContentType());
            }
        }
        return text;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch(requestCode) {
            case (SETTINGS_REQ_CODE) : {
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Received broadcast from Settings indicating changed settings.");
                    Bundle b = intent.getExtras();
                    int temp = b.getInt("Unread_email_amnt");
                    if(maximum_unread_emails != temp && temp > 0)
                    {
                        maximum_unread_emails = temp;
                        SharedPreferences settings = getSharedPreferences(PREFS, 0);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putInt("unread_email_amount", maximum_unread_emails);
                        editor.apply();
                    }
                }
                break;
            }
        }
    }

    BroadcastReceiver broadcastReceiverTriggerTTS =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received broadcast to trigger TTS conversion.");
            new Thread() {
                public void run() {
                    try {
                        while (!synthesizer_queue.isEmpty())
                        {
                            if (aState.equals(ApplicationState.IDLE)) {
                                aState = ApplicationState.TTS_CONVERSION;
                                String message = synthesizer_queue.poll();
                                Log.d(TAG, "Reading message:\n" + message);
                                TextToSpeech.sharedInstance().synthesize(message, getApplicationContext());
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    };

    BroadcastReceiver broadcastReceiverEndOfSynthesis =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received broadcast from TTS indicating end of synthesis.");
            aState = ApplicationState.IDLE;
        }
    };

    BroadcastReceiver broadcastReceiverEmailInitialized =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received broadcast indicating email initialization.");
            new Thread() {
                public void run() {
                    try {
                        if (!synthesizer_queue.isEmpty() && aState.equals(ApplicationState.IDLE)) {
                            String message = synthesizer_queue.poll();
                            Log.d(TAG, "Reading message: " + message);
                            TextToSpeech.sharedInstance().synthesize(message, getApplicationContext());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    };

    BroadcastReceiver broadcastReceiverReceivedSms =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle b = intent.getExtras();

            String message = b.getString("message");
            String contact_number = b.getString("contact");
            String contact_name = getContactName(getApplicationContext(), contact_number, true);
            String output = "Text received from ";

            if(!contact_name.equals(""))
            {
                output += contact_name;
            }
            else
                output += contact_number;

            output += " with the following message. " + message;
            Log.d(TAG, output);
            synthesizer_queue.add(output);

            Intent intent_trigger_tts = new Intent("broadcast_trigger_tts");
            sendBroadcast(intent_trigger_tts);
        }
    };

    //todo make sure this works as test with greg lead to reading his number and not name from his sms
    public static String getContactName(Context context, String search_term, boolean isNumber) {
        Uri uri = ContactsContract.Data.CONTENT_URI;
        String[] projection = {ContactsContract.Data.DISPLAY_NAME};
        String name = "";
        String selection;
        if(isNumber)
            selection = ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?";
        else
            selection = ContactsContract.CommonDataKinds.Email.ADDRESS + " LIKE ?";
        String[] selectionArgs = {"%" + search_term + "%"};

        Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);

        if(cursor != null && cursor.getCount() > 0 && cursor.moveToFirst())
        {
            name = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
        }

        if(cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        Log.d(TAG, "Obtained name: " + name);

        return name;
    }

    public static String getContactNumber(Context context, String name) {
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        String contactNumber = "";
        String selection = ContactsContract.Data.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = {"%" + name + "%"};

        Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);

        if(cursor != null && cursor.getCount() > 0 && cursor.moveToFirst())
        {
            contactNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
        }

        if(cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        Log.d(TAG, "Obtained number: " + contactNumber);

        return contactNumber;
    }

    public static String getContactEmail(Context context, String name) {
        Uri uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
        String[] projection = {ContactsContract.CommonDataKinds.Email.ADDRESS};
        String email = "";
        String selection = ContactsContract.Data.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = {"%" + name + "%"};

        Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);

        if(cursor != null && cursor.getCount() > 0 && cursor.moveToFirst())
        {
            email = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS));
        }

        if(cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return email;
    }
}

