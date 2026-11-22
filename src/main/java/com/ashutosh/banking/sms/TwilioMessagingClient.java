package com.ashutosh.banking.sms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class TwilioMessagingClient {
    private final boolean enabled;
    private final String accountSid;
    private final String authToken;
    private final String verifyServiceSid;
    private final String messagingServiceSid;
    private final RestClient rest;

    public TwilioMessagingClient(
            @Value("${vaultcore.sms.enabled:false}") boolean enabled,
            @Value("${TWILIO_ACCOUNT_SID:}") String accountSid,
            @Value("${TWILIO_AUTH_TOKEN:}") String authToken,
            @Value("${TWILIO_VERIFY_SERVICE_SID:}") String verifyServiceSid,
            @Value("${TWILIO_MESSAGING_SERVICE_SID:}") String messagingServiceSid) {
        this.enabled = enabled;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.verifyServiceSid = verifyServiceSid;
        this.messagingServiceSid = messagingServiceSid;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    private void requireEnabled() {
        if (!enabled || accountSid.isBlank() || authToken.isBlank()) {
            throw new IllegalStateException("SMS provider not configured");
        }
    }

    private Map<?, ?> postForm(String url, MultiValueMap<String, String> data) {
        return rest.post().uri(url)
                .headers(headers -> headers.setBasicAuth(accountSid, authToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(data)
                .retrieve()
                .body(Map.class);
    }

    public void startVerification(String phone) {
        requireEnabled();
        if (verifyServiceSid.isBlank()) throw new IllegalStateException("Twilio Verify service not configured");
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("To", phone);
        data.add("Channel", "sms");
        postForm("https://verify.twilio.com/v2/Services/" + verifyServiceSid + "/Verifications", data);
    }

    public boolean checkVerification(String phone, String code) {
        requireEnabled();
        if (verifyServiceSid.isBlank()) throw new IllegalStateException("Twilio Verify service not configured");
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("To", phone);
        data.add("Code", code);
        Map<?, ?> result = postForm("https://verify.twilio.com/v2/Services/"
                + verifyServiceSid + "/VerificationCheck", data);
        return result != null && "approved".equals(result.get("status"));
    }

    public void sendTransactionAlert(String phone, String message) {
        requireEnabled();
        if (messagingServiceSid.isBlank()) throw new IllegalStateException("Twilio Messaging service not configured");
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("To", phone);
        data.add("MessagingServiceSid", messagingServiceSid);
        data.add("Body", message);
        postForm("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json", data);
    }
}
