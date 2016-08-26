 /**
  * © Copyright IBM Corporation 2015
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  **/

package com.ibm.watson.developer_cloud.android.examples;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;

import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.app.ActionBar;
import android.app.Fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

// IBM Watson SDK
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.dto.SpeechConfiguration;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.ISpeechDelegate;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.android.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.android.speech_common.v1.TokenProvider;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;

public class MainActivity extends Activity {
    TextView textTTS;
    ActionBar.Tab tabSTT, tabTTS;
    FragmentTabSTT fragmentTabSTT = new FragmentTabSTT();
    FragmentTabTTS fragmentTabTTS = new FragmentTabTTS();

    private static final String TAG = "MainActivity";

    static Queue<String> synthesizer_queue = new LinkedList<>();

    static MailHandler mailSender = new MailHandler("jeanvdberg1994@gmail.com", "<password>");

    int maximum_unread_emails = 10; //todo allow user to change this value in settings

    public static class FragmentTabSTT extends Fragment implements ISpeechDelegate {

        // session recognition results
        private static String mRecognitionResults = "";

        private enum ConnectionState {
            IDLE, CONNECTING, CONNECTED
        }

        ConnectionState mState = ConnectionState.IDLE;
        public View mView = null;
        public Context mContext = null;
        public JSONObject jsonModels = null;
        private Handler mHandler = null;

        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            mView = inflater.inflate(R.layout.tab_stt, container, false);
            mContext = getActivity().getApplicationContext();
            mHandler = new Handler();

            setText();
            if (!initSTT()) {
                displayResult("Error: no authentication credentials/token available, please enter your authentication information", false);
                return mView;
            }

            if (jsonModels == null) {
                jsonModels = new STTCommands().doInBackground();
                if (jsonModels == null) {
                    displayResult("Please, check internet connection.", false);
                    return mView;
                }
            }
            addItemsOnSpinnerModels();

            displayStatus("please, press the button to start speaking");

            Button buttonRecord = (Button)mView.findViewById(R.id.buttonRecord);
            buttonRecord.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {

                    if (mState == ConnectionState.IDLE) {
                        mState = ConnectionState.CONNECTING;
                        Log.d(TAG, "onClickRecord: IDLE -> CONNECTING");
                        Spinner spinner = (Spinner)mView.findViewById(R.id.spinnerModels);
                        spinner.setEnabled(false);
                        mRecognitionResults = "";
                        displayResult(mRecognitionResults, false);
                        ItemModel item = (ItemModel)spinner.getSelectedItem();
                        SpeechToText.sharedInstance().setModel(item.getModelName());
                        displayStatus("connecting to the STT service...");
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
                        Spinner spinner = (Spinner)mView.findViewById(R.id.spinnerModels);
                        spinner.setEnabled(true);
                        SpeechToText.sharedInstance().stopRecognition(); //uses OnMessage() function to display results
                        setButtonState(false);
                    }//todo find way to detect end of speaking then use .stoprecognition function
                }
            });

            return mView;
        }

        private String getModelSelected() {

            Spinner spinner = (Spinner)mView.findViewById(R.id.spinnerModels);
            ItemModel item = (ItemModel)spinner.getSelectedItem();
            return item.getModelName();
        }

        public URI getHost(String url){
            try {
                return new URI(url);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            return null;
        }

        // initialize the connection to the Watson STT service
        private boolean initSTT() {

            // DISCLAIMER: please enter your credentials or token factory in the lines below
            String username = getString(R.string.STTUsername);
            String password = getString(R.string.STTPassword);

            String tokenFactoryURL = getString(R.string.defaultTokenFactory);
            String serviceURL = "wss://stream.watsonplatform.net/speech-to-text/api";

            SpeechConfiguration sConfig = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_OGGOPUS);
            //SpeechConfiguration sConfig = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_DEFAULT);

            SpeechToText.sharedInstance().initWithContext(this.getHost(serviceURL), getActivity().getApplicationContext(), sConfig);

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

        protected void setText() {

            Typeface roboto = Typeface.createFromAsset(getActivity().getApplicationContext().getAssets(), "font/Roboto-Bold.ttf");
            Typeface notosans = Typeface.createFromAsset(getActivity().getApplicationContext().getAssets(), "font/NotoSans-Regular.ttf");

            // title
            TextView viewTitle = (TextView)mView.findViewById(R.id.title);
            String strTitle = getString(R.string.sttTitle);
            SpannableStringBuilder spannable = new SpannableStringBuilder(strTitle);
            spannable.setSpan(new AbsoluteSizeSpan(47), 0, strTitle.length(), 0);
            spannable.setSpan(new CustomTypefaceSpan("", roboto), 0, strTitle.length(), 0);
            viewTitle.setText(spannable);
            viewTitle.setTextColor(0xFF325C80);

            // instructions
            TextView viewInstructions = (TextView)mView.findViewById(R.id.instructions);
            String strInstructions = getString(R.string.sttInstructions);
            SpannableString spannable2 = new SpannableString(strInstructions);
            spannable2.setSpan(new AbsoluteSizeSpan(20), 0, strInstructions.length(), 0);
            spannable2.setSpan(new CustomTypefaceSpan("", notosans), 0, strInstructions.length(), 0);
            viewInstructions.setText(spannable2);
            viewInstructions.setTextColor(0xFF121212);
        }

        public class ItemModel {

            private JSONObject mObject = null;

            public ItemModel(JSONObject object) {
                mObject = object;
            }

            public String toString() {
                try {
                    return mObject.getString("description");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            public String getModelName() {
                try {
                    return mObject.getString("name");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }

        protected void addItemsOnSpinnerModels() {

            Spinner spinner = (Spinner)mView.findViewById(R.id.spinnerModels);
            int iIndexDefault = 0;

            JSONObject obj = jsonModels;
            ItemModel [] items = null;
            try {
                JSONArray models = obj.getJSONArray("models");

                // count the number of Broadband models (narrowband models will be ignored since they are for telephony data)
                Vector<Integer> v = new Vector<>();
                for (int i = 0; i < models.length(); ++i) {
                    if (models.getJSONObject(i).getString("name").indexOf("Broadband") != -1) {
                        v.add(i);
                    }
                }
                items = new ItemModel[v.size()];
                int iItems = 0;
                for (int i = 0; i < v.size() ; ++i) {
                    items[iItems] = new ItemModel(models.getJSONObject(v.elementAt(i)));
                    if (models.getJSONObject(v.elementAt(i)).getString("name").equals(getString(R.string.modelDefault))) {
                        iIndexDefault = iItems;
                    }
                    ++iItems;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if (items != null) {
                ArrayAdapter<ItemModel> spinnerArrayAdapter = new ArrayAdapter<ItemModel>(getActivity(), android.R.layout.simple_spinner_item, items);
                spinner.setAdapter(spinnerArrayAdapter);
                spinner.setSelection(iIndexDefault);
            }
        }

        public void displayResult(final String result, final boolean complete) {
            final Runnable runnableUi = new Runnable(){
                @Override
                public void run() {
                    TextView textResult = (TextView)mView.findViewById(R.id.textResult);
                    if(complete) {
                        //process string here to find keywords: Text, Mail, Phone numbers (10 digits) and Names of Contacts
                        String textArray[] = result.split(" ");
                        if(textArray[0].equalsIgnoreCase("text"))
                        {
                            String contact_name = textArray[1];

                            Log.d(TAG, "Attempting to obtain number of contact");

                            String phone_num = getContactNumber(getActivity().getApplicationContext(), contact_name);

                            Log.d(TAG, "Obtained number: " + phone_num);

                            //todo check if getContactNumber works
                            //if it doesn't work, try move functions to this thread

                            if(!phone_num.equals("")) {
                                String msg = "";
                                String number = "";
                                int length = textArray.length;

                                for (int i = 2; i < length - 1; i++) {
                                    msg += textArray[i] + " ";
                                }
                                msg += textArray[length - 1];

                                SMSSender smsSender = new SMSSender();
                                smsSender.sendSMS(number, msg);
                            }
                            else {
                                //unknown contact name or incorrectly translated
                                //todo inform user about error, ignore the queue if the queue is not empty
                            }
                        }
                        else
                        if(textArray[0].equalsIgnoreCase("mail"))
                        {
                            String contact = textArray[1];

                            Log.d(TAG, "Attempting to obtain email address of contact");

                            String email_address = getContactEmail(getActivity().getApplicationContext(), contact);

                            Log.d(TAG, "Obtained email address: " + email_address);

                            //todo check if getContactEmail works
                            //if it doesn't work, try move functions to this thread

                            //TEXT <NAME> <CONTENTS>
                            //MAIL <NAME> <SUBJECT> END SUBJECT <BODY>
                            //assumes 1 word names

                            if(email_address != "") {
                                String msg = "";
                                String subject = "";
                                int msg_start = 2;

                                int length = textArray.length;
                                for (int i = 2; i < length; i++) {
                                    if(textArray[i].equalsIgnoreCase("end") && textArray[i].equalsIgnoreCase("subject"))
                                    {
                                        msg_start = i + 2;
                                        for (int j = 2; j < msg_start; j++) {
                                            subject += textArray[i] + " ";
                                        }
                                        break;
                                    }
                                }

                                for (int i = msg_start; i < length - 1; i++) {
                                    msg += textArray[i] + " ";
                                }
                                msg += textArray[length - 1];

                                try
                                {
                                    mailSender.sendMail(email_address, subject, msg);
                                }
                                catch(MessagingException e)
                                {
                                    Log.e(TAG, "Messaging Exception when sending email.");
                                    e.printStackTrace();
                                }
                                catch (Exception e)
                                {
                                    Log.e(TAG, "Unknown Exception when sending email.");
                                    e.printStackTrace();
                                }

                            }
                            else {
                                //unknown contact name or incorrectly translated
                                //todo inform user about error, ignore the queue if the queue is not empty
                            }
                        }
                        else {
                            //todo inform user that command was not understood, ignore the queue if the queue is not empty
                            //could also check if a number was spoken manually
                        }
                    }

                    textResult.setText(result);
                }
            };

            new Thread(){
                public void run(){
                    mHandler.post(runnableUi);
                }
            }.start();
        }

        public void displayStatus(final String status) {
            /*final Runnable runnableUi = new Runnable(){
                @Override
                public void run() {
                    TextView textResult = (TextView)mView.findViewById(R.id.sttStatus);
                    textResult.setText(status);
                }
            };
            new Thread(){
                public void run(){
                    mHandler.post(runnableUi);
                }
            }.start();*/
        }

        public void setButtonLabel(final int buttonId, final String label) {
            final Runnable runnableUi = new Runnable(){
                @Override
                public void run() {
                    Button button = (Button)mView.findViewById(buttonId);
                    button.setText(label);
                }
            };
            new Thread(){
                public void run(){
                    mHandler.post(runnableUi);
                }
            }.start();
        }

        public void setButtonState(final boolean bRecording) {

            final Runnable runnableUi = new Runnable(){
                @Override
                public void run() {
                    int iDrawable = bRecording ? R.drawable.button_record_stop : R.drawable.button_record_start;
                    Button btnRecord = (Button)mView.findViewById(R.id.buttonRecord);
                    btnRecord.setBackground(getResources().getDrawable(iDrawable));
                }
            };
            new Thread(){
                public void run(){
                    mHandler.post(runnableUi);
                }
            }.start();
        }

        // delegates ----------------------------------------------

        public void onOpen() {
            Log.d(TAG, "onOpen");
            displayStatus("successfully connected to the STT service");
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
            displayStatus("connection closed");
            setButtonLabel(R.id.buttonRecord, "Record");
            mState = ConnectionState.IDLE;
        }

        public void onMessage(String message) {

            Log.d(TAG, "onMessage, message: " + message);
            try {
                JSONObject jObj = new JSONObject(message);
                // state message
                if(jObj.has("state")) {
                    Log.d(TAG, "Status message: " + jObj.getString("state"));
                }
                // results message
                else if (jObj.has("results")) {
                    //if has result
                    Log.d(TAG, "Results message: ");
                    JSONArray jArr = jObj.getJSONArray("results");
                    for (int i=0; i < jArr.length(); i++) {
                        JSONObject obj = jArr.getJSONObject(i);
                        JSONArray jArr1 = obj.getJSONArray("alternatives");
                        String str = jArr1.getJSONObject(0).getString("transcript");
                        // remove whitespaces if the language requires it
                        String model = this.getModelSelected();
                        if (model.startsWith("ja-JP") || model.startsWith("zh-CN")) {
                            str = str.replaceAll("\\s+","");
                        }
                        String strFormatted = Character.toUpperCase(str.charAt(0)) + str.substring(1);
                        if (obj.getString("final").equals("true")) {

                            //todo
                            mState = ConnectionState.IDLE;
                            Log.d(TAG, "onClickRecord: CONNECTED -> IDLE");
                            Spinner spinner = (Spinner)mView.findViewById(R.id.spinnerModels);
                            spinner.setEnabled(true);
                            SpeechToText.sharedInstance().stopRecognition(); //uses OnMessage() function to display results
                            setButtonState(false);
                            //todo: check how this operates at home

                            String stopMarker = (model.startsWith("ja-JP") || model.startsWith("zh-CN")) ? "。" : ". ";
                            mRecognitionResults += strFormatted.substring(0,strFormatted.length()-1) + stopMarker;


                            displayResult(mRecognitionResults, true);
                        } else {
                            displayResult(mRecognitionResults + strFormatted, false);
                        }
                        break;
                    }
                } else {
                    displayResult("unexpected data coming from stt server: \n" + message, false);
                }

            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON");
                e.printStackTrace();
            }
        }

        public void onAmplitude(double amplitude, double volume) {
            //Logger.e(TAG, "amplitude=" + amplitude + ", volume=" + volume);
        }
    }

    public static class FragmentTabTTS extends Fragment {

        public View mView = null;
        public Context mContext = null;
        public JSONObject jsonVoices = null;
        private Handler mHandler = null;

        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            Log.d(TAG, "onCreateTTS");
            mView = inflater.inflate(R.layout.tab_tts, container, false);
            mContext = getActivity().getApplicationContext();

            setText();
            if (!initTTS()) {
                TextView viewPrompt = (TextView) mView.findViewById(R.id.prompt);
                viewPrompt.setText("Error: no authentication credentials or token available, please enter your authentication information");
                return mView;
            }

            if (jsonVoices == null) {
                jsonVoices = new TTSCommands().doInBackground();
                if (jsonVoices == null) {
                    return mView;
                }
            }
            addItemsOnSpinnerVoices();
            updatePrompt(getString(R.string.voiceDefault));

            Spinner spinner = (Spinner) mView.findViewById(R.id.spinnerVoices);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {

                    Log.d(TAG, "setOnItemSelectedListener");
                    final Runnable runnableUi = new Runnable() {
                        @Override
                        public void run() {
                            FragmentTabTTS.this.updatePrompt(FragmentTabTTS.this.getSelectedVoice());
                        }
                    };
                    new Thread() {
                        public void run() {
                            mHandler.post(runnableUi);
                        }
                    }.start();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                    // your code here
                }
            });

            mHandler = new Handler();

            Log.d(TAG, "Starting reading of unread emails."); //todo: change READ to READ_WRITE in smsreceiver class else you probably reread the same emails, want to set them to read after reading

            while(!synthesizer_queue.isEmpty())
            {
                String message = synthesizer_queue.poll();
                TextToSpeech.sharedInstance().synthesize(message); //todo: test how well this works
            }

            return mView;
        }

        public URI getHost(String url){
            try {
                return new URI(url);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            return null;
        }

        private boolean initTTS() {

            // DISCLAIMER: please enter your credentials or token factory in the lines below

            String username = getString(R.string.TTSUsername);
            String password = getString(R.string.TTSPassword);
            String tokenFactoryURL = getString(R.string.defaultTokenFactory);
            String serviceURL = "https://stream.watsonplatform.net/text-to-speech/api";

            TextToSpeech.sharedInstance().initWithContext(this.getHost(serviceURL));

            // token factory is the preferred authentication method (service credentials are not distributed in the client app)
            if (!tokenFactoryURL.equals(getString(R.string.defaultTokenFactory))) {
                TextToSpeech.sharedInstance().setTokenProvider(new MyTokenProvider(tokenFactoryURL));
            }
            // Basic Authentication
            else if (!username.equals(getString(R.string.defaultUsername))) {
                TextToSpeech.sharedInstance().setCredentials(username, password);
            } else {
                // no authentication method available
                return false;
            }

            //TextToSpeech.sharedInstance().setVoice(getString(R.string.voiceDefault));
            TextToSpeech.sharedInstance().setVoice("en-US_MichaelVoice");

            return true;
        }

        protected void setText() {

            Typeface roboto = Typeface.createFromAsset(getActivity().getApplicationContext().getAssets(), "font/Roboto-Bold.ttf");
            Typeface notosans = Typeface.createFromAsset(getActivity().getApplicationContext().getAssets(), "font/NotoSans-Regular.ttf");

            TextView viewTitle = (TextView)mView.findViewById(R.id.title);
            String strTitle = getString(R.string.ttsTitle);
            SpannableString spannable = new SpannableString(strTitle);
            spannable.setSpan(new AbsoluteSizeSpan(47), 0, strTitle.length(), 0);
            spannable.setSpan(new CustomTypefaceSpan("", roboto), 0, strTitle.length(), 0);
            viewTitle.setText(spannable);
            viewTitle.setTextColor(0xFF325C80);

            TextView viewInstructions = (TextView)mView.findViewById(R.id.instructions);
            String strInstructions = getString(R.string.ttsInstructions);
            SpannableString spannable2 = new SpannableString(strInstructions);
            spannable2.setSpan(new AbsoluteSizeSpan(20), 0, strInstructions.length(), 0);
            spannable2.setSpan(new CustomTypefaceSpan("", notosans), 0, strInstructions.length(), 0);
            viewInstructions.setText(spannable2);
            viewInstructions.setTextColor(0xFF121212);
        }

        public class ItemVoice {

            public JSONObject mObject = null;

            public ItemVoice(JSONObject object) {
                mObject = object;
            }

            public String toString() {
                try {
                    return mObject.getString("name");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }

        public void addItemsOnSpinnerVoices() {

            Spinner spinner = (Spinner)mView.findViewById(R.id.spinnerVoices);
            int iIndexDefault = 0;

            JSONObject obj = jsonVoices;
            ItemVoice [] items = null;
            try {
                JSONArray voices = obj.getJSONArray("voices");
                items = new ItemVoice[voices.length()];
                for (int i = 0; i < voices.length(); ++i) {
                    items[i] = new ItemVoice(voices.getJSONObject(i));
                    if (voices.getJSONObject(i).getString("name").equals(getString(R.string.voiceDefault))) {
                        iIndexDefault = i;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (items != null) {
                ArrayAdapter<ItemVoice> spinnerArrayAdapter = new ArrayAdapter<ItemVoice>(getActivity(), android.R.layout.simple_spinner_item, items);
                spinner.setAdapter(spinnerArrayAdapter);
                spinner.setSelection(iIndexDefault);
            }
        }

        // return the selected voice
        public String getSelectedVoice() {

            // return the selected voice
            Spinner spinner = (Spinner)mView.findViewById(R.id.spinnerVoices);
            ItemVoice item = (ItemVoice)spinner.getSelectedItem();
            String strVoice = null;
            try {
                strVoice = item.mObject.getString("name");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return strVoice;
        }

        // update the prompt for the selected voice
        public void updatePrompt(final String strVoice) {

            TextView viewPrompt = (TextView)mView.findViewById(R.id.prompt);
            if (strVoice.startsWith("en-US") || strVoice.startsWith("en-GB")) {
                viewPrompt.setText(getString(R.string.ttsEnglishPrompt));
            } else if (strVoice.startsWith("es-ES")) {
                viewPrompt.setText(getString(R.string.ttsSpanishPrompt));
            } else if (strVoice.startsWith("fr-FR")) {
                viewPrompt.setText(getString(R.string.ttsFrenchPrompt));
            } else if (strVoice.startsWith("it-IT")) {
                viewPrompt.setText(getString(R.string.ttsItalianPrompt));
            } else if (strVoice.startsWith("de-DE")) {
                viewPrompt.setText(getString(R.string.ttsGermanPrompt));
            } else if (strVoice.startsWith("ja-JP")) {
                viewPrompt.setText(getString(R.string.ttsJapanesePrompt));
            }
        }
    }

    public class MyTabListener implements ActionBar.TabListener {

    Fragment fragment;
    public MyTabListener(Fragment fragment) {
        this.fragment = fragment;
    }

    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
        ft.replace(R.id.fragment_container, fragment);
    }

    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
        ft.remove(fragment);
    }

    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
        // nothing done here
    }
    }

    public static class STTCommands extends AsyncTask<Void, Void, JSONObject> {

        protected JSONObject doInBackground(Void... none) {

            return SpeechToText.sharedInstance().getModels();
        }
    }

    public static class TTSCommands extends AsyncTask<Void, Void, JSONObject> {

        protected JSONObject doInBackground(Void... none) {

            return TextToSpeech.sharedInstance().getVoices();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Strictmode needed to run the http/wss request for devices > Gingerbread
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.GINGERBREAD) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        //setContentView(R.layout.activity_main);
        setContentView(R.layout.activity_tab_text);

        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        tabSTT = actionBar.newTab().setText("Speech to Text");
        tabTTS = actionBar.newTab().setText("Text to Speech");

        tabSTT.setTabListener(new MyTabListener(fragmentTabSTT));
        tabTTS.setTabListener(new MyTabListener(fragmentTabTTS));

        actionBar.addTab(tabSTT);
        actionBar.addTab(tabTTS);

        //actionBar.setStackedBackgroundDrawable(new ColorDrawable(Color.parseColor("#B5C0D0")));

        registerReceiver(broadcastReceiver, new IntentFilter("broadcast_sms"));

        try{
            Message[] emails = mailSender.getUnreadMail();
            int unread_count = emails.length;

            int maximum = (unread_count > maximum_unread_emails) ? maximum_unread_emails : unread_count;
            int minimum = (emails.length > maximum_unread_emails) ? (unread_count - maximum_unread_emails - 1) : 0;

            String unread_email_msg = "You have " + unread_count + " unread emails.";
            unread_email_msg += "The latest " + maximum + " emails shall now be read out loud";
            //todo: allow user to say yes or no if they want this to occur

            synthesizer_queue.add(unread_email_msg);

            for (int i = unread_count - 1; i > minimum; i--) {
                String message = getMailMessage(emails[i]);
                //todo test queue thoroughly
                synthesizer_queue.add(message);
            }

            Iterator it = synthesizer_queue.iterator();

            Log.d(TAG, "Queue contains " + synthesizer_queue.size() + " elements.");

            while(it.hasNext())
            {
                String iteratorValue = (String) it.next();
                Log.d(TAG, "Current msg:\n" + iteratorValue);
            }
        }
        catch(MessagingException e)
        {
            Log.e(TAG, "Messaging Exception when fetching unread mail.");
            e.printStackTrace();
        }

        mailSender.setListener(new MyListener() {
            @Override
            public void callback(Message[] emails, int length) {
                for (int i = 0; i < length; i++) {
                    String message = getMailMessage(emails[i]);
                    synthesizer_queue.add(message);
                }
            }
        });
        mailSender.getIncomingMail();
    }

    public String getMailMessage(Message mail)
    {
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

            int index = from.indexOf('<'); //Jean van den Berg <jeanvdberg1994@gmail.com>
            if(from.charAt(index - 1) == ' ') {
                index--;
            }
            from = from.substring(0, index);

            full_message = "Mail received from: " + from + ".\nThe subject is: " + subject + ".\nThe contents are as follows. " + body;
            //todo check what happens with synthezier when the body does not end with a fullstop and there is an attachment that is announced.

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

    public String getTextFromMimeMultipart(MimeMultipart multipart) throws MessagingException, IOException
    {
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
    protected void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(broadcastReceiver, new IntentFilter("broadcast_sms"));
    }

    BroadcastReceiver broadcastReceiver =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Bundle b = intent.getExtras();

            String message = b.getString("message");
            String contact_number = b.getString("contact");

            Log.d(TAG, "Received message in main activity: " + message);
            Log.d(TAG, "Received message from phone number: " + contact_number);

            String contact_name = getContactName(getApplicationContext(), contact_number, true);

            Log.d(TAG, "Phone number identified as: " + contact_name);

            String output = "Text received from ";

            if(!contact_name.equals(""))
            {
                output += contact_name;
            }
            else
                output += contact_number;

            //String number = getContactNumber(getApplicationContext(), contact_name);
            //String email = getContactEmail(getApplicationContext(), contact_name);
            //String name_test = getContactName(getApplicationContext(), email, false);
            //output += " (" + number + ", " + email + ", " + name_test;

            output += " with the following message: " + message;

            Log.d(TAG, output);

            synthesizer_queue.add(output);

            //TextToSpeech.sharedInstance().setVoice("en-US_MichaelVoice");

            //TextToSpeech.sharedInstance().synthesize(output);
        }
    };

    public static String getContactName(Context context, String search_term, boolean isNumber)
    {
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

    public static String getContactNumber(Context context, String name)
    {
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

    public static String getContactEmail(Context context, String name)
    {
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

    Log.d(TAG, "Obtained email address: " + email);

    return email;
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public void playTTS(View view) throws JSONException {

        // TextToSpeech.sharedInstance().setVoice(fragmentTabTTS.getSelectedVoice());
        //TextToSpeech.sharedInstance().setVoice("en-US_MichaelVoice");
        //Log.d(TAG, fragmentTabTTS.getSelectedVoice());

        //Get text from text box
        textTTS = (TextView)fragmentTabTTS.mView.findViewById(R.id.prompt);
        String ttsText=textTTS.getText().toString();
        Log.d(TAG, ttsText);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(textTTS.getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);

        //Call the sdk function
        //TextToSpeech.sharedInstance().synthesize(ttsText);
        //sendEmail("Test", "Hi, this is a test email, good luck.", "jeanvdberg1994@gmail.com");
        //readEmails();
    }

//    public static void sendEmail(final String subject, final String message, final String recipient) {
//        new Thread(){
//            public void run(){
//                try {
//                    mailSender.sendMail(recipient, subject, message);
//                    Log.d(TAG, "Mail sent to " + recipient);
//                }
//                catch(Exception e)
//                {
//                    e.printStackTrace();
//                }
//            }
//        }.start();
//    }

//    public void readEmails()
//    {
//        new Thread(){
//            public void run(){
//                try {
//                    //MailHandler mailSender = new MailHandler("jeanvdberg1994@gmail.com", "sometimes5567");
//                    Message[] test = mailSender.getUnreadMail();
//                    int len = test.length;
//                    Log.d(TAG, "Obtained emails in inbox");
//                    Log.d(TAG, "Inbox contains " + len + " emails");
//                    if(len > 0)
//                        Log.d(TAG, "Last emails subject: " + test[len - 1].getSubject());
//
//                    int amntEmails = 10;
//                    if(len < 10)
//                        amntEmails = len;
//
//                    TextToSpeech.sharedInstance().setVoice("en-US_MichaelVoice");
//
//                    for (int i = len - 1; i > len - amntEmails; i--) {
//                        //Log.d(TAG, "Subject: " + test[i].getSubject());
//                        Log.d(TAG, "Email: " + test[i].toString());
//                        TextToSpeech.sharedInstance().synthesize(test[i].toString());
//                    }
//                }
//                catch(MessagingException e)
//                {
//                    Log.e(TAG, "Messaging Exception when fetching unread mail.");
//                    e.printStackTrace();
//                }
//
//            }
//        }.start();
//    }
}
