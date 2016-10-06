package com.project.jean.project;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * The SMSReceiver class directly access texts that are received by the smartphone. Details are sent
 * to the main activity once extracted.
 */
public class SMSReceiver extends BroadcastReceiver
{
    private static final String TAG = "SMS_Receiver";

    /**
     * The method that is called when the smartphone receives an SMS.
     *
     * The function ensures a SMS has been received then extracts the important data from the SMS.
     * The function obtains the message contents and contact number of the sender. The details are
     * attached to an intent and broadcasted to a broadcast receiver in the Home Page.
     * @param context The context of the application.
     * @param intent The intent containing the details pertaining to the SMS.
     */
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if(intent.getAction().equals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))
        {
            Log.d(TAG, "SMS Received");
            Bundle bundle = intent.getExtras(); //Extract the texts from the intent
            SmsMessage[] messages = null;
            String str = "";
            if (bundle != null) //Ensure bundle contains an SMS
            {
                Log.d(TAG, "Extracting SMS");
                Object[] pdus = (Object[]) bundle.get("pdus"); //Obtain the pdus from the bundle
                messages = new SmsMessage[pdus.length]; //Define the size of the array
                for (int i = 0; i < messages.length; i++){ //Loop through all the pdus
                    messages[i] = SmsMessage.createFromPdu((byte[])pdus[i]); //Create an SmsMessage from the pdus //deprecated version of createFromPdu
                    str += messages[i].getMessageBody(); //Obtain the contents of the SmsMessage //returns null if non-text based sms
                }

                String contact_number = messages[0].getOriginatingAddress(); //Obtain number of sender
                if(contact_number.startsWith("+27")) { //probably redundant
                    contact_number = "0" + contact_number.substring(3);
                }

                Intent broadcast_intent = new Intent("broadcast_sms");
                broadcast_intent.putExtra("message", str); //Add details to the intent
                broadcast_intent.putExtra("contact", contact_number);
                context.sendBroadcast(broadcast_intent); //Send the broadcast
            }
        }
        else
        {
            Log.d(TAG, "Incorrect action received");//Should never occur
        }
    }
}
