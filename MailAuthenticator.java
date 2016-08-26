package com.ibm.watson.developer_cloud.android.examples;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;

public class MailAuthenticator extends Authenticator {
    private String email_address;
    private String password;
    public MailAuthenticator(String email_address, String password)
    {
        super();
        this.email_address = email_address;
        this.password = password;
    }
    @Override
    protected PasswordAuthentication getPasswordAuthentication(){
        return new PasswordAuthentication(email_address, password);
    }

    public String getEmailAddress()
    {
        return email_address;
    }

    public String getPassword() {
        return password;
    }
}
