import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic OpenAI-compatible boundary used only by the Compose integration gate. */
public final class ModelStub {
    private static final AtomicInteger requests = new AtomicInteger();
    private static final AtomicInteger invalidThinking = new AtomicInteger();
    private static final AtomicInteger renewalToolCalls = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(8081), 0);
        server.createContext("/health", exchange -> respond(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/stats", exchange -> respond(exchange, 200,
                "{\"requests\":" + requests.get() + ",\"invalidThinking\":" + invalidThinking.get()
                        + ",\"renewalToolCalls\":" + renewalToolCalls.get() + "}"));
        server.createContext("/v1/chat/completions", ModelStub::complete);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    private static void complete(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"method\"}");
            return;
        }
        var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.incrementAndGet();
        if (!body.contains("\"thinking\"") || !body.contains("\"type\":\"disabled\"")) {
            invalidThinking.incrementAndGet();
            respond(exchange, 400, "{\"error\":\"thinking must be disabled\"}");
            return;
        }
        if (body.contains("查询我的借阅") && body.contains("\"name\":\"get_my_business\"")) {
            respond(exchange, 400, "{\"error\":\"visitor received account tool\"}");
            return;
        }
        if (body.contains("帮我预填续借") && !body.contains("\"role\":\"tool\"")) {
            renewalToolCalls.incrementAndGet();
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"","tool_calls":[{"id":"integration-renewal-1","type":"function","function":{"name":"prefill_renewal","arguments":"{\\"txId\\":\\"4001\\"}"}}]}}]}
                    """);
            return;
        }
        var answer = body.contains("帮我预填续借") ? "请核对后提交" : "脚本化集成回答";
        respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"" + answer
                + "\",\"tool_calls\":[]}}]}");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
