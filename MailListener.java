package com.project.jean.project;

import javax.mail.Message;

/**
 * An interface that is used by the Home Page to create an interface to the Mail Handler in order
 * for the Mail Handler class to send the details of received emails to the Home Page.
 */
public interface MailListener {
    /**
     * Function that is called by the Mail Handler to send the data to the Home Page.
     * @param emails An array of email Messages.
     * @param length The amount of emails in the array.
     */
    void callback(Message[] emails, int length);
}
