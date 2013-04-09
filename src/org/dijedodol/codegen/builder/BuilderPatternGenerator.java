/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dijedodol.codegen.builder;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.spi.editor.codegen.CodeGenerator;
import org.netbeans.spi.editor.codegen.CodeGeneratorContextProvider;
import org.openide.util.Lookup;

public class BuilderPatternGenerator implements CodeGenerator {

    JTextComponent textComp;

    /**
     *
     * @param context containing JTextComponent and possibly other items
     * registered by {@link CodeGeneratorContextProvider}
     */
    private BuilderPatternGenerator(Lookup context) { // Good practice is not to save Lookup outside ctor
        textComp = context.lookup(JTextComponent.class);
    }

    @MimeRegistration(mimeType = "text/x-java", service = CodeGenerator.Factory.class)
    public static class Factory implements CodeGenerator.Factory {

        public List<? extends CodeGenerator> create(Lookup context) {
            return Collections.singletonList(new BuilderPatternGenerator(context));
        }
    }

    /**
     * The name which will be inserted inside Insert Code dialog
     */
    public String getDisplayName() {
        return "Generate Builder";
    }

    /**
     * This will be invoked when user chooses this Generator from Insert Code
     * dialog
     */
    public void invoke() {
        Document doc = textComp.getDocument();
        JavaSource javaSource = JavaSource.forDocument(doc);
        try {
            javaSource.runModificationTask(new Task<WorkingCopy>() {
                @Override
                public void run(WorkingCopy workingCopy) throws Exception {
                    workingCopy.toPhase(Phase.RESOLVED);
                    CompilationUnitTree cut = workingCopy.getCompilationUnit();
                    TreeMaker make = workingCopy.getTreeMaker();
                    for (Tree typeDecl : cut.getTypeDecls()) {
                        if (typeDecl.getKind() == Tree.Kind.CLASS) {
                            ClassTree clazz = (ClassTree) typeDecl;
                            ClassTree builderClassTree = make.Class(
                                    make.Modifiers(EnumSet.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)),
                                    "Builder",
                                    Collections.<TypeParameterTree>emptyList(),
                                    null,
                                    Collections.<Tree>emptyList(),
                                    Collections.<Tree>emptyList());

                            for (Tree classMemberTree : clazz.getMembers()) {
                                if (classMemberTree.getKind() == Tree.Kind.VARIABLE) {
                                    VariableTree classVariableTree = (VariableTree) classMemberTree;

                                    VariableTree builderVariableTree = createBuilderVariable(workingCopy, make, builderClassTree, classVariableTree);
                                    MethodTree builderSetterTree = createBuilderSetter(workingCopy, make, builderClassTree, builderVariableTree);

                                    workingCopy.rewrite(classVariableTree, make.setInitialValue(classVariableTree, null));
                                    builderClassTree = make.addClassMember(builderClassTree, builderVariableTree);
                                    builderClassTree = make.addClassMember(builderClassTree, builderSetterTree);
                                }
                            }

                            ClassTree newClazz = make.addClassMember(clazz, builderClassTree);
                            workingCopy.rewrite(clazz, newClazz);
                            System.out.println("Uyeee");
                        }
                    }
                }
            }).commit();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private VariableTree createBuilderVariable(WorkingCopy workingCopy, TreeMaker make, ClassTree builderClassTree, VariableTree classVariableTree) {
        return make.Variable(
                classVariableTree.getModifiers(),
                classVariableTree.getName(),
                classVariableTree.getType(),
                classVariableTree.getInitializer());
    }

//    private MethodTree createBuilderSetter(TreeMaker make, TypeElement, VariableTree builderVariableTree) {
//        ModifiersTree modifiersTree = make.Modifiers(EnumSet.of(Modifier.PUBLIC));
//        List<VariableTree> parameters = Collections.<VariableTree>singletonList(
//                make.Variable(make.Modifiers(EnumSet.of(Modifier.FINAL)), builderVariableTree.getName().toString(), builderVariableTree.getType(), null));
//
//        TypeElement builderClassElement = workingCopy.getElements().getTypeElement(builderClassTree.getSimpleName());
//        BlockTree body = make.Block(
//                Collections.<StatementTree>singletonList(make.Return(make.Identifier("this"))), false);
//
//        return make.Method(
//                modifiersTree,
//                builderVariableTree.getName().toString(),
//                make.Type(builderClassTree.getSimpleName().toString()),
//                Collections.<TypeParameterTree>emptyList(),
//                parameters,
//                Collections.<ExpressionTree>emptyList(),
//                body,
//                null);
//    }
    private MethodTree createBuilderSetter(WorkingCopy workingCopy, TreeMaker make, ClassTree builderClassTree, VariableTree builderVariableTree) {
        ModifiersTree modifiersTree = make.Modifiers(EnumSet.of(Modifier.PUBLIC));
        List<VariableTree> parameters = Collections.<VariableTree>singletonList(
                make.Variable(make.Modifiers(EnumSet.of(Modifier.FINAL)), builderVariableTree.getName().toString(), builderVariableTree.getType(), null));

        //TypeElement builderClassElement = workingCopy.getElements().getTypeElement(builderClassTree.getSimpleName());
        BlockTree body = make.Block(
                Collections.<StatementTree>singletonList(make.Return(make.Identifier("this"))), false);

        return make.Method(
                modifiersTree,
                builderVariableTree.getName().toString(),
                make.Type(builderClassTree.getSimpleName().toString()),
                Collections.<TypeParameterTree>emptyList(),
                parameters,
                Collections.<ExpressionTree>emptyList(),
                body,
                null);
    }
}
