package com.tom.cpm.shared.util;

import java.awt.image.BufferedImage;

import com.tom.cpm.shared.MinecraftClientAccess;

public class DynamicTexture {
	private BufferedImage image;
	private boolean needReload;
	private ITexture texture;

	public DynamicTexture(BufferedImage image) {
		this.image = image;
		needReload = true;
	}

	public void bind() {
		if(texture == null)this.texture = MinecraftClientAccess.get().createTexture();
		if(needReload) {
			texture.load(image);
			needReload = false;
		}
		texture.bind();
	}

	public void markDirty() {
		needReload = true;
	}

	public void setImage(BufferedImage image) {
		this.image = image;
		this.needReload = true;
	}

	public void free() {
		if(texture != null)texture.free();
	}

	public ITexture getNative() {
		return texture;
	}

	public static interface ITexture {
		void bind();
		void load(BufferedImage image);
		void free();
	}

	public BufferedImage getImage() {
		return image;
	}
}
