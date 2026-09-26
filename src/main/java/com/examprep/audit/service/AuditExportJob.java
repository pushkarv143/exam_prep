package com.examprep.audit.service;

import com.examprep.audit.dto.AuditDtos;
import com.examprep.jobs.JobContext;
import com.examprep.jobs.JobHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/** Background job "audit.export": writes matching audit rows to a CSV artifact (max 200k rows). */
@Component
@RequiredArgsConstructor
public class AuditExportJob implements JobHandler {

    public static final String TYPE = "audit.export";
    public static final int MAX_ROWS = 200_000;
    private static final String[] HEADER = {"occurred_at", "actor_email", "actor_roles", "action", "entity_type",
            "entity_id", "outcome", "status_code", "error_code", "reason", "changes", "http_method", "path", "ip",
            "user_agent", "request_id", "id"};

    private final AuditQueryService query;
    private final ObjectMapper json;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public int maxAttempts() {
        return 2;
    }

    @Override
    public JsonNode run(JobContext ctx) throws IOException {
        AuditDtos.AuditFilter filter = json.treeToValue(ctx.params(), AuditDtos.AuditFilter.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});   // UTF-8 BOM so Excel opens it correctly
        AtomicInteger count = new AtomicInteger();
        var mapper = query.mapper();
        try (CSVPrinter csv = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader(HEADER).build())) {
            ctx.progress(1, "Reading audit log");
            query.stream(filter, MAX_ROWS, rs -> {
                AuditDtos.AuditEntryDto e = mapper.mapRow(rs, count.get());
                try {
                    csv.printRecord(DateTimeFormatter.ISO_INSTANT.format(e.occurredAt()), e.actorEmail(),
                            e.actorRoles(), e.action(), e.entityType(), e.entityId(), e.outcome(), e.statusCode(),
                            e.errorCode(), e.reason(), e.changes() == null ? "" : e.changes().toString(),
                            e.httpMethod(), e.path(), e.ip(), e.userAgent(), e.requestId(), e.id());
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
                int n = count.incrementAndGet();
                if (n % 2000 == 0) {
                    ctx.checkCancelled();
                    ctx.progress(Math.min(95, 5 + n * 90 / MAX_ROWS), n + " rows written");
                }
            });
        }
        String filename = "audit-log-" + ctx.jobId().toString().substring(0, 8) + ".csv";
        ctx.saveArtifact(filename, "text/csv; charset=utf-8", out.toByteArray());
        ctx.progress(100, count.get() + " rows exported");
        return json.createObjectNode().put("rows", count.get()).put("truncated", count.get() >= MAX_ROWS);
    }
}
