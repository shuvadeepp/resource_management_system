package tech.csm.llm;
 

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tech.csm.dto.LlmPayload;

@Component
public class HttpLlmClient implements LlmClient {

    private final RestClient restClient;

    public HttpLlmClient(@Value("${llm.api.url:http://localhost:8000/api/match}") String llmUrl,
                         @Value("${llm.api.connect-timeout-ms:5000}") int connectTimeoutMs,
                         @Value("${llm.api.read-timeout-ms:60000}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(llmUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public String analyze(LlmPayload payload) {
        return restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
    }
}