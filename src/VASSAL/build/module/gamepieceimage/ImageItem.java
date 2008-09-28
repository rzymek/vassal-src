/*
 * $Id$
 * 
 * Copyright (c) 2005 by Rodney Kinney, Brent Easton
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

package VASSAL.build.module.gamepieceimage;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.List;

import VASSAL.build.AutoConfigurable;
import VASSAL.configure.StringEnum;
import VASSAL.configure.VisibilityCondition;
import VASSAL.tools.ImageUtils;
import VASSAL.tools.SequenceEncoder;
import VASSAL.tools.imageop.AbstractTileOpImpl;
import VASSAL.tools.imageop.ImageOp;
import VASSAL.tools.imageop.Op;

public class ImageItem extends Item {

  public static final String TYPE = "Image"; //$NON-NLS-1$
  
  public static final String SRC_VARIABLE = "Specified in individual images";
  public static final String SRC_FIXED = "Fixed for this layout";
  
  protected static final String IMAGE = "image"; //$NON-NLS-1$
  public static final String SOURCE = "source"; //$NON-NLS-1$

  protected String imageSource = SRC_FIXED;
  protected String imageName = ""; //$NON-NLS-1$
  @Deprecated protected Image image = null;
  protected ImageOp srcOp;
  protected Rectangle imageBounds = new Rectangle();
 
  public ImageItem() {
    super();
  }

  public ImageItem(GamePieceLayout l) {
    super(l);
  }

  public ImageItem(GamePieceLayout l, String n) {
    this(l);
    setConfigureName(n);
  }
  
  public String[] getAttributeDescriptions() {
    final String a[] = new String[] { "Image:  ", "Image is:  " };
    final String b[] = super.getAttributeDescriptions();
    final String c[] = new String[a.length + b.length];
    System.arraycopy(b, 0, c, 0, 2);
    System.arraycopy(a, 0, c, 2, a.length);
    System.arraycopy(b, 2, c, a.length+2, b.length-2);
    return c;
  }

  public Class<?>[] getAttributeTypes() {
    final Class<?> a[] = new Class<?>[] { Image.class, TextSource.class };
    final Class<?> b[] = super.getAttributeTypes();
    final Class<?> c[] = new Class<?>[a.length + b.length];
    System.arraycopy(b, 0, c, 0, 2);
    System.arraycopy(a, 0, c, 2, a.length);
    System.arraycopy(b, 2, c, a.length+2, b.length-2);
    return c;
  }

  public String[] getAttributeNames() {
    final String a[] = new String[] { IMAGE, SOURCE };
    final String b[] = super.getAttributeNames();
    final String c[] = new String[a.length + b.length];
    System.arraycopy(b, 0, c, 0, 2);
    System.arraycopy(a, 0, c, 2, a.length);
    System.arraycopy(b, 2, c, a.length+2, b.length-2);
    return c;
  }
  
  public void setAttribute(String key, Object o) {
    if (IMAGE.equals(key)) {
      if (o instanceof String) {
        imageName = (String) o;
      }
      else {
        if (o == null) {
          imageName = null;
        }
        else {
          imageName = ((File) o).getName();
        }
      }
    }
    else if (SOURCE.equals(key)) {
      imageSource = (String) o;
    }
    else {
      super.setAttribute(key, o);
    }
    
    if (layout != null) {
      layout.refresh();
    }   
  }
  
  public String getAttributeValueString(String key) {
    if (IMAGE.equals(key)) {
      return imageName;
    }  
    else if (SOURCE.equals(key)) {
      return imageSource;
    }
    else {
      return super.getAttributeValueString(key);
    }
  }
  
  public VisibilityCondition getAttributeVisibility(String name) {
    if (ROTATION.equals(name)) {
       return falseCond;
     }
    else if (IMAGE.equals(name)) {
      return fixedCond;
    }
     else {
       return super.getAttributeVisibility(name);
     }
   }

  private VisibilityCondition falseCond = new VisibilityCondition() {
    public boolean shouldBeVisible() {
      return false;
    }
  };
  
  private VisibilityCondition fixedCond = new VisibilityCondition() {
    public boolean shouldBeVisible() {
      return imageSource.equals(SRC_FIXED);
    }
  };
  
  public static class TextSource extends StringEnum {
    public String[] getValidValues(AutoConfigurable target) {
      return new String[] { SRC_VARIABLE, SRC_FIXED };
    }
  }
  public void draw(Graphics g, GamePieceImage defn) {
    loadImage(defn);
    Point origin = layout.getPosition(this);
    
    if (isAntialias()) {    
      ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
    } 
    else {
      ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    if (srcOp != null) {
      final Image img = srcOp.getImage();
      if (img != null) {
        g.drawImage(img, origin.x, origin.y, null);
      }
    }
  }
  
  public String getType() {
    return TYPE;
  }

  public Dimension getSize() {
    return imageBounds.getSize();
  }

  public boolean isFixed() {
    return imageSource.equals(SRC_FIXED);
  }
  
  protected void loadImage(GamePieceImage defn) {
    ImageItemInstance Ii = null;
    if (defn != null) {
      Ii = defn.getImageInstance(getConfigureName());
    }
    if (Ii == null) {
      Ii = new ImageItemInstance();
    }
    
    String iName;
    if (imageSource.equals(SRC_FIXED)) {
      iName = imageName;
    }
    else {
      iName = Ii.getImageName();
    }
    
//    image = null;
    
    if (iName != null) {
      if (iName.trim().length() == 0) {
        srcOp = BaseOp.op;
      }
      else {
/*
      try {
        image = GameModule.getGameModule().getDataArchive().getCachedImage(iName);
//        imageBounds = DataArchive.getImageBounds(image);
        imageBounds = ImageUtils.getBounds((BufferedImage) image);
      }
      catch (IOException e) {
      }
*/
        srcOp = Op.load(iName);
      }
      imageBounds = ImageUtils.getBounds(srcOp.getSize());
    }
    else {
      imageBounds = new Rectangle();
    }
  }
 
  protected static final class BaseOp extends AbstractTileOpImpl {
    private BaseOp() { }

    private static final BaseOp op = new BaseOp();

    public Image eval() throws Exception {
      final BufferedImage im =
        new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
      final Graphics2D bg = im.createGraphics();
      bg.setColor(Color.black);
      bg.drawRect(0, 0, 9, 9);
      bg.drawLine(0, 0, 9, 9);
      bg.drawLine(0, 9, 9, 0);
      bg.dispose();
      return im;
    }

    protected void fixSize() { }

    @Override 
    public Dimension getSize() {
      return new Dimension(10,10);
    }
 
    @Override 
    public int getWidth() {
      return 10;
    }

    @Override
    public int getHeight() {
      return 10;
    }

    public List<VASSAL.tools.opcache.Op<?>> depends() {
      return Collections.emptyList();
    }

    // NB: This ImageOp doesn't need custom equals() or hashCode()
    // because it's a singleton.
  }
 
  public static Item decode(GamePieceLayout l, String s) {
    final SequenceEncoder.Decoder sd = new SequenceEncoder.Decoder(s, ';');    
    final ImageItem item = new ImageItem(l);
    
    sd.nextToken();
    item.imageName = sd.nextToken(""); //$NON-NLS-1$
    item.imageSource = sd.nextToken(SRC_FIXED);
    
    return item;
  }
  
  public String encode() {
    final SequenceEncoder se1 = new SequenceEncoder(TYPE, ';');
    
    se1.append(imageName)
       .append(imageSource);
   
    final SequenceEncoder se2 = new SequenceEncoder(se1.getValue(), '|');
    se2.append(super.encode());
    
    return se2.getValue();
  }
}
