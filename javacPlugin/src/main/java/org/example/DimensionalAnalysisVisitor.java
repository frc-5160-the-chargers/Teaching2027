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
    private final Map<Tree, Unit> treeUnits = new HashMap<>();

    public DimensionalAnalysisVisitor(Trees trees) {
        this.trees = trees;
    }

    public void scanCompilationUnit(CompilationUnitTree cu) {
        this.compilationUnit = cu;
        scan(cu, null);
    }

    private Unit checkAndGetHasUnit(Element element) {
        if (element == null) return null;
        HasUnit annotation = element.getAnnotation(HasUnit.class);
        return annotation == null ? null : Unit.parse(annotation.value());
    }

    private void emitError(Tree node, String message) {
        trees.printMessage(Diagnostic.Kind.ERROR, message, node, compilationUnit);
    }

    @Override
    public Unit visitVariable(VariableTree node, Void p) {
        Element element = trees.getElement(getCurrentPath());
        Unit declaredUnit = checkAndGetHasUnit(element);

        Unit initUnit = null;
        if (node.getInitializer() != null) {
            initUnit = scan(node.getInitializer(), p);
        }

        if (declaredUnit != null) {
            if (element != null) {
                symbolUnits.put(element, declaredUnit);
            }
            if (initUnit != null && !initUnit.isDimensionless()) {
                if (!declaredUnit.equals(initUnit)) {
                    if (declaredUnit.getDimensionMap().equals(initUnit.getDimensionMap())) {
                        emitError(node.getInitializer() != null ? node.getInitializer() : node,
                                initUnit.toHumanName() + " cannot be assigned to " + declaredUnit.toHumanName() + ".");
                    } else {
                        emitError(node.getInitializer() != null ? node.getInitializer() : node,
                                "dimension mismatch");
                    }
                }
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
        Unit methodUnit = checkAndGetHasUnit(element);
        if (methodUnit != null && element != null) {
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
            Unit expectedUnit = checkAndGetHasUnit(methodElement);
            if (expectedUnit != null && node.getExpression() != null) {
                Unit exprUnit = scan(node.getExpression(), p);
                if (exprUnit != null && !exprUnit.isDimensionless() && !expectedUnit.equals(exprUnit)) {
                    if (expectedUnit.getDimensionMap().equals(exprUnit.getDimensionMap())) {
                        emitError(node.getExpression(),
                                exprUnit.toHumanName() + " cannot be returned for unit " + expectedUnit.toHumanName() + ".");
                    } else {
                        emitError(node.getExpression(), "dimension mismatch");
                    }
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
            Unit annotated = checkAndGetHasUnit(element);
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
            Unit annotated = checkAndGetHasUnit(element);
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
        Unit methodUnit = checkAndGetHasUnit(element);
        if (methodUnit == null && element != null && symbolUnits.containsKey(element)) {
            methodUnit = symbolUnits.get(element);
        }

        List<? extends ExpressionTree> args = node.getArguments();
        if (element instanceof ExecutableElement) {
            ExecutableElement exec = (ExecutableElement) element;
            List<? extends VariableElement> params = exec.getParameters();
            for (int i = 0; i < args.size(); i++) {
                ExpressionTree arg = args.get(i);
                Unit argUnit = scan(arg, p);
                if (i < params.size()) {
                    VariableElement param = params.get(i);
                    Unit paramUnit = checkAndGetHasUnit(param);
                    if (paramUnit != null && argUnit != null && !argUnit.isDimensionless() && !paramUnit.equals(argUnit)) {
                        if (paramUnit.getDimensionMap().equals(argUnit.getDimensionMap())) {
                            emitError(arg, argUnit.toHumanName() + " cannot be passed for parameter unit " + paramUnit.toHumanName() + ".");
                        } else {
                            emitError(arg, "dimension mismatch");
                        }
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
            Unit res = left.multiply(right);
            treeUnits.put(node, res);
            return res;
        } else if (kind == Tree.Kind.DIVIDE) {
            Unit res = left.divide(right);
            treeUnits.put(node, res);
            return res;
        } else if (kind == Tree.Kind.PLUS || kind == Tree.Kind.MINUS) {
            if (left.isDimensionless() && right.isDimensionless()) {
                treeUnits.put(node, Unit.DIMENSIONLESS);
                return Unit.DIMENSIONLESS;
            }
            if (left.isDimensionless() || right.isDimensionless()) {
                emitError(node, "dimension mismatch");
                return left.isDimensionless() ? right : left;
            }
            if (left.equals(right)) {
                treeUnits.put(node, left);
                return left;
            }
            if (left.getDimensionMap().equals(right.getDimensionMap())) {
                emitError(node, right.toHumanName() + " cannot be added to " + left.toHumanName() + ".");
            } else {
                emitError(node, "dimension mismatch");
            }
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
            Unit declared = checkAndGetHasUnit(lhsElement);
            if (declared != null) {
                if (rhsUnit != null && !rhsUnit.isDimensionless() && !declared.equals(rhsUnit)) {
                    if (declared.getDimensionMap().equals(rhsUnit.getDimensionMap())) {
                        emitError(node.getExpression(), rhsUnit.toHumanName() + " cannot be assigned to " + declared.toHumanName() + ".");
                    } else {
                        emitError(node.getExpression(), "dimension mismatch");
                    }
                }
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
        Unit lhsUnit = lhsElement != null ? (symbolUnits.containsKey(lhsElement) ? symbolUnits.get(lhsElement) : checkAndGetHasUnit(lhsElement)) : Unit.DIMENSIONLESS;

        if (lhsUnit == null) lhsUnit = Unit.DIMENSIONLESS;
        if (rhsUnit == null) rhsUnit = Unit.DIMENSIONLESS;

        Tree.Kind kind = node.getKind();
        if (kind == Tree.Kind.PLUS_ASSIGNMENT || kind == Tree.Kind.MINUS_ASSIGNMENT) {
            if (!lhsUnit.isDimensionless() && !rhsUnit.isDimensionless() && !lhsUnit.equals(rhsUnit)) {
                if (lhsUnit.getDimensionMap().equals(rhsUnit.getDimensionMap())) {
                    emitError(node.getExpression(), rhsUnit.toHumanName() + " cannot be added to " + lhsUnit.toHumanName() + ".");
                } else {
                    emitError(node.getExpression(), "dimension mismatch");
                }
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
