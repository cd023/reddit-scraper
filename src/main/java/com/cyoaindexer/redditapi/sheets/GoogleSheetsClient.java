package com.cyoaindexer.redditapi.sheets;

import com.cyoaindexer.redditapi.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class GoogleSheetsClient {
    private static final String SHEETS_BASE = "https://sheets.googleapis.com/v4/spreadsheets/";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCOPE = "https://www.googleapis.com/auth/spreadsheets";

    private final AppConfig config;
    private final HttpClient httpClient;
    private AccessToken cachedToken;

    public GoogleSheetsClient(AppConfig config) {
        this(config, HttpClient.newHttpClient());
    }

    public GoogleSheetsClient(AppConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    public void ensureHeader(String tab, List<String> columns) {
        List<List<String>> rows = readRows(tab);
        if (rows.isEmpty()) {
            replaceValues(tab, List.of(columns));
        }
    }

    public List<List<String>> readRows(String tab) {
        JsonNode root = requestJson("GET", "/values/" + encodedRange(tab, "A:Z"), null);
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode row : root.path("values")) {
            List<String> cells = new ArrayList<>();
            row.forEach(cell -> cells.add(cell.asText("")));
            rows.add(cells);
        }
        return rows;
    }

    public void appendRows(String tab, List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        requestJson(
                "POST",
                "/values/" + encodedRange(tab, "A1") + ":append?valueInputOption=RAW&insertDataOption=INSERT_ROWS",
                Map.of("values", rows)
        );
    }

    public void replaceValues(String tab, List<List<String>> rows) {
        requestJson("POST", "/values:batchClear", Map.of("ranges", List.of(tab)));
        requestJson(
                "PUT",
                "/values/" + encodedRange(tab, "A1") + "?valueInputOption=RAW",
                Map.of("values", rows == null ? List.of() : rows)
        );
    }

    private JsonNode requestJson(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(SHEETS_BASE + config.googleSheetId() + path))
                    .header("Authorization", "Bearer " + accessToken())
                    .header("Content-Type", "application/json");

            if ("GET".equals(method)) {
                builder.GET();
            } else {
                String json = body == null ? "" : JSON.writeValueAsString(body);
                builder.method(method, HttpRequest.BodyPublishers.ofString(json));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Google Sheets request failed: " + response.statusCode() + " " + response.body());
            }
            if (response.body() == null || response.body().isBlank()) {
                return JSON.createObjectNode();
            }
            return JSON.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Google Sheets request failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Sheets request was interrupted.", exception);
        }
    }

    private synchronized String accessToken() {
        if (cachedToken != null && cachedToken.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return cachedToken.value();
        }

        try {
            JsonNode credentials = JSON.readTree(resolveCredentialJson());
            String clientEmail = credentials.path("client_email").asText("");
            String privateKeyPem = credentials.path("private_key").asText("").replace("\\n", "\n");
            String tokenUri = credentials.path("token_uri").asText("https://oauth2.googleapis.com/token");
            if (clientEmail.isBlank() || privateKeyPem.isBlank()) {
                throw new IllegalStateException("Google service account JSON must include client_email and private_key.");
            }

            long now = Instant.now().getEpochSecond();
            String jwt = createJwt(clientEmail, privateKeyPem, tokenUri, now);
            String body = "grant_type=" + urlEncode("urn:ietf:params:oauth:grant-type:jwt-bearer")
                    + "&assertion=" + urlEncode(jwt);
            HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUri))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Google token request failed: " + response.statusCode() + " " + response.body());
            }
            JsonNode token = JSON.readTree(response.body());
            String value = token.path("access_token").asText("");
            long expiresIn = token.path("expires_in").asLong(3600);
            if (value.isBlank()) {
                throw new IllegalStateException("Google token response did not include an access token.");
            }
            cachedToken = new AccessToken(value, Instant.now().plusSeconds(Math.max(60, expiresIn - 60)));
            return value;
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("Google authentication failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google authentication was interrupted.", exception);
        }
    }

    private String resolveCredentialJson() throws IOException {
        if (config.googleServiceAccountJson().isPresent()) {
            String raw = config.googleServiceAccountJson().get();
            if (raw.trim().startsWith("{")) {
                return raw;
            }
            return Files.readString(Path.of(raw));
        }
        return Files.readString(Path.of(config.googleApplicationCredentials().orElseThrow()));
    }

    private static String createJwt(String clientEmail, String privateKeyPem, String tokenUri, long now)
            throws IOException, GeneralSecurityException {
        String header = base64Url(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
        String payload = base64Url(JSON.writeValueAsBytes(Map.of(
                "iss", clientEmail,
                "scope", SCOPE,
                "aud", tokenUri,
                "iat", now,
                "exp", now + 3600
        )));
        String signingInput = header + "." + payload;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey(privateKeyPem));
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(signature.sign());
    }

    private static PrivateKey privateKey(String pem) throws GeneralSecurityException {
        String clean = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(clean);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private static String encodedRange(String tab, String range) {
        return urlEncode(tab + "!" + range);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record AccessToken(String value, Instant expiresAt) {
    }
}
