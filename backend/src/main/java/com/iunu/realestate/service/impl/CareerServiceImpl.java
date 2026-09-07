package com.iunu.realestate.service.impl;

import com.iunu.realestate.service.CareerService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerServiceImpl implements CareerService {

    private static final String COMPANY_EMAIL = "info@iunu-eg.com";
    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Override
    public void sendApplication(String fullName, String email, String phone, String position, String message, MultipartFile resume) {
        if (!mailEnabled) {
            log.info("Career mail disabled. Application from {} for {} was received.", email, position);
            return;
        }

        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(COMPANY_EMAIL);
            helper.setReplyTo(email);
            helper.setSubject("Career application: " + position + " - " + fullName);
            helper.setText(""
                    + "New career application\n\n"
                    + "Name: " + fullName + "\n"
                    + "Email: " + email + "\n"
                    + "Phone: " + phone + "\n"
                    + "Position: " + position + "\n\n"
                    + "Message:\n" + message, false);

            if (resume != null && !resume.isEmpty()) {
                helper.addAttachment(safeAttachmentName(resume.getOriginalFilename()),
                        new ByteArrayResource(resume.getBytes()));
            }
            mailSender.send(mail);
        } catch (MailException | MessagingException | IOException exception) {
            log.error("Failed to send career application from {}", email, exception);
            throw new IllegalStateException("Unable to send career application", exception);
        }
    }

    /**
     * The uploaded filename is attacker-controlled and ends up as an
     * attachment name in someone's inbox, so strip any directory component
     * ("../../etc/passwd" -> "passwd") and anything that is not a plain
     * filename character before using it.
     */
    public static String safeAttachmentName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "cv.pdf";
        }

        String baseName = originalFilename
                .replace('\\', '/')
                .substring(originalFilename.replace('\\', '/').lastIndexOf('/') + 1)
                .replaceAll("[^A-Za-z0-9._-]", "_");

        // A name that was entirely separators/dots ("..", "/", "") is not usable.
        if (baseName.isBlank() || baseName.chars().allMatch(c -> c == '.' || c == '_')) {
            return "cv.pdf";
        }
        return baseName.length() > 100 ? baseName.substring(baseName.length() - 100) : baseName;
    }
}
