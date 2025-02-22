package com.tom.cpmcore;

import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;

import com.tom.cpm.shared.config.PlayerData;
import com.tom.cpm.shared.network.NetH;
import com.tom.cpm.shared.network.NetH.ServerNetH;

public class CPMTransformerService implements IClassTransformer {
	public static final Logger LOG = LogManager.getLogger("CPM Core");
	private static final String HOOKS_CLASS = "com/tom/cpmcore/CPMASMClientHooks";
	private static final String HOOKS_CLASS_SERVER = "com/tom/cpmcore/CPMASMServerHooks";
	private static final String NO_MODEL_SETUP_FIELD = "cpm$noModelSetup";
	private static final String HAS_MOD_FIELD = "cpm$hasMod";
	private static final String DATA_FIELD = "cpm$data";
	private static Map<String, UnaryOperator<ClassNode>> transformers;

	public static void init() {
		transformers = new HashMap<>();

		transformers.put("net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer", new UnaryOperator<ClassNode>() {

			@Override
			public ClassNode apply(ClassNode input) {
				InsnList lst = new InsnList();
				int modelBase = 8;
				lst.add(new VarInsnNode(Opcodes.ALOAD, modelBase));//ModelBase modelbase
				lst.add(new VarInsnNode(Opcodes.ALOAD, 7));//GameProfile profile
				lst.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS_CLASS, "renderSkull", "(Lnet/minecraft/client/model/ModelBase;Lcom/mojang/authlib/GameProfile;)V", false));

				InsnList lst2 = new InsnList();
				lst2.add(new VarInsnNode(Opcodes.ALOAD, modelBase));//ModelBase modelbase
				lst2.add(new VarInsnNode(Opcodes.ALOAD, 7));//GameProfile profile
				lst2.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS_CLASS, "renderSkullPost", "(Lnet/minecraft/client/model/ModelBase;Lcom/mojang/authlib/GameProfile;)V", false));

				MethodNode m = null;

				for(MethodNode method : input.methods) {
					if(method.desc.equals("(FFFIFILcom/mojang/authlib/GameProfile;)V")) {
						m = method;
						LOG.info("CPM Skull Hook: found method");
						break;
					}
				}

				for (ListIterator<AbstractInsnNode> it = m.instructions.iterator(); it.hasNext(); ) {
					AbstractInsnNode insnNode = it.next();
					if(insnNode instanceof VarInsnNode){
						VarInsnNode nd = (VarInsnNode) insnNode;
						if(nd.getOpcode() == Opcodes.ALOAD && nd.var == modelBase) {
							m.instructions.insertBefore(nd, lst);
							LOG.info("CPM Skull Hook: injected (Pre)");
						} else if(insnNode instanceof InsnNode){
							if(insnNode.getOpcode() == Opcodes.RETURN) {
								m.instructions.insertBefore(insnNode, lst2);
								LOG.info("CPM Skull Hook: injected (Post)");
							}
						}
					}
				}
				return input;
			}
		});
		transformers.put("net.minecraft.client.resources.SkinManager", new UnaryOperator<ClassNode>() {

			@Override
			public ClassNode apply(ClassNode input) {
				for(MethodNode m : input.methods) {
					if((m.name.equals("a") && m.desc.equals("(Lcom/mojang/authlib/minecraft/MinecraftProfileTexture;Lcom/mojang/authlib/minecraft/MinecraftProfileTexture$Type;Lbro;)Lbqx;")) || m.name.equals("func_152789_a")) {
						LOG.info("CPM Load Skin Hook: Found loadSkin method");
						InsnList lst = new InsnList();
						lst.add(new VarInsnNode(Opcodes.ALOAD, 1));
						lst.add(new VarInsnNode(Opcodes.ALOAD, 2));
						lst.add(new VarInsnNode(Opcodes.ALOAD, 3));
						lst.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS_CLASS, "loadSkinHook", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(m.desc)), false));
						m.instructions.insertBefore(m.instructions.getFirst(), lst);
						LOG.info("CPM Load Skin Hook: injected");
						break;
					}
				}
				return input;
			}
		});
		transformers.put("net.minecraft.client.renderer.entity.RendererLivingEntity", new UnaryOperator<ClassNode>() {

			@Override
			public ClassNode apply(ClassNode input) {
				for(MethodNode m : input.methods) {
					if((m.name.equals("a") && m.desc.equals("(Lsv;DDDFF)V")) || m.name.equals("doRender")) {
						LOG.info("CPM Armor Hook: Found loadSkin method");
						for (ListIterator<AbstractInsnNode> it = m.instructions.iterator(); it.hasNext(); ) {
							AbstractInsnNode insnNode = it.next();
							if(insnNode instanceof MethodInsnNode) {
								MethodInsnNode mn = (MethodInsnNode) insnNode;
								if((mn.name.equals("a") && mn.desc.equals("(Lsa;FFFFFF)V")) || mn.name.equals("render")) {
									LOG.info("CPM Armor Hook: Found render call");
									Type[] argsD = Type.getArgumentTypes(mn.desc);
									Type[] args = new Type[argsD.length + 2];
									args[0] = Type.getObjectType(mn.owner);
									System.arraycopy(argsD, 0, args, 1, argsD.length);
									args[args.length - 1] = Type.getType("Lnet/minecraft/client/renderer/entity/RendererLivingEntity;");
									mn.desc = Type.getMethodDescriptor(Type.VOID_TYPE, args);
									mn.name = "renderPass";
									mn.setOpcode(Opcodes.INVOKESTATIC);
									mn.owner = HOOKS_CLASS;
									InsnList lst = new InsnList();
									lst.add(new VarInsnNode(Opcodes.ALOAD, 0));
									m.instructions.insertBefore(insnNode, lst);
									LOG.info("CPM Armor Hook/Layer: injected");
								}
							}
						}
					}
				}
				return input;
			}
		});
		transformers.put("com.tom.cpmcore.CPMASMClientHooks", new UnaryOperator<ClassNode>() {

			@Override
			public ClassNode apply(ClassNode input) {
				for(MethodNode method : input.methods) {
					if(method.name.equals("setNoSetup")) {
						method.instructions.clear();
						method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
						method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
						method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, Type.getArgumentTypes(method.desc)[0].getInternalName(), NO_MODEL_SETUP_FIELD, "Z"));
						method.instructions.add(new InsnNode(Opcodes.RETURN));
						LOG.info("CPM ASM fields/No Render: injected");
					}
				}
				return input;
			}
		});
		transformers.put("net.minecraft.client.model.ModelBiped", new UnaryOperator<ClassNode>() {

			@Override
			public ClassNode apply(ClassNode input) {
				input.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, NO_MODEL_SETUP_FIELD, "Z", null, 0));

				for(MethodNode method : input.methods) {
					if((method.name.equals("a") && method.desc.equals("(FFFFFFLsa;)V")) || method.name.equals("setRotationAngles")) {
						LOG.info("CPM Armor Hook/No setup: found setRotationAngles method");
						InsnList lst = new InsnList();
						lst.add(new VarInsnNode(Opcodes.ALOAD, 0));
						lst.add(new FieldInsnNode(Opcodes.GETFIELD, input.name, NO_MODEL_SETUP_FIELD, "Z"));
						LabelNode lbln = new LabelNode();
						lst.add(new JumpInsnNode(Opcodes.IFEQ, lbln));
						lst.add(new InsnNode(Opcodes.RETURN));
						lst.add(lbln);
						method.instructions.insertBefore(method.instructions.getFirst(), lst);
						LOG.info("CPM Armor Hook/No setup: injected");
					}
				}
				return input;
			}
		});
		transformers.put("net.minecraft.client.Minecraft", new UnaryOperator<ClassNode>() {

			@Override
			public ClassNode apply(ClassNode input) {
				InsnList lst = new InsnList();
				lst.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS_CLASS, "onLogout", "()V", false));

				for(MethodNode method : input.methods) {
					if(method.name.equals("loadWorld") || (method.name.equals("a") && method.desc.equals("(Lbjf;Ljava/lang/String;)V"))) {
						LOG.info("CPM ClientLogout Hook: Found loadWorld");
						for (ListIterator<AbstractInsnNode> it = method.instructions.iterator(); it.hasNext(); ) {
							AbstractInsnNode insnNode = it.next();
							if(insnNode instanceof MethodInsnNode) {
								MethodInsnNode mn = (MethodInsnNode) insnNode;
								if((mn.name.equals("b") && mn.desc.equals("()V") && mn.getPrevious() instanceof FieldInsnNode && ((FieldInsnNode)mn.getPrevious()).name.equals("q")) || mn.name.equals("func_146257_b")) {
									method.instructions.insert(mn, lst);
									LOG.info("CPM ClientLogout Hook: injected");
								}
							}
						}
					}
				}
				return input;
			}
		});
		transformers.put("net.minecraft.client.network.NetHandlerPlayClient", new UnaryOperator<ClassNode>() {

			@Override
			public ClassNode apply(ClassNode input) {
				InsnList lst = new InsnList();
				lst.add(new VarInsnNode(Opcodes.ALOAD, 1));
				lst.add(new VarInsnNode(Opcodes.ALOAD, 0));
				lst.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS_CLASS, "onClientPacket", "(Lnet/minecraft/network/play/server/S3FPacketCustomPayload;Lnet/minecraft/client/network/NetHandlerPlayClient;)Z", false));
				LabelNode lbln = new LabelNode();
				lst.add(new JumpInsnNode(Opcodes.IFEQ, lbln));
				lst.add(new InsnNode(Opcodes.RETURN));
				lst.add(lbln);

				input.interfaces.add(Type.getInternalName(NetH.class));

				input.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, HAS_MOD_FIELD, "Z", null, 0));

				for(MethodNode method : input.methods) {
					if(method.name.equals("handleCustomPayload") || (method.name.equals("a") && method.desc.equals("(Lgr;)V"))) {
						LOG.info("CPM ClientNet Hook: Found handleCustomPayload");
						method.instructions.insertBefore(method.instructions.getFirst(), lst);
						LOG.info("CPM ClientNet Hook: injected");
					}
				}

				injectHasMod("ClientNet", input);

				return input;
			}
		});
		transformers.put("net.minecraft.network.NetHandlerPlayServer", new UnaryOperator<ClassNode>() {

			@Override
			public ClassNode apply(ClassNode input) {
				InsnList lst = new InsnList();
				lst.add(new VarInsnNode(Opcodes.ALOAD, 1));
				lst.add(new VarInsnNode(Opcodes.ALOAD, 0));
				lst.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS_CLASS_SERVER, "onServerPacket", "(Lnet/minecraft/network/play/client/C17PacketCustomPayload;Lnet/minecraft/network/NetHandlerPlayServer;)Z", false));
				LabelNode lbln = new LabelNode();
				lst.add(new JumpInsnNode(Opcodes.IFEQ, lbln));
				lst.add(new InsnNode(Opcodes.RETURN));
				lst.add(lbln);

				input.interfaces.add(Type.getInternalName(ServerNetH.class));

				input.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, HAS_MOD_FIELD, "Z", null, 0));
				input.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, DATA_FIELD, Type.getDescriptor(PlayerData.class), null, null));

				for(MethodNode method : input.methods) {
					if(method.name.equals("processVanilla250Packet") || (method.name.equals("a") && method.desc.equals("(Liz;)V"))) {
						LOG.info("CPM ServerNet Hook: Found processCustomPayload");
						method.instructions.insertBefore(method.instructions.getFirst(), lst);
						LOG.info("CPM ServerNet Hook: injected");
					}
				}

				injectHasMod("ServerNet", input);

				MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "cpm$getEncodedModelData", Type.getMethodDescriptor(Type.getType(PlayerData.class)), null, null);
				input.methods.add(method);
				method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
				method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, input.name, DATA_FIELD, Type.getDescriptor(PlayerData.class)));
				method.instructions.add(new InsnNode(Opcodes.ARETURN));
				LOG.info("CPM ServerNet/getData: injected");

				method = new MethodNode(Opcodes.ACC_PUBLIC, "cpm$setEncodedModelData", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(PlayerData.class)), null, null);
				input.methods.add(method);
				method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
				method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
				method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, input.name, DATA_FIELD, Type.getDescriptor(PlayerData.class)));
				method.instructions.add(new InsnNode(Opcodes.RETURN));
				LOG.info("CPM ServerNet/setData: injected");
				return input;
			}
		});
		transformers.put("net.minecraft.client.renderer.EntityRenderer", new UnaryOperator<ClassNode>() {

			@Override
			public ClassNode apply(ClassNode input) {
				for(MethodNode method : input.methods) {
					if(method.desc.equals("(FJ)V") && (method.name.equals("a") || method.name.equals("renderWorld"))) {
						LOG.info("CPM Hand hook: found renderWorld method");
						for (ListIterator<AbstractInsnNode> it = method.instructions.iterator(); it.hasNext(); ) {
							AbstractInsnNode insnNode = it.next();
							if(insnNode instanceof MethodInsnNode) {
								MethodInsnNode mn = (MethodInsnNode) insnNode;
								if(mn.desc.equals("(FI)V") && (mn.name.equals("renderHand") || (mn.owner.equals("blt") && mn.name.equals("b")))) {
									LOG.info("CPM Hand hook: found renderHand method node");
									while (it.hasNext()) {
										insnNode = it.next();
										if(insnNode instanceof VarInsnNode) {
											method.instructions.insertBefore(insnNode, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS_CLASS, "unbindHand", "()V", false));
											LOG.info("CPM Hand hook: injected");
											break;
										}
									}
								}
							}
						}
					}
				}
				return input;
			}
		});
	}

	private static void injectHasMod(String name, ClassNode input) {
		MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "cpm$hasMod", "()Z", null, null);
		input.methods.add(method);
		method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, input.name, HAS_MOD_FIELD, "Z"));
		method.instructions.add(new InsnNode(Opcodes.IRETURN));
		LOG.info("CPM " + name + "/hasMod: injected");

		method = new MethodNode(Opcodes.ACC_PUBLIC, "cpm$setHasMod", "(Z)V", null, null);
		input.methods.add(method);
		method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
		method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, input.name, HAS_MOD_FIELD, "Z"));
		method.instructions.add(new InsnNode(Opcodes.RETURN));
		LOG.info("CPM " + name + "/setHasMod: injected");
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if(transformers == null)return basicClass;
		UnaryOperator<ClassNode> tr = transformers.get(transformedName);
		if(tr != null) {
			ClassNode classNode = new ClassNode();
			ClassReader classReader = new ClassReader(basicClass);
			classReader.accept(classNode, 0);
			LOG.info("Applying cpm transformer: " + transformedName);
			classNode = tr.apply(classNode);
			ClassWriter writer = new ClassWriter(0);
			classNode.accept(writer);
			return writer.toByteArray();
		}
		return basicClass;
	}

}
