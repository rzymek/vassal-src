/*
 * $Id: ZoneHighlight.java 965 2006-09-03 16:34:05 +0000 (Sun, 03 Sep 2006) rodneykinney $
 *
 * Copyright (c) 2000-2006 by Brent Easton, Rodney Kinney
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
package VASSAL.build.module.map.boardPicker.board.mapgrid;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Hashtable;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.AutoConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.configure.ColorConfigurer;
import VASSAL.configure.Configurer;
import VASSAL.configure.ConfigurerFactory;
import VASSAL.configure.StringEnum;
import VASSAL.configure.VisibilityCondition;

/**
 * @author Brent Easton
 * A Class that defines a method of highlighting the a zone in
 * a multi-zoned grid.
 */
public class ZoneHighlight extends AbstractConfigurable  {

  public static final String NAME = "name";
  public static final String COLOR = "color";
  public static final String COVERAGE = "coverage";
  public static final String WIDTH = "width";
  public static final String STYLE = "style";
  public static final String IMAGE = "image";
  public static final String OPACITY = "opacity";

  public static final String COVERAGE_FULL = "Entire Zone";
  public static final String COVERAGE_BORDER = "Zone Border";
  public static final String STYLE_PLAIN = "Plain";
  public static final String STYLE_STRIPES = "Striped";
  public static final String STYLE_CROSS = "Crosshatched";
  public static final String STYLE_IMAGE = "Tiled Image";
  
  protected Color color = null;
  protected String coverage = COVERAGE_FULL;
  protected int width = 1;
  protected String style = STYLE_PLAIN;
  protected String imageName = null;
  protected int opacity = 100;
  
  protected TexturePaint paint;

  public ZoneHighlight() {
    setConfigureName("");
  }
  
  /* 
   * Stage 1 - Only Plain Style and Border Coverage is implemented
   */
  public void draw(Graphics2D g2d, Shape s, double scale) {

    if ((color != null && opacity > 0 ) || STYLE_IMAGE.equals(style)) {
      Stroke oldStroke = g2d.getStroke();
      Color oldColor = g2d.getColor();
      Composite oldComposite = g2d.getComposite();
      Paint oldPaint = g2d.getPaint();
      if (!STYLE_PLAIN.equals(style)) {
        g2d.setPaint(getPaint());
      }
      else {    
        g2d.setColor(color);
      }
      
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity/100.0f));
      g2d.addRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING,
          RenderingHints.VALUE_ANTIALIAS_ON));
      
      if (COVERAGE_FULL.equals(coverage)) {
        g2d.fill(s);
      }
      else {
        Stroke stroke = new BasicStroke((float)(width*scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        g2d.setStroke(stroke);
        g2d.draw(s);
      }
    
      g2d.setColor(oldColor);
      g2d.setStroke(oldStroke);
      g2d.setComposite(oldComposite);
      g2d.setPaint(oldPaint);
    }
  }

  protected Paint getPaint() {
    if (paint == null) {
      if (style.equals(STYLE_IMAGE)) {
        try {
          ImageIcon i = new ImageIcon(GameModule.getGameModule().getDataArchive().getCachedImage(imageName));
          BufferedImage bi = new BufferedImage(i.getIconWidth(), i.getIconHeight(), BufferedImage.TYPE_4BYTE_ABGR);
          Graphics2D big = bi.createGraphics();
          big.drawImage(i.getImage(), 0, 0, null);          
          paint = new TexturePaint(bi, new Rectangle(0, 0, bi.getWidth(), bi.getHeight()));
        }
        catch (IOException e) {
          System.err.println("Unable to locate image " + imageName);
        }
      }
      else {
        BufferedImage bi = new BufferedImage(6, 6, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D big = bi.createGraphics();  
        big.setColor(color);
        if (style.equals(STYLE_STRIPES)) {
          big.drawLine(0, 5, 5, 0);
        }
        else if (style.equals(STYLE_CROSS)) {
          big.drawLine(0, 5, 5, 0);
          big.drawLine(1, 0, 5, 4);
        }
        Rectangle r = new Rectangle(0,0,6,6);
        paint = new TexturePaint(bi, r);
      }
    }
    return paint;
  }
  
  public String getName() {
    return getConfigureName();
  }
  
  public Color getColor() {
    return color;
  }
  
  public String[] getAttributeNames() {
    String s[] = {NAME, COLOR, COVERAGE, WIDTH, STYLE, IMAGE, OPACITY};
    return s;
  }

  public String[] getAttributeDescriptions() {
    return new String[]{"Name:  ", "Color:  ", "Coverage:  ", "Width:  ", "Style:  ", "Image:  ", "Opacity(%):  "};
  }

  public Class[] getAttributeTypes() {
    return new Class[]{String.class, Color.class, Coverage.class, Integer.class, Style.class, Icon.class, OpacityConfig.class};
  }

  public static class Coverage extends StringEnum {
    public String[] getValidValues(AutoConfigurable target) {
      return new String[]{COVERAGE_FULL, COVERAGE_BORDER};
    }
  }

  public static class Style extends StringEnum {
    public String[] getValidValues(AutoConfigurable target) {
      return new String[]{STYLE_PLAIN, STYLE_STRIPES, STYLE_CROSS, STYLE_IMAGE};
    }
  }

  public static class OpacityConfig implements ConfigurerFactory {

    public Configurer getConfigurer(AutoConfigurable c, String key, String name) {
      return ((ZoneHighlight) c).new PercentageConfigurer(key, name, new Integer(((ZoneHighlight) c).opacity));
    }
    
  }
  
  public void addTo(Buildable b) {
    ((ZonedGridHighlighter) b).addHighlight(this);
  }

  public void removeFrom(Buildable b) {
    ((ZonedGridHighlighter) b).removeHighlight(this);
  }

  public static String getConfigureTypeName() {
    return "Zone Highlight";
  }

  public VASSAL.build.module.documentation.HelpFile getHelpFile() {
    return HelpFile.getReferenceManualPage("ZonedGrid.htm","ZoneHighlighter");
  }

  public String getAttributeValueString(String key) {
    if (NAME.equals(key)) {
      return getConfigureName();
    }
    else if (COLOR.equals(key)) {
      return ColorConfigurer.colorToString(color);
    }
    else if (COVERAGE.equals(key)) {
      return coverage;
    }
    else if (WIDTH.equals(key)) {
      return String.valueOf(width);
    }
    else if (STYLE.equals(key)) {
      return style;
    }
    else if (OPACITY.equals(key)) {
      return String.valueOf(opacity);
    }
    else if (IMAGE.equals(key)) {
      return imageName;
    }
    return null;
  }

  public void setAttribute(String key, Object val) {
    if (val == null)
      return;

    if (NAME.equals(key)) {
      setConfigureName((String) val);
    }
    else if (COLOR.equals(key)) {
      if (val instanceof String) {
        val = ColorConfigurer.stringToColor((String) val);
      }
      if (val != null) {
        color = (Color) val;
      }
    }
    else if (COVERAGE.equals(key)) {
      coverage = (String) val;
    }
    else if (WIDTH.equals(key)) {
      if (val instanceof String) {
        val = new Integer((String) val);
      }
      width = ((Integer) val).intValue();
    }
    else if (STYLE.equals(key)) {
      style = (String) val;
      paint = null;
    }
    else if (OPACITY.equals(key)) {
      if (val instanceof String) {
        val = new Integer((String) val);
      }
      opacity = ((Integer) val).intValue();
    }
    else if (IMAGE.equals(key)) {
      imageName = (String) val;
    }
  }

  public Class[] getAllowableConfigureComponents() {
    return new Class[0];
  }
  
  public VisibilityCondition getAttributeVisibility(String name) {
    if (IMAGE.equals(name)) {
      return new VisibilityCondition() {
        public boolean shouldBeVisible() {
          return STYLE_IMAGE.equals(style);
        }
      };
    }
    else if (WIDTH.equals(name)) {
      return new VisibilityCondition() {
        public boolean shouldBeVisible() {
          return COVERAGE_BORDER.equals(coverage);
        }
      };
    }
    else {
      return super.getAttributeVisibility(name);
    }
  }
  
  public class PercentageConfigurer extends Configurer {

    protected int percentage = 50;
    
    public PercentageConfigurer(String key, String name, Object val) {
      super(key, name, val);
      percentage = ((Integer) val).intValue();
    }
    
    public String getValueString() {
      return String.valueOf(percentage);
    }

    public void setValue(String s) {
      percentage = (new Integer(s)).intValue();      
    }

    public Component getControls() {

      JSlider slider = new JSlider(JSlider.HORIZONTAL,0,100,percentage);

      Hashtable labelTable = new Hashtable();
      labelTable.put( new Integer(0), new JLabel("Transparent") );
      labelTable.put( new Integer(50), new JLabel("50%") );
      labelTable.put( new Integer(100), new JLabel("Opaque") );

      slider.setMajorTickSpacing(10);
      slider.setPaintTicks(true);
      slider.setLabelTable(labelTable);
      slider.setPaintLabels(true);
      slider.setBorder(javax.swing.BorderFactory.createTitledBorder(name));
      slider.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          JSlider source = (JSlider)e.getSource();
          if (!source.getValueIsAdjusting()) {    
            percentage = source.getValue();
          }
        }});

      return slider;
    }
    
  }

}