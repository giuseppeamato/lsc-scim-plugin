package it.pz8.lsc.plugins.connectors.scim.bean;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable value object representing a SCIM multivalued attribute selector
 * (the expression inside square brackets, e.g. {@code type eq "home" and primary eq true}).
 *
 * <p>Grammar supported:
 * <pre>
 *   selector := clause ( " and " clause )*
 *   clause   := attr " eq " value
 *   attr     := "type" | "display" | "primary"
 *   value    := quotedString | unquotedString | "true" | "false"
 * </pre>
 *
 * <p>Operators ({@code eq}, {@code and}) are recognized case-insensitively (RFC 7644 §3.4.2.2).
 * String values may be written with or without surrounding double quotes; the canonical form
 * emitted by {@link #toScimFilter()} always quotes string values.
 *
 * @author Giuseppe Amato
 */
public final class ScimSelector implements Serializable {

    private static final long serialVersionUID = -7356864559969027931L;

	public static final String TYPE = "type";
    public static final String DISPLAY = "display";
    public static final String PRIMARY = "primary";
    public static final String VALUE = "value";
    public static final String EQ_OPERATOR = " eq ";
    public static final String AND_OPERATOR = " and ";

    public static final Pattern BRACKET_PATTERN = Pattern.compile("\\[([^\\[\\]]*)\\]");

    private static final Pattern AND_SPLIT = Pattern.compile("(?i)\\s+and\\s+");
    private static final Pattern EQ_SPLIT = Pattern.compile("(?i)\\s+eq\\s+");
    private static final Set<String> ALLOWED_ATTRS = Set.of(TYPE, DISPLAY, PRIMARY);
    private static final String[] CANONICAL_ORDER = {TYPE, DISPLAY, PRIMARY};

    private final LinkedHashMap<String, Object> clauses;

    private ScimSelector(LinkedHashMap<String, Object> clauses) {
        this.clauses = clauses;
    }

    public static ScimSelector empty() {
        return new ScimSelector(new LinkedHashMap<>());
    }

    /**
     * Parses the body of a selector (the substring inside square brackets).
     * Returns an empty selector for {@code null} or blank input.
     */
    public static ScimSelector parse(String body) {
        LinkedHashMap<String, Object> parsed = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return new ScimSelector(parsed);
        }
        for (String clause : AND_SPLIT.split(body.trim())) {
            String[] parts = EQ_SPLIT.split(clause.trim(), 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid SCIM selector clause: '" + clause + "'");
            }
            String attr = parts[0].trim();
            if (!ALLOWED_ATTRS.contains(attr)) {
                throw new IllegalArgumentException("Unsupported selector attribute: '" + attr
                        + "' (allowed: " + ALLOWED_ATTRS + ")");
            }
            parsed.put(attr, parseValue(attr, parts[1].trim()));
        }
        return new ScimSelector(parsed);
    }

    /**
     * Builds a selector from the sub-attribute map of a flattened multivalued element,
     * including only the canonical selector attributes ({@code type}, {@code display},
     * {@code primary}) that are present. The {@code value} field is always skipped.
     */
    public static ScimSelector fromFlatElement(Map<String, Object> subAttrs) {
        LinkedHashMap<String, Object> built = new LinkedHashMap<>();
        if (subAttrs != null) {
            for (String attr : CANONICAL_ORDER) {
                Object v = subAttrs.get(attr);
                if (v != null) {
                    built.put(attr, normalize(attr, v));
                }
            }
        }
        return new ScimSelector(built);
    }

    /**
     * Extracts the selector body from a fully qualified attribute name.
     * E.g. {@code emails[type eq home]} returns {@code "type eq home"};
     * {@code emails[]} returns the empty string; {@code emails} returns {@code null}.
     */
    public static String extractBody(String attributeName) {
        if (attributeName == null) {
            return null;
        }
        Matcher m = BRACKET_PATTERN.matcher(attributeName);
        return m.find() ? m.group(1) : null;
    }

    public boolean isEmpty() {
        return clauses.isEmpty();
    }

    public boolean has(String attr) {
        return clauses.containsKey(attr);
    }

    public Object get(String attr) {
        return clauses.get(attr);
    }

    /**
     * Canonical SCIM filter form with quoted strings and raw booleans, attributes in
     * canonical order ({@code type}, {@code display}, {@code primary}).
     */
    public String toScimFilter() {
        if (clauses.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String attr : CANONICAL_ORDER) {
            if (clauses.containsKey(attr)) {
                if (sb.length() > 0) {
                    sb.append(AND_OPERATOR);
                }
                sb.append(attr).append(EQ_OPERATOR).append(serializeValue(clauses.get(attr)));
            }
        }
        return sb.toString();
    }

    /**
     * Returns a fresh map suitable for inclusion in a SCIM JSON element body
     * (e.g. {@code {"type":"home","primary":true}}). Caller is free to add a
     * {@code "value"} entry on top.
     */
    public Map<String, Object> toElementMap() {
        return new LinkedHashMap<>(clauses);
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(clauses);
    }

    private static Object parseValue(String attr, String raw) {
        if (PRIMARY.equals(attr)) {
            String v = unquote(raw).toLowerCase();
            if (!"true".equals(v) && !"false".equals(v)) {
                throw new IllegalArgumentException("primary clause must be 'true' or 'false', got: " + raw);
            }
            return Boolean.valueOf(v);
        }
        return unquote(raw);
    }

    private static String unquote(String raw) {
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private static Object normalize(String attr, Object value) {
        if (PRIMARY.equals(attr)) {
            if (value instanceof Boolean) {
                return value;
            }
            return Boolean.valueOf(value.toString());
        }
        return value.toString();
    }

    private static String serializeValue(Object value) {
        if (value instanceof Boolean) {
            return value.toString();
        }
        return "\"" + value + "\"";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScimSelector)) return false;
        return clauses.equals(((ScimSelector) o).clauses);
    }

    @Override
    public int hashCode() {
        return clauses.hashCode();
    }

    @Override
    public String toString() {
        return toScimFilter();
    }
}
