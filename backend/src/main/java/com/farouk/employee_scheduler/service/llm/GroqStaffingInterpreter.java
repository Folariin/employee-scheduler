package com.farouk.employee_scheduler.service.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

@Component
public class GroqStaffingInterpreter implements StaffingInterpreter, AutoCloseable {
    private final String key, model;
    private final URI endpoint;
    private final HttpClient client;
    private final int timeout;
    private final Semaphore inFlight = new Semaphore(4);
    private final JsonMapper json = JsonMapper.builder().build();
    public GroqStaffingInterpreter(@Value("${app.llm.api-key:}") String key,
            @Value("${app.llm.model:openai/gpt-oss-20b}") String model,
            @Value("${app.llm.base-url:https://api.groq.com/openai/v1}") String base,
            @Value("${app.llm.connect-timeout-seconds:5}") int connect,
            @Value("${app.llm.timeout-seconds:30}") int timeout) {
        URI uri=URI.create(base);
        if ((!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) &&
                Set.of("127.0.0.1","localhost","[::1]").contains(uri.getHost())))
                || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || connect<1 || connect>30 || timeout<1 || timeout>120 || model.isBlank() || model.length()>150)
            throw new IllegalArgumentException("Invalid LLM endpoint, model or bounded timeouts");
        this.key=key; this.model=model; this.timeout=timeout;
        endpoint=URI.create(base.replaceAll("/+$", "")+"/chat/completions");
        client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connect)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Override public String interpret(LocalDate week,String instructions) {
        if(key.isBlank())throw failure(HttpStatus.SERVICE_UNAVAILABLE,"AI interpretation is not configured");
        if(!inFlight.tryAcquire())throw failure(HttpStatus.TOO_MANY_REQUESTS,"AI interpretation is busy; try again later");
        try {
            var body=Map.of("model",model,"temperature",0,"max_completion_tokens",4096,"stream",false,
                "messages",List.of(Map.of("role","system","content",InterpretationSchema.PROMPT+"\nSelected week: "+week+" through "+week.plusDays(6)),
                    Map.of("role","user","content",instructions)),
                "response_format",Map.of("type","json_schema","json_schema",Map.of("name","staffing_interpretation","strict",true,"schema",json.readTree(InterpretationSchema.JSON))));
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(timeout))
                .header("Authorization","Bearer "+key).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var future=client.sendAsync(request,info -> boundedBody());
            HttpResponse<byte[]> response;
            try { response=future.get(timeout,TimeUnit.SECONDS); }
            catch(TimeoutException e){future.cancel(true);throw failure(HttpStatus.GATEWAY_TIMEOUT,"AI provider timed out; no changes were made");}
            catch(InterruptedException e){future.cancel(true);throw e;}
            if(response.statusCode()==429)throw failure(HttpStatus.TOO_MANY_REQUESTS,"AI provider rate limit reached; try again later");
            if(response.statusCode()!=200)throw failure(HttpStatus.BAD_GATEWAY,"AI provider failed; no changes were made");
            var root=json.readTree(response.body());
            var choice=root.path("choices").path(0);
            if(!"stop".equals(choice.path("finish_reason").asString()) || !choice.path("message").path("content").isString())
                throw failure(HttpStatus.BAD_GATEWAY,"AI provider returned an incomplete response");
            return choice.path("message").path("content").asString();
        } catch(ResponseStatusException e){throw e;}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw failure(HttpStatus.SERVICE_UNAVAILABLE,"AI request interrupted");}
        catch(ExecutionException e){
            if(e.getCause() instanceof HttpTimeoutException)throw failure(HttpStatus.GATEWAY_TIMEOUT,"AI provider timed out; no changes were made");
            throw failure(HttpStatus.BAD_GATEWAY,"AI provider returned an invalid response or is unavailable");
        }
        catch(Exception e){throw failure(HttpStatus.BAD_GATEWAY,"AI provider returned an invalid response or is unavailable");}
        finally {inFlight.release();}
    }
    // Bound bytes while streaming, before Jackson sees them. The outer timeout covers the complete body.
    private static HttpResponse.BodySubscriber<byte[]> boundedBody() {
        var downstream=HttpResponse.BodySubscribers.ofByteArray();
        return new HttpResponse.BodySubscriber<>() {
            private Flow.Subscription subscription; private int size;
            public CompletionStage<byte[]> getBody(){return downstream.getBody();}
            public void onSubscribe(Flow.Subscription s){subscription=s;downstream.onSubscribe(s);}
            public void onNext(List<ByteBuffer> chunks){
                for(var chunk:chunks)size+=chunk.remaining();
                if(size>65536){subscription.cancel();downstream.onError(new IllegalStateException("Provider response too large"));}
                else downstream.onNext(chunks);
            }
            public void onError(Throwable t){downstream.onError(t);}
            public void onComplete(){downstream.onComplete();}
        };
    }
    @jakarta.annotation.PreDestroy
    @Override public void close(){client.close();}
    private static ResponseStatusException failure(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
