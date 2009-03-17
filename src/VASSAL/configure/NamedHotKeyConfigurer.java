/*
 * $Id: NamedHotKeyConfigurer.java 2893 2008-01-27 20:15:23Z uckelman $
 *
 * Copyright (c) 2008 by Rodney Kinney, Brent Easton
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

import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import VASSAL.tools.NamedKeyManager;
import VASSAL.tools.NamedKeyStroke;

/**
 * A Configurer for {@link NamedKeyStroke} values
 */
public class NamedHotKeyConfigurer extends Configurer implements KeyListener {
  private JTextField tf;
  private JPanel p;
  private boolean named;
  private JTextField keyName;
  private char lastChar;

  public NamedHotKeyConfigurer(String key, String name) {
    this(key, name, new NamedKeyStroke());
  }

  public NamedHotKeyConfigurer(String key, String name, NamedKeyStroke val) {
    super(key, name, val);
    named = val != null && val.isNamed();
  }

  public void setValue(Object o) {
    super.setValue(o);
    named = value != null && ((NamedKeyStroke) value).isNamed();
    if (! named) {
      if (tf != null
          && !tf.getText().equals(keyToString())) {
        tf.setText(keyToString());
      }
    }
  }

  public String keyToString() {
    return getString((NamedKeyStroke) getValue());
  }

  public Object getValue() {
    return super.getValue();
  }
  
  public String getValueString() {
    return encode((NamedKeyStroke) getValue());
  }

  public NamedKeyStroke getValueNamedKeyStroke() {
    return (NamedKeyStroke) value;
  }
   
  public void setValue(String s) {
    setValue(s == null ? null : decode(s));
  }

  public java.awt.Component getControls() {
    if (p == null) {
      p = new JPanel();
      p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
      tf = new JTextField(16);
      tf.setMaximumSize(new Dimension(tf.getMaximumSize().width,tf.getPreferredSize().height));
      tf.setText(keyToString());
      tf.addKeyListener(this);
      p.add(new JLabel(getName()));
      p.add(tf);
      
      keyName = new JTextField(16);
      keyName.setText(getValueNamedKeyStroke() == null ? null : getValueNamedKeyStroke().getName());
      keyName.setMaximumSize(new Dimension(keyName.getMaximumSize().width,keyName.getPreferredSize().height));
      keyName.addKeyListener(new KeyListener() {
        public void keyReleased(KeyEvent e) {
          switch (e.getKeyCode()) {
            case KeyEvent.VK_DELETE:
            case KeyEvent.VK_BACK_SPACE:
              if (keyName.getText().length() == 0) {
                named = false;
                setValue(null);
                updateVisibility();
              }
              break;
            case KeyEvent.VK_SHIFT:
            case KeyEvent.VK_CONTROL:
            case KeyEvent.VK_META:
            case KeyEvent.VK_ALT:
              break;
            default:
              if (isPrintableAscii(e.getKeyChar())) {
                setValue(new NamedKeyStroke(NamedKeyManager.getMarkerKeyStroke(), keyName.getText()));
              }
              else {
                named = false;
                setValue(NamedKeyStroke.getKeyStrokeForEvent(e));
                updateVisibility();
              }
          }
        }
        public void keyPressed(KeyEvent e) {          
        }
        public void keyTyped(KeyEvent e) {          
        }
      });
      p.add(keyName);
      updateVisibility();
    }
    return p;
  }
  
  protected void updateVisibility() {
    tf.setVisible(!isNamed());
    keyName.setVisible(isNamed());
    Window w = SwingUtilities.getWindowAncestor(p);
    if (w != null) {
      w.pack();
    }
  }
  
  public boolean isNamed() {
    return named;
  }

  public void keyTyped(KeyEvent e) {
    lastChar = e.getKeyChar();
  }

  public void keyPressed(KeyEvent e) {
    switch (e.getKeyCode()) {
      case KeyEvent.VK_DELETE:
      case KeyEvent.VK_BACK_SPACE:
        setValue(null);
        break;
      case KeyEvent.VK_SHIFT:
      case KeyEvent.VK_CONTROL:
      case KeyEvent.VK_META:
      case KeyEvent.VK_ALT:
        break;
      default:
        final NamedKeyStroke namedStroke = getValueNamedKeyStroke();
        if (namedStroke != null) {
          final int thisChar = e.getKeyChar();
          if (isPrintableAscii(lastChar) && isPrintableAscii(thisChar)) {
            final String name = "" + lastChar + e.getKeyChar();
            named = true;
            keyName.setText(name);
            setValue(new NamedKeyStroke(name));
            updateVisibility();
            keyName.requestFocus();
            break;            
          }
        }
        setValue(NamedKeyStroke.getKeyStrokeForEvent(e));
    }
  }

  public void keyReleased(KeyEvent e) {
    if (!named) {
      tf.setText(getString((NamedKeyStroke) getValue()));
    }
  }

  protected boolean isPrintableAscii (char c) {
    return isPrintableAscii((int) c);
  }
  
  protected boolean isPrintableAscii(int i) {
    return i >= ' ' && i <= '~';
  }
  
  /**
   * A plain text representation of a KeyStroke.  Doesn't differ much
   * from {@link KeyEvent#getKeyText}
   */
  public static String getString(NamedKeyStroke k) {
    return (k == null || k.isNull()) ? "" : getString(k.getStroke());
  }
  
  public static String getString(KeyStroke k) {  
    String s = NamedKeyManager.isNamed(k) ? "" : HotKeyConfigurer.getString(k);
    return s;
  }

  /**
   * Decode a String into a NamedKeyStroke
   */
  public static NamedKeyStroke decode(String s) {
    if (s == null) {
      return NamedKeyStroke.NULL_KEYSTROKE;
    }
    String[] parts = s.split(",");
    if (parts.length < 2) {
      return NamedKeyStroke.NULL_KEYSTROKE;
    }
    
    try {
      KeyStroke stroke = KeyStroke.getKeyStroke
          (Integer.parseInt(parts[0]),
           Integer.parseInt(parts[1]));
      String name = null;
      if (parts.length > 2) {
        name = parts[2];
      }
      return new NamedKeyStroke(stroke, name);
    }
    catch (Exception e) {
      return NamedKeyStroke.NULL_KEYSTROKE;
    }

  }

  /**
   * Encode a NamedKeyStroke into a String
   */
  public static String encode(NamedKeyStroke stroke) {
    if (stroke == null) {
      return "";
    }
    KeyStroke key = stroke.getStroke();
    if (key == null) {
      return "";
    }
    String s = key.getKeyCode() + "," + key.getModifiers();
    if (stroke.isNamed()) {
      s += "," + stroke.getName();
    }
    return s;
  }

}
