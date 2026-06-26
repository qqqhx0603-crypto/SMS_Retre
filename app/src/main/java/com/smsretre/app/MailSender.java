package com.smsretre.app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

final class MailSender {
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    void send(MailConfig config, String subject, String body) throws Exception {
        if (!config.isComplete()) {
            throw new IllegalStateException("邮箱配置不完整");
        }

        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        Socket rawSocket = new Socket();
        rawSocket.connect(new InetSocketAddress(Constants.SMTP_HOST, Constants.SMTP_SSL_PORT), CONNECT_TIMEOUT_MS);
        try (SSLSocket socket = (SSLSocket) factory.createSocket(
                rawSocket,
                Constants.SMTP_HOST,
                Constants.SMTP_SSL_PORT,
                true
        )) {
            socket.setSoTimeout(READ_TIMEOUT_MS);
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

            expect(reader, 220);
            sendLine(writer, "EHLO sms-retre.local");
            expect(reader, 250);

            sendLine(writer, "AUTH LOGIN");
            expect(reader, 334);
            sendLine(writer, base64(config.senderEmail));
            expect(reader, 334);
            sendLine(writer, base64(config.authCode));
            expect(reader, 235);

            sendLine(writer, "MAIL FROM:<" + config.senderEmail + ">");
            expect(reader, 250);
            sendLine(writer, "RCPT TO:<" + config.recipientEmail + ">");
            expect(reader, 250);
            sendLine(writer, "DATA");
            expect(reader, 354);

            writer.write(buildMessage(config, subject, body));
            writer.write("\r\n.\r\n");
            writer.flush();
            expect(reader, 250);

            sendLine(writer, "QUIT");
            expect(reader, 221);
        } catch (SocketTimeoutException e) {
            throw new SocketTimeoutException("连接或读取 QQ 邮箱 SMTP 超时");
        }
    }

    private static String buildMessage(MailConfig config, String subject, String body) {
        return "From: <" + config.senderEmail + ">\r\n"
                + "To: <" + config.recipientEmail + ">\r\n"
                + "Subject: " + encodedWord(subject) + "\r\n"
                + "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()) + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + mimeBase64(body);
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodedWord(String value) {
        return "=?UTF-8?B?" + base64(value) + "?=";
    }

    private static String mimeBase64(String value) {
        byte[] encoded = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encode(value.getBytes(StandardCharsets.UTF_8));
        return new String(encoded, StandardCharsets.US_ASCII);
    }

    private static void sendLine(BufferedWriter writer, String line) throws Exception {
        writer.write(line);
        writer.write("\r\n");
        writer.flush();
    }

    private static void expect(BufferedReader reader, int expectedCode) throws Exception {
        String response = readResponse(reader);
        if (response.length() < 3) {
            throw new IllegalStateException("SMTP 响应为空");
        }
        int actualCode;
        try {
            actualCode = Integer.parseInt(response.substring(0, 3));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("无法解析 SMTP 响应: " + response, e);
        }
        if (actualCode != expectedCode) {
            throw new IllegalStateException("SMTP 期望 " + expectedCode + "，实际响应: " + response);
        }
    }

    private static String readResponse(BufferedReader reader) throws Exception {
        StringBuilder result = new StringBuilder();
        String line;
        do {
            line = reader.readLine();
            if (line == null) {
                throw new IllegalStateException("SMTP 连接被服务器关闭");
            }
            if (result.length() > 0) {
                result.append(" | ");
            }
            result.append(line);
        } while (line.length() >= 4 && line.charAt(3) == '-');
        return result.toString();
    }
}
