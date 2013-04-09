/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dijedodol.codegen.builder.util;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.WorkingCopy;

/**
 *
 * @author gde.satrigraha
 */
public class BuilderClassGeneratorTask implements Task<WorkingCopy> {

    @Override
    public void run(WorkingCopy workingCopy) throws Exception {
        workingCopy.toPhase(JavaSource.Phase.RESOLVED);
        CompilationUnitTree cut = workingCopy.getCompilationUnit();
        TreeMaker make = workingCopy.getTreeMaker();
        for (Tree typeDecl : cut.getTypeDecls()) {
            if (typeDecl.getKind() == Tree.Kind.CLASS) {
                ClassTree targetClassTree = (ClassTree) typeDecl;
                ClassTree builderClassTree = createBuilderClass(workingCopy, make);

                Map<VariableTree, VariableTree> mappedVariables = new LinkedHashMap<VariableTree, VariableTree>();
                for (Tree classMemberTree : targetClassTree.getMembers()) {
                    if (classMemberTree.getKind() == Tree.Kind.VARIABLE) {
                        VariableTree classVariableTree = (VariableTree) classMemberTree;
                        if (isBuildable(classVariableTree)) {
                            VariableTree builderVariableTree = createBuilderVariable(workingCopy, make, builderClassTree, classVariableTree);
                            MethodTree builderSetterTree = createBuilderSetter(workingCopy, make, builderClassTree, builderVariableTree);

                            VariableTree newVariableTree = make.setInitialValue(classVariableTree, null);
                            workingCopy.rewrite(classVariableTree, newVariableTree);
                            classVariableTree = newVariableTree;

                            builderClassTree = make.addClassMember(builderClassTree, builderVariableTree);
                            builderClassTree = make.addClassMember(builderClassTree, builderSetterTree);

                            mappedVariables.put(classVariableTree, builderVariableTree);
                        }
                    }
                }
                MethodTree targetClassConstructor = ensureTargetConstructor(make, targetClassTree, mappedVariables);
                if (targetClassConstructor != null) {
                    ClassTree newClassTree = make.addClassMember(targetClassTree, targetClassConstructor);
                    workingCopy.rewrite(targetClassTree, newClassTree);
                    targetClassTree = newClassTree;
                }
                ClassTree newClassTree = make.addClassMember(targetClassTree, builderClassTree);
                workingCopy.rewrite(targetClassTree, newClassTree);
                targetClassTree = newClassTree;
            }
        }
    }

    private boolean isBuildable(VariableTree variableTree) {
        ModifiersTree modifiersTree = variableTree.getModifiers();
        for (Modifier modifier : modifiersTree.getFlags()) {
            if (modifier == Modifier.STATIC) {
                return false;
            } else if (modifier == Modifier.FINAL) {
                if (variableTree.getInitializer() != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private ClassTree createBuilderClass(WorkingCopy workingCopy, TreeMaker make) {
        ClassTree builderClassTree = make.Class(
                make.Modifiers(EnumSet.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)),
                "Builder",
                Collections.<TypeParameterTree>emptyList(),
                null,
                Collections.<Tree>emptyList(),
                Collections.<Tree>emptyList());

        MethodTree builderClassEmptyConstructor = make.Constructor(
                make.Modifiers(EnumSet.of(Modifier.PUBLIC)),
                Collections.<TypeParameterTree>emptyList(),
                Collections.<VariableTree>emptyList(),
                Collections.<ExpressionTree>emptyList(),
                make.Block(Collections.<StatementTree>emptyList(), false));

        builderClassTree = make.addClassMember(builderClassTree, builderClassEmptyConstructor);
        return builderClassTree;
    }

    private VariableTree createBuilderVariable(WorkingCopy workingCopy, TreeMaker make, ClassTree builderClassTree, VariableTree classVariableTree) {
        return make.Variable(
                make.Modifiers(EnumSet.of(Modifier.PRIVATE)),
                classVariableTree.getName(),
                classVariableTree.getType(),
                classVariableTree.getInitializer());
    }

    private MethodTree createBuilderSetter(WorkingCopy workingCopy, TreeMaker make, ClassTree builderClassTree, VariableTree builderVariableTree) {
        VariableTree parameter = make.Variable(make.Modifiers(EnumSet.of(Modifier.FINAL)), builderVariableTree.getName(), builderVariableTree.getType(), null);
        List<VariableTree> parameters = Collections.<VariableTree>singletonList(parameter);

        AssignmentTree bodyAssignment = make.Assignment(make.Identifier("this." + builderVariableTree.getName()), make.Identifier(parameter.getName()));
        ReturnTree bodyReturn = make.Return(make.Identifier("this"));

        List<StatementTree> bodyStatements = new LinkedList<StatementTree>();
        bodyStatements.add(make.ExpressionStatement(bodyAssignment));
        bodyStatements.add(bodyReturn);
        BlockTree body = make.Block(bodyStatements, false);

        return make.Method(
                make.Modifiers(EnumSet.of(Modifier.PUBLIC)),
                builderVariableTree.getName(),
                make.Type(builderClassTree.getSimpleName().toString()),
                Collections.<TypeParameterTree>emptyList(),
                parameters,
                Collections.<ExpressionTree>emptyList(),
                body,
                null);
    }

    private MethodTree ensureTargetConstructor(TreeMaker make, ClassTree targetClassTree, Map<VariableTree, VariableTree> mappedVariables) {
        List<VariableTree> finalVariableTrees = new LinkedList<VariableTree>();
        for (Entry<VariableTree, VariableTree> entry : mappedVariables.entrySet()) {
            VariableTree targetClassVariable = entry.getKey();
            boolean finalModifier = false;
            for (Modifier modifier : targetClassVariable.getModifiers().getFlags()) {
                if (modifier == Modifier.FINAL) {
                    finalModifier = true;
                    break;
                }
            }

            if (finalModifier) {
                finalVariableTrees.add(entry.getKey());
            }
        }

        boolean needConstructor = true;
        for (Tree member : targetClassTree.getMembers()) {
            if (member.getKind() == Tree.Kind.METHOD) {
                MethodTree methodTree = (MethodTree) member;
                if (methodTree.getName().contentEquals("<init>")) {
                    if (methodTree.getParameters().size() == finalVariableTrees.size()) {
                        boolean match = true;
                        for (int i = 0; i < methodTree.getParameters().size(); i++) {
                            VariableTree methodParam = methodTree.getParameters().get(i);
                            VariableTree finalVariable = finalVariableTrees.get(i);
                            if (!methodParam.getType().equals(finalVariable.getType())) {
                                match = false;
                                break;
                            }
                        }
                        needConstructor = !match;
                    }
                }
            }
        }

        if (needConstructor) {
            List<VariableTree> parameters = new ArrayList<VariableTree>(finalVariableTrees.size());
            for (VariableTree variableTree : finalVariableTrees) {
                VariableTree parameter = make.Variable(
                        make.Modifiers(EnumSet.of(Modifier.FINAL)),
                        variableTree.getName(),
                        variableTree.getType(),
                        null);
                parameters.add(parameter);
            }

            MethodTree constructor = make.Constructor(
                    make.Modifiers(EnumSet.of(Modifier.PUBLIC)),
                    Collections.<TypeParameterTree>emptyList(),
                    parameters,
                    Collections.<ExpressionTree>emptyList(),
                    make.Block(Collections.<StatementTree>emptyList(), false));

            return constructor;
        } else {
            return null;
        }
    }
}
