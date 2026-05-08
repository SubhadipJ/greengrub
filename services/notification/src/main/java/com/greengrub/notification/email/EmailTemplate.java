package com.greengrub.notification.email;

import lombok.Getter;

public enum EmailTemplate {
    DONATION_CONFIRMATION("donation.html","Donation has beed created successfully");

    @Getter
    private final String template;
    @Getter
    private final String description;
    EmailTemplate(String template, String description) {
        this.template = template;
        this.description = description;
    }
}
