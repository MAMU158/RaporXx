package com.raporx.model;

import java.util.UUID;

/** Raporlanan (veya aranan) oyuncunun çevrimdışı da olsa geçerli kimliği. */
public record TargetRef(UUID uuid, String name) {
}
