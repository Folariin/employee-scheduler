package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.GenerationPreview;
import com.farouk.employee_scheduler.dto.GenerationRequest;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Bounded, short-lived, single-instance previews. Tokens never authorize arbitrary assignments. */
@Component
public class GenerationPreviewStore {
    public static final Duration LIFETIME=Duration.ofMinutes(10);
    private final Clock clock;
    private final LinkedHashMap<UUID,Stored> entries=new LinkedHashMap<>();
    public record Stored(String owner, String fingerprint, GenerationRequest request, GenerationPreview preview) {}
    public GenerationPreviewStore(){this(Clock.systemUTC());}
    GenerationPreviewStore(Clock clock){this.clock=clock;}
    public Instant now(){return clock.instant();}
    public synchronized void put(Stored entry) {
        entries.values().removeIf(e->!e.preview().expiresAt().isAfter(now()));
        var owned=entries.entrySet().stream().filter(e->e.getValue().owner().equals(entry.owner())).map(Map.Entry::getKey).toList();
        for(int i=0;i<=owned.size()-8;i++)entries.remove(owned.get(i));
        while(entries.size()>=256)entries.remove(entries.keySet().iterator().next());
        entries.put(entry.preview().previewToken(),entry);
    }
    public synchronized Stored get(UUID token,UUID scheduleId,String owner) {
        var stored=entries.get(token);
        if(stored==null || !stored.owner().equals(owner) || !stored.preview().scheduleId().equals(scheduleId)
                || !stored.preview().expiresAt().isAfter(now()))
            throw new ResponseStatusException(CONFLICT,"Preview expired, belongs to another session/week, or was already applied; generate a new preview");
        return stored;
    }
    public synchronized void remove(UUID token){entries.remove(token);}
}
