package com.examprep.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * Message templates with {@code {{placeholder}}} substitution. They are kept in code
 * for simplicity. Move them to a DB table or a template engine when marketing needs to
 * edit copy without a deploy.
 *
 * <p>{@code sensitive} templates contain secrets (such as reset links). Their body is
 * redacted before it is persisted, and they are never retried by the background job.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationTemplate {

    WELCOME(
            "Welcome to ExamPrep, {{name}}!",
            """
            Hi {{name}},

            Your ExamPrep account is ready. Explore test series for JEE and NEET and take \
            your first free mock test today:
            {{appUrl}}

            All the best for your preparation!
            Team ExamPrep""",
            false),

    PASSWORD_RESET(
            "Reset your ExamPrep password",
            """
            Hi {{name}},

            We received a request to reset your password. Open the link below to choose a new one:
            {{resetUrl}}

            This link expires in {{expiresInMinutes}} minutes and can be used only once. If you \
            did not request this, you can ignore this email; your password will not change.

            Team ExamPrep""",
            true),

    PAYMENT_SUCCESS(
            "Payment received - {{seriesName}}",
            """
            Hi {{name}},

            We have received your payment of {{currency}} {{amount}} for "{{seriesName}}".
            Payment reference: {{paymentId}}

            You now have access to all tests in this series:
            {{appUrl}}

            Team ExamPrep""",
            false),

    RESULT_PUBLISHED(
            "Your result for {{testTitle}} is out",
            """
            Hi {{name}},

            Your result for "{{testTitle}}" is now available.
            Score: {{score}} / {{maxScore}}   Rank: {{rank}}   Percentile: {{percentile}}

            View the detailed analysis and solutions:
            {{resultUrl}}

            Team ExamPrep""",
            false);

    public static final String REDACTED = "[redacted: contains a one-time secret]";

    private final String subjectTemplate;
    private final String bodyTemplate;
    private final boolean sensitive;

    public String renderSubject(Map<String, ?> vars) {
        return render(subjectTemplate, vars);
    }

    public String renderBody(Map<String, ?> vars) {
        return render(bodyTemplate, vars);
    }

    private static String render(String template, Map<String, ?> vars) {
        String out = template;
        for (Map.Entry<String, ?> e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue()));
        }
        return out;
    }
}
