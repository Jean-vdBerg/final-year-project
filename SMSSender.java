package com.project.jean.project;

import android.telephony.SmsManager;
import android.util.Log;

/**
 * The SMSSender class is used to send texts.
 */
public class SMSSender
{
    /**
     * The function is called to send an SMS to the intended recipient with the specified message
     * contents.
     * @param phoneNumber A string containing the recipients phone number.
     * @param message A string containing the message for the recipient.
     */
    public void sendSMS(String phoneNumber, String message)
    {
//        String SENT = "SMS_SENT";
//        String DELIVERED = "SMS_DELIVERED";
//
//        PendingIntent sentPI = PendingIntent.getBroadcast(this, 0,
//                new Intent(SENT), 0);
//
//        PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0,
//                new Intent(DELIVERED), 0);
//
//        //---when the SMS has been sent---
//        registerReceiver(new BroadcastReceiver(){
//            @Override
//            public void onReceive(Context arg0, Intent arg1) {
//                switch (getResultCode())
//                {
//                    case Activity.RESULT_OK:
//                        Toast.makeText(getBaseContext(), "SMS sent",
//                                Toast.LENGTH_SHORT).show();
//                        break;
//                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
//                        Toast.makeText(getBaseContext(), "Generic failure",
//                                Toast.LENGTH_SHORT).show();
//                        break;
//                    case SmsManager.RESULT_ERROR_NO_SERVICE:
//                        Toast.makeText(getBaseContext(), "No service",
//                                Toast.LENGTH_SHORT).show();
//                        break;
//                    case SmsManager.RESULT_ERROR_NULL_PDU:
//                        Toast.makeText(getBaseContext(), "Null PDU",
//                                Toast.LENGTH_SHORT).show();
//                        break;
//                    case SmsManager.RESULT_ERROR_RADIO_OFF:
//                        Toast.makeText(getBaseContext(), "Radio off",
//                                Toast.LENGTH_SHORT).show();
//                        break;
//                }
//            }
//        }, new IntentFilter(SENT));
//
//        //---when the SMS has been delivered---
//        registerReceiver(new BroadcastReceiver(){
//            @Override
//            public void onReceive(Context arg0, Intent arg1) {
//                switch (getResultCode())
//                {
//                    case Activity.RESULT_OK:
//                        Toast.makeText(getBaseContext(), "SMS delivered",
//                                Toast.LENGTH_SHORT).show();
//                        break;
//                    case Activity.RESULT_CANCELED:
//                        Toast.makeText(getBaseContext(), "SMS not delivered",
//                                Toast.LENGTH_SHORT).show();
//                        break;
//                }
//            }
//        }, new IntentFilter(DELIVERED));

        SmsManager sms = SmsManager.getDefault(); //create a SmsManager object.
        sms.sendTextMessage(phoneNumber, null, message, null, null); //Send the text message.
        Log.d("SMSSender", "Sms sent");
        //sms.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI);
    }
}
