package com.examprep.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Human-readable structural diff of two JSON documents, stored as {@code audit_log.changes}:
 * <pre>
 * [{"path": "title", "op": "changed", "from": "Mock 1", "to": "Mock 1 (revised)"},
 *  {"path": "permissions", "op": "added", "to": ["test.publish"]},
 *  {"path": "permissions", "op": "removed", "from": ["test.delete"]}]
 * </pre>
 * Objects are compared key by key. Arrays of scalars (tags, permissions) report added/removed
 * items; other arrays are reported as replaced when they differ.
 */
public final class JsonDiff {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final int MAX_CHANGES = 500;

    private JsonDiff() {
    }

    public static ArrayNode diff(JsonNode before, JsonNode after) {
        ArrayNode out = F.arrayNode();
        walk("", normalise(before), normalise(after), out);
        return out;
    }

    private static JsonNode normalise(JsonNode n) {
        return n == null || n.isMissingNode() ? F.nullNode() : n;
    }

    private static void walk(String path, JsonNode a, JsonNode b, ArrayNode out) {
        if (out.size() >= MAX_CHANGES || a.equals(b)) {
            return;
        }
        if (a.isObject() && b.isObject()) {
            Set<String> keys = new LinkedHashSet<>();
            a.fieldNames().forEachRemaining(keys::add);
            b.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                walk(path.isEmpty() ? key : path + "." + key, normalise(a.get(key)), normalise(b.get(key)), out);
            }
            return;
        }
        if (a.isArray() && b.isArray() && scalars(a) && scalars(b)) {
            List<JsonNode> added = minus(b, a);
            List<JsonNode> removed = minus(a, b);
            if (!added.isEmpty()) {
                out.add(change(path, "added", null, F.arrayNode().addAll(added)));
            }
            if (!removed.isEmpty()) {
                out.add(change(path, "removed", F.arrayNode().addAll(removed), null));
            }
            if (added.isEmpty() && removed.isEmpty()) {
                out.add(change(path, "reordered", a, b));
            }
            return;
        }
        String op = a.isNull() ? "added" : b.isNull() ? "removed" : "changed";
        out.add(change(path, op, a.isNull() ? null : a, b.isNull() ? null : b));
    }

    private static boolean scalars(JsonNode array) {
        for (Iterator<JsonNode> it = array.elements(); it.hasNext(); ) {
            if (it.next().isContainerNode()) {
                return false;
            }
        }
        return true;
    }

    private static List<JsonNode> minus(JsonNode from, JsonNode remove) {
        List<JsonNode> result = new ArrayList<>();
        Set<JsonNode> other = new LinkedHashSet<>();
        remove.elements().forEachRemaining(other::add);
        from.elements().forEachRemaining(n -> {
            if (!other.contains(n)) {
                result.add(n);
            }
        });
        return result;
    }

    private static ObjectNode change(String path, String op, JsonNode from, JsonNode to) {
        ObjectNode n = F.objectNode();
        n.put("path", path.isEmpty() ? "(root)" : path);
        n.put("op", op);
        if (from != null) {
            n.set("from", from);
        }
        if (to != null) {
            n.set("to", to);
        }
        return n;
    }
}
