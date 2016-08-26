package com.project.jean.project;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.View;

public class DisplayHelp extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_help);
    }

    public void onClick(View view)
    {
        //SmsManager sms = SmsManager.getDefault();
        //sms.sendTextMessage("5556", null, "hiiiiii", null, null);
        finish();
    }
}
