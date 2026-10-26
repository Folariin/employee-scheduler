package com.farouk.employee_scheduler.service.llm;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class GroqStaffingInterpreterTest {
 private final java.util.List<GroqStaffingInterpreter> clients=new java.util.ArrayList<>();
 private HttpServer server;private ExecutorService executor;private String base;
 private final AtomicInteger status=new AtomicInteger(200),delay=new AtomicInteger();
 private final AtomicReference<String> response=new AtomicReference<>("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}]}");
 private final AtomicReference<String> request=new AtomicReference<>(),authorization=new AtomicReference<>();
 @BeforeEach void start() throws Exception {server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(executor);server.createContext("/openai/v1/chat/completions",exchange->{try{request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));Thread.sleep(delay.get());byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status.get(),bytes.length);exchange.getResponseBody().write(bytes);}catch(Exception ignored){}finally{exchange.close();}});server.start();base="http://127.0.0.1:"+server.getAddress().getPort()+"/openai/v1";}
 @AfterEach void stop(){server.stop(0);executor.shutdownNow();clients.forEach(GroqStaffingInterpreter::close);}
 private GroqStaffingInterpreter client(int timeout){var client=new GroqStaffingInterpreter("mock-test-key","test-model",base,1,timeout);clients.add(client);return client;}
 @Test void sendsStrictSchemaWithoutToolsOrRoster() {assertEquals("{}",client(5).interpret(LocalDate.of(2026,10,26),"Three Monday"));var body=JsonMapper.builder().build().readTree(request.get());assertEquals("Bearer mock-test-key",authorization.get());assertEquals("json_schema",body.get("response_format").get("type").asString());assertTrue(body.get("response_format").get("json_schema").get("strict").asBoolean());assertEquals(4096,body.get("max_completion_tokens").asInt());assertFalse(body.has("tools"));assertEquals("user",body.get("messages").get(1).get("role").asString());assertFalse(request.get().contains("mock-test-key"));}
 @ParameterizedTest @ValueSource(ints={400,401,403,500,503}) void providerFailuresAreSanitized(int code){status.set(code);response.set("secret provider diagnostic");var error=assertThrows(ResponseStatusException.class,()->client(5).interpret(LocalDate.now(),"test"));assertEquals(502,error.getStatusCode().value());assertFalse(error.toString().contains("secret provider diagnostic"));}
 @Test void rateLimitReturns429(){status.set(429);assertEquals(429,assertThrows(ResponseStatusException.class,()->client(5).interpret(LocalDate.now(),"test")).getStatusCode().value());}
 @Test void timeoutReturns504(){delay.set(2000);assertEquals(504,assertThrows(ResponseStatusException.class,()->client(1).interpret(LocalDate.now(),"test")).getStatusCode().value());}
 @ParameterizedTest @ValueSource(strings={"not json","{}","{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}"}) void invalidOrTruncatedResponseRejected(String value){response.set(value);assertEquals(502,assertThrows(ResponseStatusException.class,()->client(5).interpret(LocalDate.now(),"test")).getStatusCode().value());}
 @Test void boundsBodyBeforeParsing(){response.set("x".repeat(70000));assertEquals(502,assertThrows(ResponseStatusException.class,()->client(5).interpret(LocalDate.now(),"test")).getStatusCode().value());}
 @Test void missingKeyDoesNotCallProvider(){var client=new GroqStaffingInterpreter("","model",base,1,1);assertEquals(503,assertThrows(ResponseStatusException.class,()->client.interpret(LocalDate.now(),"test")).getStatusCode().value());assertNull(request.get());}
 @Test void rejectsUnsafeConfiguration(){for(String url:new String[]{"http://example.com","https://user:password@example.com","https://example.com?key=secret"})assertThrows(IllegalArgumentException.class,()->new GroqStaffingInterpreter("test","model",url,1,5));assertThrows(IllegalArgumentException.class,()->new GroqStaffingInterpreter("test","model",base,1,121));}
}
