package com.project.jean.project;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;

/**
 * Class used to authenticate the provided username and password.
 */
public class MailAuthenticator extends Authenticator {
    private String email_address;
    private String password;

    /**
     * Constructor to set the details.
     * @param email_address Email address of the user.
     * @param password Password of the user.
     */
    public MailAuthenticator(String email_address, String password)
    {
        super();
        this.email_address = email_address;
        this.password = password;
        //Set details.
    }

    /**
     * Authenticates the provided details.
     * @return Result indicating of the provided details are valid.
     */
    @Override
    protected PasswordAuthentication getPasswordAuthentication(){
        return new PasswordAuthentication(email_address, password);
    }
}
