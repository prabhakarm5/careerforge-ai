package com.trackai.backend.security;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OAuth2UserInfoService extends DefaultOAuth2UserService {

        private final RestTemplate restTemplate = new RestTemplate();

        @Override
        public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
                OAuth2User oauth2User = super.loadUser(userRequest);
                String registrationId = userRequest.getClientRegistration().getRegistrationId();

                if (!"github".equalsIgnoreCase(registrationId) || oauth2User.getAttribute("email") != null) {
                        return oauth2User;
                }

                // GitHub profile API returns null email when the user keeps email private.
                // With user:email scope we can safely fetch the primary verified email instead.
                String primaryEmail = fetchGithubPrimaryEmail(userRequest);
                if (primaryEmail == null || primaryEmail.isBlank()) {
                        return oauth2User;
                }

                Map<String, Object> attributes = new LinkedHashMap<>(oauth2User.getAttributes());
                attributes.put("email", primaryEmail);

                return new DefaultOAuth2User(
                                oauth2User.getAuthorities(),
                                attributes,
                                userRequest.getClientRegistration()
                                                .getProviderDetails()
                                                .getUserInfoEndpoint()
                                                .getUserNameAttributeName());
        }

        private String fetchGithubPrimaryEmail(OAuth2UserRequest userRequest) {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(userRequest.getAccessToken().getTokenValue());

                ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                                "https://api.github.com/user/emails",
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                                });

                List<Map<String, Object>> emails = response.getBody();
                if (emails == null) {
                        emails = new ArrayList<>();
                }

                return emails.stream()
                                .filter(item -> Boolean.TRUE.equals(item.get("primary")))
                                .filter(item -> Boolean.TRUE.equals(item.get("verified")))
                                .map(item -> String.valueOf(item.get("email")))
                                .findFirst()
                                .orElse(null);
        }
}