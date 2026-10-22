package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GenerationPreviewStoreTest {
    static class MutableClock extends Clock {
        Instant time=Instant.parse("2026-10-26T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return time;}
    }
    @Test void expirationAndOwnerWeekBindingAreEnforced() {
        var clock=new MutableClock();var store=new GenerationPreviewStore(clock);UUID week=UUID.randomUUID(),token=UUID.randomUUID();
        var preview=new GenerationPreview(token,week,clock.instant().plus(GenerationPreviewStore.LIFETIME),List.of(),List.of(),List.of());
        store.put(new GenerationPreviewStore.Stored("admin","digest",null,preview));
        assertNotNull(store.get(token,week,"admin"));
        assertThrows(ResponseStatusException.class,()->store.get(token,week,"other"));
        assertThrows(ResponseStatusException.class,()->store.get(token,UUID.randomUUID(),"admin"));
        clock.time=preview.expiresAt();assertThrows(ResponseStatusException.class,()->store.get(token,week,"admin"));
    }
    @Test void perOwnerBoundEvictsOldestRatherThanGrowingWithoutLimit() {
        var store=new GenerationPreviewStore();UUID week=UUID.randomUUID();var tokens=new ArrayList<UUID>();
        for(int i=0;i<9;i++) {
            UUID token=UUID.randomUUID();tokens.add(token);
            store.put(new GenerationPreviewStore.Stored("admin","digest",null,new GenerationPreview(token,week,store.now().plusSeconds(600),List.of(),List.of(),List.of())));
        }
        assertThrows(ResponseStatusException.class,()->store.get(tokens.getFirst(),week,"admin"));assertNotNull(store.get(tokens.getLast(),week,"admin"));
    }
    @Test void globalBoundEvictsOldestAcrossDifferentAdmins() {
        var store=new GenerationPreviewStore();UUID week=UUID.randomUUID(),first=null,last=null;
        for(int i=0;i<257;i++) {
            UUID token=UUID.randomUUID();if(i==0)first=token;last=token;
            store.put(new GenerationPreviewStore.Stored("admin"+i,"digest",null,new GenerationPreview(token,week,store.now().plusSeconds(600),List.of(),List.of(),List.of())));
        }
        UUID oldest=first;
        assertThrows(ResponseStatusException.class,()->store.get(oldest,week,"admin0"));assertNotNull(store.get(last,week,"admin256"));
    }
}
