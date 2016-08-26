package com.ibm.watson.developer_cloud.android.examples;

import javax.mail.Message;

/**
 * Created by jeanv_000 on 2016/07/26.
 */
public interface MyListener {
    void callback(Message[] emails, int length);
}
