/*
 * $Id$
 *
 * Copyright (c) 2008 by Joel Uckelman
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

package VASSAL.launch;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JWindow;

import VASSAL.Info;
import VASSAL.i18n.Resources;

public class AboutVASSAL extends JWindow {
  private static final long serialVersionUID = 1L;

  public AboutVASSAL() {
    initComponents();
  }

  public AboutVASSAL(Window w) {
    super(w);
    initComponents();
  }

  private void initComponents() {
    getContentPane().setBackground(Color.black);
    setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

    final ImageIcon icon =
      new ImageIcon(getClass().getResource("/images/Splash.png"));
    final JLabel l1 = new JLabel(icon);
    l1.setAlignmentX(0.5F);
    add(l1);

    final JLabel l2 = new JLabel(
      Resources.getString("AboutScreen.vassal_version", Info.getVersion()));
    l2.setBackground(Color.blue);
    l2.setForeground(Color.white);
    l2.setHorizontalAlignment(JLabel.CENTER);
    l2.setAlignmentX(0.5F);
    add(l2);

    pack();

    final Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
    setLocation(d.width / 2 - getWidth() / 2,
                d.height / 2 -getHeight() / 2);

    addMouseListener(new MouseAdapter() {
      public void mouseReleased(MouseEvent evt) {
        setVisible(false);
        dispose();
      }
    });
  }

  public static Action getAction(final Window w) {
    final Action action = new AbstractAction() {
      private static final long serialVersionUID = 1L;

      public void actionPerformed(ActionEvent e) {
        final AboutVASSAL about = new AboutVASSAL(w);
        about.setVisible(true);
        about.toFront();
      }
    }; 

    action.putValue(Action.NAME,
                    Resources.getString("AboutScreen.about_vassal"));
    return action;
  }
}
