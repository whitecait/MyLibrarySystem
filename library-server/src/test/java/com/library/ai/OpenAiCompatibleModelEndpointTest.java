package com.library.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.ai.contract.ModelEndpoint;
import com.library.ai.internal.adapter.model.OpenAiCompatibleModelEndpoint;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** IT-AI-004：真实 HTTP 线格式固定 thinking={type:disabled}。 */
class OpenAiCompatibleModelEndpointTest {
    @Test void itAi004_productionAdapterSendsDisabledThinkingProfile() throws Exception {
        var captured=new AtomicReference<String>();var server=HttpServer.create(new InetSocketAddress(0),0);
        server.createContext("/chat",exchange->{captured.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));var response="{\"choices\":[{\"message\":{\"content\":\"ok\",\"tool_calls\":[]}}]}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();});server.start();
        try {var endpoint=new OpenAiCompatibleModelEndpoint(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort()+"/chat","test-model","secret");var result=endpoint.complete(new ModelEndpoint.ModelRequest(List.of(Map.of("role","user","content","hello")),List.of(),Map.of(),Duration.ofSeconds(8)));assertThat(result.answer()).isEqualTo("ok");var body=new ObjectMapper().readTree(captured.get());assertThat(body.at("/thinking/type").asText()).isEqualTo("disabled");assertThat(body.path("model").asText()).isEqualTo("test-model");} finally {server.stop(0);}
    }
}
