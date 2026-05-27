package com.greengrub.notification.email;

import com.greengrub.notification.dto.Donation;
import com.greengrub.notification.enums.EmailTemplate;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public void sendDonationThankYouEmail(Donation donation, String emailSubject) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name()
        );

        Context thymeleafContext = prepareThymeleafContext(donation);
        String htmlContent = templateEngine.process(EmailTemplate.DONATION_CONFIRMATION.getTemplate(), thymeleafContext);

        helper.setTo(donation.donorEmail());
        helper.setFrom(donation.customer().email());
        helper.setSubject(emailSubject);
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
        log.info("Email sent successfully to: {}", donation.donorEmail());
    }

    private Context prepareThymeleafContext(Donation donation) {
        Context context = new Context();

        context.setVariable("donorName", donation.customer().firstname() + " " + donation.customer().lastname());
        context.setVariable("organizationName", donation.organizationName());
        context.setVariable("donationId", donation.donationId());
        context.setVariable("donationDate", donation.createdAt().format(DATE_FORMATTER));
        context.setVariable("totalAmount", donation.totalAmount());
        context.setVariable("donationStatus", donation.status());
        context.setVariable("items", donation.items());

        return context;
    }
}
