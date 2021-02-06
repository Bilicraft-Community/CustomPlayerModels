package com.tom.cpm.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map.Entry;
import java.util.function.Predicate;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;

import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;

import com.tom.cpm.CommonProxy;
import com.tom.cpm.shared.MinecraftObjectHolder;
import com.tom.cpm.shared.config.Player;
import com.tom.cpm.shared.definition.ModelDefinition;
import com.tom.cpm.shared.definition.ModelDefinitionLoader;
import com.tom.cpm.shared.editor.gui.EditorGui;
import com.tom.cpm.shared.gui.GestureGui;

public class ClientProxy extends CommonProxy {
	public static MinecraftObject mc;
	private ModelDefinitionLoader loader;
	private Minecraft minecraft;

	@Override
	public void init() {
		super.init();
		try(InputStream is = ClientProxy.class.getResourceAsStream("/assets/cpm/textures/template/free_space_template.png")) {
			loader = new ModelDefinitionLoader(ImageIO.read(is), PlayerProfile::create);
		} catch (IOException e) {
			throw new RuntimeException("Failed to load template", e);
		}
		minecraft = Minecraft.getMinecraft();
		mc = new MinecraftObject(minecraft, loader);
		MinecraftObjectHolder.setClientObject(mc);
		MinecraftForge.EVENT_BUS.register(this);
		KeyBindings.init();
	}

	private PlayerProfile profile;

	@SubscribeEvent
	public void playerRenderPre(RenderPlayerEvent.Pre event) {
		tryBindModel(event.getEntityPlayer(), null);
	}

	private void tryBindModel(EntityPlayer player, Predicate<Object> unbindRule) {
		PlayerProfile profile = (PlayerProfile) loader.loadPlayer(player.getGameProfile());
		ModelDefinition def = profile.getModelDefinition();
		if(def != null) {
			if(def.getResolveState() == 0)def.startResolve();
			else if(def.getResolveState() == 2) {
				if(def.doRender()) {
					this.profile = profile;
					profile.updateFromPlayer(player);
					mc.getPlayerRenderManager().bindModel(profile.getModel(), def, unbindRule);
					mc.getPlayerRenderManager().getAnimationEngine().handleAnimation(profile);
					return;
				}
			}
		}
		mc.getPlayerRenderManager().unbindModel(profile.getModel());
	}

	@SubscribeEvent
	public void playerRenderPost(RenderPlayerEvent.Post event) {
		if(profile != null) {
			mc.getPlayerRenderManager().unbindModel(profile.getModel());
			profile = null;
		}
	}

	@SubscribeEvent
	public void openGui(GuiScreenEvent.InitGuiEvent.Post evt) {
		if(evt.getGui() instanceof GuiMainMenu || evt.getGui() instanceof GuiCustomizeSkin) {
			evt.getButtonList().add(new Button(0, 0));
		}
	}

	@SubscribeEvent
	public void buttonPress(GuiScreenEvent.ActionPerformedEvent.Pre evt) {
		if(evt.getButton() instanceof Button) {
			Minecraft.getMinecraft().displayGuiScreen(new GuiImpl(EditorGui::new, evt.getGui()));
		}
	}

	@SubscribeEvent
	public void openGui(GuiOpenEvent openGui) {
		if(openGui.getGui() == null && minecraft.currentScreen instanceof GuiImpl.Overlay) {
			openGui.setGui(((GuiImpl.Overlay) minecraft.currentScreen).getGui());
		}
	}

	@SubscribeEvent
	public void renderHand(RenderSpecificHandEvent evt) {
		tryBindModel(Minecraft.getMinecraft().player, PlayerRenderManager::unbindHand);
		this.profile = null;
	}

	@SubscribeEvent
	public void renderTick(RenderTickEvent evt) {
		if(evt.phase == Phase.START) {
			mc.getPlayerRenderManager().getAnimationEngine().update(evt.renderTickTime);
		}
	}

	@SubscribeEvent
	public void clientTick(ClientTickEvent evt) {
		if(evt.phase == Phase.START && !minecraft.isGamePaused()) {
			mc.getPlayerRenderManager().getAnimationEngine().tick();
		}

		if (minecraft.player == null || evt.phase == Phase.START)
			return;

		if(KeyBindings.gestureMenuBinding.isPressed()) {
			minecraft.displayGuiScreen(new GuiImpl(GestureGui::new, null));
		}

		if(KeyBindings.renderToggleBinding.isPressed()) {
			Player.setEnableRendering(!Player.isEnableRendering());
		}

		for (Entry<Integer, KeyBinding> e : KeyBindings.quickAccess.entrySet()) {
			if(e.getValue().isPressed()) {
				mc.getPlayerRenderManager().getAnimationEngine().onKeybind(e.getKey());
			}
		}
	}

	@SubscribeEvent
	public void onRenderName(RenderLivingEvent.Specials.Pre<AbstractClientPlayer> evt) {
		if(evt.getEntity() instanceof AbstractClientPlayer) {
			if(!Player.isEnableNames())
				evt.setCanceled(true);
		}
	}

	public static class Button extends GuiButton {

		public Button(int x, int y) {
			super(99, x, y, 100, 20, I18n.format("button.cpm.open_editor"));
		}

	}
}
