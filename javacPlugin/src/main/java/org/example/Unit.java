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

    private record Token(double scalarTerm, Map<String, Integer> unit) {
        public Token multiply(Token other) {
            return new Token(scalarTerm * other.scalarTerm, combineUnits(other, 1));
        }

        public Token divide(Token other) {
            return new Token(scalarTerm / other.scalarTerm, combineUnits(other, -1));
        }

        private HashMap<String, Integer> combineUnits(Token other, int multiplier) {
            var map = new HashMap<String, Integer>();
            if (unit != null) {
                map.putAll(unit);
            }
            if (other.unit() != null) {
                for (var unitEntry : other.unit.entrySet()) {
                    map.merge(unitEntry.getKey(), unitEntry.getValue() * multiplier, Integer::sum);
                }
            }
            return map.isEmpty() ? null : map;
        }
    }

    public static final Unit DIMENSIONLESS = new Unit(Collections.emptyMap(), 1, "dimensionless");

    private static final ExpressionParser PARSER = new ExpressionParser();
    private static final Map<String, String> SHORT_PREFIX_UNITS = new HashMap<>();
    private static final Map<String, String> BASE_UNITS = new HashMap<>();
    private static final Map<String, String> COMPOUND_UNITS = new HashMap<>();
    private static final Map<String, Double> PREFIXES = new HashMap<>();
    private static final Operator MULTIPLY_OP = new Operator("*", 1, Operator.Associativity.EITHER, 0);

    static {
        PREFIXES.put("milli", 0.001);
        PREFIXES.put("centi", 0.01);
        PREFIXES.put("deci", 0.1);
        PREFIXES.put("deca", 10.0);
        PREFIXES.put("deka", 10.0);
        PREFIXES.put("hecto", 100.0);
        PREFIXES.put("kilo", 1000.0);

        SHORT_PREFIX_UNITS.put("mm", "millimeter");
        SHORT_PREFIX_UNITS.put("cm", "centimeter");
        SHORT_PREFIX_UNITS.put("km", "kilometer");
        SHORT_PREFIX_UNITS.put("kg", "kilogram");
        SHORT_PREFIX_UNITS.put("ms", "millisecond");
        SHORT_PREFIX_UNITS.put("mg", "milligram");

        BASE_UNITS.put("meters", "meter");
        BASE_UNITS.put("meter", "meter");
        BASE_UNITS.put("m", "meter");

        BASE_UNITS.put("seconds", "second");
        BASE_UNITS.put("second", "second");
        BASE_UNITS.put("sec", "second");
        BASE_UNITS.put("s", "second");

        BASE_UNITS.put("grams", "gram");
        BASE_UNITS.put("gram", "gram");
        BASE_UNITS.put("g", "gram");

        BASE_UNITS.put("amperes", "ampere");
        BASE_UNITS.put("ampere", "ampere");
        BASE_UNITS.put("amp", "ampere");
        BASE_UNITS.put("amps", "ampere");
        BASE_UNITS.put("a", "ampere");

        BASE_UNITS.put("kelvin", "kelvin");
        BASE_UNITS.put("kelvins", "kelvin");
        BASE_UNITS.put("k", "kelvin");
        BASE_UNITS.put("celsius", "celsius");
        BASE_UNITS.put("fahrenheit", "fahrenheit");

        BASE_UNITS.put("rotations", "rotation");
        BASE_UNITS.put("rotation", "rotation");
        BASE_UNITS.put("rot", "rotation");
        BASE_UNITS.put("rots", "rotation");
        BASE_UNITS.put("revolutions", "rotation");
        BASE_UNITS.put("revolution", "rotation");

        BASE_UNITS.put("radians", "radian");
        BASE_UNITS.put("radian", "radian");
        BASE_UNITS.put("rad", "radian");

        BASE_UNITS.put("degrees", "degree");
        BASE_UNITS.put("degree", "degree");
        BASE_UNITS.put("deg", "degree");

        BASE_UNITS.put("minutes", "minute");
        BASE_UNITS.put("minute", "minute");
        BASE_UNITS.put("min", "minute");

        BASE_UNITS.put("hours", "hour");
        BASE_UNITS.put("hour", "hour");

        BASE_UNITS.put("inches", "inch");
        BASE_UNITS.put("inch", "inch");
        BASE_UNITS.put("in", "inch");

        BASE_UNITS.put("feet", "foot");
        BASE_UNITS.put("foot", "foot");
        BASE_UNITS.put("ft", "foot");

        BASE_UNITS.put("pounds", "pound");
        BASE_UNITS.put("pound", "pound");
        BASE_UNITS.put("lb", "pound");

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

        COMPOUND_UNITS.put("gramsquaremeters", "g * m^2");
        COMPOUND_UNITS.put("gramsquaremeter", "g * m^2");
        COMPOUND_UNITS.put("grammeterssquared", "g * m^2");

        COMPOUND_UNITS.put("newtonmeters", "kg * m / s^2");
        COMPOUND_UNITS.put("newtonmeter", "kg * m / s^2");

        COMPOUND_UNITS.put("poundinch", "lb * in");
        COMPOUND_UNITS.put("poundinches", "lb * in");
        COMPOUND_UNITS.put("poundfeet", "lb * ft");
        COMPOUND_UNITS.put("poundfoot", "lb * ft");
    }

    private final Map<String, Integer> baseUnits; // Canonical base unit -> exponent
    private final int scalarTerm;
    private final String rawExpression;

    public Unit(Map<String, Integer> baseUnits, int scalarTerm, String rawExpression) {
        Map<String, Integer> cleaned = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : baseUnits.entrySet()) {
            if (entry.getValue() != null && entry.getValue() != 0) {
                cleaned.put(entry.getKey(), entry.getValue());
            }
        }
        this.baseUnits = Collections.unmodifiableMap(cleaned);
        this.scalarTerm = scalarTerm;
        this.rawExpression = rawExpression;
    }

    public Map<String, Integer> getBaseUnits() {
        return baseUnits;
    }

    public boolean isDimensionless() {
        return baseUnits.isEmpty();
    }

    private static boolean isConcreteDecimal(double value) {
        return Math.abs(value - (int) value) > 1e-9;
    }

    public static Dimension getDimensionOfBaseUnit(String canonicalBaseUnit) {
        if (canonicalBaseUnit == null || canonicalBaseUnit.isEmpty()) return Dimension.UNKNOWN;
        var unit = SHORT_PREFIX_UNITS.get(canonicalBaseUnit);
        if (unit == null) {
            unit = canonicalBaseUnit;
        }
        for (var prefix : PREFIXES.keySet()) {
            if (unit.startsWith(prefix)) {
                unit = unit.substring(prefix.length());
                break;
            }
        }

        return switch (unit) {
            case "second", "minute", "hour" -> Dimension.TIME;
            case "rotation", "radian", "degree" -> Dimension.ANGLE;
            case "meter", "inch", "foot" -> Dimension.LENGTH;
            case "gram", "pound" -> Dimension.MASS;
            case "ampere" -> Dimension.ELECTRIC_CURRENT;
            case "kelvin", "celsius", "fahrenheit" -> Dimension.TEMPERATURE;
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

    static LinkedList<Object> convertToPostfix(String expr) {
        var result = new LinkedList<>();
        var parsed = PARSER.parsePostfix(expr);
        outer: for (var exp: parsed) {
            if (!(exp instanceof Variable)) {
                result.add(exp);
                continue;
            }

            String prefix = null;
            String unit = exp.toString().trim().toLowerCase();

            if (SHORT_PREFIX_UNITS.containsKey(unit)) {
                unit = SHORT_PREFIX_UNITS.get(unit);
            }

            for (var prefixOption: PREFIXES.keySet()) {
                if (unit.startsWith(prefixOption)) {
                    prefix = prefixOption;
                    unit = unit.substring(prefix.length());
                    break;
                }
            }

            if (BASE_UNITS.containsKey(unit)) {
                if (prefix == null) {
                    result.add(BASE_UNITS.get(unit));
                } else {
                    result.addAll(List.of(PREFIXES.get(prefix), BASE_UNITS.get(unit), MULTIPLY_OP));
                }
                continue;
            }

            for (var compoundUnitEntry: COMPOUND_UNITS.entrySet()) {
                if (compoundUnitEntry.getKey().equals(unit)) {
                    var expandedUnitExp = compoundUnitEntry.getValue();
                    if (prefix != null) {
                        expandedUnitExp = PREFIXES.get(prefix) + " * " + expandedUnitExp;
                    }
                    result.addAll(convertToPostfix(expandedUnitExp));
                    continue outer;
                }
            }

            throw new IllegalArgumentException("Invalid unit expression: " + exp);
        }
        return result;
    }

    private static Token parseUnitToken(LinkedList<Object> postfix) {
        var stack = new ArrayDeque<Token>();
        for (var token : postfix) {
            switch (token) {
                case Number num -> stack.push(new Token(num.doubleValue(), null));
                case String variableToken -> {
                    var map = new HashMap<String, Integer>();
                    map.put(variableToken, 1);
                    stack.push(new Token(1, map));
                }
                case Operator _ -> {
                    String sym = token.toString();
                    switch (sym) {
                        case "(1)" -> {}
                        case "*" -> {
                            Token right = stack.pop(), left = stack.pop();
                            stack.push(left.multiply(right));
                        }
                        case "/" -> {
                            Token right = stack.pop(), left = stack.pop();
                            stack.push(left.divide(right));
                        }
                        case "^" -> {
                            Token exponent = stack.pop(), base = stack.pop();
                            if (exponent.unit != null || isConcreteDecimal(exponent.scalarTerm)) {
                                throw new UnsupportedOperationException("Only integer exponents supported: " + exponent);
                            }
                            var newToken = new Token(
                                (int) Math.pow(base.scalarTerm, exponent.scalarTerm),
                                scale(base.unit, (int) Math.round(exponent.scalarTerm))
                            );
                            stack.push(newToken);
                        }
                        default -> throw new UnsupportedOperationException("Unsupported operator: " + sym);
                    }
                }
                default -> throw new IllegalStateException("Unexpected token type: " + token.getClass());
            }
        }
        return stack.pop();
    }

    private static Map<String, Integer> scale(Map<String, Integer> m, int factor) {
        Map<String, Integer> result = new HashMap<>();
        m.forEach((k, v) -> {
            int nv = v * factor;
            if (nv != 0) result.put(k, nv);
        });
        return result;
    }

    public static Unit parseFromWPILib(String expr) {
        return parse(
            expr
                .replace(".per", "/")
                .replace("Per", "/")
                .replace(".mult", "*")
                .replace("org.wpilib.units.Units.", "")
                .replace("Units.", "")
        );
    }

    public static Unit parse(String expr) {
        if (expr == null || expr.trim().isEmpty() || expr.trim().equals("1")) {
            return DIMENSIONLESS;
        }
        if (expr.contains("+") || expr.contains("-")) {
            throw new RuntimeException("Operands + and - aren't valid in unit expressions");
        }
        var unitToken = parseUnitToken(convertToPostfix(expr.toLowerCase()));
        if (isConcreteDecimal(Math.log10(unitToken.scalarTerm))) {
            throw new IllegalArgumentException("Unit expressions must only have a power of ten scalar term.");
        }
        return new Unit(
            unitToken.unit == null ? Collections.emptyMap() : unitToken.unit,
            (int) unitToken.scalarTerm,
            expr
        );
    }

    public Unit multiply(Unit other) {
        if (this.isDimensionless()) return other;
        if (other.isDimensionless()) return this;

        var newMap = new HashMap<>(this.baseUnits);
        for (var entry : other.baseUnits.entrySet()) {
            newMap.put(entry.getKey(), newMap.getOrDefault(entry.getKey(), 0) + entry.getValue());
        }
        return new Unit(newMap, scalarTerm * other.scalarTerm, "(" + this.rawExpression + ")*(" + other.rawExpression + ")");
    }

    public Unit divide(Unit other) {
        if (other.isDimensionless()) return this;

        var newMap = new HashMap<>(this.baseUnits);
        for (var entry : other.baseUnits.entrySet()) {
            newMap.put(entry.getKey(), newMap.getOrDefault(entry.getKey(), 0) - entry.getValue());
        }
        return new Unit(newMap, scalarTerm / other.scalarTerm, "(" + this.rawExpression + ")/(" + other.rawExpression + ")");
    }

    public String toHumanName() {
        if (isDimensionless()) return "dimensionless";
        return rawExpression;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof Unit unit) {
            return Math.abs(scalarTerm - unit.scalarTerm) < 1e-9 && Objects.equals(baseUnits, unit.baseUnits);
        } else {
            return false;
        }
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
