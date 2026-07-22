package com.trackai.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.net.URI;
import java.net.URISyntaxException;

public final class HttpsOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public HttpsOAuth2AuthorizationRequestResolver(ClientRegistrationRepository registrations) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return secure(delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return secure(delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest secure(OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) {
            return null;
        }
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .redirectUri(securePublicRedirectUri(authorizationRequest.getRedirectUri()))
                .build();
    }

    static String securePublicRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return redirectUri;
        }
        try {
            URI uri = new URI(redirectUri);
            String host = uri.getHost();
            boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
            if (!local && "http".equalsIgnoreCase(uri.getScheme())) {
                return new URI("https", uri.getUserInfo(), host,
                        uri.getPort() == 80 ? -1 : uri.getPort(),
                        uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
            }
            return redirectUri;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid OAuth redirect URI", exception);
        }
    }
}