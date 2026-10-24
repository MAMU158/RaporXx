package com.raporx.model;

/** Hile korumasından gelen tek bir flag kaydı. */
public record FlagEntry(long time, String check, double vl, String details) {
}
