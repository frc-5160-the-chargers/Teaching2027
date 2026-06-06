package frc.gradle;

import com.github.javaparser.ast.Node;

public class ErrorLogger {
    private static String currentFilePath = "unknown file";

    static void setFilePath(String path) {
        currentFilePath = path == null ? "unknown file" : path;
    }

    static void throwError(String message, Node node) {
        var begin = node.getBegin();
        if (begin.isPresent()) {
            throw new RuntimeException(currentFilePath + ": Line " + begin.get().line + ": " + message);
        } else {
            throw new RuntimeException(currentFilePath + ": " + message);
        }
    }
}
