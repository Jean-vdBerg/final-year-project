package com.project.jean.project;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

public class DisplaySettings extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        int amount = extras.getInt("email_amount", 2);
        setContentView(R.layout.activity_settings);
        EditText number = (EditText) findViewById(R.id.editTextEmailAmount);
        number.setText(Integer.toString(amount));
    }

    public void onClickOk(View view) {
        EditText number = (EditText) findViewById(R.id.editTextEmailAmount);
        int amount = Integer.parseInt(number.getText().toString()); //todo save this amount to some file
        Intent intent_settings = new Intent("broadcast_settings_changed");
        intent_settings.putExtra("Unread_email_amnt", amount);
        setResult(RESULT_OK, intent_settings);
        finish();
    }

    public void onClickCheckbox(View view) {
        CheckBox box = (CheckBox) view.findViewById(R.id.checkBoxDeviceChoice);
        if (box.isChecked()) {
            //todo activate local recording and playback
        } else
        {
            //todo activate wireless recording and playback
        }
    }
}
