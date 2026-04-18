package ru.warmine.fairwol.wcglow.transformer;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import ru.warmine.fairwol.wcglow.util.ASMUtils;

public class ASMOutlineTransformer implements IClassTransformer {

    private static final String HOOK_OWNER = "ru/warmine/fairwol/wcglow/glow/EntityOutlineController";
    private static final String I_CAMERA = "net/minecraft/client/renderer/culling/ICamera";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"net.minecraft.client.renderer.RenderGlobal".equals(transformedName)) {
            return bytes;
        }

        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);

        boolean changed = patchIsRenderEntityOutlines(node);
        changed |= patchRenderEntities(node);
        changed |= patchRenderEntityOutlineFramebuffer(node);

        if (!changed) {
            return bytes;
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private boolean patchIsRenderEntityOutlines(ClassNode node) {
        MethodNode methodNode = ASMUtils.findMethodNode(node, "isRenderEntityOutlines", "()Z");
        if (methodNode == null) {
            methodNode = ASMUtils.findMethodNode(node, "func_174985_d", "()Z");
        }

        if (methodNode == null) {
            return false;
        }

        InsnList insns = new InsnList();
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOK_OWNER,
                "shouldRenderVanillaEntityOutlines",
                "(Lnet/minecraft/client/renderer/RenderGlobal;)Z",
                false
        ));
        insns.add(new InsnNode(Opcodes.IRETURN));

        methodNode.instructions.clear();
        methodNode.tryCatchBlocks.clear();
        methodNode.instructions.add(insns);
        methodNode.maxStack = 1;
        methodNode.maxLocals = 1;
        return true;
    }

    private boolean patchRenderEntities(ClassNode node) {
        MethodNode methodNode = ASMUtils.findMethodNode(
                node,
                "renderEntities",
                "(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V"
        );

        if (methodNode == null) {
            methodNode = ASMUtils.findMethodNode(node, "func_180446_a", "(Lpk;Lbia;F)V");
        }

        if (methodNode == null) {
            return false;
        }

        for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode) || insn.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                continue;
            }

            MethodInsnNode methodInsnNode = (MethodInsnNode) insn;
            if (!"isRenderEntityOutlines".equals(methodInsnNode.name) && !"func_174985_d".equals(methodInsnNode.name)) {
                continue;
            }

            AbstractInsnNode next = nextMeaningful(methodInsnNode);
            if (!(next instanceof JumpInsnNode) || next.getOpcode() != Opcodes.IFEQ) {
                continue;
            }

            JumpInsnNode jumpInsnNode = (JumpInsnNode) next;
            AbstractInsnNode anchor = previousMeaningful(methodInsnNode);
            if (anchor == null) {
                return false;
            }

            methodNode.instructions.insertBefore(anchor, buildRenderEntitiesHook(jumpInsnNode.label));
            return true;
        }

        return false;
    }

    private boolean patchRenderEntityOutlineFramebuffer(ClassNode node) {
        MethodNode methodNode = ASMUtils.findMethodNode(node, "renderEntityOutlineFramebuffer", "()V");
        if (methodNode == null) {
            return false;
        }

        for (AbstractInsnNode insn = methodNode.instructions.getLast(); insn != null; insn = insn.getPrevious()) {
            if (insn.getOpcode() != Opcodes.RETURN) {
                continue;
            }

            methodNode.instructions.insertBefore(insn, new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HOOK_OWNER,
                    "afterFramebufferDraw",
                    "()V",
                    false
            ));
            return true;
        }

        return false;
    }

    private InsnList buildRenderEntitiesHook(LabelNode skipLabel) {
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 2));
        list.add(new VarInsnNode(Opcodes.FLOAD, 3));
        list.add(new VarInsnNode(Opcodes.DLOAD, 5));
        list.add(new VarInsnNode(Opcodes.DLOAD, 7));
        list.add(new VarInsnNode(Opcodes.DLOAD, 9));
        list.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOK_OWNER,
                "renderEntityOutlines",
                "(L" + I_CAMERA + ";FDDD)Z",
                false
        ));
        list.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        return list;
    }

    private AbstractInsnNode previousMeaningful(AbstractInsnNode node) {
        AbstractInsnNode current = node.getPrevious();
        while (current != null && (current.getType() == AbstractInsnNode.FRAME
                || current.getType() == AbstractInsnNode.LABEL
                || current.getType() == AbstractInsnNode.LINE)) {
            current = current.getPrevious();
        }
        return current;
    }

    private AbstractInsnNode nextMeaningful(AbstractInsnNode node) {
        AbstractInsnNode current = node.getNext();
        while (current != null && (current.getType() == AbstractInsnNode.FRAME
                || current.getType() == AbstractInsnNode.LABEL
                || current.getType() == AbstractInsnNode.LINE)) {
            current = current.getNext();
        }
        return current;
    }
}
