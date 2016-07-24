package com.ibm.watson.developer_cloud.android.examples;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

public class SMSReceiver extends BroadcastReceiver
{
    private static final String TAG = "SMS_Receiver";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if(intent.getAction().equals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))
        {
            Log.d(TAG, "SMS Received");
            Bundle bundle = intent.getExtras();
            SmsMessage[] messages = null;
            String str = "";
            if (bundle != null)
            {
                Log.d(TAG, "Extracting SMS");
                Object[] pdus = (Object[]) bundle.get("pdus");
                messages = new SmsMessage[pdus.length];
                for (int i = 0; i < messages.length; i++){
                    messages[i] = SmsMessage.createFromPdu((byte[])pdus[i]); //deprecated version of createFromPdu
                    str += messages[i].getMessageBody(); //returns null if non-text based sms
                }

                String contact_number = messages[0].getOriginatingAddress();

                if(contact_number.startsWith("+27")) {
                    contact_number = "0" + contact_number.substring(3);
                }

                Intent broadcast_intent = new Intent("broadcast_sms");
                broadcast_intent.putExtra("message", str);
                broadcast_intent.putExtra("contact", contact_number);
                context.sendBroadcast(broadcast_intent);

                Toast.makeText(context, str, Toast.LENGTH_LONG).show();
            }
        }
        else
        {
            Log.d(TAG, "Incorrect action received");
        }
    }
}
