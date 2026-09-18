package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.databind.*;
import com.ff.mycloud.ai.agent.plan.AgentPlanParser;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real LangChain4j HTTP serialization against a local OpenAI-compatible fixture; no external API. */
class AgentModelProtocolTest {
    @Test void realProviderAdapterPlansCallsToolsAndParsesUsage() throws Exception {
        ObjectMapper json=new ObjectMapper();
        List<JsonNode> requests=new CopyOnWriteArrayList<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{
            requests.add(json.readTree(exchange.getRequestBody()));
            int index=requests.size();
            Map<String,Object> message=new LinkedHashMap<>();message.put("role","assistant");
            if(index==2) {
                message.put("tool_calls",List.of(Map.of("id","call-1","type","function","function",Map.of("name","listFiles","arguments","{}"))));
            } else message.put("content",index==1?"{\"steps\":[\"列出文件\"]}":"report.txt");
            byte[] body=json.writeValueAsBytes(Map.of("id","fixture","object","chat.completion","created",1,"model","fixture-model","choices",List.of(Map.of("index",0,"message",message,"finish_reason",index==2?"tool_calls":"stop")),"usage",Map.of("prompt_tokens",11,"completion_tokens",4,"total_tokens",15)));
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try {
            var model=OpenAiChatModel.builder().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/v1").apiKey("local-fixture").modelName("fixture-model").timeout(Duration.ofSeconds(5)).maxRetries(0).build();
            var store=mock(AgentRunStore.class);when(store.save(any())).thenReturn(true);
            var run=new AgentRun();run.setUserId(1L);run.setConversationId(10L);run.setMessage("列出文件");run.setDeadline(LocalDateTime.now().plusMinutes(1));
            var tools=new AgentEngineTest.TestTools();
            new AgentEngine(new AgentProperties(),new AgentPlanParser(json),store).execute(run,model,List.of(tools),List.of(),(n,d)->{});
            assertEquals(3,requests.size());assertEquals(1,tools.calls);assertEquals(45,run.totalTokens());
            assertEquals("listFiles",requests.get(1).path("tools").get(0).path("function").path("name").asText());
            assertTrue(requests.get(2).path("messages").toString().contains("tool_call_id"));
            assertEquals(2048,requests.get(1).path("max_tokens").asInt());
        } finally { server.stop(0); }
    }
}
