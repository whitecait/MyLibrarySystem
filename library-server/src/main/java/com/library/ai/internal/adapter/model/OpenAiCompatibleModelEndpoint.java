package com.library.ai.internal.adapter.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.ai.contract.ModelEndpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/** OpenAI-compatible Chat Completions Adapter；线格式固定 thinking.type=disabled。 */
@Component
public class OpenAiCompatibleModelEndpoint implements ModelEndpoint {
    private final HttpClient http=HttpClient.newBuilder().build();
    private final ObjectMapper json;
    private final String endpoint;
    private final String model;
    private final String apiKey;
    public OpenAiCompatibleModelEndpoint(ObjectMapper json,
            @Value("${library.ai.endpoint-url:}") String endpoint,
            @Value("${library.ai.model:}") String model,
            @Value("${library.ai.api-key:}") String apiKey) {
        this.json=json; this.endpoint=endpoint; this.model=model; this.apiKey=apiKey;
    }
    @Override public ModelResponse complete(ModelRequest request) throws ModelFailure {
        if (endpoint.isBlank() || model.isBlank() || apiKey.isBlank()) throw new ModelUnavailable("AI 端点未配置");
        try {
            var toolSchemas=request.tools().stream().map(t->Map.of("type","function","function",Map.of(
                    "name",t.name(),"description",t.level()+" "+t.requiredCapability(),
                    "parameters",parseSchema(t.inputSchema())))).toList();
            var body=new LinkedHashMap<String,Object>();
            body.put("model",model); body.put("messages",request.messages()); body.put("tools",toolSchemas);
            body.put("thinking",Map.of("type","disabled"));
            var httpRequest=HttpRequest.newBuilder(URI.create(endpoint)).timeout(request.timeout())
                    .header("Authorization","Bearer "+apiKey).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response=http.send(httpRequest,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300) throw new ModelFailure("模型端点 HTTP "+response.statusCode());
            JsonNode message=json.readTree(response.body()).at("/choices/0/message");
            var calls=new ArrayList<ToolCall>();
            message.path("tool_calls").forEach(call->{
                try { calls.add(new ToolCall(call.at("/function/name").asText(),
                        json.convertValue(json.readTree(call.at("/function/arguments").asText("{}")),Map.class))); }
                catch(Exception e){ throw new ModelFailure("模型工具参数非法",e); }
            });
            return new ModelResponse(message.path("content").asText(""),List.of(),calls);
        } catch(ModelFailure e){throw e;} catch(Exception e){throw new ModelFailure("模型端点失败",e);}
    }
    private JsonNode parseSchema(String value) {
        try { return json.readTree(value); }
        catch (Exception e) { throw new ModelFailure("工具 schema 非法", e); }
    }
}
