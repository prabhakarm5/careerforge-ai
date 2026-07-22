package com.trackai.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpsOAuth2AuthorizationRequestResolverTest {

    @Test
    void upgradesPublicHttpRedirectAndKeepsLocalDevelopment() {
        assertThat(HttpsOAuth2AuthorizationRequestResolver.securePublicRedirectUri(
                "http://dr07bawk90aps.cloudfront.net/login/oauth2/code/github"))
                .isEqualTo("https://dr07bawk90aps.cloudfront.net/login/oauth2/code/github");
        assertThat(HttpsOAuth2AuthorizationRequestResolver.securePublicRedirectUri(
                "http://localhost:9092/login/oauth2/code/github"))
                .isEqualTo("http://localhost:9092/login/oauth2/code/github");
        assertThat(HttpsOAuth2AuthorizationRequestResolver.securePublicRedirectUri(
                "https://api.careerforge.example/login/oauth2/code/github"))
                .isEqualTo("https://api.careerforge.example/login/oauth2/code/github");
    }
}