/*
 * $Id$
 *
 * Copyright (c) 2008-2009 by Brent Easton
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
package VASSAL.tools;

import java.applet.AudioClip;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import VASSAL.build.BadDataReport;
import VASSAL.build.GameModule;
import VASSAL.i18n.Resources;
import VASSAL.tools.io.IOUtils;

public class Mp3AudioClip implements AudioClip {

  protected String name;
  protected Player player;
  protected InputStream stream;

  public Mp3AudioClip(String name) {
    this.name = name;
  }

  public void play() {
    try {
      stream = GameModule.getGameModule().getDataArchive().getInputStream(name);
      player = new Player(stream);
    }
    catch (JavaLayerException e) {
      ErrorDialog.bug(e);
    }
    catch (FileNotFoundException e) {
      ErrorDialog.dataError(new BadDataReport(Resources.getString("Error.not_found", name), "", e));
    }
    catch (IOException e) {
      ErrorDialog.bug(e);
    }
    finally {
      IOUtils.closeQuietly(stream);
    }

    // run in new thread to play in background
    new Thread() {
        public void run() {
          try {
            if (player != null) {
              player.play();
            }
          }
          catch (JavaLayerException e) {
            ErrorDialog.bug(e);
          }
        }
    }.start();
  }

  public void stop() {
    if (player != null) {
      player.close();
      IOUtils.closeQuietly(stream);
    }
  }

  public void loop() {
    // Not used by Vassal
  }


}