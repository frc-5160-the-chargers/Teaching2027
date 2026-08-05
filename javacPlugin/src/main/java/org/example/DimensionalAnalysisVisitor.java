package org.example;

import com.sun.source.tree.*;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AST visitor for unit inference and dimensional analysis validation.
 * Emits errors for incompatible operations and unrecognized unit expressions.
 */
public class DimensionalAnalysisVisitor extends TreePathScanner<Unit, Void> {
    private final Trees trees;
    private CompilationUnitTree compilationUnit;
    private final Map<Element, Unit> symbolUnits = new HashMap<>();

    public DimensionalAnalysisVisitor(Trees trees) {
        this.trees = trees;
    }

    public void scanCompilationUnit(CompilationUnitTree cu) {
        this.compilationUnit = cu;
        scan(cu, null);
    }

    private Unit checkAndGetHasUnit(Tree node, Element element) {
        if (element == null) return null;
        HasUnit annotation = element.getAnnotation(HasUnit.class);
        try {
            return annotation == null ? null : Unit.parse(annotation.value());
        } catch (Exception e) {
            trees.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), node, compilationUnit);
            throw new IllegalArgumentException("Invalid unit expression was used; check the message above for details.", e);
        }
    }

    private Unit checkAndGetOverrideUnit(Tree node, Element element) {
        if (element == null) return null;
        OverrideUnit annotation = element.getAnnotation(OverrideUnit.class);
        try {
            return annotation == null ? null : Unit.parse(annotation.value());
        } catch (Exception e) {
            trees.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), node, compilationUnit);
            throw new IllegalArgumentException("Invalid unit expression was used; check the message above for details.", e);
        }
    }

    private void dimensionMismatchError(Tree node, Unit expected, Unit actual) {
        trees.printMessage(Diagnostic.Kind.ERROR, "Unit mismatch: " + expected.toHumanName() + " != " + actual.toHumanName(), node, compilationUnit);
    }

    @Override
    public Unit visitVariable(VariableTree node, Void p) {
        Element element = trees.getElement(getCurrentPath());
        Unit declaredUnit = checkAndGetHasUnit(node, element);

        Unit initUnit = null;
        if (node.getInitializer() != null) {
            initUnit = scan(node.getInitializer(), p);
        }

        // @OverrideUnit takes precedence over both @HasUnit and inferred unit
        Unit overrideUnit = checkAndGetOverrideUnit(node, element);

        if (overrideUnit != null) {
            symbolUnits.put(element, overrideUnit);
            return overrideUnit;
        }

        if (declaredUnit != null) {
            symbolUnits.put(element, declaredUnit);
            if (initUnit != null && !initUnit.isDimensionless() && !declaredUnit.equals(initUnit)) {
                dimensionMismatchError(node.getInitializer() != null ? node.getInitializer() : node, declaredUnit, initUnit);
            }
            return declaredUnit;
        } else {
            if (initUnit != null && !initUnit.isDimensionless() && element != null) {
                symbolUnits.put(element, initUnit);
            }
            return initUnit != null ? initUnit : Unit.DIMENSIONLESS;
        }
    }

    @Override
    public Unit visitMethod(MethodTree node, Void p) {
        Element element = trees.getElement(getCurrentPath());
        Unit methodUnit = checkAndGetHasUnit(node, element);

        // @OverrideUnit takes precedence over @HasUnit for method return type
        Unit overrideUnit = checkAndGetOverrideUnit(node, element);

        if (overrideUnit != null) {
            methodUnit = overrideUnit;
        }

        if (methodUnit != null) {
            symbolUnits.put(element, methodUnit);
        }
        super.visitMethod(node, p);
        return methodUnit;
    }

    @Override
    public Unit visitReturn(ReturnTree node, Void p) {
        MethodTree enclosingMethod = findEnclosingMethod();
        if (enclosingMethod != null) {
            Element methodElement = trees.getElement(TreePath.getPath(compilationUnit, enclosingMethod));
            Unit expectedUnit = checkAndGetHasUnit(node, methodElement);

            // @OverrideUnit takes precedence over @HasUnit for return type checking
            Unit overrideUnit = checkAndGetOverrideUnit(node, methodElement);
            if (overrideUnit != null) {
                expectedUnit = overrideUnit;
            }

            if (expectedUnit != null && node.getExpression() != null) {
                Unit exprUnit = scan(node.getExpression(), p);
                if (exprUnit != null && !exprUnit.isDimensionless() && !expectedUnit.equals(exprUnit)) {
                    dimensionMismatchError(node.getExpression(), expectedUnit, exprUnit);
                }
            }
        }
        return super.visitReturn(node, p);
    }

    private MethodTree findEnclosingMethod() {
        TreePath path = getCurrentPath();
        while (path != null) {
            if (path.getLeaf() instanceof MethodTree) {
                return (MethodTree) path.getLeaf();
            }
            path = path.getParentPath();
        }
        return null;
    }

    @Override
    public Unit visitIdentifier(IdentifierTree node, Void p) {
        Element element = trees.getElement(getCurrentPath());
        if (element != null) {
            if (symbolUnits.containsKey(element)) {
                return symbolUnits.get(element);
            }
            // @OverrideUnit takes precedence over @HasUnit for identifiers
            Unit overrideAnnotated = checkAndGetOverrideUnit(node, element);
            if (overrideAnnotated != null) {
                symbolUnits.put(element, overrideAnnotated);
                return overrideAnnotated;
            }
            Unit annotated = checkAndGetHasUnit(node, element);
            if (annotated != null) {
                symbolUnits.put(element, annotated);
                return annotated;
            }
        }
        return Unit.DIMENSIONLESS;
    }

    @Override
    public Unit visitMemberSelect(MemberSelectTree node, Void p) {
        Element element = trees.getElement(getCurrentPath());
        if (element != null) {
            if (symbolUnits.containsKey(element)) {
                return symbolUnits.get(element);
            }
            // @OverrideUnit takes precedence over @HasUnit for member selects
            Unit overrideAnnotated = checkAndGetOverrideUnit(node, element);
            if (overrideAnnotated != null) {
                symbolUnits.put(element, overrideAnnotated);
                return overrideAnnotated;
            }
            Unit annotated = checkAndGetHasUnit(node, element);
            if (annotated != null) {
                symbolUnits.put(element, annotated);
                return annotated;
            }
        }
        return Unit.DIMENSIONLESS;
    }

    @Override
    public Unit visitMethodInvocation(MethodInvocationTree node, Void p) {
        Element element = trees.getElement(getCurrentPath());
        Unit methodUnit = checkAndGetHasUnit(node, element);

        // @OverrideUnit takes precedence over @HasUnit for method invocations
        Unit overrideMethodUnit = checkAndGetOverrideUnit(node, element);
        if (overrideMethodUnit != null) {
            methodUnit = overrideMethodUnit;
        }

        if (
            node.getMethodSelect() instanceof MemberSelectTree memberSelect &&
            memberSelect.getIdentifier().toString().equals("in")
        ) {
            var receiver = trees.getTypeMirror(new TreePath(getCurrentPath(), memberSelect.getExpression()));
            if (receiver == null || !receiver.toString().contains("org.wpilib.units")) {
                return Unit.DIMENSIONLESS;
            }
            var conversionOutUnit = Unit.parseFromWPILib(node.getArguments().getFirst().toString());
            if (methodUnit != null) {
                trees.printMessage(
                    Diagnostic.Kind.ERROR,
                    "@HasUnit is redundant: unit already inferred to be " + conversionOutUnit,
                    node,
                    compilationUnit
                );
                return methodUnit;
            }
            methodUnit = conversionOutUnit;
        }

        if (methodUnit == null && element != null && symbolUnits.containsKey(element)) {
            methodUnit = symbolUnits.get(element);
        }

        List<? extends ExpressionTree> args = node.getArguments();
        if (element instanceof ExecutableElement exec) {
            List<? extends VariableElement> params = exec.getParameters();
            for (int i = 0; i < args.size(); i++) {
                ExpressionTree arg = args.get(i);
                Unit argUnit = scan(arg, p);
                if (i < params.size()) {
                    VariableElement param = params.get(i);
                    Unit paramUnit = checkAndGetHasUnit(node, param);
                    if (paramUnit != null && argUnit != null && !argUnit.isDimensionless() && !paramUnit.equals(argUnit)) {
                        dimensionMismatchError(arg, paramUnit, argUnit);
                    }
                }
            }
        } else {
            for (ExpressionTree arg : args) {
                scan(arg, p);
            }
        }

        return methodUnit != null ? methodUnit : Unit.DIMENSIONLESS;
    }

    @Override
    public Unit visitBinary(BinaryTree node, Void p) {
        Unit left = scan(node.getLeftOperand(), p);
        Unit right = scan(node.getRightOperand(), p);

        if (left == null) left = Unit.DIMENSIONLESS;
        if (right == null) right = Unit.DIMENSIONLESS;

        Tree.Kind kind = node.getKind();
        if (kind == Tree.Kind.MULTIPLY) {
            return left.multiply(right);
        } else if (kind == Tree.Kind.DIVIDE) {
            return left.divide(right);
        } else if (kind == Tree.Kind.PLUS || kind == Tree.Kind.MINUS) {
            if (left.isDimensionless() && right.isDimensionless()) {
                return Unit.DIMENSIONLESS;
            }
            if (left.equals(right)) {
                return left;
            }
            dimensionMismatchError(node, left, right);
            return left;
        }

        return left.isDimensionless() ? right : left;
    }

    @Override
    public Unit visitParenthesized(ParenthesizedTree node, Void p) {
        Unit inner = scan(node.getExpression(), p);
        return inner != null ? inner : Unit.DIMENSIONLESS;
    }

    @Override
    public Unit visitAssignment(AssignmentTree node, Void p) {
        Unit rhsUnit = scan(node.getExpression(), p);
        TreePath variablePath = new TreePath(getCurrentPath(), node.getVariable());
        Element lhsElement = trees.getElement(variablePath);

        if (lhsElement != null) {
            Unit declared = checkAndGetHasUnit(node, lhsElement);

            // @OverrideUnit takes precedence over @HasUnit for assignment targets
            Unit overrideDeclared = checkAndGetOverrideUnit(node, lhsElement);
            if (overrideDeclared != null) {
                declared = overrideDeclared;
            }

            if (declared != null && rhsUnit != null && !rhsUnit.isDimensionless() && !declared.equals(rhsUnit)) {
                dimensionMismatchError(node.getExpression(), declared, rhsUnit);
            } else if (rhsUnit != null && !rhsUnit.isDimensionless()) {
                symbolUnits.put(lhsElement, rhsUnit);
            }
        }
        return rhsUnit != null ? rhsUnit : Unit.DIMENSIONLESS;
    }

    @Override
    public Unit visitCompoundAssignment(CompoundAssignmentTree node, Void p) {
        Unit rhsUnit = scan(node.getExpression(), p);
        TreePath variablePath = new TreePath(getCurrentPath(), node.getVariable());
        Element lhsElement = trees.getElement(variablePath);

        Unit lhsUnit = Unit.DIMENSIONLESS;
        if (lhsElement != null) {
            // @OverrideUnit takes precedence over cached symbol units and @HasUnit
            Unit overrideLhs = checkAndGetOverrideUnit(node, lhsElement);
            if (overrideLhs != null) {
                symbolUnits.put(lhsElement, overrideLhs);
                lhsUnit = overrideLhs;
            } else if (symbolUnits.containsKey(lhsElement)) {
                lhsUnit = symbolUnits.get(lhsElement);
            } else {
                Unit hasUnit = checkAndGetHasUnit(node, lhsElement);
                if (hasUnit != null) {
                    symbolUnits.put(lhsElement, hasUnit);
                    lhsUnit = hasUnit;
                }
            }
        }

        if (lhsUnit == null) lhsUnit = Unit.DIMENSIONLESS;
        if (rhsUnit == null) rhsUnit = Unit.DIMENSIONLESS;

        Tree.Kind kind = node.getKind();
        if (kind == Tree.Kind.PLUS_ASSIGNMENT || kind == Tree.Kind.MINUS_ASSIGNMENT) {
            if (!lhsUnit.isDimensionless() && !rhsUnit.isDimensionless() && !lhsUnit.equals(rhsUnit)) {
                dimensionMismatchError(node.getExpression(), lhsUnit, rhsUnit);
            }
        } else if (kind == Tree.Kind.MULTIPLY_ASSIGNMENT) {
            Unit newUnit = lhsUnit.multiply(rhsUnit);
            if (lhsElement != null) symbolUnits.put(lhsElement, newUnit);
        } else if (kind == Tree.Kind.DIVIDE_ASSIGNMENT) {
            Unit newUnit = lhsUnit.divide(rhsUnit);
            if (lhsElement != null) symbolUnits.put(lhsElement, newUnit);
        }
        return lhsUnit;
    }
}
