package com.ibm.watson.developer_cloud.android.examples;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.FolderClosedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.MessageCountListener;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.FlagTerm;

import android.util.Log;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

public class MailHandler {
    private String smtp_host = "smtp.gmail.com";
    private String mail_host = "imap.gmail.com";
    private Session imap_session;
    private Session smtp_session;
    private Folder inbox;
    private Folder inbox2;
    private Store store;
    private IMAPStore store2;
    private MailAuthenticator authenticator;
    private static String email_address = "jeanvdberg1994@gmail.com";
    private static String password = "<password>";

    private static final String TAG = "MailHandler";

    static {
        Security.addProvider(new JSSEProvider());
    }

    public MailHandler(String mail_address, String password) {
        authenticator = new MailAuthenticator(mail_address, password);

        Properties props_smtp = new Properties();
        props_smtp.put("mail.smtp.auth", "true");
        props_smtp.put("mail.smtp.starttls.enable", "true");
        props_smtp.put("mail.smtp.host", smtp_host);
        props_smtp.put("mail.smtp.port", "587");

       // smtp_session = Session.getDefaultInstance(props_smtp, authenticator);

        smtp_session = Session.getInstance(props_smtp);

        Properties props_imap = new Properties();
        props_imap.setProperty("mail.store.protocol", "imaps");
        props_imap.setProperty("mail.imaps.host", mail_host);
        props_imap.setProperty("mail.imaps.port", "993");
       // props_imap.setProperty("mail.imaps.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
       // props_imap.setProperty("mail.imaps.socketFactory.fallback", "false");
        props_imap.setProperty("mail.imaps.timeout", "100000");

        imap_session = Session.getInstance(props_imap);

        try{
            store2 = (IMAPStore) imap_session.getStore("imaps");
            store2.connect(authenticator.getEmailAddress(), authenticator.getPassword());
            if(!store2.hasCapability("IDLE"))
                throw new RuntimeException("Server does not support IDLE"); //should never happen

            inbox2 = (IMAPFolder) store2.getFolder("Inbox");
            inbox2.addMessageCountListener(new MessageCountListener() {
                @Override
                public void messagesAdded(MessageCountEvent messageCountEvent) {
                    Log.d(TAG, "Message has been added");

                    Message[] messages = messageCountEvent.getMessages();
                    try {
                        Log.d(TAG, "The subject is: " + messages[0].getSubject());
                    }
                    catch(MessagingException e)
                    {
                        e.printStackTrace();
                    }
                    //todo pass message to main activity
                }

                @Override
                public void messagesRemoved(MessageCountEvent messageCountEvent) {
                    Log.d(TAG, "Message has been removed");
                }
            });

            checkEmails(inbox2);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public void checkEmails(final Folder folder) {
        new Thread(){
            volatile boolean active = true;

            public void kill()
            {
                active = false;
            }

            public void run(){
                while(active) {
                    try {
                        openFolder(folder);
                        Log.d(TAG, "IMAP folder being set to idle");
                        ((IMAPFolder) folder).idle();
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

    public static void openFolder(final Folder inbox) throws MessagingException
    {
        if(inbox == null)
        {
            throw new MessagingException("Folder is null");
        }
        else
        {
            Store store = inbox.getStore();
            if(store != null && !store.isConnected())
            {
                store.connect(email_address, password);
            }

            if(!inbox.isOpen())
            {
                inbox.open(Folder.READ_ONLY);
                if(!inbox.isOpen())
                    throw new MessagingException("Unable to open folder");
            }
        }
    }

//    public static class MailThread extends Thread{
//        private final Folder folder;
//        private boolean active = true;
//
//        public MailThread(Folder folder)
//        {
//            super();
//            this.folder = folder;
//        }
//
//        public synchronized void kill()
//        {
//            active = false;
//        }
//
//        @Override
//        public void run(){
//            while(active)
//            {
//                try{
//                    Log.d(TAG, "IMAP folder being set to idle");
//                    ((IMAPFolder) folder).idle();
//                }
//                catch(Exception e)
//                {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }

    public synchronized void sendMail(String subject, String body, String sender, String recipients) throws Exception {
        MimeMessage message = new MimeMessage(smtp_session);
        DataHandler handler = new DataHandler(new ByteArrayDataSource(body.getBytes(), "text/plain"));
        message.setSender(new InternetAddress(sender));
        message.setSubject(subject);
        message.setDataHandler(handler);
        if (recipients.indexOf(',') > 0)
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipients));
        else
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipients));
        Transport.send(message);
    }

    public Message[] getMail() throws MessagingException
    {
        store = imap_session.getStore("imaps");
        store.connect(mail_host, authenticator.getEmailAddress(), authenticator.getPassword());
        //inbox  = store.getFolder("Inbox");

        inbox = store.getFolder("Inbox");

        inbox.open(Folder.READ_ONLY);
        inbox.addMessageCountListener(new MessageCountListener() {
            @Override
            public void messagesAdded(MessageCountEvent messageCountEvent) {
                Log.d(TAG, "Message has been added");
            }

            @Override
            public void messagesRemoved(MessageCountEvent messageCountEvent) {

            }
        });

        //Message[] results = inbox.getMessages();
        //Flags.Flag.SEEN <- entire inbox
        FlagTerm flag_term = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
        Message[] results = inbox.search(flag_term);

        //((IMAPFolder) inbox).idle();

        return results;
    }

    public void closeInbox()
    {
        try
        {
            if(inbox2.isOpen())
                inbox2.close(false);
            if(store2.isConnected())
                store2.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

//    public synchronized void sendMail(String subject, String body,
//                                      String senderEmail, String recipients, String filePath,
//                                      String logFilePath) throws Exception {
//        boolean fileExists = new File(filePath).exists();
//        if (fileExists) {
//
//            String from = senderEmail;
//            String to = recipients;
//            String fileAttachment = filePath;
//
//            // Define message
//            MimeMessage message = new MimeMessage(session);
//            message.setFrom(new InternetAddress(from));
//            message.addRecipient(Message.RecipientType.TO, new InternetAddress(
//                    to));
//            message.setSubject(subject);
//
//            // create the message part
//            MimeBodyPart messageBodyPart = new MimeBodyPart();
//
//            // fill message
//            messageBodyPart.setText(body);
//
//            Multipart multipart = new MimeMultipart();
//            multipart.addBodyPart(messageBodyPart);
//
//            // Part two is attachment
//            messageBodyPart = new MimeBodyPart();
//            DataSource source = new FileDataSource(fileAttachment);
//            messageBodyPart.setDataHandler(new DataHandler(source));
//            messageBodyPart.setFileName("screenShoot.jpg");
//            multipart.addBodyPart(messageBodyPart);
//
//            // part three for logs
//            messageBodyPart = new MimeBodyPart();
//            DataSource sourceb = new FileDataSource(logFilePath);
//            messageBodyPart.setDataHandler(new DataHandler(sourceb));
//            messageBodyPart.setFileName("logs.txt");
//            multipart.addBodyPart(messageBodyPart);
//
//            // Put parts in message
//            message.setContent(multipart);
//
//            // Send the message
//            Transport.send(message);
//        } else {
//            sendMail(subject, body, senderEmail, recipients);
//        }
//    }

//    public synchronized void sendMail(String subject, String body,
//                                      String senderEmail, String recipients, String logFilePath)
//            throws Exception {
//
//        File file= new File(logFilePath);
//        boolean fileExists =file.exists();
//        if (fileExists) {
//
//            String from = senderEmail;
//            String to = recipients;
//
//            // Define message
//            MimeMessage message = new MimeMessage(session);
//            message.setFrom(new InternetAddress(from));
//            message.addRecipient(Message.RecipientType.TO, new InternetAddress(
//                    to));
//            message.setSubject(subject);
//
//            // create the message part
//            MimeBodyPart messageBodyPart = new MimeBodyPart();
//
//            // fill message
//            messageBodyPart.setText(body);
//
//            Multipart multipart = new MimeMultipart();
//            multipart.addBodyPart(messageBodyPart);
//
//            // part three for logs
//            messageBodyPart = new MimeBodyPart();
//            DataSource sourceb = new FileDataSource(logFilePath);
//            messageBodyPart.setDataHandler(new DataHandler(sourceb));
//            messageBodyPart.setFileName(file.getName());
//            multipart.addBodyPart(messageBodyPart);
//
//            // Put parts in message
//            message.setContent(multipart);
//
//            // Send the message
//            Transport.send(message);
//        } else {
//            sendMail(subject, body, senderEmail, recipients);
//        }
//    }

    public class ByteArrayDataSource implements DataSource {
        private byte[] data;
        private String type;

        public ByteArrayDataSource(byte[] data, String type) {
            super();
            this.data = data;
            this.type = type;
        }

        public ByteArrayDataSource(byte[] data) {
            super();
            this.data = data;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getContentType() {
            if (type == null)
                return "application/octet-stream";
            else
                return type;
        }

        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(data);
        }

        public String getName() {
            return "ByteArrayDataSource";
        }

        public OutputStream getOutputStream() throws IOException {
            throw new IOException("Not Supported");
        }
    }
}
