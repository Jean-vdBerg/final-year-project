package com.project.jean.project;

import android.util.Log;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.FolderClosedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.MessageCountListener;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;

/**
 * Class that is responsible for interacting with SMTP and IMAP servers in order to both send
 * and receive emails programmatically. The class extracts unread emails, can send emails, and can
 * receive incoming emails when the application is active.
 */
public class MailHandler{

    private String smtp_host = "smtp.gmail.com"; //SMTP server to connect to.
    private String mail_host = "imap.gmail.com"; //IMAP server to connect to.
    private Session imap_session;
    private Session smtp_session;
    private Folder inbox;
    private Store store;
    private static String email_address = "";
    private static String password = "";

    private static final String TAG = "MailHandler";

    private MailListener listener;

    static {
        Security.addProvider(new JSSEProvider()); //Adds more security to the mail handler class.
    }

    /**
     * Constructor which initiates the connection sessions to the SMTP and IMAP servers. The
     * constructor requires a valid Gmail email address and password to connect.
     * @param new_email_address Email address used for login.
     * @param new_password Password used for login.
     */
    public MailHandler(String new_email_address, String new_password) {
        email_address = new_email_address;
        password = new_password;
        MailAuthenticator authenticator = new MailAuthenticator(email_address, password);
        //Authenticates the provides email address and password.

        Properties props_smtp = new Properties();
        props_smtp.setProperty("mail.smtp.auth", "true");
        props_smtp.setProperty("mail.smtp.starttls.enable", "true");
        props_smtp.setProperty("mail.smtp.host", smtp_host); //Set host server.
        props_smtp.setProperty("mail.smtp.port", "587");
        //Set properties of the connection.

        smtp_session = Session.getDefaultInstance(props_smtp, authenticator);
        //Connects to the SMTP server using the provided details.

        Properties props_imap = new Properties();
        props_imap.setProperty("mail.store.protocol", "imaps");
        props_imap.setProperty("mail.imaps.host", mail_host); //Set host server.
        props_imap.setProperty("mail.imaps.port", "993");
        props_imap.setProperty("mail.imaps.timeout", "100000"); //Long timeout to listen to inbox.
        //Set properties of the connection to the IMAP server.

        imap_session = Session.getInstance(props_imap);
        //Connects to the IMAP server using the provided details.
    }

    /**
     * Sets the listener of the Mail Handler class to the listener instantiated in the Home Page.
     * @param listener Listener created by the Home Page that is used to send the email messages
     *                 to the Home Page.
     */
    public void setListener(MailListener listener)
    {
        this.listener = listener;
    }

    /**
     * Function that is called in order to run a new thread that is responsible for ensuring the
     * IMAP folder is kept open as the folder will time out if not kept open. This ensures message
     * added events will continously trigger when emails are received.
     * @param folder The folder to keep open.
     */
    public void checkEmails(final Folder folder) {
        new Thread(){
            volatile boolean active = true;

            /**
             * Function that allows the thread to be stopped.
             */
            public void kill()
            {
                active = false;
            }

            /**
             * Function that is called when the thread is activated.
             */
            public void run(){
                while(active) {
                    try {
                        openFolder(folder); //Ensure the folder is open
                        Log.d(TAG, "IMAP folder being set to idle");
                        ((IMAPFolder) folder).idle(); //Set the folder to idle.
                    }
                    catch(FolderClosedException e)
                    {
                        //ignore
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    /**
     * Function that ensures a selected folder is open so that the folder can be set to idle.
     * @param folder The folder that must be kept open.
     * @throws MessagingException Exception handled by function that calls this function.
     */
    public static void openFolder(final Folder folder) throws MessagingException
    {
        if(folder == null) //Ensure the folder is valid.
        {
            throw new MessagingException("Folder is null."); //Folder is null, throw exception.
        }
        else
        {
            Store store = folder.getStore(); //Get the store of the folder.
            if(store != null && !store.isConnected()) //Check if the store is connected.
            {
                store.connect(email_address, password); //Connect to the store if it is not connected.
            }

            if(!folder.isOpen()) //Check if the folder is open.
            {
                folder.open(Folder.READ_WRITE); //Reopen the folder if it is not open.
                if(!folder.isOpen())
                    throw new MessagingException("Unable to open folder"); //Folder can't be opened, throw exception.
            }
        }
    }

    /**
     * Function that is called in order to send a new email.
     * @param recipients The recipients email address.
     * @param subject The subject of the email.
     * @param body The contents of the email.
     * @throws MessagingException Function throws MessagingExceptions that occur when attempting to send email.
     */
    public synchronized void sendMail(String recipients, String subject, String body) throws MessagingException {
        MimeMessage message = new MimeMessage(smtp_session); //Created a new Mime Message.
        ByteArrayDataSource message_contents = new ByteArrayDataSource(body.getBytes(), "text/plain");
        //Convert the message to a format that can be sent via email and set the mime type.
        DataHandler handler = new DataHandler(message_contents); //Create a Data Handler to contain the message contents.
        message.setSender(new InternetAddress(email_address)); //Set email sender
        message.setSubject(subject); //Set subject
        message.setDataHandler(handler); //Set data handler
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipients)); //Set recipient
        Transport.send(message); //Send the email.
        Log.d(TAG, "Mail sent");
    }

    /**
     *
     * @return
     * @throws MessagingException
     */
    public Message[] getUnreadMail() throws MessagingException
    {
        store = imap_session.getStore("imaps");
        store.connect(mail_host, email_address, password);
        inbox = store.getFolder("Inbox");
        inbox.open(Folder.READ_WRITE);

        FlagTerm flag_term = new FlagTerm(new Flags(Flags.Flag.SEEN), false);

        return inbox.search(flag_term);
    }

    /**
     * Adds a message count listener to the inbox which triggers an event when a message is added.
     * This event is used to send the new email to the Home Page using the Mail Listener interface.
     */
    public void getIncomingMail()
    {
        closeInbox(store, inbox);
        try{
            store = imap_session.getStore("imaps");
            store.connect(email_address, password); //Connect to the store
            if(!((IMAPStore)store).hasCapability("IDLE")) //Ensure IMAP server has IDLE capability
                Log.e(TAG, "Server does not support IDLE"); //should never happen

            inbox = store.getFolder("Inbox"); //Obtain the inbox folder
            inbox.addMessageCountListener(new MessageCountListener() { //Add the listener to the folder
                /**
                 * Function that triggers when a new message is added to the inbox of the user.
                 * @param messageCountEvent Event that contains the details of the new email.
                 */
                @Override
                public void messagesAdded(MessageCountEvent messageCountEvent) {
                    Log.d(TAG, "Message has been added");

                    Message[] messages = messageCountEvent.getMessages(); //Obtain the email.
                    listener.callback(messages, messages.length); //Send the email to the Home Page.
                }

                /**
                 * Function that triggers when a message is removed from the inbox of the user.
                 * @param messageCountEvent Event that contains the details of the new email.
                 */
                @Override
                public void messagesRemoved(MessageCountEvent messageCountEvent) {
                    Log.d(TAG, "Message has been removed");
                }
            });

            checkEmails(inbox);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Closes the store and folder that are currently connected to.
     * @param store The Store object to close.
     * @param folder The Folder object to close.
     */
    public void closeInbox(Store store, Folder folder)
    {
        try
        {
            if(folder.isOpen()) //Ensure the folder is open.
                folder.close(false); //Close the folder.
            if(store.isConnected()) //Ensure the store is open.
                store.close(); //Close the store.
        }
        catch(MessagingException e)
        {
            Log.d(TAG, "MessagingException when closing inbox.");
            e.printStackTrace();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Class that is used to convert a String to a valid format which can be used to send an email where
     * the data is in the form of an InputStream and the MIME type is specified.
     */
    public class ByteArrayDataSource implements DataSource {
        private byte[] data;
        private String type;

        /**
         * Constructor that sets private variables.
         * @param data The email contents to store in the byte array.
         * @param type The mime content type.
         */
        public ByteArrayDataSource(byte[] data, String type) {
            super();
            this.data = data;
            this.type = type;
            //Sets variables.
        }

        /**
         * Returns the MIME content type.
         * @return String indicating MIME content type.
         */
        public String getContentType() {
            return type;
        }

        /**
         * Converts the byte array data to an input stream.
         * @return InputStream that contains the data in a different format.
         * @throws IOException
         */
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(data);
        }

        /**
         * Obtains name of Class.
         * @return String indicating class name.
         */
        public String getName() {
            return "ByteArrayDataSource";
        }

        /**
         * Unused but required function to implement DataSource
         * @return
         * @throws IOException
         */
        public OutputStream getOutputStream() throws IOException {
            throw new IOException("Not Supported");
        }
    }
}
