package mailer;

import org.apache.commons.io.input.CharSequenceInputStream;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Properties;

public class Mailer {
    private static final String DEFAULT_MAILER_NAME = "Patent Search Platform Alert <ehallmarkjava@gmail.com>";
    private static final String DEFAULT_MAIL_TO = "ehallmark@gttgrp.com";
    private final String username = "ehallmarkjava@gmail.com";
    private final String password = "JavaMailer11415";
    private Session session;
    public Mailer() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

    }

    public void sendMail(String subject, String body) throws MessagingException {
        sendMail(DEFAULT_MAILER_NAME, DEFAULT_MAIL_TO, subject, body);
    }


    public void sendMail(String subject, String message, Exception exception) {
        ByteArrayOutputStream is = new ByteArrayOutputStream();
        exception.printStackTrace(new PrintStream(is));
        try {
            sendMail(DEFAULT_MAILER_NAME, DEFAULT_MAIL_TO, subject, message + "\n" + is.toString());
            is.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMail(String toEmail, String subject, String body) throws MessagingException {
        sendMail(DEFAULT_MAILER_NAME, toEmail, subject, body);
    }

    public void sendMail(String fromEmail, String toEmail, String subject, String body) throws MessagingException {
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromEmail));
        message.setRecipients(Message.RecipientType.TO,
                InternetAddress.parse(toEmail));
        message.setSubject(subject);
        message.setText(body);
        Transport.send(message);
    }

    public static void main(String[] args) throws Exception {
        Mailer mailer = new Mailer();
        //mailer.sendMail("ehallmar@usc.edu", "Test mail", "This is a test!");
        try {
            String isNull = null;
            isNull.length();
        } catch(Exception e) {
            mailer.sendMail("subject", "message", e);
        }
    }
}
