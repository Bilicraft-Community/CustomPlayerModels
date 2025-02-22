package com.tom.cpm.client;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.WorldSelectionScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.resources.IResource;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import com.tom.cpl.gui.Frame;
import com.tom.cpl.gui.IGui;
import com.tom.cpl.gui.IKeybind;
import com.tom.cpl.util.DynamicTexture.ITexture;
import com.tom.cpl.util.Image;
import com.tom.cpl.util.ImageIO.IImageIO;
import com.tom.cpm.shared.MinecraftClientAccess;
import com.tom.cpm.shared.MinecraftObjectHolder;
import com.tom.cpm.shared.definition.ModelDefinitionLoader;
import com.tom.cpm.shared.model.SkinType;
import com.tom.cpm.shared.network.NetH;
import com.tom.cpm.shared.network.NetHandler;
import com.tom.cpm.shared.util.MojangSkinsAPI;

public class MinecraftObject implements MinecraftClientAccess {
	/** The default skin for the Steve model. */
	private static final ResourceLocation TEXTURE_STEVE = new ResourceLocation("textures/entity/steve.png");
	/** The default skin for the Alex model. */
	private static final ResourceLocation TEXTURE_ALEX = new ResourceLocation("textures/entity/alex.png");

	private final Minecraft mc;
	private final ModelDefinitionLoader loader;
	private final PlayerRenderManager prm;
	public MinecraftObject(Minecraft mc) {
		this.mc = mc;
		MinecraftObjectHolder.setClientObject(this);
		loader = new ModelDefinitionLoader(PlayerProfile::create);
		prm = new PlayerRenderManager(loader);
	}

	@Override
	public Image getVanillaSkin(SkinType skinType) {
		ResourceLocation loc;
		switch (skinType) {
		case SLIM:
			loc = TEXTURE_ALEX;
			break;

		case DEFAULT:
		case UNKNOWN:
		default:
			loc = TEXTURE_STEVE;
			break;
		}
		try(IResource r = mc.getResourceManager().getResource(loc)) {
			return Image.loadFrom(r.getInputStream());
		} catch (IOException e) {
		}
		return null;
	}

	@Override
	public PlayerRenderManager getPlayerRenderManager() {
		return prm;
	}

	@Override
	public ITexture createTexture() {
		return new DynTexture(mc);
	}

	public static class DynTexture extends DynamicTexture implements ITexture {
		private final ResourceLocation loc;
		private final Minecraft mc;
		private static ResourceLocation bound_loc;

		public DynTexture(Minecraft mc) {
			super(1, 1, true);
			this.mc = mc;
			loc = mc.getTextureManager().getDynamicTextureLocation("cpm", this);
		}

		@Override
		public void bind() {
			bound_loc = loc;
			if(mc.getTextureManager().getTexture(loc) == null)
				mc.getTextureManager().loadTexture(loc, this);
		}

		@Override
		public void load(Image texture) {
			NativeImage ni = NativeImageIO.createFromBufferedImage(texture);
			setTextureData(ni);
			TextureUtil.prepareImage(this.getGlTextureId(), ni.getWidth(), ni.getHeight());
			updateDynamicTexture();
		}

		public static ResourceLocation getBoundLoc() {
			return bound_loc;
		}

		@Override
		public void free() {
			mc.getTextureManager().deleteTexture(loc);
		}
	}

	@Override
	public void executeLater(Runnable r) {
		mc.enqueue(r);
	}

	@Override
	public ModelDefinitionLoader getDefinitionLoader() {
		return loader;
	}

	@Override
	public SkinType getSkinType() {
		return SkinType.get(DefaultPlayerSkin.getSkinType(mc.getSession().getProfile().getId()));
	}

	@Override
	public void setEncodedGesture(int value) {
		Set<PlayerModelPart> s = ObfuscationReflectionHelper.getPrivateValue(GameSettings.class, mc.gameSettings, "field_178882_aU");
		setEncPart(s, value, 0, PlayerModelPart.HAT);
		setEncPart(s, value, 1, PlayerModelPart.JACKET);
		setEncPart(s, value, 2, PlayerModelPart.LEFT_PANTS_LEG);
		setEncPart(s, value, 3, PlayerModelPart.RIGHT_PANTS_LEG);
		setEncPart(s, value, 4, PlayerModelPart.LEFT_SLEEVE);
		setEncPart(s, value, 5, PlayerModelPart.RIGHT_SLEEVE);
		mc.gameSettings.sendSettingsToServer();
	}

	private static void setEncPart(Set<PlayerModelPart> s, int value, int off, PlayerModelPart part) {
		if((value & (1 << off)) != 0)s.add(part);
		else s.remove(part);
	}

	@Override
	public boolean isInGame() {
		return mc.player != null;
	}

	@Override
	public Object getPlayerIDObject() {
		return mc.getSession().getProfile();
	}

	@Override
	public Object getCurrentPlayerIDObject() {
		return mc.player != null ? mc.player.getGameProfile() : null;
	}

	@Override
	public List<IKeybind> getKeybinds() {
		return KeyBindings.kbs;
	}

	@Override
	public ServerStatus getServerSideStatus() {
		return mc.player != null ? ((NetH)mc.getConnection()).cpm$hasMod() ? ServerStatus.INSTALLED : ServerStatus.SKIN_LAYERS_ONLY : ServerStatus.OFFLINE;
	}

	public static ResourceLocation getDefaultSkin(UUID playerUUID) {
		return DefaultPlayerSkin.getSkinType(playerUUID).equals("slim") ? TEXTURE_ALEX : TEXTURE_STEVE;
	}

	@Override
	public File getGameDir() {
		return mc.gameDir;
	}

	@Override
	public void openGui(Function<IGui, Frame> creator) {
		mc.displayGuiScreen(new GuiImpl(creator, mc.currentScreen));
	}

	@Override
	public Runnable openSingleplayer() {
		return () -> mc.displayGuiScreen(new WorldSelectionScreen(mc.currentScreen));
	}

	@Override
	public NetHandler<?, ?, ?, ?, ?> getNetHandler() {
		return ClientProxy.INSTANCE.netHandler;
	}

	@Override
	public IImageIO getImageIO() {
		return new NativeImageIO();
	}

	@Override
	public void applySkin(Image skin, SkinType type) throws IOException {
		MojangSkinsAPI.uploadSkin(mc.getSession().getProfile().getId(), mc.getSession().getToken(), type, skin);
		MojangSkinsAPI.clearYggdrasilCache(mc.getSessionService());
		mc.getProfileProperties().clear();
		mc.getProfileProperties();//refresh
	}
}
