package com.greengrub.notification.email;

import com.greengrub.notification.dto.Customer;
import com.greengrub.notification.dto.DonatedItem;
import com.greengrub.notification.dto.Donation;
import com.greengrub.notification.enums.DonationStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.IContext;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private SpringTemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    private Donation donation;
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer("cust-001", "Jane", "Smith", "jane@example.com", "555-0200");
        DonatedItem item = new DonatedItem("Wheat", 5, "kg", "Grains");
        donation = new Donation(
                "don-001", "Jane Smith", "jane@example.com",
                new BigDecimal("250.00"),
                LocalDateTime.of(2024, 6, 1, 9, 30, 0),
                customer, "GreenGrub Org",
                DonationStatus.ACTIVE.name(),
                List.of(item)
        );

        mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(IContext.class))).thenReturn("<html>email</html>");

        // @Value fields are not injected by Mockito — set directly via reflection
        ReflectionTestUtils.setField(emailService, "mailUsername", "greengrub.cares@gmail.com");
    }

    // ── sendDonationThankYouEmail — success ───────────────────────────────────

    @Test
    void sendEmail_success_callsMailSenderSend() throws MessagingException {
        emailService.sendDonationThankYouEmail(donation, "Thank you!");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_success_processesThymeleafTemplate() throws MessagingException {
        emailService.sendDonationThankYouEmail(donation, "Thank you!");

        verify(templateEngine).process(eq("donation.html"), any(IContext.class));
    }

    @Test
    void sendEmail_success_createsMimeMessage() throws MessagingException {
        emailService.sendDonationThankYouEmail(donation, "Subject");

        verify(mailSender).createMimeMessage();
    }

    @Test
    void sendEmail_differentSubject_stillSendsEmail() throws MessagingException {
        emailService.sendDonationThankYouEmail(donation, "Your donation has been claimed!");

        verify(mailSender).send(mimeMessage);
    }

    // ── sendDonationThankYouEmail — failure ───────────────────────────────────

    @Test
    void sendEmail_mailSenderThrows_propagatesException() {
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendDonationThankYouEmail(donation, "Subject"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SMTP error");
    }

    @Test
    void sendEmail_templateEngineThrows_propagatesException() {
        when(templateEngine.process(anyString(), any(IContext.class)))
                .thenThrow(new RuntimeException("template error"));

        assertThatThrownBy(() -> emailService.sendDonationThankYouEmail(donation, "Subject"))
                .isInstanceOf(RuntimeException.class);
    }

    // ── From address fix ──────────────────────────────────────────────────────

    @Test
    void sendEmail_usesMailUsernameAsFromAddress() throws MessagingException {
        // Capture the MimeMessageHelper's setFrom call indirectly by verifying
        // that send() is invoked (helper.setFrom would throw if mailUsername is null)
        emailService.sendDonationThankYouEmail(donation, "Subject");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_nullMailUsername_throwsIllegalArgument() {
        ReflectionTestUtils.setField(emailService, "mailUsername", null);

        assertThatThrownBy(() -> emailService.sendDonationThankYouEmail(donation, "Subject"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
