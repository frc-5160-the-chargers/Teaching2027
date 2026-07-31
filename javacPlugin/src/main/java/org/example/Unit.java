package org.example;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Representation of a physical unit as a multiset (map) of base units to integer powers.
 * Supports SI base dimensions and compound unit expansion (e.g. newton = kg * m / s^2).
 */
public class Unit {
    public enum Dimension {
        TIME,
        ANGLE,
        LENGTH,
        MASS,
        ELECTRIC_CURRENT,
        TEMPERATURE,
        AMOUNT_OF_SUBSTANCE,
        LUMINOUS_INTENSITY,
        UNKNOWN
    }

    public static final Unit DIMENSIONLESS = new Unit(Collections.emptyMap(), "dimensionless");

    private static final Map<String, String> COMPOUND_UNITS = new HashMap<>();

    static {
        // Compound unit definitions
        COMPOUND_UNITS.put("newton", "kg * m / s^2");
        COMPOUND_UNITS.put("newtons", "kg * m / s^2");
        COMPOUND_UNITS.put("n", "kg * m / s^2");

        COMPOUND_UNITS.put("joule", "kg * m^2 / s^2");
        COMPOUND_UNITS.put("joules", "kg * m^2 / s^2");
        COMPOUND_UNITS.put("j", "kg * m^2 / s^2");

        COMPOUND_UNITS.put("watt", "kg * m^2 / s^3");
        COMPOUND_UNITS.put("watts", "kg * m^2 / s^3");
        COMPOUND_UNITS.put("w", "kg * m^2 / s^3");

        COMPOUND_UNITS.put("pascal", "kg / (m * s^2)");
        COMPOUND_UNITS.put("pascals", "kg / (m * s^2)");
        COMPOUND_UNITS.put("pa", "kg / (m * s^2)");

        COMPOUND_UNITS.put("coulomb", "A * s");
        COMPOUND_UNITS.put("coulombs", "A * s");
        COMPOUND_UNITS.put("c", "A * s");

        COMPOUND_UNITS.put("volt", "kg * m^2 / (s^3 * A)");
        COMPOUND_UNITS.put("volts", "kg * m^2 / (s^3 * A)");
        COMPOUND_UNITS.put("v", "kg * m^2 / (s^3 * A)");

        COMPOUND_UNITS.put("ohm", "kg * m^2 / (s^3 * A^2)");
        COMPOUND_UNITS.put("ohms", "kg * m^2 / (s^3 * A^2)");

        COMPOUND_UNITS.put("farad", "s^4 * A^2 / (kg * m^2)");
        COMPOUND_UNITS.put("farads", "s^4 * A^2 / (kg * m^2)");
        COMPOUND_UNITS.put("f", "s^4 * A^2 / (kg * m^2)");

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

    public String getRawExpression() {
        return rawExpression;
    }

    public boolean isDimensionless() {
        return baseUnits.isEmpty();
    }

    public static String canonicalize(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        String lower = trimmed.toLowerCase();
        return switch (lower) {
            case "seconds", "second", "sec", "s" -> "s";
            case "minutes", "minute", "min" -> "min";
            case "hours", "hour", "h" -> "h";
            case "rotations", "rotation", "rot" -> "rot";
            case "radians", "radian", "rad" -> "rad";
            case "degrees", "degree", "deg" -> "deg";
            case "meters", "meter", "m" -> "m";
            case "kilograms", "kilogram", "kg" -> "kg";
            case "grams", "gram", "g" -> "g";
            case "ampere", "amperes", "amp", "amps", "a" -> "A";
            case "kelvin", "kelvins", "k", "degc", "degf" -> "K";
            case "mole", "moles", "mol" -> "mol";
            case "candela", "candelas", "cd" -> "cd";
            default -> trimmed;
        };
    }

    public static Dimension getDimensionOfBaseUnit(String canonicalBaseUnit) {
        return switch (canonicalBaseUnit) {
            case "s", "min", "h" -> Dimension.TIME;
            case "rot", "rad", "deg" -> Dimension.ANGLE;
            case "m", "cm", "mm", "km", "ft", "in" -> Dimension.LENGTH;
            case "kg", "g", "lb" -> Dimension.MASS;
            case "A" -> Dimension.ELECTRIC_CURRENT;
            case "K" -> Dimension.TEMPERATURE;
            case "mol" -> Dimension.AMOUNT_OF_SUBSTANCE;
            case "cd" -> Dimension.LUMINOUS_INTENSITY;
            default -> Dimension.UNKNOWN;
        };
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

    public static Unit parse(String expr) {
        if (expr == null || expr.trim().isEmpty() || expr.trim().equals("1")) {
            return DIMENSIONLESS;
        }

        String trimmed = expr.trim();
        Map<String, Integer> result = new HashMap<>();

        // Handle parentheses by simple replacement if needed or fraction split
        String normalized = trimmed.replaceAll("\\(", "").replaceAll("\\)", "");

        String[] parts = normalized.split("/");
        if (parts.length > 2) {
            parseTerms(parts[0], 1, result);
            for (int i = 1; i < parts.length; i++) {
                parseTerms(parts[i], -1, result);
            }
        } else if (parts.length == 2) {
            parseTerms(parts[0], 1, result);
            parseTerms(parts[1], -1, result);
        } else {
            parseTerms(parts[0], 1, result);
        }

        return new Unit(result, trimmed);
    }

    private static void parseTerms(String part, int sign, Map<String, Integer> target) {
        part = part.trim();
        if (part.isEmpty() || part.equals("1")) return;

        String[] terms = part.split("[\\*\\s]+");
        for (String term : terms) {
            term = term.trim();
            if (term.isEmpty() || term.equals("1")) continue;

            String unitName;
            int exp = 1;

            if (term.contains("^")) {
                String[] sub = term.split("\\^");
                unitName = sub[0].trim();
                try {
                    exp = Integer.parseInt(sub[1].trim());
                } catch (NumberFormatException e) {
                    exp = 1;
                }
            } else {
                unitName = term;
            }

            String lowerUnit = unitName.toLowerCase();
            if (COMPOUND_UNITS.containsKey(lowerUnit)) {
                // Expand compound unit recursively
                String compoundExpr = COMPOUND_UNITS.get(lowerUnit);
                Unit expanded = parse(compoundExpr);
                for (Map.Entry<String, Integer> entry : expanded.getBaseUnits().entrySet()) {
                    target.put(entry.getKey(), target.getOrDefault(entry.getKey(), 0) + (sign * exp * entry.getValue()));
                }
            } else {
                String canonical = canonicalize(unitName);
                target.put(canonical, target.getOrDefault(canonical, 0) + (sign * exp));
            }
        }
    }

    public Unit multiply(Unit other) {
        if (this.isDimensionless()) return other;
        if (other.isDimensionless()) return this;

        Map<String, Integer> newMap = new HashMap<>(this.baseUnits);
        for (Map.Entry<String, Integer> entry : other.baseUnits.entrySet()) {
            newMap.put(entry.getKey(), newMap.getOrDefault(entry.getKey(), 0) + entry.getValue());
        }
        return new Unit(newMap, this.rawExpression + "*" + other.rawExpression);
    }

    public Unit divide(Unit other) {
        if (other.isDimensionless()) return this;

        Map<String, Integer> newMap = new HashMap<>(this.baseUnits);
        for (Map.Entry<String, Integer> entry : other.baseUnits.entrySet()) {
            newMap.put(entry.getKey(), newMap.getOrDefault(entry.getKey(), 0) - entry.getValue());
        }
        return new Unit(newMap, this.rawExpression + "/" + other.rawExpression);
    }

    public String toHumanName() {
        if (isDimensionless()) return "dimensionless";

        // Check if baseUnits matches a compound unit
        String compoundName = getKnownCompoundName(this.baseUnits);
        if (compoundName != null) {
            return compoundName;
        }

        StringBuilder num = new StringBuilder();
        StringBuilder den = new StringBuilder();

        for (Map.Entry<String, Integer> entry : baseUnits.entrySet()) {
            String canonical = entry.getKey();
            int exp = entry.getValue();
            String name = getHumanNameForCanonical(canonical);

            if (exp > 0) {
                if (num.length() > 0) num.append("*");
                num.append(name);
                if (exp > 1) num.append("^").append(exp);
            } else if (exp < 0) {
                if (den.length() > 0) den.append("*");
                den.append(name);
                if (exp < -1) den.append("^").append(-exp);
            }
        }

        if (num.length() == 0) num.append("1");
        if (den.length() > 0) {
            return num.toString() + "/" + den.toString();
        } else {
            return num.toString();
        }
    }

    private static String getKnownCompoundName(Map<String, Integer> baseUnits) {
        if (baseUnits.equals(parse("N").getBaseUnits())) return "newtons";
        if (baseUnits.equals(parse("J").getBaseUnits())) return "joules";
        if (baseUnits.equals(parse("W").getBaseUnits())) return "watts";
        if (baseUnits.equals(parse("Pa").getBaseUnits())) return "pascals";
        if (baseUnits.equals(parse("C").getBaseUnits())) return "coulombs";
        if (baseUnits.equals(parse("V").getBaseUnits())) return "volts";
        if (baseUnits.equals(parse("ohm").getBaseUnits())) return "ohms";
        if (baseUnits.equals(parse("F").getBaseUnits())) return "farads";
        if (baseUnits.equals(parse("Hz").getBaseUnits())) return "hertz";
        return null;
    }

    private static String getHumanNameForCanonical(String canonical) {
        return switch (canonical) {
            case "rot" -> "rotations";
            case "rad" -> "radians";
            case "deg" -> "degrees";
            case "s" -> "seconds";
            case "min" -> "minutes";
            case "h" -> "hours";
            case "m" -> "meters";
            case "kg" -> "kilograms";
            case "A" -> "amperes";
            case "K" -> "kelvins";
            case "mol" -> "moles";
            case "cd" -> "candelas";
            default -> canonical;
        };
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
