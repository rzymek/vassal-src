/*
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

package VASSAL.build;

import VASSAL.configure.ConfigureTree;
import VASSAL.counters.Decorator;
import VASSAL.counters.EditablePiece;

/**
 * General-purpose condition indicating that VASSAL has encountered data that's inconsistent with the current module.
 * A typical example would be failing to find a map/board/image/prototype from the supplied name.  Covers a variety of 
 * situations where the most likely cause is a module version compatibility issue.
 * 
 * This is for recoverable errors that occur during game play, as opposed to {@link IllegalBuildException}, 
 * which covers errors when building a module
 * @see ErrorDialog.dataError()
 * @author rodneykinney
 *
 */
public class BadDataReport {
  private String message;
  private String data;
  private Throwable cause;

  public BadDataReport() {
  }

  /**
   * Basic Bad Data Report
   * 
   * @param message Message to display
   * @param data Data causing error
   * @param cause Throwable that generated error
   */
  public BadDataReport(String message, String data, Throwable cause) {
    this(null, null, message, data, cause);
  }

  public BadDataReport(String message, String data) {
    this(message, data, null);
  }
  
  /**
   * Expanded Bad Data Report called by Traits.
   * Display additional information to aid debugging
   * 
   * @param piece Trait that generated the error
   * @param message Resource message key to display
   * @param data Data causing error
   * @param cause Throwable that generated error
   */
  public BadDataReport(EditablePiece piece, String message, String data, Throwable cause) { 
    this(Decorator.getOutermost(piece).getLocalizedName(), piece.getDescription(), message, data, cause);
  }

  public BadDataReport(EditablePiece piece, String message, String data) { 
    this(Decorator.getOutermost(piece).getLocalizedName(), piece.getDescription(), message, data, null);
  }

  public BadDataReport(EditablePiece piece, String message) { 
    this(Decorator.getOutermost(piece).getLocalizedName(), piece.getDescription(), message, "", null);
  }
  
  public BadDataReport(String pieceName, String traitDesc, String message, String data, Throwable cause) {
    String m = ((pieceName != null && pieceName.length() > 0) ? pieceName+" " : "");
    m += ((traitDesc != null && traitDesc.length() > 0) ? "["+traitDesc+"] " : "");
    m += m.length() > 0 ? "- " : "";
    m += message;
    this.message = m;
    this.cause = cause;
    this.data = data;
  }
  
  /**
   * Expanded Bad Data Report for AbstractConfigurables.
   * Display the name and type of the Configurable
   * 
   * @param c AbstractConfigurable that generated the error
   * @param message Resource message key to display
   * @param data Data causing error
   * @param cause Throwable that generated error
   */
  public BadDataReport(AbstractConfigurable c, String message, String data, Throwable cause) {
    this.message = c.getConfigureName() + "[" + ConfigureTree.getConfigureName(c.getClass())+"]: "+message;
    this.cause = cause;
    this.data = data;
  }

  public BadDataReport(AbstractConfigurable c, String messageKey, String data) { 
    this(c, messageKey, data, null);
  }
  
  public String getMessage() {
    return message;
  }

  public Throwable getCause() {
    return cause;
  }

  public String getData() {
    return data;
  }

}
