package com.travyn.common.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    @Async
    public void sendVerificationEmail(String toEmail, String firstName, String token) {
        String verifyUrl = baseUrl + "/verify-email?token=" + token;
        String subject = "Verify your Travyn account";

        String html = buildEmailTemplate(
                firstName,
                "Welcome to Travyn! 🌍",
                "You're one step away from finding your perfect travel companions. "
                        + "Click the button below to verify your email and activate your account.",
                "Verify My Email",
                verifyUrl,
                "This link expires in 24 hours. If you didn't create a Travyn account, you can safely ignore this email."
        );

        sendHtmlEmail(toEmail, subject, html);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String firstName, String token) {
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        String subject = "Reset your Travyn password";

        String html = buildEmailTemplate(
                firstName,
                "Password Reset Request",
                "We received a request to reset your password. "
                        + "Click the button below to choose a new password.",
                "Reset Password",
                resetUrl,
                "This link expires in 1 hour. If you didn't request a password reset, "
                        + "you can safely ignore this email — your password will remain unchanged."
        );

        sendHtmlEmail(toEmail, subject, html);
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String firstName) {
        String subject = "Welcome to Travyn — You're verified! ✅";
        String html = buildEmailTemplate(
                firstName,
                "You're in! Welcome to Travyn 🌍",
                "Your account is verified and ready to go. You've already completed Aadhaar verification, "
                        + "so you can start exploring trips and connecting with travel companions right away.",
                "Explore Travyn",
                "http://localhost:3000/dashboard",
                "Your identity has been verified via Aadhaar. Your trust score has been boosted by 50 points."
        );
        sendHtmlEmail(toEmail, subject, html);
    }

    @Async
    public void sendLocationShareEmail(String toEmail, String contactName, String travelerName, String destination, String link) {
        String subject = "URGENT: Live Location Sharing for " + destination;
        String html = buildEmailTemplate(
                contactName,
                "Live Tracking 🗺️",
                travelerName + " has shared their live location with you for their trip to " + destination + ".",
                "Track Live Location",
                link,
                "This secure tracking link is valid for 72 hours. Please monitor this link to ensure " + travelerName + "'s safety."
        );
        sendHtmlEmail(toEmail, subject, html);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "Travyn");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private String buildEmailTemplate(
            String firstName,
            String heading,
            String message,
            String buttonText,
            String buttonUrl,
            String footerNote
    ) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;background-color:#06080c;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#06080c;padding:40px 20px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="520" cellpadding="0" cellspacing="0" style="background-color:#141c2b;border-radius:16px;border:1px solid rgba(255,255,255,0.06);overflow:hidden;">

                          <!-- Header -->
                          <tr>
                            <td style="padding:32px 40px 24px;text-align:center;background:linear-gradient(135deg,rgba(45,212,168,0.08),rgba(240,160,48,0.05));">
                              <div style="display:inline-block;width:44px;height:44px;line-height:44px;text-align:center;background:linear-gradient(135deg,#2dd4a8,#1fae8a);border-radius:12px;font-size:22px;color:#06080c;font-weight:bold;margin-bottom:16px;">
                                &#9992;
                              </div>
                              <h1 style="margin:0;font-size:22px;font-weight:700;color:#f8fafc;letter-spacing:-0.01em;">
                                %s
                              </h1>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:28px 40px 16px;">
                              <p style="margin:0 0 16px;font-size:15px;color:#e2e8f0;line-height:1.7;">
                                Hi <strong>%s</strong>,
                              </p>
                              <p style="margin:0 0 28px;font-size:15px;color:#94a3b8;line-height:1.7;">
                                %s
                              </p>

                              <!-- Button -->
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td align="center" style="padding:8px 0 28px;">
                                    <a href="%s"
                                       style="display:inline-block;padding:14px 36px;background:linear-gradient(135deg,#1fae8a,#2dd4a8);color:#06080c;font-size:15px;font-weight:700;text-decoration:none;border-radius:50px;letter-spacing:0.01em;">
                                      %s &rarr;
                                    </a>
                                  </td>
                                </tr>
                              </table>

                              <p style="margin:0 0 8px;font-size:12px;color:#64748b;line-height:1.6;">
                                If the button doesn't work, copy and paste this link:
                              </p>
                              <p style="margin:0 0 16px;font-size:12px;color:#2dd4a8;word-break:break-all;line-height:1.5;">
                                %s
                              </p>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="padding:20px 40px 28px;border-top:1px solid rgba(255,255,255,0.06);">
                              <p style="margin:0 0 16px;font-size:12px;color:#475569;line-height:1.6;">
                                %s
                              </p>
                              <p style="margin:0;font-size:12px;color:#475569;">
                                &mdash; The Travyn Team
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(heading, firstName, message, buttonUrl, buttonText, buttonUrl, footerNote);
    }
}
