package com.project.jean.project;

import javax.mail.Message;

public interface MailListener {
    void callback(Message[] emails, int length);
}
