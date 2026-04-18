package ru.warmine.fairwol.wcglow.util;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class ASMUtils {

    private ASMUtils() {
    }

    public static MethodNode findMethodNode(ClassNode classNode, String name, String desc) {
        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.name.equals(name) && methodNode.desc.equals(desc)) {
                return methodNode;
            }
        }
        return null;
    }
}
