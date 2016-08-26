package com.ibm.watson.developer_cloud.android.examples;

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

import android.util.Log;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

public class MailHandler{

    private String smtp_host = "smtp.gmail.com";
    private String mail_host = "imap.gmail.com";
    private Session imap_session;
    private Session smtp_session;
    private Folder inbox;
    private Store store;
    private static String email_address = "";
    private static String password = "";

    private static final String TAG = "MailHandler";

    private MyListener listener;

    static {
        Security.addProvider(new JSSEProvider());
    }

    public MailHandler(String new_email_address, String new_password) {
        email_address = new_email_address;
        password = new_password;
        MailAuthenticator authenticator = new MailAuthenticator(email_address, password);

        Properties props_smtp = new Properties();
        //props_smtp.setProperty("mail.transport.protocol", "smtps");
        props_smtp.setProperty("mail.smtp.auth", "true");
        props_smtp.setProperty("mail.smtp.starttls.enable", "true");
        props_smtp.setProperty("mail.smtp.host", smtp_host);
        props_smtp.setProperty("mail.smtp.port", "587");
        //props_smtp.put("mail.smtp.socketFactory.port", "465");
       // props_smtp.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        //props_smtp.put("mail.smtp.socketFactory.fallback", "false");

        smtp_session = Session.getDefaultInstance(props_smtp, authenticator);

        //smtp_session = Session.getInstance(props_smtp);

        Properties props_imap = new Properties();
        props_imap.setProperty("mail.store.protocol", "imaps");
        props_imap.setProperty("mail.imaps.host", mail_host);
        props_imap.setProperty("mail.imaps.port", "993");
        props_imap.setProperty("mail.imaps.timeout", "100000");

        imap_session = Session.getInstance(props_imap);
    }

    public void setListener(MyListener listener)
    {
        this.listener = listener;
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

    public synchronized void sendMail(String recipients, String subject, String body) throws MessagingException {
        MimeMessage message = new MimeMessage(smtp_session);
        DataHandler handler = new DataHandler(new ByteArrayDataSource(body.getBytes(), "text/plain"));
        message.setSender(new InternetAddress(email_address));
        message.setSubject(subject);
        message.setDataHandler(handler);
//        if (recipients.indexOf(',') > 0)
//            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipients));
//        else
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipients));
        Transport.send(message);
        Log.d(TAG, "Mail sent");
    }

    public Message[] getUnreadMail() throws MessagingException
    {
        store = imap_session.getStore("imaps");
        store.connect(mail_host, email_address, password);
        inbox = store.getFolder("Inbox");
        inbox.open(Folder.READ_ONLY);

        FlagTerm flag_term = new FlagTerm(new Flags(Flags.Flag.SEEN), false);

        return inbox.search(flag_term);
    }

    public void getIncomingMail()
    {
        closeInbox(store, inbox);
        try{
            store = imap_session.getStore("imaps"); //(IMAPStore)
            store.connect(email_address, password);
            if(!((IMAPStore)store).hasCapability("IDLE"))
                Log.e(TAG, "Server does not support IDLE"); //should never happen

            inbox = store.getFolder("Inbox"); //(IMAPFolder)
            inbox.addMessageCountListener(new MessageCountListener() {
                @Override
                public void messagesAdded(MessageCountEvent messageCountEvent) {
                    Log.d(TAG, "Message has been added");

                    Message[] messages = messageCountEvent.getMessages();
                    listener.callback(messages, messages.length);
                }

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

    public void closeInbox(Store store, Folder folder)
    {
        try
        {
            if(folder.isOpen())
                folder.close(false);
            if(store.isConnected())
                store.close();
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
