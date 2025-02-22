package com.tom.cpm.shared.definition;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.tom.cpl.math.Vec3f;
import com.tom.cpm.shared.model.Cube;
import com.tom.cpm.shared.model.PartRoot;
import com.tom.cpm.shared.model.PlayerModelParts;
import com.tom.cpm.shared.model.PlayerPartValues;
import com.tom.cpm.shared.model.RenderedCube;
import com.tom.cpm.shared.model.RootModelElement;
import com.tom.cpm.shared.model.SkinType;
import com.tom.cpm.shared.skin.TextureProvider;

public class VanillaDefinition extends ModelDefinition {
	private Supplier<TextureProvider> texture;
	private SkinType type;

	public VanillaDefinition(Supplier<TextureProvider> texture, SkinType type) {
		this.texture = texture;
		this.type = type;

		Map<Integer, RootModelElement> playerModelParts = new HashMap<>();
		for(int i = 0;i<PlayerModelParts.VALUES.length;i++) {
			RootModelElement elem = new RootModelElement(PlayerModelParts.VALUES[i]);
			playerModelParts.put(i, elem);
		}

		for (PlayerModelParts part : PlayerModelParts.VALUES) {
			if(part == PlayerModelParts.CUSTOM_PART)continue;
			RootModelElement elem = playerModelParts.get(part.ordinal());
			elem.hidden = true;
			Cube cube = new Cube();
			PlayerPartValues val = PlayerPartValues.getFor(part, type);
			cube.offset = val.getOffset();
			cube.rotation = new Vec3f(0, 0, 0);
			cube.pos = new Vec3f(0, 0, 0);
			cube.size = val.getSize();
			cube.scale = new Vec3f(1, 1, 1);
			cube.u = val.u;
			cube.v = val.v;
			cube.texSize = 1;
			cube.id = 0xfff0 + part.ordinal();
			RenderedCube rc = new RenderedCube(cube);
			rc.setParent(elem);
			elem.addChild(rc);

			cube = new Cube();
			cube.offset = val.getOffset();
			cube.rotation = new Vec3f(0, 0, 0);
			cube.pos = new Vec3f(0, 0, 0);
			cube.size = val.getSize();
			cube.scale = new Vec3f(1, 1, 1);
			cube.u = val.u2;
			cube.v = val.v2;
			cube.mcScale = 0.25F;
			cube.texSize = 1;
			cube.id = 0xfff8 + part.ordinal();
			rc = new RenderedCube(cube);
			rc.setParent(elem);
			elem.addChild(rc);
		}
		rootRenderingCubes = new HashMap<>();
		playerModelParts.forEach((i, e) -> rootRenderingCubes.put(PlayerModelParts.VALUES[i], new PartRoot(e)));
	}

	@Override
	public TextureProvider getSkinOverride() {
		return texture.get();
	}

	@Override
	public SkinType getSkinType() {
		return type;
	}
}
