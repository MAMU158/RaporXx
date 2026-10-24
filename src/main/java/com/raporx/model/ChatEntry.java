package com.raporx.model;

/** Oyuncunun tek bir mesajı veya komutu. */
public record ChatEntry(long time, String message) {
}
