package org.example;

import org.scijava.parsington.ExpressionParser;
import org.scijava.parsington.Operator;
import org.scijava.parsington.Variable;

import java.util.*;

/**
 * Representation of a physical unit as a multiset (map) of base units to integer powers.
 * Supports SI base dimensions, compound unit expansion, SI prefixes, and unrecognized unit detection.
 */
public class Unit {
    public enum Dimension {
        TIME,
        ANGLE,
        LENGTH,
        MASS,
        ELECTRIC_CURRENT,
        TEMPERATURE,
        UNKNOWN
    }

    public static final Unit DIMENSIONLESS = new Unit(Collections.emptyMap(), "dimensionless");

    private static final ExpressionParser PARSER = new ExpressionParser();
    private static final Map<String, String> COMPOUND_UNITS = new HashMap<>();
    private static final Map<String, String> PREFIXES = new HashMap<>();
    private static final Map<String, String> BASE_UNITS = new HashMap<>();

    static {
        // SI Prefixes from milli to kilo
        PREFIXES.put("milli", "m");
        PREFIXES.put("centi", "c");
        PREFIXES.put("deci", "d");
        PREFIXES.put("deca", "da");
        PREFIXES.put("deka", "da");
        PREFIXES.put("hecto", "h");
        PREFIXES.put("kilo", "k");
        PREFIXES.put("m", "m");
        PREFIXES.put("c", "c");
        PREFIXES.put("d", "d");
        PREFIXES.put("da", "da");
        PREFIXES.put("h", "h");
        PREFIXES.put("k", "k");

        // Base unit names -> canonical base symbol
        BASE_UNITS.put("meters", "m");
        BASE_UNITS.put("meter", "m");
        BASE_UNITS.put("m", "m");

        BASE_UNITS.put("seconds", "s");
        BASE_UNITS.put("second", "s");
        BASE_UNITS.put("sec", "s");
        BASE_UNITS.put("s", "s");

        BASE_UNITS.put("grams", "g");
        BASE_UNITS.put("gram", "g");
        BASE_UNITS.put("g", "g");

        BASE_UNITS.put("amperes", "A");
        BASE_UNITS.put("ampere", "A");
        BASE_UNITS.put("amp", "A");
        BASE_UNITS.put("amps", "A");
        BASE_UNITS.put("a", "A");

        BASE_UNITS.put("kelvin", "K");
        BASE_UNITS.put("kelvins", "K");
        BASE_UNITS.put("k", "K");
        BASE_UNITS.put("celsius", "celsius");
        BASE_UNITS.put("fahrenheit", "fahrenheit");

        BASE_UNITS.put("rotations", "rot");
        BASE_UNITS.put("rotation", "rot");
        BASE_UNITS.put("rot", "rot");

        BASE_UNITS.put("radians", "rad");
        BASE_UNITS.put("radian", "rad");
        BASE_UNITS.put("rad", "rad");

        BASE_UNITS.put("degrees", "deg");
        BASE_UNITS.put("degree", "deg");
        BASE_UNITS.put("deg", "deg");

        BASE_UNITS.put("minutes", "min");
        BASE_UNITS.put("minute", "min");
        BASE_UNITS.put("min", "min");

        BASE_UNITS.put("hours", "h");
        BASE_UNITS.put("hour", "h");

        BASE_UNITS.put("bytes", "B");
        BASE_UNITS.put("byte", "B");
        BASE_UNITS.put("b", "B");

        BASE_UNITS.put("liters", "L");
        BASE_UNITS.put("liter", "L");
        BASE_UNITS.put("l", "L");

        BASE_UNITS.put("inches", "in");
        BASE_UNITS.put("inch", "in");
        BASE_UNITS.put("in", "in");

        BASE_UNITS.put("feet", "ft");
        BASE_UNITS.put("foot", "ft");
        BASE_UNITS.put("ft", "ft");

        // Compound unit definitions
        COMPOUND_UNITS.put("newton", "kg * m / s^2");
        COMPOUND_UNITS.put("newtons", "kg * m / s^2");

        COMPOUND_UNITS.put("joule", "kg * m^2 / s^2");
        COMPOUND_UNITS.put("joules", "kg * m^2 / s^2");

        COMPOUND_UNITS.put("watt", "kg * m^2 / s^3");
        COMPOUND_UNITS.put("watts", "kg * m^2 / s^3");

        COMPOUND_UNITS.put("pascal", "kg / (m * s^2)");
        COMPOUND_UNITS.put("pascals", "kg / (m * s^2)");

        COMPOUND_UNITS.put("coulomb", "A * s");
        COMPOUND_UNITS.put("coulombs", "A * s");

        COMPOUND_UNITS.put("volt", "kg * m^2 / (s^3 * A)");
        COMPOUND_UNITS.put("volts", "kg * m^2 / (s^3 * A)");

        COMPOUND_UNITS.put("ohm", "kg * m^2 / (s^3 * A^2)");
        COMPOUND_UNITS.put("ohms", "kg * m^2 / (s^3 * A^2)");

        COMPOUND_UNITS.put("farad", "s^4 * A^2 / (kg * m^2)");
        COMPOUND_UNITS.put("farads", "s^4 * A^2 / (kg * m^2)");

        COMPOUND_UNITS.put("hertz", "1 / s");
        COMPOUND_UNITS.put("hz", "1 / s");
    }

    private final Map<String, Integer> baseUnits; // Canonical base unit -> exponent
    private final String rawExpression;

    public Unit(Map<String, Integer> baseUnits, String rawExpression) {
        Map<String, Integer> cleaned = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : baseUnits.entrySet()) {
            if (entry.getValue() != null && entry.getValue() != 0) {
                cleaned.put(entry.getKey(), entry.getValue());
            }
        }
        this.baseUnits = Collections.unmodifiableMap(cleaned);
        this.rawExpression = rawExpression;
    }

    public Map<String, Integer> getBaseUnits() {
        return baseUnits;
    }

    public boolean isDimensionless() {
        return baseUnits.isEmpty();
    }

    public static boolean isKnownUnitTerm(String term) {
        if (term == null) return false;
        String trimmed = term.trim();
        if (trimmed.isEmpty() || trimmed.equals("1")) return true;

        String lower = trimmed.toLowerCase();
        if (BASE_UNITS.containsKey(lower) || BASE_UNITS.containsKey(trimmed)) {
            return true;
        }
        if (COMPOUND_UNITS.containsKey(lower) || COMPOUND_UNITS.containsKey(trimmed)) {
            return true;
        }

        // Long prefixes
        String[] longPrefixes = {"milli", "centi", "deci", "deca", "deka", "hecto", "kilo"};
        for (String p : longPrefixes) {
            if (lower.startsWith(p) && lower.length() > p.length()) {
                String suffix = lower.substring(p.length());
                if (BASE_UNITS.containsKey(suffix) || COMPOUND_UNITS.containsKey(suffix)) {
                    return true;
                }
            }
        }

        // Short prefixes
        String[] shortPrefixes = {"da", "m", "c", "d", "h", "k"};
        for (String p : shortPrefixes) {
            if (trimmed.startsWith(p) && trimmed.length() > p.length()) {
                String suffix = trimmed.substring(p.length());
                String lowerSuffix = suffix.toLowerCase();
                if (BASE_UNITS.containsKey(lowerSuffix) || COMPOUND_UNITS.containsKey(lowerSuffix)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static String canonicalize(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        String lower = trimmed.toLowerCase();

        if (BASE_UNITS.containsKey(lower)) {
            return BASE_UNITS.get(lower);
        }
        if (BASE_UNITS.containsKey(trimmed)) {
            return BASE_UNITS.get(trimmed);
        }

        String[] longPrefixes = {"milli", "centi", "deci", "deca", "deka", "hecto", "kilo"};
        for (String p : longPrefixes) {
            if (lower.startsWith(p)) {
                String suffix = lower.substring(p.length());
                if (BASE_UNITS.containsKey(suffix)) {
                    return PREFIXES.get(p) + BASE_UNITS.get(suffix);
                }
            }
        }

        String[] shortPrefixes = {"da", "m", "c", "d", "h", "k"};
        for (String p : shortPrefixes) {
            if (trimmed.startsWith(p) && trimmed.length() > p.length()) {
                String suffix = trimmed.substring(p.length());
                String lowerSuffix = suffix.toLowerCase();
                if (BASE_UNITS.containsKey(lowerSuffix)) {
                    return PREFIXES.get(p) + BASE_UNITS.get(lowerSuffix);
                }
            }
        }

        return trimmed;
    }

    public static Dimension getDimensionOfBaseUnit(String canonicalBaseUnit) {
        if (canonicalBaseUnit == null || canonicalBaseUnit.isEmpty()) return Dimension.UNKNOWN;

        String fundamental = extractFundamentalBase(canonicalBaseUnit);

        return switch (fundamental) {
            case "s", "min", "h" -> Dimension.TIME;
            case "rot", "rad", "deg" -> Dimension.ANGLE;
            case "m", "in", "ft" -> Dimension.LENGTH;
            case "g", "kg", "lb" -> Dimension.MASS;
            case "A" -> Dimension.ELECTRIC_CURRENT;
            case "K", "celsius", "fahrenheit" -> Dimension.TEMPERATURE;
            default -> Dimension.UNKNOWN;
        };
    }

    private static String extractFundamentalBase(String canonicalUnit) {
        if (BASE_UNITS.containsValue(canonicalUnit)) {
            return canonicalUnit;
        }
        String[] shortPrefixes = {"da", "m", "c", "d", "h", "k"};
        for (String p : shortPrefixes) {
            if (canonicalUnit.startsWith(p) && canonicalUnit.length() > p.length()) {
                String sub = canonicalUnit.substring(p.length());
                if (BASE_UNITS.containsValue(sub)) {
                    return sub;
                }
            }
        }
        return canonicalUnit;
    }

    public Map<Dimension, Integer> getDimensionMap() {
        Map<Dimension, Integer> dims = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : baseUnits.entrySet()) {
            Dimension d = getDimensionOfBaseUnit(entry.getKey());
            if (d != Dimension.UNKNOWN) {
                dims.put(d, dims.getOrDefault(d, 0) + entry.getValue());
            }
        }
        dims.entrySet().removeIf(e -> e.getValue() == 0);
        return dims;
    }

    static LinkedList<Object> convertToPostfix(String expr) {
        var result = new LinkedList<>();
        var parsed = PARSER.parsePostfix(expr);
        outer: for (var exp: parsed) {
            for (var compoundUnitEntry: COMPOUND_UNITS.entrySet()) {
                if (compoundUnitEntry.getKey().equals(exp.toString())) {
                    result.addAll(convertToPostfix(compoundUnitEntry.getValue()));
                    continue outer;
                }
            }
            if (exp instanceof Variable) {
                if (!isKnownUnitTerm(exp.toString())) {
                    throw new IllegalArgumentException("Unknown unit term: " + exp);
                }
                result.add(canonicalize(exp.toString()));
            } else {
                result.add(exp);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> toExponentMap(LinkedList<Object> postfix) {
        Deque<Object> stack = new ArrayDeque<>(); // holds Map<String,Integer> (term) or Double (number)

        for (Object token : postfix) {
            switch (token) {
                case Number _ -> stack.push(token);
                case String variableToken -> stack.push(new HashMap<>(Map.of(variableToken, 1)));
                case Operator _ -> {
                    String sym = token.toString();

                    switch (sym) {
                        case "(1)" -> {}
                        case "*", "/" -> {
                            Object right = stack.pop(), left = stack.pop();
                            stack.push(combine(left, right, sym.equals("*") ? 1 : -1));
                        }
                        case "^" -> {
                            Object exponent = stack.pop(), base = stack.pop();
                            if (exponent instanceof Integer exp) {
                                stack.push(
                                    base instanceof Number n
                                        ? Math.pow(n.doubleValue(), exp)
                                        : scale((Map<String, Integer>) base, exp)
                                );
                            } else {
                                throw new UnsupportedOperationException("Only integer exponents supported: " + exponent);
                            }
                        }
                        default -> throw new UnsupportedOperationException("Unsupported operator: " + sym);
                    }
                }
                default -> throw new IllegalStateException("Unexpected token type: " + token.getClass());
            }
        }

        Object top = stack.pop();
        return top instanceof Number ? Collections.emptyMap() : (Map<String, Integer>) top;
    }

    @SuppressWarnings("unchecked")
    private static Object combine(Object left, Object right, int sign) {
        if (left instanceof Number && right instanceof Number) {
            double l = (Double) left, r = (Double) right;
            return sign == 1 ? l * r : l / r;
        }
        if (right instanceof Number) {
            throw new UnsupportedOperationException("Cannot combine a number with a unit: " + right);
        }
        if (left instanceof Number) return sign == 1 ? right : scale((Map<String, Integer>) right, -1);

        Map<String, Integer> result = new HashMap<>((Map<String, Integer>) left);
        ((Map<String, Integer>) right).forEach((k, v) -> result.merge(k, sign * v, Integer::sum));
        result.values().removeIf(v -> v == 0);
        return result;
    }

    private static Map<String, Integer> scale(Map<String, Integer> m, int factor) {
        Map<String, Integer> result = new HashMap<>();
        m.forEach((k, v) -> { int nv = v * factor; if (nv != 0) result.put(k, nv); });
        return result;
    }

    public static Unit parse(String expr) {
        if (expr == null || expr.trim().isEmpty() || expr.trim().equals("1")) {
            return DIMENSIONLESS;
        }
        if (expr.contains("+") || expr.contains("-")) {
            throw new RuntimeException("Operands + and - aren't valid in unit expressions");
        }
        expr = expr.toLowerCase();
        return new Unit(toExponentMap(convertToPostfix(expr)), expr);
    }

    public Unit multiply(Unit other) {
        if (this.isDimensionless()) return other;
        if (other.isDimensionless()) return this;

        Map<String, Integer> newMap = new HashMap<>(this.baseUnits);
        for (Map.Entry<String, Integer> entry : other.baseUnits.entrySet()) {
            newMap.put(entry.getKey(), newMap.getOrDefault(entry.getKey(), 0) + entry.getValue());
        }
        return new Unit(newMap, "(" + this.rawExpression + ")*(" + other.rawExpression + ")");
    }

    public Unit divide(Unit other) {
        if (other.isDimensionless()) return this;

        Map<String, Integer> newMap = new HashMap<>(this.baseUnits);
        for (Map.Entry<String, Integer> entry : other.baseUnits.entrySet()) {
            newMap.put(entry.getKey(), newMap.getOrDefault(entry.getKey(), 0) - entry.getValue());
        }
        return new Unit(newMap, "(" + this.rawExpression + ")/(" + other.rawExpression + ")");
    }

    public String toHumanName() {
        if (isDimensionless()) return "dimensionless";
        return rawExpression;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Unit unit = (Unit) o;
        return Objects.equals(baseUnits, unit.baseUnits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseUnits);
    }

    @Override
    public String toString() {
        return toHumanName();
    }
}
