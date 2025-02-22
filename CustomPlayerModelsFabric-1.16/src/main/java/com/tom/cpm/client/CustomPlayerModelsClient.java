package com.tom.cpm.client;

import java.util.List;
import java.util.Map.Entry;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.options.SkinOptionsScreen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.model.Model;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;

import com.mojang.authlib.GameProfile;

import com.tom.cpm.CustomPlayerModels;
import com.tom.cpm.mixinplugin.OFDetector;
import com.tom.cpm.shared.config.ConfigKeys;
import com.tom.cpm.shared.config.ModConfig;
import com.tom.cpm.shared.config.Player;
import com.tom.cpm.shared.editor.gui.EditorGui;
import com.tom.cpm.shared.gui.GestureGui;
import com.tom.cpm.shared.model.RenderManager;
import com.tom.cpm.shared.network.NetHandler;

import io.netty.buffer.Unpooled;

public class CustomPlayerModelsClient implements ClientModInitializer {
	public static MinecraftObject mc;
	public static CustomPlayerModelsClient INSTANCE;
	public static boolean optifineLoaded;
	private RenderManager<GameProfile, PlayerEntity, Model, VertexConsumerProvider> manager;
	public NetHandler<Identifier, CompoundTag, PlayerEntity, PacketByteBuf, ClientPlayNetworkHandler> netHandler;

	@Override
	public void onInitializeClient() {
		CustomPlayerModels.LOG.info("Customizable Player Models Client Init started");
		INSTANCE = this;
		mc = new MinecraftObject(MinecraftClient.getInstance());
		optifineLoaded = OFDetector.doApply();
		ClientTickEvents.START_CLIENT_TICK.register(cl -> {
			if(!cl.isPaused())
				mc.getPlayerRenderManager().getAnimationEngine().tick();
		});
		KeyBindings.init();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null)
				return;

			if(KeyBindings.gestureMenuBinding.wasPressed()) {
				client.openScreen(new GuiImpl(GestureGui::new, null));
			}

			if(KeyBindings.renderToggleBinding.wasPressed()) {
				Player.setEnableRendering(!Player.isEnableRendering());
			}

			for (Entry<Integer, KeyBinding> e : KeyBindings.quickAccess.entrySet()) {
				if(e.getValue().wasPressed()) {
					mc.getPlayerRenderManager().getAnimationEngine().onKeybind(e.getKey());
				}
			}
		});
		manager = new RenderManager<>(mc.getPlayerRenderManager(), mc.getDefinitionLoader(), PlayerEntity::getGameProfile);
		netHandler = new NetHandler<>(Identifier::new);
		netHandler.setNewNbt(CompoundTag::new);
		netHandler.setNewPacketBuffer(() -> new PacketByteBuf(Unpooled.buffer()));
		netHandler.setWriteCompound(PacketByteBuf::writeCompoundTag, PacketByteBuf::readCompoundTag);
		netHandler.setNBTSetters(CompoundTag::putBoolean, CompoundTag::putByteArray, CompoundTag::putFloat);
		netHandler.setNBTGetters(CompoundTag::getBoolean, CompoundTag::getByteArray, CompoundTag::getFloat);
		netHandler.setContains(CompoundTag::contains);
		netHandler.setExecutor(MinecraftClient::getInstance);
		netHandler.setSendPacket((c, rl, pb) -> c.sendPacket(new CustomPayloadC2SPacket(rl, pb)), null);
		netHandler.setPlayerToLoader(PlayerEntity::getGameProfile);
		netHandler.setReadPlayerId(PacketByteBuf::readVarInt, id -> {
			Entity ent = MinecraftClient.getInstance().world.getEntityById(id);
			if(ent instanceof PlayerEntity) {
				return (PlayerEntity) ent;
			}
			return null;
		});
		netHandler.setGetClient(() -> MinecraftClient.getInstance().player);
		netHandler.setGetNet(c -> ((ClientPlayerEntity)c).networkHandler);
		CustomPlayerModels.LOG.info("Customizable Player Models Client Initialized");
	}

	public void playerRenderPre(AbstractClientPlayerEntity player, VertexConsumerProvider buffer) {
		manager.bindPlayer(player, buffer);
	}

	public void playerRenderPost() {
		manager.tryUnbind();
	}

	public void initGui(Screen screen, List<Element> children, List<AbstractButtonWidget> buttons) {
		if((screen instanceof TitleScreen && ModConfig.getCommonConfig().getSetBoolean(ConfigKeys.TITLE_SCREEN_BUTTON, true)) ||
				screen instanceof SkinOptionsScreen) {
			Button btn = new Button(0, 0, () -> MinecraftClient.getInstance().openScreen(new GuiImpl(EditorGui::new, screen)));
			buttons.add(btn);
			children.add(btn);
		}
	}

	public void renderHand(VertexConsumerProvider buffer) {
		manager.bindHand(MinecraftClient.getInstance().player, buffer);
	}

	public void renderSkull(Model skullModel, GameProfile profile, VertexConsumerProvider buffer) {
		manager.bindSkull(profile, buffer, skullModel);
	}

	public void unbind(Model model) {
		manager.tryUnbind(model);
	}

	public static class Button extends ButtonWidget {

		public Button(int x, int y, Runnable r) {
			super(x, y, 100, 20, new TranslatableText("button.cpm.open_editor"), b -> r.run());
		}

	}

	public void onLogout() {
		mc.getDefinitionLoader().clearServerData();
	}
}
