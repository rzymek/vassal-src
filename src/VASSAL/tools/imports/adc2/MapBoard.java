/*
 * Copyright (c) 2008 by Michael Kiefte
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

package VASSAL.tools.imports.adc2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;

import javax.imageio.ImageIO;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GlobalOptions;
import VASSAL.build.module.Inventory;
import VASSAL.build.module.Map;
import VASSAL.build.module.PrototypeDefinition;
import VASSAL.build.module.PrototypesContainer;
import VASSAL.build.module.map.BoardPicker;
import VASSAL.build.module.map.SetupStack;
import VASSAL.build.module.map.Zoomer;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.build.module.map.boardPicker.board.MapGrid;
import VASSAL.build.module.map.boardPicker.board.SquareGrid;
import VASSAL.build.module.map.boardPicker.board.ZonedGrid;
import VASSAL.build.module.map.boardPicker.board.MapGrid.BadCoords;
import VASSAL.build.module.map.boardPicker.board.mapgrid.HexGridNumbering;
import VASSAL.build.module.map.boardPicker.board.mapgrid.RegularGridNumbering;
import VASSAL.build.module.map.boardPicker.board.mapgrid.SquareGridNumbering;
import VASSAL.build.module.map.boardPicker.board.mapgrid.Zone;
import VASSAL.build.widget.PieceSlot;
import VASSAL.configure.StringArrayConfigurer;
import VASSAL.counters.BasicPiece;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Immobilized;
import VASSAL.counters.Marker;
import VASSAL.counters.UsePrototype;
import VASSAL.tools.SequenceEncoder;
import VASSAL.tools.imports.FileFormatException;
import VASSAL.tools.imports.Importer;

/**
 * The map board itself.
 * 
 * @author Michael Kiefte
 *
 */
public class MapBoard extends Importer {

	private static final String PLACE_NAME = "Location Names";

	/**
	 * A layout consisting of squares in a checkerboard pattern (<it>i.e.</it> each
	 * square has four neighbours).
	 * 
	 * @author Michael Kiefte
	 *
	 */
	protected class GridLayout extends Layout {

		GridLayout(int size, int columns, int rows) {
			super(size, columns, rows);
		}

		@Override
		Point coordinatesToPosition(int x, int y, boolean nullIfOffBoard) {
			if (!nullIfOffBoard || isOnMapBoard(x, y)) {
				int xx = getDeltaX() * x;
				int yy = getDeltaY() * y;
				return new Point(xx, yy);
			} else
				return null;
		}

		@Override
		Dimension getBoardSize() {
			Dimension d = new Dimension();
			d.width = getDeltaX() * nColumns;
			d.height = getDeltaY() * nRows;
			return d;
		}

		@Override
		int getDeltaX() {
			return getHexSize();
		}

		@Override
		int getDeltaY() {
			return getHexSize();
		}

		@Override 
		Point getOrigin() {
			return new Point(getHexSize() / 2, getHexSize() / 2);
		}
		
		@Override
		SquareGrid getGeometricGrid() {
			SquareGrid grid = new SquareGrid();
			grid.setOrigin(getOrigin());
			grid.setDx(getDeltaX());
			grid.setDy(getDeltaY());

			return grid;
		}

		@Override
		Rectangle getRectangle(MapSheet map) {
			Rectangle r = map.getField();

			Point upperLeft = coordinatesToPosition(r.x, r.y, false);
			Point lowerRight = coordinatesToPosition(r.x + r.width - 1, r.y
					+ r.height - 1, false);

			// get lower right-hand corner of lower right-hand square
			lowerRight.x += getHexSize() - 1;
			lowerRight.y += getHexSize() - 1;

			constrainRectangle(upperLeft, lowerRight);
			
			return new Rectangle(upperLeft.x, upperLeft.y, lowerRight.x
					- upperLeft.x + 1, lowerRight.y - upperLeft.y + 1);
		}

		@Override
		RegularGridNumbering getGridNumbering() {
			return new SquareGridNumbering();
		}

		@Override
		int getNFaces() {
			return 4;
		}
	}

	/**
	 * Redundant information about each hex. So far only used for determining
	 * the default order of line definitions for hex sides and hex lines.
	 */
	private class Hex {
		ArrayList<Line> hexLines = new ArrayList<Line>();

		ArrayList<Line> hexSides = new ArrayList<Line>();
	}

	/**
	 * Mapboard element based on terrain symbol from <code>SymbolSet</code>. Not necessarily hexagonal but can also be square.
	 */ 
	protected class HexData extends MapDrawable {

		final SymbolSet.SymbolData symbol;

		HexData(int index, SymbolSet.SymbolData symbol) {
			super(index);
			assert (symbol != null);
			this.symbol = symbol;
		}

		@Override
		void draw(Graphics2D g) {
			Point p = getPosition();
			if (symbol != null)
				g.drawImage(symbol.getImage(), null, p.x, p.y);
		}
	}
	
	/**
	 * Symbol that is placed in every hex.
	 */
	protected class MapBoardOverlay extends HexData {
		
		@Override
		void draw(Graphics2D g) {
			if (symbol != null) {
				for (int y = 0; y < getNRows(); ++y) {
					for (int x = 0; x < getNColumns(); ++x) {
						Point p = coordinatesToPosition(x, y);
						g.drawImage(symbol.getImage(), null, p.x, p.y);
					}
				}
			}
		}

		// doesn't have an index
		MapBoardOverlay(SymbolSet.SymbolData symbol) {
			super(-1, symbol);
		}		
	}

	/**
	 * A line from a hex edge to the centre as in the spoke of a wheel. Typically used for terrain
	 * features such as roads etc.
	 */
	protected class HexLine extends Line {

		private final int direction;

		HexLine(int index, int line, int direction) {
			super(index, line);
			this.direction = direction;
		}

		@Override
		int compare(LineDefinition o1, LineDefinition o2) {
			if (o1 == null && o2 == null)
				return 0;
			else if (o1 == null)
				return 1;
			else if (o2 == null)
				return -1;
			int priority = o1.getHexLineDrawPriority() - o2.getHexLineDrawPriority();
			if (priority != 0)
				return priority;
			else
				return super.compare(o1, o2);
		}

		/* 
		 * Under normal circumstances, map board elements get drawn one at a time. Hex sides and hex lines are
		 * actually continuous from one hex or square to the next. Although ADC2 draws each segment separately
		 * anyway, this creates poor-looking corner and edge effects.  Instead of that, the importer composes longer
		 * lines made up of many continuous smaller segments and then draws a single line in one go.  To do this,
		 * instead of drawing itself as it's called, each segment adds itself to a line and the last segment in the
		 * list calls the draw method for all of the lines thus created.
		 */
		@Override
		void draw(Graphics2D g) {
			LineDefinition l = getLine();

			if (l != null) {
				Point pos = getPosition();
				int size = getLayout().getHexSize();
				pos.translate(size / 2, size / 2);
				Layout lo = getLayout();

				// if ((direction & 0x1) > 0) // horizontal north west
				if ((direction & 0x6) > 0) {// north west; 0x4 = version 1
					Point nw = lo.getNorthWest(hexIndex);
					nw.translate(size / 2, size / 2);
					l.addLine(pos.x, pos.y, (pos.x + nw.x) / 2.0f, (pos.y + nw.y) / 2.0f);
				}
				if ((direction & 0x8) > 0) {// west
					Point w = lo.getWest(hexIndex);
					w.translate(size / 2, size / 2);
					l.addLine(pos.x, pos.y, (pos.x + w.x) / 2.0f, (pos.y + w.y) / 2.0f);
				}
				if ((direction & 0x30) > 0) { // south west; 0x10 = version 1
					Point sw = lo.getSouthWest(hexIndex);
					sw.translate(size / 2, size / 2);
					l.addLine(pos.x, pos.y, (pos.x + sw.x) / 2.0f, (pos.y + sw.y) / 2.0f);
				}
				// if ((direction & 0x40) > 0) // horizontal south west
				if ((direction & 0x80) > 0) {// south
					Point s = lo.getSouth(hexIndex);
					s.translate(size / 2, size / 2);
					l.addLine(pos.x, pos.y, (pos.x + s.x) / 2.0f, (pos.y + s.y) / 2.0f);
				}
				if ((direction & 0x100) > 0) {// north
					Point n = lo.getNorth(hexIndex);
					n.translate(size / 2, size / 2);
					l.addLine(pos.x, pos.y, (pos.x + n.x) / 2.0f,
							(pos.y + n.y) / 2.0f);
				}
				// if ((direction & 0x200) > 0) // horizontal north east
				if ((direction & 0xC00) > 0) { // north east; 0x800 = version 1
					Point ne = lo.getNorthEast(hexIndex);
					ne.translate(size / 2, size / 2);
					l.addLine(pos.x, pos.y, (pos.x + ne.x) / 2.0f, (pos.y + ne.y) / 2.0f);
				}
				if ((direction & 0x1000) > 0) {// east
					Point e = lo.getEast(hexIndex);
					e.translate(size / 2, size / 2);
					l.addLine(pos.x, pos.y, (pos.x + e.x) / 2.0f, (pos.y + e.y) / 2.0f);
				}
				if ((direction & 0x6000) > 0) { // south east; 0x2000 = version 1
					Point se = lo.getSouthEast(hexIndex);
					se.translate(size / 2, size / 2);
					l.addLine(pos.x, pos.y, (pos.x + se.x) / 2.0f, (pos.y + se.y) / 2.0f);
				}
				// if ((direction & 0x8000) > 0) // horizontal south east
			}
			
			// if this is the last one, draw all of the compiled lines.
			if (this == hexLines.get(hexLines.size() - 1))
				drawLines(g, BasicStroke.CAP_BUTT);
		}
		
		@Override
		ArrayList<Line> getLineList(Hex h) {
			return h.hexLines;
		}
	}

	/**
	 * The edges of a hex or square.
 	 */
	protected class HexSide extends Line {

		// flags indicating which side to draw.
		private final int side;

		HexSide(int index, int line, int side) {
			super(index, line);
			this.side = side;
		}

		@Override
		int compare(LineDefinition o1, LineDefinition o2) {
			if (o1 == null && o2 == null)
				return 0;
			else if (o1 == null)
				return 1;
			else if (o2 == null)
				return -1;
			int priority = o1.getHexSideDrawPriority() - o2.getHexSideDrawPriority();
			if (priority != 0)
				return priority;
			else
				return super.compare(o1, o2);
		}

		// see the comments for HexLine.draw(Graphics2D).
		@Override
		void draw(Graphics2D g) {

			LineDefinition l = getLine();

			if (l != null) {
				Point p = getPosition();
				int size = getLayout().getHexSize();
				int dX = getLayout().getDeltaX();
				int dY = getLayout().getDeltaY();

				if ((side & 0x1) > 0) { // vertical SW
					Point sw = getLayout().getSouthWest(hexIndex);
					sw.translate(dX, 0);
					Point s = getLayout().getSouth(hexIndex);
					l.addLine(p.x, sw.y, p.x + (size / 5), s.y);
				}
				if ((side & 0x2) > 0) { // vertical NW
					Point sw = getLayout().getSouthWest(hexIndex);
					sw.translate(dX, 0);
					l.addLine(p.x, sw.y, p.x + (size / 5), p.y);
				}
				if ((side & 0x4) > 0) { // vertical N
					l.addLine(p.x + (size / 5), p.y, p.x + dX, p.y);
				}
				if ((side & 0x8) > 0) { // horizontal SW
					Point se = getLayout().getSouthEast(hexIndex);
					l.addLine(p.x, p.y + dY, se.x, p.y + dY + (size / 5));
				}
				if ((side & 0x10) > 0) { // horizontal W
					l.addLine(p.x, p.y + (size / 5), p.x, p.y + dY);
				}
				if ((side & 0x20) > 0) { // horizontal NW
					Point ne = getLayout().getNorthEast(hexIndex);					
					l.addLine(p.x, p.y + (size / 5), ne.x, p.y);
				}
				if ((side & 0x40) > 0) { // square left
					l.addLine(p.x, p.y, p.x, p.y + dY);
				}
				if ((side & 0x80) > 0) { // square top
					l.addLine(p.x, p.y, p.x + dX, p.y);
				}
			}

			// if this is the last one, draw all the lines.
			if (this == hexSides.get(hexSides.size() - 1))
				drawLines(g, BasicStroke.CAP_ROUND);
		}
		
		@Override
		ArrayList<Line> getLineList(Hex h) {
			return h.hexSides;
		}
	}

	/**
	 * Hexes aligned along rows.
	 */
	protected class HorizontalHexLayout extends HorizontalLayout {

		HorizontalHexLayout(int size, int columns, int rows) {
			super(size, columns, rows);
		}

		@Override
		Dimension getBoardSize() {
			Dimension d = new Dimension();
			d.width = getDeltaX() * nColumns + getHexSize() / 2;
			d.height = getDeltaY() * nRows + getHexSize() / 5 + 1;
			return d;
		}

		@Override
		int getDeltaX() {
			return getHexSize() - (isPreV208Layout()?2:0);
		}

		@Override
		int getDeltaY() {
			return getHexSize() * 4 / 5 - 1;
		}

		@Override
		Point getOrigin() {
			return new Point(getHexSize() / 2, getHexSize() / 2 - (isPreV208Layout() ? 1 : 0));
		}
		
		@Override
		HexGrid getGeometricGrid() {
			HexGrid mg = new HexGrid();
		
			mg.setSideways(true);
		
			// VASSAL defines these sideways. Height always refers to the major
			// dimension, and Dy always refers to height whether they're sideways or not.
			mg.setOrigin(getOrigin());
			mg.setDy(getDeltaX());
			mg.setDx(getDeltaY());
		
			return mg;
		}						
	}
	
	/**
	 * A layout consisting of squares in which every second row is shifted to the
	 * right by one half-width. Used to approximate hexagons as each square has
	 * six neighbours.
	 * 
	 * @author Michael Kiefte
	 *
	 */
	protected class GridOffsetRowLayout extends HorizontalLayout {

		GridOffsetRowLayout(int size, int columns, int rows) {
			super(size, columns, rows);
		}

		@Override
		Dimension getBoardSize() {
			Dimension d = new Dimension();
			d.height = getDeltaY() * nRows + 1;
			d.width = getDeltaX() * nColumns + getHexSize()/2 + 1;
			return d;
		}

		@Override
		int getDeltaX() {
			return getHexSize();
		}

		@Override
		int getDeltaY() {
			return getHexSize();
		}

		@Override
		Point getOrigin() {
			return new Point(getHexSize() * 7 / 12, getHexSize() / 2);
		}
		
		@Override
		AbstractConfigurable getGeometricGrid() {
			HexGrid mg = new HexGrid();

			mg.setSideways(true);
			
			mg.setOrigin(getOrigin());
			mg.setDx(getDeltaY());
			mg.setDy(getDeltaX());

			return mg;
		}			
	}
	
	/**
	 * A layout in which every second row is offset by one-half hex or square.
	 */
	protected abstract class HorizontalLayout extends Layout {

		HorizontalLayout(int size, int columns, int rows) {
			super(size, columns, rows);
		}

		@Override
		int getNFaces() {
			return 6;
		}

		@Override
		void setGridNumberingOffsets(RegularGridNumbering numbering, MapSheet sheet) {
			Point position = coordinatesToPosition(sheet.getField().x, sheet.getField().y, true);
			position.translate(getDeltaX()/2, getDeltaY()/2);
			int rowOffset = numbering.getColumn(position);
			int colOffset = numbering.getRow(position);
			
			rowOffset = -rowOffset + sheet.getTopLeftRow();
			colOffset = -colOffset + sheet.getTopLeftCol();
			
			numbering.setAttribute(RegularGridNumbering.H_OFF, rowOffset);
			numbering.setAttribute(RegularGridNumbering.V_OFF, colOffset);
		}

		@Override
		void initGridNumbering(RegularGridNumbering numbering, MapSheet sheet) {
			// TODO: staggering doesn't appear to be handled at all!
			super.initGridNumbering(numbering, sheet);
			numbering.setAttribute(RegularGridNumbering.FIRST, sheet.rowsAndCols() ? "H" : "V");
			numbering.setAttribute(RegularGridNumbering.H_TYPE, sheet.numericRows() ? "N" : "A");
			numbering.setAttribute(RegularGridNumbering.V_TYPE, sheet.numericCols() ? "N" : "A");
		}

		@Override
		HexGridNumbering getGridNumbering() {
			return new HexGridNumbering();
		}

		@Override
		Point coordinatesToPosition(int x, int y, boolean nullIfOffBoard) {
			if (!nullIfOffBoard || isOnMapBoard(x, y)) {
				int xx = getDeltaX() * x + (y % 2) * getDeltaX() / 2;
				int yy = getDeltaY() * y;
				return new Point(xx, yy);
			} else
				return null;
		}

		@Override
		Point getNorthEast(int index) {
			int row = getRow(index);
			int col = getCol(index) + Math.abs(row) % 2;
			--row;
			return coordinatesToPosition(col, row, false);
		}

		@Override
		Point getNorthWest(int index) {
			int row = getRow(index) - 1;
			int col = getCol(index) - Math.abs(row) % 2;
			return coordinatesToPosition(col, row, false);
		}

		@Override
		Rectangle getRectangle(MapSheet map) {
			Rectangle r = map.getField();

			Point upperLeft = coordinatesToPosition(r.x, r.y, false);
			Point lowerRight = coordinatesToPosition(r.x + r.width - 1, r.y
					+ r.height - 1, false);

			// adjust for staggering of hexes
			if (map.firstHexLeft()) // next one down is to the left
				upperLeft.x -= getHexSize() / 2;

			// adjust x of bottom right-hand corner
			if (r.y % 2 == (r.y + r.height - 1) % 2) { // both even or both odd
				if (map.firstHexRight())
					lowerRight.x += getHexSize() / 2;
				// check to see if lower right-hand corner is on the wrong
				// square
			} 
			else if (r.y % 2 == 1) { 
				// top is odd and bottom is even
				if (map.firstHexLeft())
					lowerRight.x += getHexSize() / 2;
				else 
					lowerRight.x += getHexSize();
			} 
			else if (map.firstHexLeft() && r.y % 2 == 0) 
				// top is even and bottom is odd
				lowerRight.x -= getHexSize() / 2;

			// get lower right corner of lower right hex
			lowerRight.x += getHexSize() - 1;
			lowerRight.y += getHexSize() - 1;

			// adjust so that we don't overlap the centres of hexes that don't
			// belong to this sheet
			upperLeft.x += getHexSize() / 5;
			lowerRight.x -= getHexSize() / 5;

			constrainRectangle(upperLeft, lowerRight);
			
			return new Rectangle(upperLeft.x, upperLeft.y, lowerRight.x
					- upperLeft.x + 1, lowerRight.y - upperLeft.y + 1);
		}

		@Override
		Point getSouthEast(int index) {
			int row = getRow(index);
			int col = getCol(index) + Math.abs(row) % 2;
			++row;
			return coordinatesToPosition(col, row, false);
		}

		@Override
		Point getSouthWest(int index) {
			int row = getRow(index) + 1;
			int col = getCol(index) - Math.abs(row) % 2;
			return coordinatesToPosition(col, row, false);
		}
	}

	/**
	 * A drawable line such as a river, border, or road.
	 */
	protected abstract class Line extends MapDrawable {

		// index of line definition. don't know the actual line definitions until later
		private final int line;

		Line(int index, int line) {
			super(index);
			this.line = line;
			if (hexes == null)
				hexes = new Hex[getNColumns() * getNRows()];
			if (hexes[index] == null)
				hexes[index] = new Hex();
			getLineList(hexes[index]).add(this);
		}

		/**
		 * @return The <code>LineDefinition</code> for this line.
		 */
		LineDefinition getLine() {
			return getLineDefinition(line);
		}

		/**
		 * @return The list of lines by hex.
		 */
		abstract ArrayList<Line> getLineList(Hex h);

		// I no longer remember how this works, but I do remember it took a long time to figure out.
		int compare(LineDefinition o1, LineDefinition o2) {
			if (o1 == null && o2 == null)
				return 0;
			else if (o1 == null)
				return 1;
			else if (o2 == null)
				return -1;
			// go through all the hexes
			// and determine file order for lines
			for (Hex h : hexes) {
				if (h == null)
					continue;
				boolean index1 = false;
				boolean index2 = false;
				for (Line hl : getLineList(h)) {
					if (hl.getLine() == o1) {
						if (index2)
							return 1;
						index1 = true;
					} else if (hl.getLine() == o2) {
						if (index1)
							return -1;
						index2 = true;
					}
				}
			}
			return 0;
		}
		
		void drawLines(Graphics2D g, int cap) {
			ArrayList<LineDefinition> lds = new ArrayList<LineDefinition>(lineDefinitions.length);
			lds.addAll(Arrays.asList(lineDefinitions));
			
			// find the next line in priority
			while (lds.size() > 0) {
				LineDefinition lowest = null;
				for (LineDefinition ld : lds) {
					if (ld == null)
						continue;
					else if (lowest == null || compare(ld, lowest) < 0)
						lowest = ld;
				}
				if (lowest == null)
					break;
				else {
					lowest.draw(g, cap);
					lowest.clearPoints();
					lds.remove(lowest);
				}
			}			
		}
	}

	/**
	 * Line styles for hex sides and hex lines.
	 */
	protected class LineDefinition {

		private final Color color;

		private int hexLineDrawPriority;

		private int hexSideDrawPriority;

		// using floats because we really want to aim for the centre pixel, not necessarily
		// the space between pixels--only important for aliasing effects.
		private ArrayList<ArrayList<Point2D.Float>> points = new ArrayList<ArrayList<Point2D.Float>>();

		// line width
		private final int size;

		private final LineStyle style;

		LineDefinition(Color color, int size, MapBoard.LineStyle style) {
			this.color = color;
			this.size = size;
			this.style = style;
		}

		private void setHexLineDrawPriority(int priority) {
			// only change the priority if it hasn't already been set.
			if (hexLineDrawPriority == 0)
				hexLineDrawPriority = priority;
		}

		private void setHexSideDrawPriority(int priority) {			
			if (hexSideDrawPriority == 0)
				hexSideDrawPriority = priority;
		}

		Color getColor() {
			return color;
		}

		BasicStroke getStroke(int cap) {
			if (size <= 0 || style == null)
				return null;
			return style.getStroke(size, cap);
		}

		void addLine(float x1, float y1, float x2, float y2) {
			addLine(new Point2D.Float(x1, y1), new Point2D.Float(x2, y2));
		}

		void addLine(int x1, int y1, float x2, float y2) {
			addLine(new Point2D.Float((float) x1, (float) y1),
					new Point2D.Float(x2, y2));
		}

		void addLine(int x1, int y1, int x2, int y2) {
			addLine(new Point2D.Float((float) x1, (float) y1),
					new Point2D.Float((float) x2, (float) y2));
		}

		/**
		 * Add a line to the line list for later processing. Attach it to already existing line if possible.
		 * Otherwise start a new one.
		 */
		void addLine(Point2D.Float a, Point2D.Float b) {
			// find out if this line is attached to any other line in the list.
			// if not create a line.
			for (int i = 0; i < points.size(); ++i) {
				ArrayList<Point2D.Float> lineA = points.get(i);
				if (a.equals(lineA.get(0))) { // a at the start of lineA
					// repeated segment?
					if (b.equals(lineA.get(1)))
						return;
					// find out if this segment joins two lines already in
					// existance
					for (int j = 0; j < points.size(); ++j) {
						if (i == j)
							continue;
						ArrayList<Point2D.Float> lineB = points.get(j);
						if (b.equals(lineB.get(0))) { // point A at start of lineA and point B at start of lineB
							if (lineA.size() < lineB.size()) { // insert A before B
								for (int k = 0; k < lineA.size(); ++k)
									lineB.add(0, lineA.get(k));
								points.remove(i);
							} else { // insert B before A
								for (int k = 0; k < lineB.size(); ++k)
									lineA.add(0, lineB.get(k));
								points.remove(j);
							}
							return;
						} else if (b.equals(lineB.get(lineB.size() - 1))) { 
							// point A at start of lineA and point B at end of lineB
							lineB.addAll(lineA);
							points.remove(i);
							return;
						}
					}
					// point A at start of lineA and point B is open
					lineA.add(0, b);
					return;
				} else if (a.equals(lineA.get(lineA.size() - 1))) { 
					// Point A is at end of line A
					if (b.equals(lineA.get(lineA.size() - 2))) // repeated segment?
						return;
					for (int j = 0; j < points.size(); ++j) {
						if (i == j) // skip closed loops
							continue;
						ArrayList<Point2D.Float> lineB = points.get(j);
						if (b.equals(lineB.get(0))) { 
							// point A at end of line A and point B at start of lineB
							lineA.addAll(lineB);
							points.remove(j);
							return;
						} else if (b.equals(lineB.get(lineB.size() - 1))) { 
							// point A at end of lineA and point B at end of lineB
							if (lineA.size() < lineB.size()) { // add line A to B
								for (int k = lineA.size() - 1; k >= 0; --k)
									lineB.add(lineA.get(k));
								points.remove(i);
							} else { // add line B to A
								for (int k = lineB.size() - 1; k >= 0; --k)
									lineA.add(lineB.get(k));
								points.remove(j);
							}
							return;
						}
					}
					// point A at the end of lineA and point B is open
					lineA.add(b);
					return;
				}
				// find out if the segment already exists
				for (int j = 1; j < lineA.size() - 1; ++j)
					if (a.equals(lineA.get(j))
							&& (b.equals(lineA.get(j - 1)) || b.equals(lineA
									.get(j + 1))))
						return;
			}

			// point A is open (not attached)
			for (ArrayList<Point2D.Float> line : points) {
				if (b.equals(line.get(0))) { // B at the start of the line
					// repeated segment?
					if (a.equals(line.get(1)))
						return;
					line.add(0, a);
					return;
				} else if (b.equals(line.get(line.size() - 1))) { 
					// B at the end of the line
					if (a.equals(line.get(line.size() - 2)))
						return;
					line.add(a);
					return;
				}
			}

			// both A and B are open
			ArrayList<Point2D.Float> newLine = new ArrayList<Point2D.Float>(2);
			newLine.add(a);
			newLine.add(b);
			points.add(newLine);
		}

		/**
		 * start fresh.
		 */
		void clearPoints() {
			points.clear();
		}

		void draw(Graphics2D g, int cap) {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			BasicStroke stroke = getStroke(cap);
			if (stroke == null)
				return;
			g.setStroke(stroke);
			g.setColor(getColor());
			GeneralPath gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
			for (ArrayList<Point2D.Float> line : points) {
				gp.moveTo(line.get(0).x, line.get(0).y);
				for (Point2D.Float p : line) {
					if (!p.equals(line.get(0)))
						gp.lineTo(p.x, p.y);
					else if (p != line.get(0))
						gp.closePath();
				}
			}
			g.draw(gp);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		}

		int getHexLineDrawPriority() {
			return hexLineDrawPriority;
		}

		int getHexSideDrawPriority() {
			return hexSideDrawPriority;
		}
	}

	/**
	 * line patter such as dashed or dotted or solid
	 */
	protected enum LineStyle {
		DASH_DOT(new float[] { 12.0f, 8.0f, 4.0f, 8.0f }), 
		DASH_DOT_DOT(new float[] { 12.f, 4.0f, 4.0f, 4.0f, 4.0f, 4.0f }), 
		DASHED(new float[] { 12.0f, 8.0f }), 
		DOTTED(new float[] { 4.0f, 4.0f }), 
		SOLID(null);

		private float[] dash;

		LineStyle(float[] dash) {
			this.dash = dash;
		}

		BasicStroke getStroke(int size, int cap) {
			if (dash == null)
				// nice effect if it's a solid line
				return new BasicStroke(size, cap, BasicStroke.JOIN_ROUND);
			else
				return new BasicStroke(size, BasicStroke.CAP_BUTT,
						BasicStroke.JOIN_ROUND, 0.0f, dash, 0.0f);
		}
	}

	/**
	 * Anything that can be drawn on the map and is associated with a particular hex.
	 */ 
	protected abstract class MapDrawable {

		// hex index in row-major order: determines location on map
		protected final int hexIndex;

		MapDrawable(int index) {
			this.hexIndex = index;
		}

		abstract void draw(Graphics2D g);

		int getHexIndex() {
			return hexIndex;
		}

		/**
		 * @return Upper left-hand corner of hex or square.
		 */ 
		Point getPosition() {
			return indexToPosition(hexIndex);
		}

		/**
		 * @return Enclosing rectangle for hex or square.
		 */ 
		Rectangle getRectangle() {
			Rectangle r = new Rectangle(getPosition());
			int width = getLayout().getHexSize();
			r.width = width;
			r.height = width;
			return r;
		}
	}

	/**
	 * Defines the numbering system for an ADC2 mapboard.
	 */
	protected class MapSheet {

		private int topLeftCol;
		
		private int topLeftRow;
		
		private final Rectangle field;

		private final String name;

		private final int nColChars;

		private final int nRowChars;

		private final int style;

		private Zone zone;

		MapSheet(String name, Rectangle playingFieldPosition, int style, int nColChars, int nRowChars) {
			this.name = name;
			this.field = playingFieldPosition;
			this.style = style;
			this.nColChars = nColChars;
			this.nRowChars = nRowChars;
		}

		/**
		 * @return A rectangle giving the bounds of the hex coordinates.
		 */
		Rectangle getField() {
			return field;
		}

		/**
		 * @return The sheet name.
		 */
		String getName() {
			return name;
		}

		/**
		 * @return Number of characters in the column label.
		 */
		int getNColChars() {
			return nColChars;
		}

		/**
		 * @return Number of characters in the row label.
		 */
		int getNRowChars() {
			return nRowChars;
		}

		/**
		 * Used by the grid numbering system in VASSAL.
		 */
		String getRectangleAsString() {
			Rectangle r = getLayout().getRectangle(this);
			if (r == null)
				return null;
			return r.x + "," + r.y + ";" + (r.x + r.width - 1) + "," + r.y
					+ ";" + (r.x + r.width - 1) + "," + (r.y + r.height - 1)
					+ ";" + r.x + "," + (r.y + r.height - 1);
		}

		/**
		 * @return <code>true</code> if column labels are alphabetic.
		 */
		boolean alphaCols() {
			return !numericCols();
		}

		/**
		 * @return <code>true</code> if row labels are alphabetic.
		 */
		boolean alphaRows() {
			return !numericRows();
		}

		/**
		 * @return <code>true</code> if columns are first in coordinate label.
		 */
		boolean colsAndRows() {
			return (style & 0x2) > 0;
		}

		/**
		 * @return <code>true</code> if column labels increase going left.
		 */
		boolean colsIncreaseLeft() {
			return !colsIncreaseRight();
		}

		/**
		 * @return <code>true</code> if column labels increase going right.
		 */
		boolean colsIncreaseRight() {
			return (style & 0x10) > 0;
		}

		/**
		 * @return <code>true</code> if the row label of odd-numbered columns is shifted down.
		 */
		boolean firstHexDown() {
			return (style & 0x40) > 0 && getLayout() instanceof VerticalLayout;
		}

		/**
		 * @return <code>true</code> if the row label of odd-numbered rows is shifted left.
		 */
		boolean firstHexLeft() {
			return (style & 0x40) > 0 && getLayout() instanceof HorizontalLayout;
		}

		/**
		 * @return <code>true</code> if the row label of odd-numbered rows is shifted right.
		 */
		boolean firstHexRight() {
			return (style & 0x40) == 0 && getLayout() instanceof HorizontalLayout;
		}

		/**
		 * @return <code>true</code> if the row label of odd-numbered columns is shifted down.
		 */
		boolean firstHexUp() {
			return (style & 0x40) == 0 && getLayout() instanceof VerticalLayout;
		}

		RegularGridNumbering getGridNumbering() {
			// numbering system
			RegularGridNumbering gn = getLayout().getGridNumbering();
			getLayout().initGridNumbering(gn, this);
			return gn;
		}

		/**
		 * Creates a VASSAL <code>Zone</code> if not already created and initializes settings.
		 */
		Zone getZone() {			
			if (zone == null) {
				zone = new Zone();
				zone.setConfigureName(getName());

				String rect = getRectangleAsString();
				if (rect == null)
					return null;
				zone.setAttribute(Zone.PATH, rect);
				zone.setAttribute(Zone.LOCATION_FORMAT, "$name$ $gridLocation$");
				AbstractConfigurable mg = getLayout().getGeometricGrid();

				// add numbering system to grid
				RegularGridNumbering gn = getGridNumbering();
				gn.addTo(mg);
				mg.add(gn);			

				// add grid to zone
				mg.addTo(zone);
				zone.add(mg);

				getLayout().setGridNumberingOffsets(gn, this);
			}

			return zone;
		}

		/**
		 * @return <code>true</code> if column labels are numeric.
		 */
		boolean numericCols() {
			return (style & 0x4) > 0;
		}

		/**
		 * @return <code>true</code> if row labels are numeric.
		 */
		boolean numericRows() {
			return (style & 0x8) > 0;
		}

		/**
		 * Rows before columns?
		 */
		boolean rowsAndCols() {
			return !colsAndRows();
		}

		/**
		 * @return <code>true</code> if row labels increase downward.
		 */
		boolean rowsIncreaseDown() {
			return (style & 0x20) > 0;
		}

		/**
		 * @return <code>true</code> if row labels increase upward.
		 */
		boolean rowsIncreaseUp() {
			return !rowsIncreaseDown();
		}

		/**
		 * @return Index of the top left column on the map sheet.
		 */
		int getTopLeftCol() {
			return topLeftCol;
		}

		/**
		 * Sets the top left column index of the map sheet.
		 */
		void setTopLeftCol(int topLeftCol) {
			this.topLeftCol = topLeftCol;
		}

		/**
		 * @return Index of the top left row of the map sheet.
		 */
		int getTopLeftRow() {
			return topLeftRow;
		}

		/**
		 * Sets the top left row index of the map sheet.
		 */
		void setTopLeftRow(int topLeftRow) {
			this.topLeftRow = topLeftRow;
		}
	}

	/**
	 * Place name element which includes not only the name itself, but the font and style that it
	 * should be drawn with.
	 */
	protected class PlaceName extends MapDrawable {

		// text colour
		private final Color color;

		// bit flags
		private final int font;

		// position relative to the hex. not really orientation. e.g., can't
		// have vertical text.
		private final PlaceNameOrientation orientation;

		// font size
		private final int size;

		// the actual name
		private final String text;

		PlaceName(int index, String text, Color color, PlaceNameOrientation orientation, int size, int font) {
			super(index);
			this.text = text;
			assert (color != null);
			this.color = color;
			assert (orientation != null);
			this.orientation = orientation;
//			assert (size > 0);
			this.size = size;
			font &= 0x7f;
			int fontIndex = font & 0xf;
			if (fontIndex < 1 || fontIndex > 9) {
				fontIndex = 9;
				font = font & 0xf0 | fontIndex;
			}
			this.font = font;
		}

		Font getFont() {
			int size = getSize();
			return size == 0 ? null : getDefaultFont(getSize(), font);
		}

		/**
		 * Get the position based on the hex index, font and orientation.
		 */
		Point getPosition(Graphics2D g) {
			Point p = getPosition();
			if (getSize() == 0)
				return p;
			assert (g.getFont() == getFont());
			FontMetrics fm = g.getFontMetrics();
			int size = getLayout().getHexSize();

			switch (orientation) {
			case LOWER_CENTER:
			case UPPER_CENTER:
			case LOWER_RIGHT:
			case UPPER_RIGHT:
			case UPPER_LEFT:
			case LOWER_LEFT:
			case HEX_CENTER:
				p.x += size / 2; // middle of the hex.
				break;
			case CENTER_RIGHT:
				p.x += size; // right of hex
				break;
			case CENTER_LEFT:
				break;
			}
			switch (orientation) {
			case LOWER_CENTER:
			case UPPER_CENTER:
			case HEX_CENTER:
				// text centered
				p.x -= fm.charsWidth(text.toCharArray(), 0, text.length()) / 2;
				break;
			case UPPER_LEFT:
			case LOWER_LEFT:
			case CENTER_LEFT:
				// right justified
				p.x -= fm.charsWidth(text.toCharArray(), 0, text.length());
				break;
			case LOWER_RIGHT:
			case UPPER_RIGHT:
			case CENTER_RIGHT:
				break;
			}
			switch (orientation) {
			case LOWER_CENTER:
			case LOWER_RIGHT:
			case LOWER_LEFT:
				p.y += size + fm.getAscent();
				break;
			case UPPER_CENTER:
			case UPPER_RIGHT:
			case UPPER_LEFT:
				p.y -= fm.getDescent();
				break;
			case CENTER_LEFT:
			case CENTER_RIGHT:
			case HEX_CENTER:
				p.y += size / 2 + fm.getHeight() / 2 - fm.getDescent();
				break;
			}
			return p;
		}

		// scale the size more appropriately--for some reason ADC2 font sizes
		// don't correspond to anything else.
		int getSize() {
			return size == 0 ? 0 : (size + 1) * 4 / 3 - 1;
		}

		@Override
		void draw(Graphics2D g) {
			if (getSize() == 0)
				return;
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			g.setFont(getFont());
			g.setColor(color);
			Point p = getPosition(g);
			g.drawString(text, p.x, p.y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_OFF);
		}

		String getText() {
			return text;
		}
	}

	protected enum PlaceNameOrientation {
		CENTER_LEFT, CENTER_RIGHT, HEX_CENTER, LOWER_CENTER, LOWER_LEFT, LOWER_RIGHT, UPPER_CENTER, UPPER_LEFT, UPPER_RIGHT;
	}

	/**
	 * A layout consisting of squares in which every second column is shifted
	 * downward by one half width.  This is done to approximate hexagons as each
	 * square as six neighbours.
	 * 
	 * @author Michael Kiefte
	 *
	 */
	protected class GridOffsetColumnLayout extends VerticalLayout {

		GridOffsetColumnLayout(int size, int columns, int rows) {
			super(size, columns, rows);
		}

		@Override
		Dimension getBoardSize() {
			Dimension d = new Dimension();
			d.width = getDeltaX() * nColumns + 1;
			d.height = getDeltaY() * nRows + getHexSize() / 2 + 1;
			return d;
		}

		@Override
		int getDeltaX() {
			return getHexSize();
		}

		@Override
		int getDeltaY() {
			return getHexSize();
		}

		@Override
		Point getOrigin() {
			return new Point(getHexSize() * 7 / 12, getHexSize() / 2);
		}
		
		@Override
		AbstractConfigurable getGeometricGrid() {
			HexGrid mg = new HexGrid();

			mg.setOrigin(getOrigin());
			mg.setDx(getDeltaX());
			mg.setDy(getDeltaY());

			return mg;
		}	
	}

	/**
	 * Hexes in columns.
	 */
	protected class VerticalHexLayout extends VerticalLayout {

		VerticalHexLayout(int size, int columns, int rows) {
			super(size, columns, rows);
		}

		@Override
		Dimension getBoardSize() {
			Dimension d = new Dimension();
			d.width = getDeltaX() * nColumns + getHexSize() / 5 + 1;
			d.height = getDeltaY() * nRows + getHexSize() / 2 + 1;
			return d;
		}

		@Override
		int getDeltaX() {
			return getHexSize() * 4 / 5 - (isPreV208Layout() ? 1 : 0);
		}

		@Override
		int getDeltaY() {
			return getHexSize() - (isPreV208Layout() ? 2 : 1);
		}

		@Override
		Point getOrigin() {
			return new Point(getHexSize() / 2, getHexSize() / 2 - (isPreV208Layout() ? 1 : 0));
		}
		
		@Override
		HexGrid getGeometricGrid() {
			HexGrid mg = new HexGrid();

			mg.setOrigin(getOrigin());
			mg.setDx(getDeltaX());
			mg.setDy(getDeltaY());
			
			return mg;
		}
	}

	/**
	 * A layout in which every second column is offset--either hexes or squares.
	 */
	protected abstract class VerticalLayout extends Layout {

		@Override
		int getNFaces() {
			return 6;
		}

		VerticalLayout(int size, int columns, int rows) {
			super(size, columns, rows);
		}

		@Override
		HexGridNumbering getGridNumbering() {
			return new HexGridNumbering();
		}
		
		@Override
		void initGridNumbering(RegularGridNumbering numbering, MapSheet sheet) {
			boolean stagger = false;
			if (sheet.firstHexDown() && sheet.getField().x%2 == 1)
				stagger = true;
			else if (sheet.firstHexUp() && sheet.getField().x%2 == 0)
				stagger = true;
			numbering.setAttribute(HexGridNumbering.STAGGER, stagger);
			super.initGridNumbering(numbering, sheet);
		}

		@Override
		Point coordinatesToPosition(int x, int y, boolean nullIfOffBoard) {
			if (!nullIfOffBoard || isOnMapBoard(x, y)) {
				int xx = getDeltaX() * x;
				int yy = getDeltaY() * y + x % 2 * getDeltaY() / 2;
				return new Point(xx, yy);
			} else
				return null;
		}

		@Override
		Point getNorthEast(int index) {
			int col = getCol(index) + 1;
			int row = getRow(index) - Math.abs(col) % 2;
			return coordinatesToPosition(col, row, false);
		}

		@Override
		Point getNorthWest(int index) {
			int col = getCol(index) - 1;
			int row = getRow(index) - Math.abs(col) % 2;
			return coordinatesToPosition(col, row, false);
		}

		@Override
		Rectangle getRectangle(MapSheet map) {
			Rectangle r = map.getField();

			if (r.width <= 0 || r.height <= 0)
				return null;
			
			Point upperLeft = coordinatesToPosition(r.x, r.y, false);
			Point lowerRight = coordinatesToPosition(r.x + r.width - 1, r.y
					+ r.height - 1, false);

			// adjust for staggering of hexes
			if (map.firstHexUp()) // next one over is above
				upperLeft.y -= getHexSize() / 2;

			// adjust y of bottom right-hand corner
			if (r.x % 2 == (r.x + r.width - 1) % 2) { // both even or both odd
				if (map.firstHexDown())
					lowerRight.y += getHexSize() / 2;
				// check to see if lower right-hand corner is on the wrong
				// square
			} else if (r.x % 2 == 1) { 
				// left is odd and right is even
				if (map.firstHexDown())
					lowerRight.y += getHexSize();
				else
					lowerRight.y += getHexSize()/2;
			} else if (map.firstHexUp() && r.x % 2 == 0) { 
				// left is even and right is odd
				lowerRight.y -= getHexSize() / 2;
			}

			// get lower right corner of lower right hex
			lowerRight.x += getHexSize() - 1;
			lowerRight.y += getHexSize() - 1;

			// adjust so that we don't overlap the centres of hexes that don't
			// belong to this sheet
			upperLeft.y += getHexSize() / 5;
			lowerRight.y -= getHexSize() / 5;

			constrainRectangle(upperLeft, lowerRight);
			
			return new Rectangle(upperLeft.x, upperLeft.y, lowerRight.x
					- upperLeft.x + 1, lowerRight.y - upperLeft.y + 1);
		}

		@Override
		Point getSouthEast(int index) {
			int col = getCol(index);
			int row = getRow(index) + Math.abs(col) % 2;
			++col;
			return coordinatesToPosition(col, row, false);
		}

		@Override
		Point getSouthWest(int index) {
			int col = getCol(index);
			int row = getRow(index) + Math.abs(col) % 2;
			--col;
			return coordinatesToPosition(col, row, false);
		}
	}

	/**
	 * How the hexes or squares are organized on the map board.
	 */
	protected abstract class Layout {

		protected final int nColumns;

		protected final int nRows;

		// Size of the hexes or squares.
		private final int size;

		Layout(int size, int columns, int rows) {
			this.size = size;
			this.nColumns = columns;
			this.nRows = rows;
		}

		protected int getRow(int index) {
			return index / nColumns;
		}
		
		protected int getCol(int index) {
			return index % nColumns;
		}
		
		/**
		 * Move the upper left and lower-right points to just within the map board.
		 */
		void constrainRectangle(Point upperLeft, Point lowerRight) {
			if (upperLeft.x < 0)
				upperLeft.x = 0;
			if (upperLeft.y < 0)
				upperLeft.y = 0;
			Dimension d = getBoardSize();
			
			if (lowerRight.x >= d.width)
				lowerRight.x = d.width-1;
			if (lowerRight.y >= d.height)
				lowerRight.y = d.height-1;
		}

		/**
		 * @return number of flat sides. <i>e.g.</i>, four for squares, six for hexes.
		 */
		abstract int getNFaces();
		
		/**
		 * Set attributes of the <code>GridNumbering</code> object based on map board parameters.
		 */
		void initGridNumbering(RegularGridNumbering numbering, MapSheet sheet) {
			numbering.setAttribute(RegularGridNumbering.FIRST, sheet.colsAndRows() ? "H" : "V");
			numbering.setAttribute(RegularGridNumbering.H_TYPE, sheet.numericCols() ? "N" : "A");
			numbering.setAttribute(RegularGridNumbering.H_LEADING, sheet.getNColChars()-1);
			numbering.setAttribute(RegularGridNumbering.H_DESCEND, sheet.colsIncreaseLeft());
			numbering.setAttribute(RegularGridNumbering.H_DESCEND, sheet.colsIncreaseLeft());
			numbering.setAttribute(RegularGridNumbering.V_TYPE, sheet.numericRows() ? "N" : "A");
			numbering.setAttribute(RegularGridNumbering.V_LEADING, sheet.getNRowChars()-1);
			numbering.setAttribute(RegularGridNumbering.V_DESCEND, sheet.rowsIncreaseUp());
		}

		/**
		 * Set the offset in the grid numbering system according to the specified map sheet.
		 */
		void setGridNumberingOffsets(RegularGridNumbering numbering, MapSheet sheet) {	
			Point position = coordinatesToPosition(sheet.getField().x, sheet.getField().y, true);
			// shift to the middle of the hex
			position.translate(getDeltaX()/2, getDeltaY()/2);
			// use the numbering system to find out where we are
			int rowOffset = numbering.getRow(position);
			int colOffset = numbering.getColumn(position);
			
			rowOffset = -rowOffset + sheet.getTopLeftRow();
			colOffset = -colOffset + sheet.getTopLeftCol();
			
			numbering.setAttribute(RegularGridNumbering.H_OFF, colOffset);
			numbering.setAttribute(RegularGridNumbering.V_OFF, rowOffset);
		}
		
		/**
		 * @return an uninitialized grid numbering system appropriate for this layout
		 */
		abstract RegularGridNumbering getGridNumbering();

		/**
		 * Returns a point corresponding the the upper-left corner of the square
		 * specified by the coordinates.
		 * 
		 * @param x column
		 * @param y row
		 * @param nullIfOffBoard return null if not on the board. Otherwise the point
		 *        may not be valid.
		 * @return the point corresponding to the upper-left-hand corner of the square.
		 */
		abstract Point coordinatesToPosition(int x, int y, boolean nullIfOffBoard);

		/**
		 * @return board image size dimensions in pixels.
		 */
		abstract Dimension getBoardSize();

		/**
		 * @return the distance in pixels to the next square on the right.
		 */
		abstract int getDeltaX();

		/**
		 * @return the distance in pixels ot the next square below
		 */
		abstract int getDeltaY();

		/**
		 * Returns the location of the hex or square to the East.
		 * 
		 * @param index raw index (columns increasing fastest).
		 * @return the position in pixels of the next hex or square to the East.
		 */
		Point getEast(int index) {
			int row = getRow(index);
			int col = getCol(index) + 1;
			return coordinatesToPosition(col, row, false);
		}

		/**
		 * @return an initialized VASSAL hex grid appropriate for the current layout
		 */
		abstract AbstractConfigurable getGeometricGrid();

		/**
		 * Returns the location of the hex or square to the North.
		 * 
		 * @param index raw index (columns increasing fastest).
		 * @return the position in pixels of the next hex or square to the North.
		 */
		Point getNorth(int index) {
			int row = getRow(index) - 1;
			int col = getCol(index);
			return coordinatesToPosition(col, row, false);
		}

		/**
		 * Returns the location of the hex or square to the NorthEast.
		 * 
		 * @param index raw index (columns increasing fastest).
		 * @return the position in pixels of the next hex or square to the NorthEast.
		 */
		Point getNorthEast(int index) {
			int row = getRow(index) - 1;
			int col = getCol(index) + 1;
			return coordinatesToPosition(col, row, false);
		}

		/**
		 * Returns the location of the hex or square to the NorthWest.
		 * 
		 * @param index raw index (columns increasing fastest).
		 * @return the position in pixels of the next hex or square to the NorthWest.
		 */
		Point getNorthWest(int index) {
			int row = getRow(index) - 1;
			int col = getCol(index) - 1;
			return coordinatesToPosition(col, row, false);
		}

		/**
		 * @return the centre in pixels of a square or hex relative to the top-left corner.
		 */
		abstract Point getOrigin();

		/**
		 * Returns a rectangle in pixels that encloses the given <code>MapSheet</code>. 
		 * Returns null if <code>MapSheet</code> has a negative size.
		 */
		abstract Rectangle getRectangle(MapSheet map);

		/**
		 * @return the size of the hexes or squares in pixels.
		 */
		int getHexSize() {
			return size;
		}

		/**
		 * Returns the location of the hex or square to the South.
		 * 
		 * @param index raw index (columns increasing fastest).
		 * @return the position in pixels of the next hex or square to the South.
		 */
		Point getSouth(int index) {
			int row = getRow(index) + 1;
			int col = getCol(index);
			return coordinatesToPosition(col, row, false);
		}

		/**
		 * Returns the location of the hex or square to the SouthEast.
		 * 
		 * @param index raw index (columns increasing fastest).
		 * @return the position in pixels of the next hex or square to the SouthEast.
		 */
		Point getSouthEast(int index) {
			int row = getRow(index) + 1;
			int col = getCol(index) + 1;
			return getLayout().coordinatesToPosition(col, row, false);
		}

		/**
		 * Returns the location of the hex or square to the SouthWest.
		 * 
		 * @param index raw index (columns increasing fastest).
		 * @return the position in pixels of the next hex or square to the SouthWest.
		 */
		Point getSouthWest(int index) {
			int row = getRow(index) + 1;
			int col = getCol(index) - 1;
			return coordinatesToPosition(col, row, false);
		}

		/**
		 * Returns the location of the hex or square to the West.
		 * 
		 * @param index raw index (columns increasing fastest).
		 * @return the position in pixels of the next hex or square to the West.
		 */
		Point getWest(int index) {
			int row = getRow(index);
			int col = getCol(index) - 1;
			return coordinatesToPosition(col, row, false);
		}
	}

	// ADCs default pallet which is referenced by index.
	private static final Color[] defaultColorPallet = { new Color(0x000000),
			new Color(0x808080), new Color(0x800000), new Color(0x808000),
			new Color(0x008000), new Color(0x008080), new Color(0x000080),
			new Color(0x800080), new Color(0x808040), new Color(0x004040),
			new Color(0x0080ff), new Color(0x004080), new Color(0x4000ff),
			new Color(0x804000), new Color(0xffffff), new Color(0xc0c0c0),
			new Color(0xff0000), new Color(0xffff00), new Color(0x00ff00),
			new Color(0x00ffff), new Color(0x0000ff), new Color(0xff00ff),
			new Color(0xffff80), new Color(0x00ff80), new Color(0x80ffff),
			new Color(0x8080ff), new Color(0xff0080), new Color(0xff8040),
			new Color(0x010101), new Color(0x0e0e0e), new Color(0x1c1c1c),
			new Color(0x2a2a2a), new Color(0x383838), new Color(0x464646),
			new Color(0x545454), new Color(0x626262), new Color(0x707070),
			new Color(0x7e7e7e), new Color(0x8c8c8c), new Color(0x9a9a9a),
			new Color(0xa8a8a8), new Color(0xb6b6b6), new Color(0xc4c4c4),
			new Color(0xd2d2d2), new Color(0xe0e0e0), new Color(0xeeeeee),
			new Color(0x330000), new Color(0x660000), new Color(0x990000),
			new Color(0xcc0000), new Color(0xc1441a), new Color(0x003300),
			new Color(0x333300), new Color(0x663300), new Color(0x993300),
			new Color(0xcc3300), new Color(0xff3300), new Color(0x006600),
			new Color(0x336600), new Color(0x666600), new Color(0x996600),
			new Color(0xcc6600), new Color(0xff6600), new Color(0x009900),
			new Color(0x339900), new Color(0x669900), new Color(0x999900),
			new Color(0xcc9900), new Color(0xff9900), new Color(0x00cc00),
			new Color(0x33cc00), new Color(0x66cc00), new Color(0x99cc00),
			new Color(0xcccc00), new Color(0xffcc00), new Color(0x00ea00),
			new Color(0x33ff00), new Color(0x66ff00), new Color(0x99ff00),
			new Color(0xccff00), new Color(0xffff00), new Color(0x000033),
			new Color(0x330033), new Color(0x660033), new Color(0x990033),
			new Color(0xcc0033), new Color(0xff0033), new Color(0x003333),
			new Color(0x663333), new Color(0x993333), new Color(0xcc3333),
			new Color(0xff3333), new Color(0x006633), new Color(0x336633),
			new Color(0x666633), new Color(0x996633), new Color(0xcc6633),
			new Color(0xff6633), new Color(0x009933), new Color(0x339933),
			new Color(0x669933), new Color(0x999933), new Color(0xcc9933),
			new Color(0xff9933), new Color(0x00cc33), new Color(0x33cc33),
			new Color(0x66cc33), new Color(0x99cc33), new Color(0xcccc33),
			new Color(0xffcc33), new Color(0x00ff33), new Color(0x33ff33),
			new Color(0x66ff33), new Color(0x99ff33), new Color(0xccff33),
			new Color(0xffff33), new Color(0x000066), new Color(0x330066),
			new Color(0x660066), new Color(0x990066), new Color(0xcc0066),
			new Color(0xff0066), new Color(0x003366), new Color(0x333366),
			new Color(0x663366), new Color(0x993366), new Color(0xcc3366),
			new Color(0xff3366), new Color(0x006666), new Color(0x336666),
			new Color(0x996666), new Color(0xcc6666), new Color(0xff6666),
			new Color(0x009966), new Color(0x339966), new Color(0x669966),
			new Color(0x999966), new Color(0xcc9966), new Color(0xff9966),
			new Color(0x00cc66), new Color(0x33cc66), new Color(0x66cc66),
			new Color(0x99cc66), new Color(0xcccc66), new Color(0xffcc66),
			new Color(0x00ff66), new Color(0x33ff66), new Color(0x66ff66),
			new Color(0x99ff66), new Color(0xccff66), new Color(0xffff66),
			new Color(0x000099), new Color(0x330099), new Color(0x660099),
			new Color(0x990099), new Color(0xcc0099), new Color(0xff0099),
			new Color(0x003399), new Color(0x333399), new Color(0x663399),
			new Color(0x993399), new Color(0xcc3399), new Color(0xff3399),
			new Color(0x006699), new Color(0x336699), new Color(0x666699),
			new Color(0x996699), new Color(0xcc6699), new Color(0xff6699),
			new Color(0x009999), new Color(0x339999), new Color(0x669999),
			new Color(0xcc9999), new Color(0xff9999), new Color(0x00cc99),
			new Color(0x33cc99), new Color(0x66cc99), new Color(0x99cc99),
			new Color(0xcccc99), new Color(0xffcc99), new Color(0x00ff99),
			new Color(0x33ff99), new Color(0x66ff99), new Color(0x99ff99),
			new Color(0xccff99), new Color(0xffff99), new Color(0x0000cc),
			new Color(0x3300cc), new Color(0x6600cc), new Color(0x9900cc),
			new Color(0xcc00cc), new Color(0xff00cc), new Color(0x0033cc),
			new Color(0x3333cc), new Color(0x6633cc), new Color(0x9933cc),
			new Color(0xcc33cc), new Color(0xff33cc), new Color(0x0066cc),
			new Color(0x3366cc), new Color(0x6666cc), new Color(0x9966cc),
			new Color(0xcc66cc), new Color(0xff66cc), new Color(0x0099cc),
			new Color(0x3399cc), new Color(0x6699cc), new Color(0x9999cc),
			new Color(0xcc99cc), new Color(0xff99cc), new Color(0x00cccc),
			new Color(0x33cccc), new Color(0x66cccc), new Color(0x99cccc),
			new Color(0xffcccc), new Color(0x00ffcc), new Color(0x33ffcc),
			new Color(0x66ffcc), new Color(0x99ffcc), new Color(0xccffcc),
			new Color(0xffffcc), new Color(0x0000ff), new Color(0x3300ff),
			new Color(0x6600ff), new Color(0x9900ff), new Color(0xcc00ff),
			new Color(0xff00ff), new Color(0x0033ff), new Color(0x3333ff),
			new Color(0x6633ff), new Color(0x9933ff), new Color(0xcc33ff),
			new Color(0xff33ff), new Color(0x0066ff), new Color(0x3366ff),
			new Color(0x6666ff), new Color(0x9966ff), new Color(0xcc66ff),
			new Color(0xff66ff), new Color(0x0099ff), new Color(0x3399ff),
			new Color(0x6699ff), new Color(0x9999ff), new Color(0xcc99ff),
			new Color(0xff99ff), new Color(0x00ccff), new Color(0x33ccff),
			new Color(0x66ccff), new Color(0x99ccff), new Color(0xccccff),
			new Color(0xffccff), new Color(0x00ffff), new Color(0x33ffff),
			new Color(0x66ffff), new Color(0x99ffff), new Color(0xccffff) };

	// Archive of fonts used for placenames. makes reuse possible and is
	// probably faster as most of the place names use only one of a very few fonts.
	private final static HashMap<Integer, Font> defaultFonts = new HashMap<Integer, Font>();

	// which level to import
	private static final int zoomLevel = 2;

	/**
	 * Returns a color from the default ADC pallet
	 */
	protected static Color getColorFromIndex(int index) {
		assert (index >= 0 && index < defaultColorPallet.length);
		return defaultColorPallet[index];
	}

	// fonts available to ADC
	private static final String[] defaultFontNames = { "Courier", "Fixedsys",
			"MS Sans Serif", "MS Serif", "Impact", "Brush Script MT",
			"System", "Times New Roman", "Arial" };

	private static final String PLACE_NAMES = "Place Names";
	
	/**
	 * Get a font based on size and font index. If this font has not already been created, then it will be generated.
	 * Can be reused later if the same font was already created.
	 * 
	 * @param size Font size.
	 * @param font Font index. See MapBoard.java for format.
	 */
	
	/* Binary format for fonts:
	 * 
	 *             00000000
	 *                 ||||_ Font name index (between 1 and 9).
	 *                |_____ Bold flag.
	 *               |______ Italics flag.
	 *              |_______ Underline flag.
	 */
	protected static Font getDefaultFont(int size, int font) {
		Integer key = new Integer((size << 8) + font);
		Font f = defaultFonts.get(key);
		if (f == null) {
			int fontIndex = font & 0xf;
			assert (fontIndex >= 1 && fontIndex <= 9);
			boolean isBold = (font & 0x0010) > 0;
			boolean isItalic = (font & 0x0020) > 0;
			boolean isUnderline = (font & 0x0040) > 0;
			String fontName = defaultFontNames[fontIndex - 1];
			int fontStyle = Font.PLAIN;
			if (isItalic)
				fontStyle |= Font.ITALIC;
			if (isBold)
				fontStyle |= Font.BOLD;
			f = new Font(fontName, fontStyle, size);
			if (isUnderline) {
				// TODO: why doesn't underlining doesn't work? Why why why?
				Hashtable<TextAttribute, Object> hash = new Hashtable<TextAttribute, Object>();
				hash.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
				f = f.deriveFont(hash);
			}
			defaultFonts.put(key, f);
		}
		return f;
	}

	// tertiary symbols.
	private final ArrayList<HexData> attributes = new ArrayList<HexData>();

	// name of the map board is derived from the file name
	private String baseName;

	// hex data organized by index
	private Hex[] hexes;

	// hexline data
	private final ArrayList<HexLine> hexLines = new ArrayList<HexLine>();

	// hexside data
	private final ArrayList<HexSide> hexSides = new ArrayList<HexSide>();

	// layout of the hexes or squares
	private Layout layout;

	// line definitions needed for hex sides and lines
	private LineDefinition[] lineDefinitions;

	// actual map image which is drawn on request
	private BufferedImage map;

	// organizes all the drawable elements in order of drawing priority
	private ArrayList<ArrayList<? extends MapDrawable>> mapElements = new ArrayList<ArrayList<? extends MapDrawable>>();

	// grid numbering systems
	private final ArrayList<MapSheet> mapSheets = new ArrayList<MapSheet>();

	// labels; not necessary actual places corresponding to a hex, although
	// that's how it's described by ADC2
	private final ArrayList<PlaceName> placeNames = new ArrayList<PlaceName>();

	// optional place symbol in addition to primary and secondary mapboard
	// symbol
	private final ArrayList<HexData> placeSymbols = new ArrayList<HexData>();

	// primary mapboard symbols. Every hex must have one even if it's null.
	private final ArrayList<HexData> primaryMapBoardSymbols = new ArrayList<HexData>();

	// and secondary mapboard symbols (typically a lot fewer)
	private final ArrayList<HexData> secondaryMapBoardSymbols = new ArrayList<HexData>();
	
	// overlay symbol. there's only one, but we make it an ArrayList<> for consistency
	// with other drawing objects
	private final ArrayList<MapBoardOverlay> overlaySymbol = new ArrayList<MapBoardOverlay>();

	// symbol set associated with this map -- needed for mapboard symbols
	private SymbolSet set;

	// How many hex columns in the map.
	private int columns;

	// How many hex rows in the map.
	private int rows;

	// background map color when hexes are not drawn.
	private Color tableColor;

	// version information needed for rendering hexes and determining hex dimensions
	private boolean isPreV208;

	// map file path
	private String path;
	
	// the exported VASSAL Map object
	private Map mainMap;

	// VASSAL board created from imported map
	private Board board;

	// The VASSAL BoardPicker object which is the tree parent of Board.
	private BoardPicker boardPicker;

	// initialize the drawing elements which must all be ArrayList<>'s.
	public MapBoard() {
		mapElements.add(primaryMapBoardSymbols);
		mapElements.add(secondaryMapBoardSymbols);
		mapElements.add(hexSides);
		mapElements.add(hexLines);
		mapElements.add(placeSymbols);
		mapElements.add(attributes);
		mapElements.add(overlaySymbol);
		mapElements.add(placeNames);
	}

	/**
	 * Generate and return the map image. Will only be generated once.
	 * 
	 * @throws IOException if some IO error occurs when reading in the map info.
	 */
	protected BufferedImage getMapImage() throws IOException {
		if (map == null) {
			Dimension d = getLayout().getBoardSize();
			map = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = map.createGraphics();
			
			// set background color
			g.setBackground(tableColor);
			g.clearRect(0, 0, d.width, d.height);
			
			// See if map image file exists
			File sml = action.getCaseInsensitiveFile(new File(forceExtension(path, "sml")), null, false, null);
			if (sml != null) 
				readScannedMapLayoutFile(sml, g);
			else {
				// If sml file doesn't exist, see if there is a single-sheet underlay image
				File underlay = action.getCaseInsensitiveFile(new File(stripExtension(path) + "-Z" + (zoomLevel+1) + ".bmp"), 
						null, false, null);
				if (underlay != null) {
					BufferedImage img = ImageIO.read(underlay);
					g.drawImage(img, null, 0, 0);
				}
			}
			
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

			// go through the list of drawable elements and put them on after getting the (optional) scanned sheets.
			for (ArrayList<? extends MapDrawable> list : mapElements)
				for (MapDrawable element : list)
					element.draw(g);
		}
		return map;
	}

	/**
	 * Many maps are actually just scanned images held in a separate file. The images are often broken up into
	 * sections. An extra file describes how the images are pasted together. This function pieces the images
	 * together using the <code>Graphics2D</code> object <code>g</code>.
	 */
	protected void readScannedMapLayoutFile(File f, Graphics2D g) throws IOException {		
		DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
		// how many image sections
		int nSheets = ADC2Utils.readBase250Word(in);
		for (int i = 0; i < nSheets; ++i) {
			// image file name
			String name = stripExtension(readWindowsFileName(in));
			File file = action.getCaseInsensitiveFile(new File(name + "-L" + (zoomLevel+1) + ".bmp"), new File(path), true, null);
			BufferedImage img = ImageIO.read(file);
			int x = 0;
			int y = 0;
			for (int j = 0; j < 3; ++j) {
				int tempx = ADC2Utils.readBase250Integer(in);
				int tempy = ADC2Utils.readBase250Integer(in);
				if (j == zoomLevel) {
					x = tempx;
					y = tempy;
				}
			}
			g.drawImage(img, null, x, y);
		}
	}

	/**
	 * Read symbol that is drawn on all hexes.
	 */
	protected void readMapBoardOverlaySymbolBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "MapBoard Overlay Symbol");
		
		SymbolSet.SymbolData overlaySymbol = getSet().getMapBoardSymbol(ADC2Utils.readBase250Word(in));
		// the reason we use an ArrayList<> here is so that it is treated like all other drawn elements
		if (overlaySymbol != null)
			this.overlaySymbol.add(new MapBoardOverlay(overlaySymbol));
	}

	/**
	 * Block of flags to indicate which elements are actually displayed.
	 */
	protected void readMapItemDrawFlagBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "Map Item Draw Flag");

		// obviously, element types can't be sorted before we do this.
		if (in.readByte() == 0)
			mapElements.remove(primaryMapBoardSymbols);
		if (in.readByte() == 0)
			mapElements.remove(secondaryMapBoardSymbols);
		if (in.readByte() == 0)
			mapElements.remove(hexSides);
		if (in.readByte() == 0)
			mapElements.remove(hexLines);
		if (in.readByte() == 0)
			mapElements.remove(placeSymbols);
		if (in.readByte() == 0)
			mapElements.remove(placeNames);
		if (in.readByte() == 0)
			mapElements.remove(attributes);
	}

	/**
	 * Attributes are tertiary symbols, any number of which can be attached to a specific hex.  Otherwise, they
	 * function the same as primary or secondary hex symbols.
	 */
	protected void readAttributeBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "Attribute Symbol");

		int nAttributes = ADC2Utils.readBase250Word(in);
		for (int i = 0; i < nAttributes; ++i) {
			int index = ADC2Utils.readBase250Word(in);
			SymbolSet.SymbolData symbol = set.getMapBoardSymbol(ADC2Utils.readBase250Word(in));
			if (isOnMapBoard(index) && symbol != null)
				attributes.add(new HexData(index, symbol));
		}
	}

	/**
	 * Read primary and secondary symbol information. Each hex may only have one of each. Additional symbols must
	 * be tertiary attributes.
	 */
	protected void readHexDataBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "Hex Data");

		int count = getNColumns() * getNRows();

		for (int i = 0; i < count; ++i) {
			// primary symbol
			int symbolIndex = ADC2Utils.readBase250Word(in);
			SymbolSet.SymbolData symbol = getSet().getMapBoardSymbol(symbolIndex);
			if (symbol != null)
				primaryMapBoardSymbols.add(new HexData(i, symbol));

			// secondary symbol
			symbolIndex = ADC2Utils.readBase250Word(in);
			symbol = getSet().getMapBoardSymbol(symbolIndex);
			if (symbol != null)
				secondaryMapBoardSymbols.add(new HexData(i, symbol));

			/* int elevation = */ ADC2Utils.readBase250Word(in);
			
			// flags for hexsides, lines, and placenames: completely ignored
			/* int additionalInformation = */ in.readUnsignedByte();
		}
	}

	/**
	 * Hex lines are like spokes of the hex and are typically used for things like roads or other elements that
	 * traverse from hex to hex.  The direction of each spoke is encoded as bit flags, and while ADC2 could encode
	 * each hex with only one record, modules typically have a separate record for every spoke resulting in
	 * data inflation.
	 */
	protected void readHexLineBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "Hex Line");

		int nHexLines = ADC2Utils.readBase250Word(in);
		for (int i = 0; i < nHexLines; ++i) {
			int index = ADC2Utils.readBase250Word(in);
			int line = in.readUnsignedByte();
			int direction = in.readUnsignedShort();
			if (isOnMapBoard(index))
				hexLines.add(new HexLine(index, line, direction));
		}
	}

	/**
	 * Information about hex sides which are used for things like rivers, etc. is read in.
	 */
	protected void readHexSideBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "Hex Side");

		int nHexSides = ADC2Utils.readBase250Word(in);
		for (int i = 0; i < nHexSides; ++i) {
			int index = ADC2Utils.readBase250Word(in);
			int line = in.readUnsignedByte();
			int side = in.readUnsignedByte();
			if (isOnMapBoard(index))
				hexSides.add(new HexSide(index, line, side));
		}
	}

	/**
	 * Information about the width, colour and style of hex sides and hex lines is read in.
	 */
	protected void readLineDefinitionBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "Line Definition");

		int nLineDefinitions = in.readUnsignedByte();
		lineDefinitions = new LineDefinition[nLineDefinitions];
		for (int i = 0; i < nLineDefinitions; ++i) {
			int colorIndex = in.readUnsignedByte();
			Color color = getColorFromIndex(colorIndex);
			int size = 0;
			for (int j = 0; j < 3; ++j) {
				int s = in.readByte();
				if (j == zoomLevel)
					size = s;
			}
			// only used when editing ADC2 maps within ADC2.
			/* String name = */ readNullTerminatedString(in);
			int styleByte = in.readUnsignedByte();
			LineStyle style;
			switch (styleByte) {
			case 2:
				style = LineStyle.DOTTED;
				break;
			case 3:
				style = LineStyle.DASH_DOT;
				break;
			case 4:
				style = LineStyle.DASHED;
				break;
			case 5:
				style = LineStyle.DASH_DOT_DOT;
				break;
			default:
				style = LineStyle.SOLID;
			}
			
			if (size > 0)
				lineDefinitions[i] = new LineDefinition(color, size, style);
			else
				lineDefinitions[i] = null;
		}
	}

	/**
	 * Read what order to draw the lines in.
	 */
	protected void readLineDrawPriorityBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "Line Draw Priority");

		in.readByte(); // unused.

		// there can only be 10 line definitions. however drawning priorities for hex sides and hex lines
		// are completely independent
		for (int i = 1; i <= 10; ++i) {
			int index = in.readUnsignedByte();
			if (index < lineDefinitions.length && lineDefinitions[index] != null)
				lineDefinitions[index].setHexLineDrawPriority(i);
		}

		for (int i = 1; i <= 10; ++i) {
			int index = in.readUnsignedByte();
			if (index < lineDefinitions.length && lineDefinitions[index] != null)
				lineDefinitions[index].setHexSideDrawPriority(i);
		}
	}

	/**
	 * Read information on hex numbering.
	 */
	protected void readMapSheetBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "Map Sheet");

		int nMapSheets = ADC2Utils.readBase250Word(in);
		for (int i = 0; i < nMapSheets; ++i) {
			int x1 = ADC2Utils.readBase250Word(in);
			int y1 = ADC2Utils.readBase250Word(in);
			int x2 = ADC2Utils.readBase250Word(in);
			int y2 = ADC2Utils.readBase250Word(in);
			Rectangle r = new Rectangle(x1, y1, x2 - x1 + 1, y2 - y1 + 1);
			// must be exactly 9 bytes or 10 if there's a terminating null at the end
			String name = readNullTerminatedString(in, 10);
			if (name.length() < 9)
				in.read(new byte[9 - name.length()]);
			int style = in.readUnsignedByte();
			in.read(new byte[2]);
			int nColChars = in.readUnsignedByte();
			int nRowChars = in.readUnsignedByte();
			if (i < nMapSheets-1) // the last one is always ignored.
				mapSheets.add(new MapSheet(name, r, style, nColChars, nRowChars));
		}
	}

	/**
	 * Read in information on hex sheets which define the hex numbering systems. This represents supplemental
	 * information--some map sheet info occurs earlier in the file.  Only the coordinates of the top-left corner
	 * are read in here.  
	 */
	protected void readHexNumberingBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "Hex Numbering");
		
		for (int i = 0; i < mapSheets.size()+1; ++i) {
			// rare case when integers are not base-250. However, they are big-endian despite being a
			// Windows application.
			int col = 0;
			for (int j = 0; j < 4; ++j) { 
				col <<= 8;
				col += in.readUnsignedByte();
			}
			int row = 0;
			for (int j = 0; j < 4; ++j) {
				row <<= 8;
				row += in.readUnsignedByte();
			}
			if (i < mapSheets.size()) {
				MapSheet ms = mapSheets.get(i);
				ms.setTopLeftCol(col);
				ms.setTopLeftRow(row);
			}
		}
	}

	/**
	 * Read and set the order of the drawn element types.
	 */
	protected void readMapItemDrawingOrderBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "Map Item Drawing Order");
		
		byte[] priority = new byte[10];
		in.read(priority);
		ArrayList<ArrayList<? extends MapDrawable>> items = new ArrayList<ArrayList<? extends MapDrawable>>(mapElements.size());
		for (int i = 0; i < mapElements.size(); ++i) {

			// invalid index: abort reordering and switch back to default
			if (priority[i] >= mapElements.size() || priority[i] < 0)
				return;
				
			if (i > 0) {
				// abort reordering and switch back to default if any indeces are repeated
				for (int j = 0; j < i; ++j) {
					if (priority[j] == priority[i])
						return;
				}
			}

			items.add(mapElements.get(priority[i]));
		}
		
		// swap default order with specified order
		mapElements = items;
	}
	
	/**
	 * Crude version information.  Comes near the end of the file!  Actually it's just a flag to indicate whether
	 * the version is < 2.08.  In version 2.08, the hexes are abutted slightly differently.
	 */
	protected void readVersionBlock(DataInputStream in) throws IOException{
		ADC2Utils.readBlockHeader(in, "File Format Version");
		
		int version = in.readByte();
		isPreV208 = version != 0;
	}

	/**
	 * The colour to fill before any elements are drawn. The fast-scroll flag is also read.
	 */
	protected void readTableColorBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "Table Color");
		
		/* int fastScrollFlag = */ in.readByte();
		tableColor = getColorFromIndex(in.readUnsignedByte());
	}
	
	/**
	 * Optional labels that can be added to hexes.  Can also include a symbol that can be added with the label.
	 */
	protected void readPlaceNameBlock(DataInputStream in) throws IOException {
		ADC2Utils.readBlockHeader(in, "Place Name");

		int nNames = ADC2Utils.readBase250Word(in);
		for (int i = 0; i < nNames; ++i) {
			int index = ADC2Utils.readBase250Word(in);
			// extra hex symbol
			SymbolSet.SymbolData symbol = getSet().getMapBoardSymbol(ADC2Utils.readBase250Word(in));
			if (symbol != null && isOnMapBoard(index))
				placeSymbols.add(new HexData(index, symbol));
			String text = readNullTerminatedString(in, 25);
			Color color = getColorFromIndex(in.readUnsignedByte());

			int size = 0;
			for (int z = 0; z < 3; ++z) {
				int b = in.readUnsignedByte();
				if (z == zoomLevel)
					size = b;
			}

			PlaceNameOrientation orientation = null;
			for (int z = 0; z < 3; ++z) {
				int o = in.readByte();
				if (z == zoomLevel) {
					switch (o) {
					case 1:
						orientation = PlaceNameOrientation.LOWER_CENTER;
						break;
					case 2:
						orientation = PlaceNameOrientation.UPPER_CENTER;
						break;
					case 3:
						orientation = PlaceNameOrientation.LOWER_RIGHT;
						break;
					case 4:
						orientation = PlaceNameOrientation.UPPER_RIGHT;
						break;
					case 5:
						orientation = PlaceNameOrientation.UPPER_LEFT;
						break;
					case 6:
						orientation = PlaceNameOrientation.LOWER_LEFT;
						break;
					case 7:
						orientation = PlaceNameOrientation.CENTER_LEFT;
						break;
					case 8:
						orientation = PlaceNameOrientation.CENTER_RIGHT;
						break;
					case 9:
						orientation = PlaceNameOrientation.HEX_CENTER;
						break;
					}
				}
			}

			int font = in.readUnsignedByte();

			if (!isOnMapBoard(index) || text.length() == 0 || orientation == null)
				continue;

			placeNames.add(new PlaceName(index, text, color, orientation, size, font));
		}
	}
	
	/**
	 * @param index Index of hex or square in row-major order starting with the upper-left-hand corner (0-based).
	 * @return <code>true</code> if <code>index</code> is valid, <code>false</code> otherwise.
	 */
	boolean isOnMapBoard(int index) {
		return isOnMapBoard(index % columns, index / columns);
	}

	/**
	 * @param x Hex or square column.
	 * @param y Hex or square row.
	 * @return <code>true</code> if <code>x</code> and <code>y</code> are within the bounds of the board
	 *         <code>false</code> otherwise.
	 */
	boolean isOnMapBoard(int x, int y) {
		return x >= 0 && x < columns && y >= 0 && y < rows;
	}

	/**
	 * @return The Layout object corresponding to this imported map.
	 */
	protected Layout getLayout() {
		return layout;
	}

	/**
	 * Returns the LineDefinition object corresponding to the given index.
	 */
	protected LineDefinition getLineDefinition(int index) {
		if (index < 0 | index >= lineDefinitions.length)
			return null;
		else
			return lineDefinitions[index];
	}

	/**
	 * @return Number of columns in the map.
	 */
	int getNColumns() {
		return columns;
	}

	/**
	 * @return Number of rows in the map.
	 */
	int getNRows() {
		return rows;
	}

	/**
	 * @return The <code>SymbolSet</code> needed by this map to render terrain and attribute elements.
	 */
	SymbolSet getSet() {
		return set;
	}

	@Override
	protected void load(File f) throws IOException {
		DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));

		baseName = stripExtension(f.getName());
		path = f.getPath();
		int header = in.readByte();
		if (header != -3)
			throw new FileFormatException("Invalid Mapboard File Header");

		// don't know what these do.
		in.read(new byte[2]);

		// get the symbol set
		String s = readWindowsFileName(in);
		String symbolSetFileName = forceExtension(s, "set");
		set = new SymbolSet();
		set.importFile(action, action.getCaseInsensitiveFile(new File(symbolSetFileName), f, true, null));

		in.readByte(); // ignored

		columns = ADC2Utils.readBase250Word(in);
		rows = ADC2Utils.readBase250Word(in);
		// presumably, they're all the same size (and they're square)
		int hexSize = set.getMapBoardSymbolSize();

		// each block read separately
		readHexDataBlock(in);
		readPlaceNameBlock(in);
		readHexSideBlock(in);
		readLineDefinitionBlock(in);
		readAttributeBlock(in);
		readMapSheetBlock(in);
		readHexLineBlock(in);
		readLineDrawPriorityBlock(in);
		// end of data blocks
		
		int orientation = in.read();
		switch(orientation) {
		case 0:
		case 1: // vertical hex orientation or grid offset column
			if (set.getMapBoardSymbolShape() == SymbolSet.Shape.SQUARE)
				layout = new GridOffsetColumnLayout(hexSize, columns, rows);
			else
				layout = new VerticalHexLayout(hexSize, columns, rows);
			break;
		case 2: // horizontal hex orientation or grid offset row
			if (set.getMapBoardSymbolShape() == SymbolSet.Shape.SQUARE)
				layout = new GridOffsetRowLayout(hexSize, columns, rows);
			else
				layout = new HorizontalHexLayout(hexSize, columns, rows);
			break;
		default: // square grid -- no offset
			layout = new GridLayout(hexSize, columns, rows);
		}
		
		/* int saveMapPosition = */ in.readByte();
		
		/* int mapViewingPosition = */ in.readShort(); // probably base-250
		
		/* int mapViewingZoomLevel = */ in.readShort(); 
		
		in.readByte(); // totally unknown
		
		// strangely, more blocks
		readTableColorBlock(in);
		readHexNumberingBlock(in); 

		try { // optional blocks
			readMapBoardOverlaySymbolBlock(in);
			readVersionBlock(in);
			readMapItemDrawingOrderBlock(in);
			readMapItemDrawFlagBlock(in);
		} catch(EOFException e) {}
	}

	/**
	 * @return The VASSAL Map object corresponding to this imported map.
	 */
	Map getMainMap() {
		if (mainMap == null)
			mainMap = GameModule.getGameModule().getAllDescendantComponentsOf(Map.class).toArray(new Map[0])[0];
		return mainMap;
	}
	
	/**
	 * @return How many sides does each hex (6) or square (4) have?
	 */
	int getNFaces() {
		return getLayout().getNFaces();
	}

	/**
	 * @return The point corresponding to the hex centre relative to the upper-left-hand corner.
	 */
	Point getCenterOffset() {
		return getLayout().getOrigin();
	}
	
	/**
	 * Given a row and column for a hex, return the point corresponding to the upper left-hand pixel.
	 */
	Point coordinatesToPosition(int x, int y) {
		return getLayout().coordinatesToPosition(x, y, true);
	}

	/**
	 * @param index Hex index in row-major order starting with the upper-left-hand corner (0-based).
	 * @return upper-left-hand point of the hex or square. Returns <code>null</code> if the index is not valid.
	 */
	Point indexToPosition(int index) {
		return getLayout().coordinatesToPosition(index % columns, index / columns, true);
	}

	/**
	 * @param index hex index in row-major order starting with the upper-left-hand corner (0-based).
	 * @param nullIfOffBoard return <code>null</code> if not a valid index. If <code>false</code> will
	 *                       return the point corresponding to the index if it were valid. 
	 * @return Point corresponding to the upper left hand corner of the hex or square.
	 */
	Point indexToPosition(int index, boolean nullIfOffBoard) {
		return getLayout().coordinatesToPosition(index % columns, index / columns,
				nullIfOffBoard);
	}
	
	/**
	 * @param index The hex index in row major order starting with the upper-left-hand corner (0-based).
	 * @return <code>Point</code> corresponding to the centre of that hex.
	 */
	Point indexToCenterPosition(int index) {
		// get upper-left-hand corner of the hex
		Point p = indexToPosition(index);
		if (p == null)
			return p;
		// shift to the centre
		p.translate(getLayout().getDeltaX()/2, getLayout().getDeltaY()/2);
		return p;
	}
	
	@Override
	public void writeToArchive() throws IOException {

		GameModule module = GameModule.getGameModule();

		// write the map image
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(getMapImage(), "png", out);
		byte[] imageDataArray = out.toByteArray();
		module.getArchiveWriter().addImage(baseName + ".png", imageDataArray);

		// map options: log formats
		getMainMap().setAttribute(Map.MOVE_WITHIN_FORMAT, "$pieceName$ moving from [$previousLocation$] to [$location$]");
		getMainMap().setAttribute(Map.MOVE_TO_FORMAT, "$pieceName$ moving from [$previousLocation$] to [$location$]");
		getMainMap().setAttribute(Map.CREATE_FORMAT, "$pieceName$ Added to [$location$]");

		boardPicker = getBoardPicker();
		board = new Board();
		board.addTo(boardPicker);
		boardPicker.add(board);
		board.setAttribute(Board.IMAGE, baseName + ".png");
		// board.setAttribute(Board.REVERSIBLE, Boolean.TRUE);
		board.setConfigureName(baseName);
		
		// so we can get hex labels
		getMainMap().setBoards(Collections.singleton(board));

		// default grid
		AbstractConfigurable ac = getLayout().getGeometricGrid();
	
		// ensure that we don't have a singleton null
		if (mapSheets.size() == 1 && mapSheets.get(0) == null)
			mapSheets.remove(0);
		
		// setup grids defined by ADC module
		if (mapSheets.size() > 0) {
			ZonedGrid zg = new ZonedGrid();
			for (MapSheet ms : mapSheets) {
				if (ms == null) // the last one is always null
					break;
				Zone z = ms.getZone();
				if (z != null) {
					z.addTo(zg);
					zg.add(z);
				}
			}

			// add default grid
			ac.addTo(zg);
			zg.add(ac);

			// add zoned grid to board
			zg.addTo(board);
			board.add(zg);
		} else {
			// add the default grid to the board
			ac.addTo(board);
			board.add(ac);
		}

		/* global properties */

		// for testing purposes
		GlobalOptions options = module.getAllDescendantComponentsOf(GlobalOptions.class).toArray(new GlobalOptions[0])[0];
		options.setAttribute(GlobalOptions.AUTO_REPORT, GlobalOptions.ALWAYS);

		// add zoom capability
		if (zoomLevel > 0) {
			Zoomer zoom = new Zoomer();
			String[] s = new String[3];
			for (int i = 0; i < 3; ++i)
				s[i] = Double.toString(set.getZoomFactor(i));
			zoom.setAttribute("zoomLevels", StringArrayConfigurer.arrayToString(s));
			zoom.addTo(mainMap);
			getMainMap().add(zoom);
		}
		
		// add place name capability
		if (placeNames.size() > 0) {
			writePlaceNames(module);
		}
		
		// set up inventory button
		final Inventory inv = new Inventory();
		inv.build(null);			
		inv.addTo(module);
		module.add(inv);		
		inv.setAttribute(Inventory.BUTTON_TEXT, "Search");
		inv.setAttribute(Inventory.TOOLTIP, "Find place by name");
		inv.setAttribute(Inventory.FILTER, "CurrentMap = Main Map");
		inv.setAttribute(Inventory.ICON, "");
		inv.setAttribute(Inventory.GROUP_BY, "Type");
	}

	/**
	 * Write out place name information as non-stackable pieces which can be searched via
	 * the piece inventory.
	 * 
	 * @param module - Game module to write to.
	 */
	protected void writePlaceNames(GameModule module) {
		// write prototype
		PrototypesContainer container = module.getAllDescendantComponentsOf(PrototypesContainer.class).iterator().next();
		PrototypeDefinition def = new PrototypeDefinition();
		def.addTo(container);
		container.add(def);
		def.setConfigureName(PLACE_NAMES);
		
		GamePiece gp = new BasicPiece();
		SequenceEncoder se = new SequenceEncoder(',');
		se.append(ADC2Utils.TYPE);
		gp = new Marker(Marker.ID + se.getValue(), gp);
		gp.setProperty(ADC2Utils.TYPE, PLACE_NAME);
		gp = new Immobilized(gp, Immobilized.ID + "n;V"); 		
		def.setPiece(gp);
		
		// write place names as pieces with no image.
		getMainMap();
		final Point offset = getCenterOffset();
		final HashSet<String> set = new HashSet<String>();
		
		for (PlaceName pn : placeNames) {
			String name = pn.getText();
			Point p = pn.getPosition();
			if (p == null)
				continue;
			if (set.contains(name))
				continue;
			set.add(name);
			SetupStack stack = new SetupStack();
			stack.addTo(mainMap);
			mainMap.add(stack);
			p.translate(offset.x, offset.y);
			String location = mainMap.locationName(p);
			stack.setAttribute(SetupStack.NAME, name);
			stack.setAttribute(SetupStack.OWNING_BOARD, board.getConfigureName());
			
			MapGrid mg = board.getGrid();
			Zone z = null;
			if (mg instanceof ZonedGrid)
				z = ((ZonedGrid) mg).findZone(p);				
			stack.setAttribute(SetupStack.X_POSITION, Integer.toString(p.x));
			stack.setAttribute(SetupStack.Y_POSITION, Integer.toString(p.y));
			if (z != null) {
				try {
					if (mg.getLocation(location) != null) {						
						assert(mg.locationName(mg.getLocation(location)).equals(location));
						stack.setAttribute(SetupStack.USE_GRID_LOCATION, true);
						stack.setAttribute(SetupStack.LOCATION, location);
					}
				}
				catch(BadCoords e) {}
			}			
			
			BasicPiece bp = new BasicPiece();
			se = new SequenceEncoder(BasicPiece.ID, ';');
			se.append("").append("").append("").append(name);
			bp.mySetType(se.getValue());			
			
			se = new SequenceEncoder(UsePrototype.ID.replaceAll(";", ""), ';');
			se.append(PLACE_NAMES);
			gp = new UsePrototype(se.getValue(), bp);
			
			PieceSlot ps = new PieceSlot(gp);
			ps.addTo(stack);
			stack.add(ps);
		}
	}

	/**
	 * Does this map board use old-style hex spacing?
	 * 
	 * @return <code>true</code> if this board is pre version 2.08, <code>false</code> if V2.08 or later.
	 */
	boolean isPreV208Layout() {
		return isPreV208;
	}

	/**
	 * @return The VASSAL board object corresponding to the imported map.
	 */
	Board getBoard() {
		return board;
	}

	/**
	 * @return The map background colour.
	 */
	Color getTableColor() {
		return tableColor;
	}

	/**
	 * @return The VASSAL BoardPicker object corresponding to this imported map.
	 */
	BoardPicker getBoardPicker() {
		if (boardPicker == null)
			boardPicker = getMainMap().getAllDescendantComponentsOf(BoardPicker.class).toArray(new BoardPicker[0])[0];
		return boardPicker;
	}
}
