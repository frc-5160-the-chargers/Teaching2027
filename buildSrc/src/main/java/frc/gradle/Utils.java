package frc.gradle;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class Utils {
    public static String addParentheses(String condition) {
        if ((condition.startsWith("!(") || condition.startsWith("(")) && condition.endsWith(")")) {
            return condition;
        }
        return "(" + condition + ")";
    }

    public static String joinWithOr(List<String> conditions) {
        if (conditions.contains("true")) {
            return "true";
        }
        return conditions.stream()
            .filter(cond -> !cond.equals("false") && !cond.isEmpty())
            .distinct()
            .map(condition -> hasAndStmt(condition) ? addParentheses(condition) : condition)
            .collect(Collectors.joining(" || "));
    }

    public static String joinWithAnd(List<String> conditions) {
        if (conditions.contains("false")) {
            return "false";
        }
        return conditions.stream()
            .filter(cond -> !cond.equals("true") && !cond.isEmpty())
            .distinct()
            .map(condition -> hasOrStmt(condition) ? addParentheses(condition) : condition)
            .collect(Collectors.joining(" && "));
    }

    public static String joinWithOr(String... conditions) {
        return joinWithOr(List.of(conditions));
    }


    public static String joinWithAnd(String... conditions) {
        return joinWithAnd(List.of(conditions));
    }

    @SafeVarargs
    public static <T> List<T> immutableAdd(List<T> list, T... items) {
        var newList = new ArrayList<>(list);
        newList.addAll(List.of(items));
        return newList;
    }

    // parses a boolean, Trigger, or BooleanSupplier, and returns the negation of that value.
    public static String negate(String condition) {
        condition = condition.trim();

        if (condition.startsWith("!(") && condition.endsWith(")")) return condition.substring(2, condition.length() - 1);
        if (condition.endsWith(".negate()")) return condition.substring(0, condition.length() - 9);
        if (hasAndStmt(condition) || hasOrStmt(condition)) return "!" + addParentheses(condition);

        if (condition.contains(" == ")) return condition.replace(" == ", " != ");
        if (condition.contains(" != ")) return condition.replace(" != ", " == ");
        if (condition.contains(" >= ")) return condition.replace(" >= ", " < ");
        if (condition.contains(" <= ")) return condition.replace(" <= ", " > ");
        if (condition.contains(" > "))  return condition.replace(" > ",  " <= ");
        if (condition.contains(" < "))  return condition.replace(" < ",  " >= ");
        if (condition.startsWith("!") && !condition.startsWith("!(")) {
            return condition.substring(1);
        }

        return "!" + condition;
    }

    public static boolean hasAndStmt(String condition) {
        return condition.contains("&&") || condition.contains(".and(");
    }

    public static boolean hasOrStmt(String condition) {
        return condition.contains("||") || condition.contains(".or(");
    }
}
