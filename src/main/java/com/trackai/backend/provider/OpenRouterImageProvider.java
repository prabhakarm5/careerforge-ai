package com.trackai.backend.provider;

import com.trackai.backend.config.OpenRouterProperties;
import com.trackai.backend.dto.image.GenerateImageRequest;
import com.trackai.backend.dto.image.GenerateImageResponse;
import com.trackai.backend.dto.openrouter.OpenRouterGenerateResponse;
import com.trackai.backend.dto.openrouter.OpenRouterImage;
import com.trackai.backend.exception.OpenRouterException;
import com.trackai.backend.util.ImageBase64Util;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenRouterImageProvider
                implements AIImageProvider {

        private final WebClient openRouterWebClient;

        private final OpenRouterProperties properties;

        /*
         * OLD CODE:
         *
         * @Override
         * public GenerateImageResponse generateImage(
         *                 GenerateImageRequest request) {
         *
         *         OpenRouterGenerateRequest.OpenRouterGenerateRequestBuilder builder = OpenRouterGenerateRequest.builder()
         *
         *                         .model(properties.getImageModel())
         *
         *                         .prompt(request.getPrompt());
         *
         *         // Future Image Edit Support
         *         if (request.getImage() != null &&
         *                         !request.getImage().isEmpty()) {
         *
         *                 builder.inputReferences(
         *
         *                                 Collections.singletonList(
         *
         *                                                 OpenRouterInputReference.builder()
         *
         *                                                                 .type("image")
         *
         *                                                                 .mimeType(
         *                                                                                 request.getImage()
         *                                                                                                 .getContentType())
         *
         *                                                                 .data(
         *                                                                                 ImageBase64Util.encode(
         *                                                                                                 request.getImage()))
         *
         *                                                                 .build()
         *
         *                                 )
         *
         *                 );
         *
         *         }
         *
         *         OpenRouterGenerateResponse response =
         *
         *                         openRouterWebClient
         *
         *                                         .post()
         *
         *                                         .uri(properties.getImageEndpoint())
         *
         *                                         .bodyValue(builder.build())
         *
         *                                         .retrieve()
         *
         *                                         .onStatus(
         *
         *                                                         HttpStatusCode::isError,
         *
         *                                                         clientResponse ->
         *
         *                                                         clientResponse
         *
         *                                                                         .bodyToMono(String.class)
         *
         *                                                                         .map(OpenRouterException::new)
         *
         *                                         )
         *
         *                                         .bodyToMono(
         *
         *                                                         OpenRouterGenerateResponse.class
         *
         *                                         )
         *
         *                                         .block();
         *
         *         if (response == null
         *                         || response.getData() == null
         *                         || response.getData().isEmpty()) {
         *
         *                 throw new RuntimeException(
         *                                 "OpenRouter returned no image.");
         *
         *         }
         *
         *         OpenRouterImage image = response.getData().get(0);
         *
         *         GenerateImageResponse.GenerateImageResponseBuilder result =
         *
         *                         GenerateImageResponse.builder()
         *
         *                                         .prompt(request.getPrompt())
         *
         *                                         .provider("OPENROUTER");
         *
         *         // URL Response
         *         if (image.getUrl() != null &&
         *                         !image.getUrl().isBlank()) {
         *
         *                 result.imageUrl(image.getUrl());
         *
         *         }
         *
         *         // Base64 Response
         *         else if (image.getB64Json() != null &&
         *                         !image.getB64Json().isBlank()) {
         *
         *                 result.imageBytes(
         *
         *                                 java.util.Base64
         *
         *                                                 .getDecoder()
         *
         *                                                 .decode(image.getB64Json())
         *
         *                 );
         *
         *         }
         *
         *         else {
         *
         *                 throw new RuntimeException(
         *                                 "No image returned from provider.");
         *
         *         }
         *
         *         return result.build();
         *
         * }
         */

        @Override
        public boolean supports(String modelId) {
                return modelId == null || modelId.isBlank() || !modelId.startsWith("huggingface:");
        }

        @Override
        public GenerateImageResponse generateImage(
                        GenerateImageRequest request) {

                OpenRouterGenerateResponse response = openRouterWebClient
                                .post()
                                .uri(properties.getImageEndpoint())
                                .bodyValue(buildRequestBody(request))
                                .retrieve()
                                .onStatus(
                                                HttpStatusCode::isError,
                                                clientResponse -> clientResponse
                                                                .bodyToMono(String.class)
                                                                .map(OpenRouterException::new))
                                .bodyToMono(OpenRouterGenerateResponse.class)
                                .block();

                if (response == null
                                || response.getData() == null
                                || response.getData().isEmpty()) {

                        throw new OpenRouterException("OpenRouter returned no image data.");
                }

                OpenRouterImage image = response.getData().get(0);

                GenerateImageResponse.GenerateImageResponseBuilder result = GenerateImageResponse.builder()
                                .prompt(request.getPrompt())
                                .modelId(properties.resolveImageModel(request.getModel()))
                                .provider("OPENROUTER");

                if (image.getB64Json() != null
                                && !image.getB64Json().isBlank()) {

                        result.imageBytes(decodeBase64Image(image.getB64Json()));

                } else if (image.getUrl() != null
                                && !image.getUrl().isBlank()) {

                        result.imageUrl(image.getUrl());

                } else {

                        throw new OpenRouterException("OpenRouter response did not contain url or b64_json.");
                }

                return result.build();
        }

        private Map<String, Object> buildRequestBody(
                        GenerateImageRequest request) {

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", properties.resolveImageModel(request.getModel()));
                body.put("prompt", request.getPrompt());

                if (request.getImage() != null
                                && !request.getImage().isEmpty()) {

                        Map<String, Object> imageUrl = new LinkedHashMap<>();
                        imageUrl.put("url", buildDataUrl(request));

                        Map<String, Object> inputReference = new LinkedHashMap<>();
                        inputReference.put("type", "image_url");
                        inputReference.put("image_url", imageUrl);

                        body.put("input_references", Collections.singletonList(inputReference));
                }

                return body;
        }

        private String buildDataUrl(
                        GenerateImageRequest request) {

                String mimeType = request.getImage().getContentType();

                if (mimeType == null
                                || mimeType.isBlank()) {

                        mimeType = "image/png";
                }

                return "data:" + mimeType + ";base64," + ImageBase64Util.encode(request.getImage());
        }

        private byte[] decodeBase64Image(
                        String value) {

                String base64 = value;

                int commaIndex = value.indexOf(',');

                if (value.startsWith("data:")
                                && commaIndex >= 0) {

                        base64 = value.substring(commaIndex + 1);
                }

                return java.util.Base64.getDecoder().decode(base64);
        }

}
