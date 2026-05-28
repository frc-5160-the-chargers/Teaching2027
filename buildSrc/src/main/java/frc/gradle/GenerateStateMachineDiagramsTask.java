package frc.gradle;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@DisableCachingByDefault
public abstract class GenerateStateMachineDiagramsTask extends DefaultTask {
    private record Transition(String fromState, String toState, String transitionCond) {}
    private static class Diagram {
        List<Transition> transitions = new ArrayList<>();
        String initialState;
        List<String> stateDefinitionOrder = new ArrayList<>();
    }

    @Input
    @Optional
    public abstract Property<String> getJavaRoot();

    @TaskAction
    public void run() throws IOException {
        extractFromDirectory(getJavaRoot().getOrElse("src/main/java"));
    }

    private void extractFromFile(File sourceFile, Map<String, Diagram> diagrams) throws IOException {
        // 1. Initialize configuration
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        config.setPreprocessUnicodeEscapes(true);
        var parser = new JavaParser(config);
        var returnAnalyzer = new ReturnConditionAnalyzer();

        String content = Files.readString(sourceFile.toPath());
        // JavaParser 3.28.1 (and earlier) has issues with unnamed variables (JEP 456)
        // even with BLEEDING_EDGE. We replace them with a valid identifier before parsing.
        // We look for '_' as a standalone parameter in lambda or catch, or local var.
        // A simple approach is to replace it when it's surrounded by non-word characters
        // but that might hit underscores in strings or comments. 
        // Given the constraints and the specific error, we'll use a slightly more targeted regex.
        content = content.replaceAll("(?<=[\\(,\\s])_(?=[\\s,\\)->])", "unused_var_");
        var cu = parser.parse(content).getResult().orElseThrow();
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            boolean returnsStateMachine = method.getTypeAsString().equals("StateMachine");
            var annotationOpt = method.getAnnotationByName("GenerateDiagram");
            if (!returnsStateMachine || annotationOpt.isEmpty()) return;

            var variableDefs = method.findAll(VariableDeclarationExpr.class);
            var stateMachineDefs = variableDefs
                .stream()
                .filter(v -> {
                    var def = v.getVariable(0).getInitializer();
                    return def.isPresent() &&
                            def.get().isObjectCreationExpr() &&
                            def.get().asObjectCreationExpr().getType().asString().equals("StateMachine");
                })
                .toList();
            if (stateMachineDefs.isEmpty()) {
                throw new RuntimeException("No StateMachine declaration found in " + method.getNameAsString());
            } else if (stateMachineDefs.size() > 1) {
                throw new RuntimeException(
                    "Multiple StateMachine declarations found in " + method.getNameAsString()
                    + ". Currently, the @GenerateDiagram annotation doesn't support multiple StateMachine declarations in the same method."
                );
            }

            var stateMachineDef = stateMachineDefs.getFirst().getVariable(0).getInitializer().orElseThrow();
            var rawDiagramName = stateMachineDef.asObjectCreationExpr().getArgument(0);
            if (!rawDiagramName.isStringLiteralExpr()) {
                throw new RuntimeException(
                    "The diagram generator requires the name " +
                    "of the state machine in '" + method.getNameAsString() +
                    "' to be a single string literal (e.g. new StateMachine(\"My Diagram\"))."
                );
            }
            var diagramName = rawDiagramName.toString().substring(1, rawDiagramName.toString().length() - 1);
            if (diagrams.containsKey(diagramName)) {
                throw new RuntimeException("You already have a State Machine named '" + diagramName + "'");
            }
            var diagram = new Diagram();
            diagrams.put(diagramName, diagram);

            variableDefs.stream()
                .filter(v -> {
                    var initializer = v.getVariable(0).getInitializer();
                    return initializer.isPresent() &&
                            initializer.get().isMethodCallExpr() &&
                            initializer.get().asMethodCallExpr().getNameAsString().equals("addState");
                })
                .forEachOrdered(v -> diagram.stateDefinitionOrder.add(v.getVariable(0).getNameAsString()));

            method.findAll(MethodCallExpr.class).forEach(call -> {
                if (call.getNameAsString().equals("setInitialState")) {
                    diagram.initialState = call.getArguments().getFirst().orElseThrow().toString();
                    return;
                }

                boolean isWhen = call.getNameAsString().equals("when");
                boolean isWhenComplete = call.getNameAsString().equals("whenComplete");
                boolean isWhenCompleteAnd = call.getNameAsString().equals("whenCompleteAnd");
                if (!isWhen && !isWhenComplete && !isWhenCompleteAnd) return;

                var toStateCallOpt = call.getScope()
                        .filter(s -> s instanceof MethodCallExpr)
                        .map(s -> ((MethodCallExpr) s));
                if (toStateCallOpt.isEmpty()) return;

                var transitionCondExpr = call.getArguments().getFirst();
                var transitionCond = "";
                if (isWhenComplete) {
                    transitionCond = "when complete";
                } else {
                    var prefix = isWhen ? "" : "when complete and ";
                    var expr = transitionCondExpr.orElseThrow().toString();
                    expr = expr.replace("() -> ", "");
                    expr = expr.replace(".getAsBoolean()", "");
                    if (expr.contains("||") || expr.contains(".or(")) {
                        expr = "(" + expr + ")";
                    }
                    transitionCond = prefix + expr;
                }

                var toStateCall = toStateCallOpt.get();
                var toState = toStateCall.getArguments().getFirst().orElseThrow();
                if (toStateCall.getNameAsString().equals("switchTo")) {
                    var fromState = toStateCall.getScope().orElseThrow().toString();
                    if (toState.isLambdaExpr()) {
                        diagram.transitions.addAll(
                            transitionsFromLambdaExpr(returnAnalyzer, toState, fromState, transitionCond)
                        );
                    } else {
                        diagram.transitions.add(new Transition(fromState, toState.toString(), transitionCond));
                    }
                } else if (toStateCall.getNameAsString().equals("to")) {
                    // If it's just a regular "to", there must be a switchFromAny before it.
                    var argList = toStateCall.getScope()
                            .filter(s -> s instanceof MethodCallExpr)
                            .map(s -> ((MethodCallExpr) s))
                            .map(MethodCallExpr::getArguments);
                    if (argList.isEmpty()) return;
                    for (var fromState: argList.get()) {
                        if (toState.isLambdaExpr()) {
                            diagram.transitions.addAll(
                                transitionsFromLambdaExpr(returnAnalyzer, toState, fromState.toString(), transitionCond)
                            );
                        } else {
                            var t = new Transition(fromState.toString(), toState.toString(), transitionCond);
                            diagram.transitions.add(t);
                        }
                    }
                }
            });
        });
    }

    private List<Transition> transitionsFromLambdaExpr(
        ReturnConditionAnalyzer returnAnalyzer,
        Expression toState,
        String fromState,
        String transitionCond
    ) {
        var transitions = new ArrayList<Transition>();
        for (var entry: returnAnalyzer.analyze(toState.asLambdaExpr()).entrySet()) {
            var innerToState = entry.getKey();
            var additionalCond = entry.getValue();
            if (additionalCond.contains("||") || additionalCond.contains(".or(")) {
                additionalCond = "(" + additionalCond + ")";
            }
            var fullCond = transitionCond + " and " + additionalCond;
            transitions.add(new Transition(fromState, innerToState, fullCond));
        }
        return transitions;
    }

    private void extractFromDirectory(String rootDir) throws IOException {
        Map<String, Diagram> diagrams = new LinkedHashMap<>();

        Files.walk(Paths.get(rootDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        extractFromFile(path.toFile(), diagrams);
                    } catch (IOException e) {
                        System.err.println("Failed to parse: " + path + " — " + e.getMessage());
                    }
                });

        for (var entry : diagrams.entrySet()) {
            var methodName = entry.getKey();
            var diagram = entry.getValue();
            var deployDir = getJavaRoot().getOrElse("src/main/java/") + "../deploy/diagrams/";
            var file = new File(deployDir + methodName + ".mermaid");
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (var writer = new FileWriter(file)) {
                writer.write(generateMermaid(diagram));
            }
        }
    }

    private String generateMermaid(Diagram diagram) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("state_definition_order: ");
        sb.append(diagram.stateDefinitionOrder);
        sb.append("\n---\n");
        sb.append("stateDiagram-v2\n");
        sb.append("    direction LR\n\n");
        for (var t : diagram.transitions) {
            sb.append("    ")
                .append(sanitize(t.fromState()))
                .append(" --> ")
                .append(sanitize(t.toState()))
                .append(" : ")
                .append(sanitize(t.transitionCond()))
                .append("\n");
        }
        if (diagram.initialState != null) {
            sb.append("\n    classDef brightGreen color: #00FF00");
            sb.append("\n    classDef amber color: #FFBF00\n");
            sb.append("    class ");
            sb.append(diagram.initialState);
            sb.append(" brightGreen\n");
        }
        return sb.toString();
    }

    /** Strips characters that break Mermaid identifiers/labels */
    private String sanitize(String s) {
        return s.replaceAll("[\"'\\n\\r]", "").trim();
    }
}