package com.ibm.watson.developer_cloud.android.examples;

import javax.mail.Message;

public interface MyListener {
    void callback(Message[] emails, int length);
}
