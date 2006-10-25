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
package VASSAL.build.widget;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.Widget;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.tools.AdjustableSpeedScrollPane;
import VASSAL.tools.DataArchive;

/**
 * A Chart is used for displaying charts and tables for the module.  The
 * charts are loaded as images stored in the DataArchive.  As a subclass of
 * Widget, a Chart may be added to any Widget,
 * but it may not contains children of its own
 */
public class Chart extends Widget {
  public static final String NAME = "chartName";
  public static final String FILE = "fileName";

  private Component chart;
  private String fileName;
  private JLabel label;

  public Chart() {
  }

  public Component getComponent() {
    if (chart == null) {
      label = new JLabel();
      try {
        label.setIcon(new ImageIcon
          (GameModule.getGameModule().getDataArchive().getCachedImage(fileName)));
      }
      catch (IOException ex) {
        label.setText("Image " + fileName + " not found");
      }
      Dimension d = label.getPreferredSize();
      if (d.width > 300 || d.height > 300) {
        JScrollPane scroll = new AdjustableSpeedScrollPane(label);
        scroll.getViewport().setPreferredSize(label.getPreferredSize());
        scroll.getViewport().setAlignmentY(0.0F);
        chart = scroll;
      }
      else {
        chart = label;
      }
    }
    return chart;
  }

  public String getFileName() {
    return fileName;
  }

  public void addTo(Buildable parent) {
  }

  public void removeFrom(Buildable parent) {
  }

  public HelpFile getHelpFile() {
    return HelpFile.getReferenceManualPage("ChartWindow.htm", "Chart");
  }

  public void setAttribute(String key, Object val) {
    if (NAME.equals(key)) {
      setConfigureName((String) val);
    }
    else if (FILE.equals(key)) {
      if (val instanceof File) {
        val = ((File) val).getName();
      }
      fileName = (String) val;
      if (label != null) {
        try {
          label.setIcon(new ImageIcon
            (GameModule.getGameModule().getDataArchive().getCachedImage(fileName)));
          label.revalidate();
        }
        catch (IOException ex) {
        }
      }
    }
  }

  /*
  public Configurer[] getAttributeConfigurers() {
      Configurer config[] = new Configurer[2];
      config[0] = new StringConfigurer(NAME,"Name");
      config[0].setValue(getConfigureName());
      listenTo(config[0]);

      config[1] = new ImageConfigurer
      (FILE,"GIF Image",
      GameModule.getGameModule().getArchiveWriter());
      config[1].setValue(fileName);
      listenTo(config[1]);

      return config;
  }
  */

  public Class[] getAllowableConfigureComponents() {
    return new Class[0];
  }

  /**
   * The Attributes of a Chart are:
   * <pre>
   * <code>NAME</code> for the name of the chart
   * <code>FILE</code> for the name of the image in the {@link DataArchive}
   * </pre>
   */
  public String[] getAttributeNames() {
    return new String[]{NAME, FILE};
  }

  public String[] getAttributeDescriptions() {
    return new String[]{"Name", "GIF Image"};
  }

  public Class[] getAttributeTypes() {
    return new Class[]{String.class, Image.class};
  }

  public String getAttributeValueString(String name) {
    if (NAME.equals(name)) {
      return getConfigureName();
    }
    else if (FILE.equals(name)) {
      return fileName;
    }
    return null;
  }
}
