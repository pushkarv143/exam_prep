package com.examprep.attempt.store;

import com.examprep.attempt.AttemptProperties;
import com.examprep.attempt.entity.AnswerState;
import com.examprep.attempt.entity.Attempt;
import com.examprep.attempt.entity.AttemptStatus;
import com.examprep.attempt.model.StudentAnswer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * All Redis state of running attempts. During a test this is the <b>source of truth</b>.
 * Postgres is written once, at submit. Redis runs with AOF and {@code noeviction}
 * (see docker-compose), so accepted autosaves survive a Redis restart and are never evicted.
 *
 * <pre>
 * attempt:{id}:meta     hash   userId testId startedAt deadline status seed shuffleQ shuffleO tab fs
 * attempt:{id}:answers  hash   q:{qid} -> {"a":answer,"st":state}   s:{qid} -> seq   t:{qid} -> seconds   v:{qid} -> visits
 * attempt:{id}:events   list   anti-cheat events (JSON), capped
 * attempts:deadlines    zset   attemptId scored by deadline (epoch ms), drained by the auto-submit scheduler
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class AttemptRedisStore {

    public static final long NOT_IN_PROGRESS = -1;
    public static final long EXPIRED = -2;
    public static final long META_MISSING = -3;

    private static final String DEADLINES = "attempts:deadlines";

    /**
     * Applies a batch of answer changes atomically.
     * KEYS: answers, meta. ARGV: nowMs, graceMs, then 5-tuples (qid, seq, payload, seconds, visits).
     * A change is applied only if its seq is >= the stored seq, so a late retry from the
     * client's offline queue can never overwrite a newer answer. Time and visits merge as
     * max(), so they never go backwards.
     */
    private static final RedisScript<Long> APPLY = new DefaultRedisScript<>("""
            local status = redis.call('HGET', KEYS[2], 'status')
            if not status then return -3 end
            if status ~= 'IN_PROGRESS' then return -1 end
            local deadline = tonumber(redis.call('HGET', KEYS[2], 'deadline'))
            if tonumber(ARGV[1]) > deadline + tonumber(ARGV[2]) then return -2 end
            local applied = 0
            local i = 3
            while i <= #ARGV do
              local qid = ARGV[i]
              local seq = tonumber(ARGV[i + 1])
              local cur = tonumber(redis.call('HGET', KEYS[1], 's:' .. qid) or '-1')
              if seq >= cur then
                redis.call('HSET', KEYS[1], 'q:' .. qid, ARGV[i + 2])
                redis.call('HSET', KEYS[1], 's:' .. qid, ARGV[i + 1])
                applied = applied + 1
              end
              local t = tonumber(ARGV[i + 3])
              if t > tonumber(redis.call('HGET', KEYS[1], 't:' .. qid) or '0') then
                redis.call('HSET', KEYS[1], 't:' .. qid, ARGV[i + 3])
              end
              local v = tonumber(ARGV[i + 4])
              if v > tonumber(redis.call('HGET', KEYS[1], 'v:' .. qid) or '0') then
                redis.call('HSET', KEYS[1], 'v:' .. qid, ARGV[i + 4])
              end
              i = i + 5
            end
            return applied
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AttemptProperties props;

    public record AttemptMeta(UUID attemptId, UUID userId, UUID testId, Instant startedAt, Instant deadline,
                              AttemptStatus status, long seed, boolean shuffleQuestions, boolean shuffleOptions,
                              int tabSwitches, int fullscreenExits) {
    }

    public record SavedAnswer(StudentAnswer answer, AnswerState state, long seq, int timeSpentSeconds, int visits) {
    }

    public record AnswerWrite(UUID questionId, long seq, StudentAnswer answer, AnswerState state,
                              int timeSpentSeconds, int visits) {
    }

    // ------------------------------------------------------------------ lifecycle

    public void init(Attempt a, boolean shuffleQuestions, boolean shuffleOptions) {
        String meta = metaKey(a.getId());
        Map<String, String> fields = new HashMap<>();
        fields.put("userId", a.getUserId().toString());
        fields.put("testId", a.getTestId().toString());
        fields.put("startedAt", String.valueOf(a.getStartedAt().toEpochMilli()));
        fields.put("deadline", String.valueOf(a.getDeadlineAt().toEpochMilli()));
        fields.put("status", a.getStatus().name());
        fields.put("seed", String.valueOf(a.getShuffleSeed()));
        fields.put("shuffleQ", shuffleQuestions ? "1" : "0");
        fields.put("shuffleO", shuffleOptions ? "1" : "0");
        fields.put("tab", String.valueOf(a.getTabSwitchCount()));
        fields.put("fs", String.valueOf(a.getFullscreenExitCount()));
        redis.opsForHash().putAll(meta, fields);
        Instant expireAt = a.getDeadlineAt().plus(Duration.ofDays(2));
        redis.expireAt(meta, expireAt);
        redis.expireAt(answersKey(a.getId()), expireAt);
        redis.expireAt(eventsKey(a.getId()), expireAt);
        if (a.getStatus() == AttemptStatus.IN_PROGRESS) {
            redis.opsForZSet().add(DEADLINES, a.getId().toString(), a.getDeadlineAt().toEpochMilli());
        }
    }

    public Optional<AttemptMeta> meta(UUID attemptId) {
        Map<Object, Object> m = redis.opsForHash().entries(metaKey(attemptId));
        if (m.isEmpty() || m.get("status") == null) {
            return Optional.empty();
        }
        return Optional.of(new AttemptMeta(attemptId,
                UUID.fromString((String) m.get("userId")),
                UUID.fromString((String) m.get("testId")),
                Instant.ofEpochMilli(Long.parseLong((String) m.get("startedAt"))),
                Instant.ofEpochMilli(Long.parseLong((String) m.get("deadline"))),
                AttemptStatus.valueOf((String) m.get("status")),
                Long.parseLong((String) m.get("seed")),
                "1".equals(m.get("shuffleQ")),
                "1".equals(m.get("shuffleO")),
                intOf(m.get("tab")),
                intOf(m.get("fs"))));
    }

    /** After the DB commit of a submit: stop accepting writes, leave the deadline queue, keep data briefly. */
    public void markSubmitted(UUID attemptId, AttemptStatus status) {
        redis.opsForHash().put(metaKey(attemptId), "status", status.name());
        redis.opsForZSet().remove(DEADLINES, attemptId.toString());
        redis.expire(answersKey(attemptId), props.submittedStateTtl());
        redis.expire(eventsKey(attemptId), props.submittedStateTtl());
        redis.expire(metaKey(attemptId), props.submittedStateTtl());
    }

    // ------------------------------------------------------------------ answers

    public long applyAnswers(UUID attemptId, Instant now, List<AnswerWrite> writes) {
        List<String> args = new ArrayList<>(2 + writes.size() * 5);
        args.add(String.valueOf(now.toEpochMilli()));
        args.add(String.valueOf(props.autosaveGrace().toMillis()));
        for (AnswerWrite w : writes) {
            args.add(w.questionId().toString());
            args.add(String.valueOf(w.seq()));
            args.add(payload(w.answer(), w.state()));
            args.add(String.valueOf(w.timeSpentSeconds()));
            args.add(String.valueOf(w.visits()));
        }
        Long result = redis.execute(APPLY, List.of(answersKey(attemptId), metaKey(attemptId)), args.toArray());
        return result == null ? META_MISSING : result;
    }

    public Map<UUID, SavedAnswer> answers(UUID attemptId) {
        Map<Object, Object> raw = redis.opsForHash().entries(answersKey(attemptId));
        Map<UUID, SavedAnswer> result = new HashMap<>();
        for (Map.Entry<Object, Object> e : raw.entrySet()) {
            String field = (String) e.getKey();
            if (!field.startsWith("q:")) {
                continue;
            }
            String qid = field.substring(2);
            Payload p = parse((String) e.getValue());
            result.put(UUID.fromString(qid), new SavedAnswer(p.a(), p.st(),
                    longOf(raw.get("s:" + qid)), intOf(raw.get("t:" + qid)), intOf(raw.get("v:" + qid))));
        }
        return result;
    }

    // ------------------------------------------------------------------ anti-cheat

    public long incrementCounter(UUID attemptId, String field, long delta) {
        Long v = redis.opsForHash().increment(metaKey(attemptId), field, delta);
        return v == null ? 0 : v;
    }

    public void appendEvents(UUID attemptId, List<String> jsonEvents) {
        if (jsonEvents.isEmpty()) {
            return;
        }
        String key = eventsKey(attemptId);
        redis.opsForList().rightPushAll(key, jsonEvents);
        redis.opsForList().trim(key, -props.maxBufferedEvents(), -1);
    }

    public List<String> events(UUID attemptId) {
        List<String> events = redis.opsForList().range(eventsKey(attemptId), 0, -1);
        return events == null ? List.of() : events;
    }

    // ------------------------------------------------------------------ auto-submit queue

    public List<UUID> dueForAutoSubmit(Instant cutoff, int limit) {
        var ids = redis.opsForZSet().rangeByScore(DEADLINES, Double.NEGATIVE_INFINITY, cutoff.toEpochMilli(), 0, limit);
        return ids == null ? List.of() : ids.stream().map(UUID::fromString).toList();
    }

    public void removeFromDeadlines(UUID attemptId) {
        redis.opsForZSet().remove(DEADLINES, attemptId.toString());
    }

    // ------------------------------------------------------------------ helpers

    private record Payload(StudentAnswer a, AnswerState st) {
    }

    private String payload(StudentAnswer answer, AnswerState state) {
        try {
            return objectMapper.writeValueAsString(new Payload(answer, state));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Payload parse(String json) {
        try {
            return objectMapper.readValue(json, Payload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt answer payload: " + json, e);
        }
    }

    private static int intOf(Object o) {
        return o == null ? 0 : Integer.parseInt(o.toString());
    }

    private static long longOf(Object o) {
        return o == null ? 0 : Long.parseLong(o.toString());
    }

    private static String metaKey(UUID id) {
        return "attempt:" + id + ":meta";
    }

    private static String answersKey(UUID id) {
        return "attempt:" + id + ":answers";
    }

    private static String eventsKey(UUID id) {
        return "attempt:" + id + ":events";
    }
}
