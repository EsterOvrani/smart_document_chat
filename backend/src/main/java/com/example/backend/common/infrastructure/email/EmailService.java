package com.example.backend.common.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailService {
    @Autowired
    private JavaMailSender emailSender;

    @Value("${app.frontend.url:http://localhost}")
    private String frontendUrl;

    public void sendVerificationEmail(String to, String subject, String verificationCode) throws MessagingException {
        try{
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // ✅ יצירת קישור האימות - רק עם הקוד המספרי!
            String verificationLink = frontendUrl + "/verify?email=" + to + "&code=" + verificationCode;

            String htmlMessage = "<!DOCTYPE html>" +
                    "<html dir='rtl'>" +
                    "<head>" +
                    "<meta charset='UTF-8'>" +
                    "<style>" +
                    "body { font-family: Arial, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; direction: rtl; }" +
                    ".container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); overflow: hidden; }" +
                    ".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; }" +
                    ".header h1 { margin: 0; font-size: 28px; }" +
                    ".content { padding: 40px 30px; text-align: center; }" +
                    ".content p { font-size: 16px; color: #333; line-height: 1.6; margin-bottom: 30px; }" +
                    ".verify-button { display: inline-block; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white !important; text-decoration: none; padding: 15px 40px; border-radius: 8px; font-size: 18px; font-weight: bold; margin: 20px 0; }" +
                    ".verify-button:hover { opacity: 0.9; }" +
                    ".footer { background-color: #f8f9fa; padding: 20px; text-align: center; font-size: 12px; color: #666; border-top: 1px solid #e1e8ed; }" +
                    ".divider { margin: 30px 0; text-align: center; color: #999; }" +
                    ".code-box { background-color: #f8f9ff; border: 2px dashed #667eea; border-radius: 8px; padding: 20px; margin: 20px 0; }" +
                    ".code-box .code { font-size: 32px; font-weight: bold; color: #667eea; letter-spacing: 8px; font-family: monospace; }" +
                    "</style>" +
                    "</head>" +
                    "<body>" +
                    "<div class='container'>" +
                    "<div class='header'>" +
                    "<h1>📚 Smart Document Chat</h1>" +
                    "</div>" +
                    "<div class='content'>" +
                    "<h2 style='color: #333; margin-bottom: 20px;'>ברוך הבא למערכת!</h2>" +
                    "<p>תודה שנרשמת למערכת ניהול המסמכים החכמה שלנו.</p>" +
                    "<p>כדי להשלים את תהליך הרשמה, אנא אמת את כתובת המייל שלך על ידי לחיצה על הכפתור:</p>" +
                    "<a href='" + verificationLink + "' class='verify-button'>✓ אמת את המייל שלי</a>" +
                    "<div class='divider'>או</div>" +
                    "<p style='font-size: 14px; color: #666;'>העתק והדבק את הקישור הבא בדפדפן:</p>" +
                    "<div class='code-box'>" +
                    "<a href='" + verificationLink + "' style='color: #667eea; word-break: break-all;'>" + verificationLink + "</a>" +
                    "</div>" +
                    "<div class='divider'>או השתמש בקוד האימות הידני:</div>" +
                "<div class='code-box'>" +
                "<div class='code'>" + verificationCode + "</div>" +
                "</div>" +
                "<p style='font-size: 13px; color: #999; margin-top: 30px;'>הקישור והקוד תקפים ל-15 דקות בלבד</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>אם לא נרשמת למערכת, אנא התעלם ממייל זה.</p>" +
                "<p>© 2025 Smart Document Chat. כל הזכויות שמורות.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlMessage, true);

        emailSender.send(message);     

        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            throw new MessagingException("נכשל בשליחת אימייל אימות", e);
        }
    }

    
}
