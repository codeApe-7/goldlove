package com.love.archive.consent.application;

public interface AuthorizationDocumentQuery {

    AuthorizationDocumentView current(String documentCode);

    AuthorizationDocumentView requireActive(String documentCode, String version);

    AuthorizationDocumentView requireVisibleToGuest(long accountId, String documentCode, String version);
}
