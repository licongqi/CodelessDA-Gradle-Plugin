package com.netease.mobidroid.plugin.utils

import com.netease.mobidroid.plugin.MethodCell
import com.netease.mobidroid.plugin.ReWriterAgent
import org.objectweb.asm.*

/**
 * Created by bryansharp(bsp0911932@163.com) on 2016/5/10.
 *
 * @author bryansharp
 * Project: FirstGradle
 * introduction:
 */
public class ModifyClassUtil {

    public
    static byte[] modifyClasses(String className, byte[] srcByteCode) {
        byte[] classBytesCode = null;
        try {
            Log.info("====start modifying ${className}====");
            classBytesCode = modifyClass(srcByteCode);
            Log.info("====revisit modified ${className}====");
            onlyVisitClassMethod(classBytesCode);
            Log.info("====finish modifying ${className}====");
            return classBytesCode;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (classBytesCode == null) {
            classBytesCode = srcByteCode;
        }
        return classBytesCode;
    }


    private
    static byte[] modifyClass(byte[] srcClass) throws IOException {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassAdapter adapter = new MethodFilterClassAdapter(classWriter);
        ClassReader cr = new ClassReader(srcClass);
        cr.accept(adapter, ClassReader.SKIP_DEBUG);
        return classWriter.toByteArray();
    }

    private
    static void onlyVisitClassMethod(byte[] srcClass) throws IOException {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        MethodFilterClassAdapter adapter = new MethodFilterClassAdapter(classWriter);
        adapter.onlyVisit = true;
        ClassReader cr = new ClassReader(srcClass);
        cr.accept(adapter, ClassReader.SKIP_DEBUG);
    }

    static class MethodFilterClassAdapter extends ClassAdapter implements Opcodes {
//        private String className;
        private List<Map<String, Object>> methodMatchMaps;
        public boolean onlyVisit = false;
        public HashMap<MethodCell, MethodCell> addMethods
        private String superName
        private ClassVisitor classVisitor

        public MethodFilterClassAdapter(
                final ClassVisitor cv) {
            super(cv);
            this.classVisitor = cv
        }

        @Override
        void visitEnd() {
            Log.logEach('* visitEnd *');
            if (addMethods != null) {
                MethodVisitor mv;
                // 添加剩下的方法，确保super.onHiddenChanged(hidden);等先被调用
                addMethods.each {
                    MethodCell key = it.getKey()
                    MethodCell value = it.getValue()

                    mv = classVisitor.visitMethod(ACC_PUBLIC, key.name, key.desc, null, null);
                    mv.visitCode();
                    mv.visitVarInsn(ALOAD, 0);
                    if (key.desc.contains('Z')) {
                        // (this,bool)
                        mv.visitVarInsn(ALOAD, 1);
                    }
                    mv.visitMethodInsn(INVOKESPECIAL, superName, key.name, key.desc);
                    mv.visitVarInsn(ALOAD, 0);
                    if (value.desc.contains('Z')) {
                        // (this,bool)
                        mv.visitVarInsn(ALOAD, 1);
                    }
                    mv.visitMethodInsn(INVOKESTATIC, ReWriterAgent.sAgentClassName, value.name, value.desc);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
                }
            }
            super.visitEnd()
        }

        @Override
        void visitAttribute(Attribute attribute) {
            Log.logEach('* visitAttribute *', attribute, attribute.type, attribute.metaClass, attribute.metaPropertyValues, attribute.properties);
            super.visitAttribute(attribute)
        }

        @Override
        AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            Log.logEach('* visitAnnotation *', desc, visible);
            return super.visitAnnotation(desc, visible)
        }

        @Override
        void visitInnerClass(String name, String outerName,
                             String innerName, int access) {
            Log.logEach('* visitInnerClass *', name, outerName, innerName, Log.accCode2String(access));
            super.visitInnerClass(name, outerName, innerName, access)
        }

        @Override
        void visitOuterClass(String owner, String name, String desc) {
            Log.logEach('* visitOuterClass *', owner, name, desc);
            super.visitOuterClass(owner, name, desc)
        }

        @Override
        void visitSource(String source, String debug) {
            Log.logEach('* visitSource *', source, debug);
            super.visitSource(source, debug)
        }

        @Override
        FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            Log.logEach('* visitField *', Log.accCode2String(access), name, desc, signature, value);
            return super.visitField(access, name, desc, signature, value)
        }

        @Override
        public void visit(int version, int access, String name,
                          String signature, String superName, String[] interfaces) {
            Log.logEach('* visit *', Log.accCode2String(access), name, signature, superName, interfaces);
            this.superName = superName
            if (interfaces != null && interfaces.contains('android/view/View$OnClickListener')) {
                this.methodMatchMaps = ReWriterAgent.getClickReWriter()
                Log.logEach('* visit *', "Class that implements OnClickListener")
            } else if (instanceOfFragment(superName)) {
                this.methodMatchMaps = ReWriterAgent.getFragmentReWriter(superName)
                addMethods = ReWriterAgent.getFragmentAddMethods()
                Log.logEach('* visit *', "Class that extends Fragment")
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        private boolean instanceOfFragment(String superName) {
            return superName.equals('android/app/Fragment') || superName.equals('android/support/v4/app/Fragment')
        }

        @Override
        public MethodVisitor visitMethod(int access, String name,
                                         String desc, String signature, String[] exceptions) {
            if (addMethods != null) {// It means this class extends Fragment
                MethodCell delCell
                addMethods.each {
                    MethodCell key = it.getKey()
                    if (name.equals(key.name) && desc.equals(key.desc)) {
                        delCell = key
                    }
                }
                if (delCell != null) {
                    // 如果该Fragment类中存在该方法则删除
                    addMethods.remove(delCell)
                }

            }
            MethodVisitor myMv = null;
            if (!onlyVisit) {
                Log.logEach("* visitMethod *", Log.accCode2String(access), name, desc, signature, exceptions);
            }
            if (methodMatchMaps != null) {
                methodMatchMaps.each {
                    Map<String, Object> map ->
                        String metName = map.get('methodName');
                        String methodDesc = map.get('methodDesc');
                        if (name.equals(metName)) {
                            Closure visit = map.get('adapter');
                            if (visit != null) {
                                if (methodDesc != null) {
                                    if (methodDesc.equals(desc)) {
                                        if (onlyVisit) {
                                            myMv = new MethodLogAdapter(cv.visitMethod(access, name, desc, signature, exceptions));
                                        } else {
                                            try {
                                                myMv = visit(cv, access, name, desc, signature, exceptions);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                myMv = null
                                            }
                                        }
                                    }
                                } else {
                                    try {
                                        myMv = visit(cv, access, name, desc, signature, exceptions);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        myMv = null
                                    }
                                }
                            }
                        }
                }
            }
            if (myMv != null) {
                if (onlyVisit) {
                    Log.logEach("* revisitMethod *", Log.accCode2String(access), name, desc, signature);
                }
                return myMv;
            } else {
                return cv.visitMethod(access, name, desc, signature, exceptions);
            }
        }
    }
}