package com.project.jean.project;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

/**
 * Displays application settings to the user.
 *
 * Class that displays relevant settings to the user. Users can adjust the settings accordingly.
 * The chosen settings are saved and remember when the application is started again. The settings
 * that can currently be changed includes:
 * Adjusting the amount of emails that are obtained from the user's email address that the user
 * has the option to have read out loud to them.
 */
public class DisplaySettings extends AppCompatActivity {

    private boolean use_stt_api = false;
    private boolean use_custom_playback_device = false;

    /**
     * This method is called when the class is instantiated by the user clicking on the settings
     * button. The current settings for the relevant options are obtained from the sent Intent
     * when the activity was created.
     * @param savedInstanceState Saved state of the Home Page.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras(); //Get the data attached to the intent.
        int amount = extras.getInt("email_amount", 2); //Get the data containing amount of unread emails to be parsed by application (default is 2)
        use_custom_playback_device = extras.getBoolean("custom_playback_device_status", false); //Get the data containing amount of unread emails to be parsed by application (default is 2)
        use_stt_api = extras.getBoolean("stt_api_status", false); //Get the data containing amount of unread emails to be parsed by application (default is 2)
        setContentView(R.layout.activity_settings); //Set the current view to the settings view.
        if(use_custom_playback_device)
        {
            CheckBox box = (CheckBox) findViewById(R.id.checkBoxDeviceChoice);
            if(box != null)
                box.setChecked(true);
        }
        if(use_stt_api)
        {
            CheckBox box = (CheckBox) findViewById(R.id.checkBoxSTTChoice);
            if(box != null)
                box.setChecked(true);
        }
        EditText number = (EditText) findViewById(R.id.editTextEmailAmount);  //Obtain the box containing unread email amount.
        if(number != null)
            number.setText(Integer.toString(amount)); //Edit the amount displayed in the box.
    }

    /**
     * When the Ok button is pressed, an Intent is sent to the Home Page containing all the
     * current settings of the settings page. Additionally, the settings page is closed.
     * @param view The view that is interacted with by the user.
     */
    public void onClickOk(View view) {
        EditText number = (EditText) findViewById(R.id.editTextEmailAmount); //Obtain unread email amount box
        int amount = Integer.parseInt(number.getText().toString()); //Extract the chosen amount.
        Intent intent_settings = new Intent("broadcast_settings_changed"); //Create new intent
        intent_settings.putExtra("Unread_email_amnt", amount); //Add the data to the intent.
        intent_settings.putExtra("Use_custom_playback_device", use_custom_playback_device); //Add the data to the intent.
        intent_settings.putExtra("Use_stt_api", use_stt_api); //Add the data to the intent.
        setResult(RESULT_OK, intent_settings); //Set the result of the activity
        finish(); //Close the view.
    }

    /**
     *
     * @param view The view that is interacted with by the user.
     */
    public void onClickCheckboxDevice(View view) {
        CheckBox box = (CheckBox) view.findViewById(R.id.checkBoxDeviceChoice);
        if (box.isChecked()) {
            //todo activate local recording and playback
            use_custom_playback_device = true;
        } else
        {
            //todo activate wireless recording and playback
            use_custom_playback_device = false;
        }
    }

    public void onClickCheckboxSTT(View view) {
        CheckBox box = (CheckBox) view.findViewById(R.id.checkBoxSTTChoice);
        if (box.isChecked()) {
            //todo activate api
            use_stt_api = true;
        } else
        {
            //todo activate dictionary + hmm
            use_stt_api = false;
        }
    }
}
