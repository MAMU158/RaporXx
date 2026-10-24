package com.raporx.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Report {
    public enum Status { OPEN, RESOLVED, REJECTED }

    public int id;
    public UUID reporterUuid;
    public String reporterName;
    public UUID targetUuid;
    public String targetName;
    public String category;
    public String subType;
    public String reason;
    public long createdAt;
    public Status status = Status.OPEN;
    public String handledBy;
    public long handledAt;
    public String note;
    public String punishment;
    /** Rapor anındaki son mesajlar (eskiden yeniye). */
    public List<ChatEntry> chat = new ArrayList<>();
    /** Rapor anındaki anti-cheat kayıtları (yeniden eskiye). */
    public List<FlagEntry> flags = new ArrayList<>();
    public boolean auto;
    public String server;
    /** Rapor anındaki oyuncu durumu (çevrimiçi/çevrimdışı, konum, ping). */
    public String info;
}
