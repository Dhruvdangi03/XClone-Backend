package com.project.Xclone_backend.report;

import jakarta.validation.constraints.NotNull;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record ReportRequest(@NotNull ReportReason reason) {
    }
}
