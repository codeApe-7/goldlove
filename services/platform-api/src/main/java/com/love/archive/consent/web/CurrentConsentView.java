package com.love.archive.consent.web;

import com.love.archive.consent.application.ConsentView;

public record CurrentConsentView(boolean hasValidConsent, ConsentView consent) {

    public static CurrentConsentView absent() {
        return new CurrentConsentView(false, null);
    }

    public static CurrentConsentView present(ConsentView consent) {
        return new CurrentConsentView(true, consent);
    }
}
