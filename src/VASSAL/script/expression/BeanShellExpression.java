/*
 * $Id$
 *
 * Copyright (c) 2009-2012 Brent Easton
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
package VASSAL.script.expression;

import java.util.Map;

import VASSAL.build.BadDataReport;
import VASSAL.build.module.properties.PropertySource;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceFilter;
import VASSAL.i18n.Resources;
import VASSAL.script.ExpressionInterpreter;
import VASSAL.tools.ErrorDialog;

/**
 * A basic beanShell expression
 */
public class BeanShellExpression extends Expression {

  protected ExpressionInterpreter interpreter;

  public BeanShellExpression (String s) {
    setExpression("{" + s + "}");
  }

  /**
   * Evaluate this expression using a BeanShell Interpreter
   */
  public String evaluate(PropertySource ps, Map<String, String> properties,
      boolean localized) throws ExpressionException {
    if (interpreter == null) {
      interpreter = ExpressionInterpreter.createInterpreter(strip(getExpression()));
    }
    return interpreter.evaluate(ps, localized);
  }


  public String toBeanShellString() {
    return strip(getExpression());
  }

  protected static String strip(String expr) {
    final String s = expr.trim();
    if (s.startsWith("{") && s.endsWith("}")) {
       return s.substring(1, s.length()-1);
    }
    return expr;
  }

  /**
   * Return a PieceFilter that selects GamePieces that cause
   * this expression to evaluate to true
   */
  public PieceFilter getFilter(final PropertySource ps) {
    return new PieceFilter() {
      public boolean accept(GamePiece piece) {
        String result = null;
        try {
          result = evaluate(piece);
        }
        catch (ExpressionException e) {
          ErrorDialog.dataError(new BadDataReport(Resources.getString("Error.expression_error"), "Expression="+getExpression()+", Error="+e.getError(), e));
        }
        return "true".equals(result);
      }
    };
  }

  /**
   * Convert a Property name to it's BeanShell equivalent.
   *
   * @param property name
   * @return beanshell equivalent
   */
  public static String convertProperty (String prop) {
    // Null Expression
    if (prop == null || prop.length() == 0) {
      return "";
    }

    // Already a bsh exopression?
    if (isBeanShellExpression(prop)) {
      return strip(prop);
    }

    // Check it follows Java variable rules
    boolean ok = Character.isJavaIdentifierStart(prop.charAt(0));
    if (ok) {
      for (int i=1; i < prop.length() && ok; i++) {
        ok = Character.isJavaIdentifierPart(prop.charAt(i));
      }
    }

    // If not a Java variable, wrap it in GetProperty()
    return ok ? prop : "GetProperty(\""+prop+"\")";
  }

  public static boolean isBeanShellExpression(String expr) {
    return expr.startsWith("{") && expr.endsWith("}");
  }

  /**
   * Create a BeanShellExpression.
   *
   * The expression may or may not be surrounded by {}.
   *
   * Create null, integer and simple Expressions as their basic type to
   * ensure efficient evaluation.
   */
  public static Expression createExpression(String s) {
    String expr;
    final String t = s.trim();

    if (t.startsWith("{") && t.endsWith("}")) {
      expr = t.substring(1, t.length() - 1).trim();
    }
    else {
      expr = t;
    }

    if (expr.trim().length() == 0) {
      return new NullExpression();
    }

    try {
      return new IntExpression(Integer.parseInt(expr));
    }
    catch (NumberFormatException e) {
      // Not an error
    }

    // Return a single String as a string without quotes
    if (expr.length() > 1 && expr.startsWith("\"") && expr.endsWith("\"")
        && expr.indexOf('"', 1) == expr.length() - 1) {
      return new StringExpression(expr.substring(1, expr.length() - 1));
    }

    return new BeanShellExpression(expr);

  }

  @Override
  public boolean equals(Object bse) {
    return super.equals(bse);
  }
}