package com.raporx.model;

/** Menülerde adım adım doldurulan, henüz gönderilmemiş rapor. */
public final class ReportDraft {
    public final TargetRef target;
    public String category;
    public String subType;
    public String reason;

    public ReportDraft(TargetRef target) {
        this.target = target;
    }
}
