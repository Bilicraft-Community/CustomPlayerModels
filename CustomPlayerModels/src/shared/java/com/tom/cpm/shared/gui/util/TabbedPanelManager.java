package com.tom.cpm.shared.gui.util;

import java.util.HashMap;
import java.util.Map;

import com.tom.cpm.shared.gui.IGui;
import com.tom.cpm.shared.gui.elements.Button;
import com.tom.cpm.shared.gui.elements.Panel;

public class TabbedPanelManager extends Panel {
	private Map<Panel, Button> buttons = new HashMap<>();
	public TabbedPanelManager(IGui gui) {
		super(gui);
	}

	public Button createTab(String name, Panel panel) {
		boolean vis = elements.isEmpty();
		panel.setVisible(vis);
		addElement(panel);
		Button btn = new Button(gui, name, null);
		btn.setAction(() -> {
			elements.forEach(p -> p.setVisible(false));
			panel.setVisible(true);
			buttons.values().forEach(b -> b.setEnabled(true));
			btn.setEnabled(false);
		});
		btn.setEnabled(!vis);
		buttons.put(panel, btn);
		return btn;
	}

	public void removeTab(Panel panel) {
		elements.remove(panel);
		buttons.remove(panel);
		if(panel.isVisible()) {
			panel.setVisible(false);
			if(!elements.isEmpty()) {
				elements.get(0).setVisible(true);
			}
		}
	}
}
