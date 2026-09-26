package com.examprep.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/** Masks secrets before anything is written to the audit log. */
public final class SensitiveData {

    public static final String MASK = "***";
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(password|passwd|secret|token|otp|totp|recovery|apikey|api_key|authorization|cvv|card).*");

    private SensitiveData() {
    }

    /** Masks values of sensitive keys, in place, recursively. Returns the same node. */
    public static JsonNode mask(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            for (Iterator<Map.Entry<String, JsonNode>> it = obj.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                if (SENSITIVE_KEY.matcher(e.getKey()).matches() && !e.getValue().isContainerNode()) {
                    e.setValue(obj.textNode(MASK));
                } else {
                    mask(e.getValue());
                }
            }
        } else if (node instanceof ArrayNode arr) {
            arr.forEach(SensitiveData::mask);
        }
        return node;
    }
}
