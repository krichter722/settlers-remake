/*******************************************************************************
 * Copyright (c) 2015
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package jsettlers.mapcreator.tools.objects;

import jsettlers.common.buildings.EBuildingType;
import jsettlers.common.map.object.BuildingObject;
import jsettlers.common.map.object.MapObject;
import jsettlers.common.map.object.MovableObject;
import jsettlers.common.movable.EMovableType;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.mapcreator.data.MapData;
import jsettlers.mapcreator.localization.EditorLabels;
import jsettlers.mapcreator.main.IPlayerSetter;
import jsettlers.mapcreator.tools.Tool;
import jsettlers.mapcreator.tools.shapes.PointShape;
import jsettlers.mapcreator.tools.shapes.ShapeType;

public class PlaceTemplateTool implements Tool {
	private final String name;
	private final TemplateObject[] objects;
	private final IPlayerSetter player;

	public PlaceTemplateTool(String name, TemplateObject[] objects, IPlayerSetter player) {
		this.name = name;
		this.objects = objects;
		this.player = player;
	}

	@Override
	public String getName() {
		return String.format(EditorLabels.getLabel("templatedescr"), name);
	}

	@Override
	public ShapeType[] getShapes() {
		return new ShapeType[] { new PointShape() };
	}

	private void addAround(MapData map, ShortPoint2D start) {
		for (TemplateObject object : objects) {
			int x = start.x + object.getDx();
			int y = start.y + object.getDy();
			map.placeObject(object.getObject(player.getActivePlayer()), x, y);
		}
	}

	public static class TemplateObject {
		private final int dx;
		private final int dy;
		private final MapObject object;

		public TemplateObject(int dx, int dy, MapObject object) {
			this.dx = dx;
			this.dy = dy;
			this.object = object;
		}

		public int getDx() {
			return dx;
		}

		public int getDy() {
			return dy;
		}

		/**
		 * Gets the object to be placed.
		 * 
		 * @param player
		 *            The current player
		 * @return
		 */
		public MapObject getObject(byte player) {
			return object;
		}
	}

	public static class TemplateBuilding extends TemplateObject {
		private final EBuildingType type;

		public TemplateBuilding(int dx, int dy, EBuildingType type) {
			super(dx, dy, null);
			this.type = type;
		}

		@Override
		public MapObject getObject(byte player) {
			return new BuildingObject(type, player);
		}
	}

	public static class TemplateMovable extends TemplateObject {
		private final EMovableType type;

		public TemplateMovable(int dx, int dy, EMovableType type) {
			super(dx, dy, null);
			this.type = type;
		}

		@Override
		public MapObject getObject(byte player) {
			return new MovableObject(type, player);
		}
	}

	@Override
	public void start(MapData data, ShapeType shape, ShortPoint2D pos) {
		addAround(data, pos);
	}

	@Override
	public void apply(MapData map, ShapeType shape, ShortPoint2D start, ShortPoint2D end, double uidx) {
	}

}
