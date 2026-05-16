package com.greengrub.notification.email;

import com.greengrub.notification.donation.Donation;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    @Async
    public void sendDonationThankYouEmail(Donation donation, String emailSubject) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );
            mimeMessage.setSubject(EmailTemplate.DONATION_CONFIRMATION.getDescription());
            Context thymeleafContext = prepareThymeleafContext(donation);

            String htmlContent = templateEngine.process(EmailTemplate.DONATION_CONFIRMATION.getTemplate(), thymeleafContext);

            helper.setTo(donation.donorEmail());
            helper.setFrom(donation.campaignName().email());
            helper.setSubject(emailSubject);
            helper.setText(htmlContent, true); // true = isHtml

            mailSender.send(mimeMessage);

            log.info("Thank-you email sent successfully to: {}", donation.donorEmail());

        } catch (MessagingException e) {
            log.error("Failed to send donation thank-you email to {} - donationId: {}",
                    donation.donorEmail(), donation.donationId(), e);
        }
    }

    private Context prepareThymeleafContext(Donation donation) {
        Context context = new Context();

        context.setVariable("donorName", donation.campaignName().firstname() +  " " + donation.campaignName().lastname());
        context.setVariable("organizationName", donation.organizationName());
        context.setVariable("donationId", donation.donationId());
        context.setVariable("donationDate", donation.createdAt().format(DATE_FORMATTER));
        context.setVariable("totalAmount", donation.totalAmount());
        context.setVariable("items", donation.items());

        // You can add more variables like logo URL, social links, etc.
        // context.setVariable("supportEmail", "support@yourorg.org");
        // context.setVariable("websiteUrl", "https://yourorg.org");

        return context;
    }

    // Optional: method to send to multiple recipients (e.g. admin + donor)
    public void sendToMultiple(List<String> emails, Donation event) {
        // You can loop or use setBcc, etc.
    }
}
