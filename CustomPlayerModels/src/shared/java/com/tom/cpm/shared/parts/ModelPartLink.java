package com.tom.cpm.shared.parts;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import com.tom.cpm.shared.definition.Link;
import com.tom.cpm.shared.definition.ModelDefinitionLoader;
import com.tom.cpm.shared.io.ChecksumInputStream;
import com.tom.cpm.shared.io.IOHelper;

public abstract class ModelPartLink implements IModelPart {
	private Link link;
	private ModelDefinitionLoader loader;

	public ModelPartLink(IOHelper in, ModelDefinitionLoader loader) throws IOException {
		link = new Link(in);
		this.loader = loader;
	}

	public ModelPartLink(Link link) {
		this.link = link;
	}

	@Override
	public IResolvedModelPart resolve() throws IOException {
		try(InputStream in = loader.load(link)) {
			if(in.read() != ModelDefinitionLoader.HEADER)throw new IOException();
			ChecksumInputStream cis = new ChecksumInputStream(in);
			try {
				return load(new IOHelper(cis), loader).resolve();
			} finally {
				int ch1 = in.read();
				int ch2 = in.read();
				if ((ch1 | ch2) < 0)
					throw new EOFException();
				if(cis.getSum() != (short)((ch1 << 8) + (ch2 << 0))) {
					throw new IOException("Sum error");
				}
			}
		}
	}

	protected abstract IModelPart load(IOHelper din, ModelDefinitionLoader loader) throws IOException;

	@Override
	public void write(IOHelper dout) throws IOException {
		link.write(dout);
	}

	@Override
	public String toString() {
		StringBuilder bb = new StringBuilder("Link\n\tPath: ");
		bb.append(link);
		return bb.toString();
	}
}
