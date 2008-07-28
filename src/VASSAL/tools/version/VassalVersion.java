/*
 * $Id: VersionTokenizer.java 3672 2008-05-29 19:04:08Z uckelman $
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

package VASSAL.tools.version;

/**
 * @author Joel Uckelman
 * @since 3.1.0
 */
public class VassalVersion extends Version {
  public VassalVersion(String v) {
    super(v, new VassalVersionTokenizer(v));
  }
}
