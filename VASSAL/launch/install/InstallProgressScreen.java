/*
 *
 * Copyright (c) 2000-2007 by Rodney Kinney
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
package VASSAL.launch.install;

import java.awt.Component;
import java.io.IOException;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JProgressBar;

/**
 * @author rkinney
 */
public abstract class InstallProgressScreen implements Screen {

  protected Box controls;
  protected JLabel status;
  protected JProgressBar progress;
  public InstallProgressScreen() {
    super();
    controls = Box.createVerticalBox();
    status = new JLabel("Downloading files");
    progress = new JProgressBar();
    progress.setIndeterminate(true);
    controls.add(status);
    controls.add(progress);
  }
  public void start(final InstallWizard wizard) {
    new Thread() {
      public void run() {
        try {
          tryInstall(wizard);
        }
        catch (IOException e) {
          e.printStackTrace();
          wizard.getDialog().setScreen(new FailureScreen(e));
        }
        catch (RuntimeException e) {
          e.printStackTrace();
          wizard.getDialog().setScreen(new FailureScreen(e));
        }
      }
    }.start();    
  }
  public Component getControls() {
    return controls;
  }

  public void next(InstallWizard wizard) {
  }
  public void setStatus(String msg) {
    status.setText(msg);
  }
  protected abstract void tryInstall(final InstallWizard wizard) throws IOException;
  
}