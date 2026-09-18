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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** IT-AI-004：真实 HTTP 线格式固定 thinking={type:disabled}。 */
class OpenAiCompatibleModelEndpointTest {
    @Test void itAi004_productionAdapterSendsDisabledThinkingProfile() throws Exception {
        var captured=new CopyOnWriteArrayList<String>();var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress(0),0);
        server.createContext("/chat",exchange->{captured.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));var payload=calls.getAndIncrement()==0?"{\"choices\":[{\"message\":{\"content\":\"\",\"tool_calls\":[{\"id\":\"call-provider-7\",\"type\":\"function\",\"function\":{\"name\":\"search_titles\",\"arguments\":\"{\\\"q\\\":\\\"db\\\"}\"}}]}}]}":"{\"choices\":[{\"message\":{\"content\":\"done\",\"tool_calls\":[]}}]}";var response=payload.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();});server.start();
        try {var mapper=new ObjectMapper();var endpoint=new OpenAiCompatibleModelEndpoint(mapper,"http://127.0.0.1:"+server.getAddress().getPort()+"/chat","test-model","secret");var first=endpoint.complete(new ModelEndpoint.ModelRequest(List.of(Map.of("role","user","content","hello")),List.of(),Map.of(),Duration.ofSeconds(8)));assertThat(first.toolCalls()).singleElement().satisfies(call->{assertThat(call.id()).isEqualTo("call-provider-7");assertThat(call.arguments()).containsEntry("q","db");});var followup=List.<Map<String,Object>>of(Map.of("role","assistant","content","","tool_calls",List.of(Map.of("id",first.toolCalls().getFirst().id(),"type","function","function",Map.of("name","search_titles","arguments","{\"q\":\"db\"}")))),Map.of("role","tool","tool_call_id",first.toolCalls().getFirst().id(),"name","search_titles","content","{}"));assertThat(endpoint.complete(new ModelEndpoint.ModelRequest(followup,List.of(),Map.of(),Duration.ofSeconds(8))).answer()).isEqualTo("done");var initial=mapper.readTree(captured.getFirst());assertThat(initial.at("/thinking/type").asText()).isEqualTo("disabled");assertThat(initial.path("model").asText()).isEqualTo("test-model");var later=mapper.readTree(captured.get(1));assertThat(later.at("/messages/0/tool_calls/0/id").asText()).isEqualTo("call-provider-7");assertThat(later.at("/messages/0/tool_calls/0/function/arguments").isTextual()).isTrue();assertThat(later.at("/messages/1/tool_call_id").asText()).isEqualTo("call-provider-7");} finally {server.stop(0);}
    }
}
