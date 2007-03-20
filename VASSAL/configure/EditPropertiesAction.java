/*
 * $Id$
 *
 * Copyright (c) 2000-2003 by Rodney Kinney
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available 
 * at http://www.opensource.org.
 */
package VASSAL.configure;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Hashtable;
import javax.swing.AbstractAction;

import VASSAL.build.Configurable;
import VASSAL.build.module.documentation.HelpWindow;

/**
 * Action to edit the Properties of a component
 */
public class EditPropertiesAction extends AbstractAction {
  private static final long serialVersionUID = 1L;

  protected Configurable target;
  protected HelpWindow helpWindow;
  protected static Hashtable openWindows = new Hashtable();
  protected Frame dialogOwner;
  protected ConfigureTree tree;

  public EditPropertiesAction(Configurable target, HelpWindow helpWindow, Frame dialogOwner) {
    super("Properties");
    this.helpWindow = helpWindow;
    this.target = target;
    this.dialogOwner = dialogOwner;
    setEnabled(target.getConfigurer() != null);
  }

  /*
   * Used by ConfigureTree where Configurers may change the children of a node
   */
  public EditPropertiesAction(Configurable target, HelpWindow helpWindow, Frame dialogOwner, ConfigureTree tree) {
    this(target, helpWindow, dialogOwner);
    this.tree = tree;
  }
  
  public void actionPerformed(ActionEvent evt) {
    PropertiesWindow w = (PropertiesWindow) openWindows.get(target);
    if (w == null) {
      w = new PropertiesWindow(dialogOwner,false,target,helpWindow);
      w.addWindowListener(new WindowAdapter() {
        public void windowClosed(WindowEvent e) {
          openWindows.remove(target);
          if (tree != null && target instanceof ConfigureTree.Mutable) {
            tree.nodeUpdated(target);          
          }
        }
      });
      openWindows.put(target,w);
      w.setVisible(true);
    }
    w.toFront();
  }
}
