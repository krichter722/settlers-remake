package jsettlers.logic.objects;

import java.util.PriorityQueue;

import jsettlers.common.landscape.ELandscapeType;
import jsettlers.common.map.shapes.MapNeighboursArea;
import jsettlers.common.map.shapes.MapShapeFilter;
import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.material.EMaterialType;
import jsettlers.common.material.ESearchType;
import jsettlers.common.position.ISPosition2D;
import jsettlers.common.position.RelativePoint;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.logic.map.newGrid.interfaces.AbstractHexMapObject;
import jsettlers.logic.map.newGrid.interfaces.IHexMovable;
import jsettlers.logic.objects.arrow.ArrowObject;
import jsettlers.logic.objects.building.BuildingWorkAreaMarkObject;
import jsettlers.logic.objects.building.ConstructionMarkObject;
import jsettlers.logic.objects.corn.Corn;
import jsettlers.logic.objects.stack.StackMapObject;
import jsettlers.logic.objects.stone.Stone;
import jsettlers.logic.objects.tree.Tree;
import jsettlers.logic.timer.ITimerable;
import jsettlers.logic.timer.Timer100Milli;
import synchronic.timer.NetworkTimer;

/**
 * This class manages the MapObjects on the grid. It handles timed events like growth interrupts of a tree or deletion of arrows.
 * 
 * @author Andreas Eberle
 * 
 */
public class MapObjectsManager implements ITimerable {
	private final IMapObjectsManagerGrid grid;
	private final PriorityQueue<TimeEvent> timingQueue = new PriorityQueue<TimeEvent>();

	public MapObjectsManager(IMapObjectsManagerGrid grid) {
		this.grid = grid;
		Timer100Milli.add(this);
	}

	@Override
	public void timerEvent() {
		int gameTime = NetworkTimer.getGameTime();

		TimeEvent curr = null;
		curr = timingQueue.peek();
		while (curr != null && curr.isOutDated(gameTime)) {
			timingQueue.poll();
			if (curr.shouldRemoveObject()) {
				removeMapObject(curr.mapObject.getPos(), curr.mapObject);
			} else {
				curr.getMapObject().changeState();
			}

			curr = timingQueue.peek();
		}

	}

	@Override
	public void kill() {
		Timer100Milli.remove(this);
	}

	public boolean executeSearchType(ISPosition2D pos, ESearchType type) {
		switch (type) {
		case CUTTABLE_TREE:
			return cutTree(pos);

		case CUTTABLE_STONE:
			cutStone(pos);
			return true;

		case PLANTABLE_TREE:
			return plantTree(new ShortPoint2D(pos.getX(), pos.getY() + 1));

		case CUTTABLE_CORN:
			return cutCorn(pos);

		case PLANTABLE_CORN:
			return plantCorn(pos);

		default:
			System.err.println("can't handle search type in executeSearchType(): " + type);
			return false;
		}
	}

	private void cutStone(ISPosition2D pos) {
		IMapObjectsManagerTile tile = getTile((short) (pos.getX() - 2), (short) (pos.getY() - 1));
		AbstractHexMapObject stone = tile.getMapObject(EMapObjectType.STONE);
		stone.cutOff();

		if (!stone.canBeCut()) {
			addSelfDeletingMapObject(pos, EMapObjectType.CUT_OFF_STONE, Stone.DECOMPOSE_DELAY, (byte) -1);
			tile.removeMapObjectType(EMapObjectType.STONE);
		}
	}

	private boolean plantTree(ISPosition2D pos) {
		Tree tree = new Tree(pos);
		addMapObject(pos, tree);
		timingQueue.offer(new TimeEvent(tree, Tree.GROWTH_DURATION, false));
		return true;
	}

	private boolean plantCorn(ISPosition2D pos) {
		IMapObjectsManagerTile tile = getTile(pos);
		tile.setLandscape(ELandscapeType.EARTH);
		for (ISPosition2D cur : new MapShapeFilter(new MapNeighboursArea(pos), grid.getWidth(), grid.getHeight())) {
			getTile(cur).setLandscape(ELandscapeType.EARTH);
		}
		Corn corn = new Corn(pos);
		addMapObject(pos, corn);
		timingQueue.offer(new TimeEvent(corn, Corn.GROWTH_DURATION, false));
		timingQueue.offer(new TimeEvent(corn, Corn.GROWTH_DURATION + Corn.DECOMPOSE_DURATION, false));
		timingQueue.offer(new TimeEvent(corn, Corn.GROWTH_DURATION + Corn.DECOMPOSE_DURATION + Corn.REMOVE_DURATION, true));
		return true;
	}

	private boolean cutCorn(ISPosition2D pos) {
		IMapObjectsManagerTile tile = getTile(pos);
		if (tile != null) { // TODO remove the cast
			AbstractObjectsManagerObject corn = (AbstractObjectsManagerObject) tile.getMapObject(EMapObjectType.CORN_ADULT);
			if (corn.cutOff()) {
				timingQueue.offer(new TimeEvent(corn, Corn.REMOVE_DURATION, true));
				return true;
			}
		}
		return false;
	}

	private boolean cutTree(ISPosition2D pos) {
		IMapObjectsManagerTile tile = getTile((short) (pos.getX() - 1), (short) (pos.getY() - 1));
		if (tile != null) {
			AbstractObjectsManagerObject tree = (AbstractObjectsManagerObject) tile.getMapObject(EMapObjectType.TREE_ADULT);
			if (tree.cutOff()) {
				timingQueue.offer(new TimeEvent(tree, Tree.DECOMPOSE_DURATION, true));
				return true;
			}
		}
		return false;
	}

	private IMapObjectsManagerTile getTile(short x, short y) {
		return grid.getTile(x, y);
	}

	private IMapObjectsManagerTile getTile(ISPosition2D pos) {
		return grid.getTile(pos.getX(), pos.getY());
	}

	private boolean addMapObject(ISPosition2D pos, AbstractHexMapObject mapObject) {
		for (RelativePoint point : mapObject.getBlockedTiles()) {
			IMapObjectsManagerTile tile = getTile(point.calculatePoint(pos));
			if (tile == null || tile.isBlocked()) {
				return false;
			}
		}

		getTile(pos).addMapObject(mapObject);

		setBlockedForObject(pos, mapObject, true);
		return true;
	}

	public void removeMapObjectType(ISPosition2D pos, EMapObjectType mapObjectType) {
		AbstractHexMapObject removed = getTile(pos).removeMapObjectType(mapObjectType);

		if (removed != null) {
			setBlockedForObject(pos, removed, false);
			AbstractHexMapObject object = getTile(pos).getMapObject(mapObjectType);
		}
	}

	public void removeMapObject(ISPosition2D pos, AbstractHexMapObject mapObject) {
		boolean removed = getTile(pos).removeMapObject(mapObject);

		if (removed) {
			setBlockedForObject(pos, mapObject, false);
		}
	}

	private void setBlockedForObject(ISPosition2D pos, AbstractHexMapObject mapObject, boolean blocked) {
		for (RelativePoint point : mapObject.getBlockedTiles()) {
			IMapObjectsManagerTile tile = getTile(point.calculatePoint(pos));
			if (tile != null) {
				tile.setBlocked(blocked);
			}
		}
	}

	public void addStone(ISPosition2D pos, int capacity) {
		addMapObject(pos, new Stone(capacity));
	}

	public void addArrowObject(IHexMovable enemyPos, ISPosition2D ownPos, float strength) {
		ArrowObject arrow = new ArrowObject(enemyPos, ownPos, strength);
		addMapObject(enemyPos.getPos(), arrow);
		timingQueue.offer(new TimeEvent(arrow, arrow.getEndTime(), false));
		timingQueue.offer(new TimeEvent(arrow, arrow.getEndTime() + ArrowObject.DECOMPOSE_DELAY, true));
	}

	public void addSimpleMapObject(ISPosition2D pos, EMapObjectType objectType, boolean blocking, byte player) {
		addMapObject(pos, new StandardMapObject(objectType, blocking, player));
	}

	public void addBuildingWorkAreaObject(ISPosition2D pos, float progress) {
		addMapObject(pos, new BuildingWorkAreaMarkObject(progress));
	}

	public void addSelfDeletingMapObject(ISPosition2D pos, EMapObjectType mapObjectType, float duration, byte player) {
		SelfDeletingMapObject object = new SelfDeletingMapObject(pos, mapObjectType, duration, player);
		addMapObject(pos, object);
		timingQueue.add(new TimeEvent(object, duration, true));
	}

	private static class TimeEvent implements Comparable<TimeEvent> {
		private final AbstractObjectsManagerObject mapObject;
		private final int eventTime;
		private final boolean shouldRemove;

		/**
		 * 
		 * @param mapObject
		 * @param duration
		 *            in seconds
		 * @param shouldRemove
		 *            if true, the map object will be removed after this event
		 */
		protected TimeEvent(AbstractObjectsManagerObject mapObject, float duration, boolean shouldRemove) {
			this.mapObject = mapObject;
			this.shouldRemove = shouldRemove;
			this.eventTime = (int) (NetworkTimer.getGameTime() + duration * 1000);
		}

		public boolean isOutDated(int gameTime) {
			return gameTime > eventTime;
		}

		private AbstractObjectsManagerObject getMapObject() {
			return mapObject;
		}

		public boolean shouldRemoveObject() {
			return shouldRemove;
		}

		@Override
		public int compareTo(TimeEvent o) {
			return this.eventTime - o.eventTime;
		}

	}

	public void setConstructionMarking(ISPosition2D pos, byte value) {
		IMapObjectsManagerTile tile = grid.getTile(pos.getX(), pos.getY());
		if (value >= 0) {
			ConstructionMarkObject markObject = (ConstructionMarkObject) tile.getMapObject(EMapObjectType.CONSTRUCTION_MARK);
			if (markObject == null) {
				addMapObject(pos, new ConstructionMarkObject(value));
			} else {
				markObject.setConstructionValue(value);
			}
		} else {
			removeMapObjectType(pos, EMapObjectType.CONSTRUCTION_MARK);
		}
	}

	public boolean canPush(ISPosition2D position, EMaterialType material) {
		StackMapObject stackObject = (StackMapObject) grid.getTile(position.getX(), position.getY()).getMapObject(EMapObjectType.STACK_OBJECT);

		return stackObject == null || stackObject.getMaterialType() == material && !stackObject.isFull();
	}

	public boolean pushMaterial(ISPosition2D position, EMaterialType materialType) {
		StackMapObject stackObject = (StackMapObject) grid.getTile(position.getX(), position.getY()).getMapObject(EMapObjectType.STACK_OBJECT);

		if (stackObject == null) {
			grid.getTile(position.getX(), position.getY()).addMapObject(new StackMapObject(materialType, (byte) 1));
			return true;
		} else {
			if (stackObject.getMaterialType() != materialType || stackObject.isFull()) { // TODO reuse empty stack objects
				return false;
			} else {
				stackObject.increment();
				return true;
			}
		}
	}

	public boolean popMaterial(ISPosition2D position, EMaterialType materialType) {
		StackMapObject stackObject = (StackMapObject) grid.getTile(position.getX(), position.getY()).getMapObject(EMapObjectType.STACK_OBJECT);

		if (stackObject == null) {
			return false;
		} else {
			if (stackObject.getMaterialType() != materialType || stackObject.isEmpty()) {
				return false;
			} else {
				stackObject.decrement();
				if (stackObject.isEmpty()) { // remove empty stack object
					removeMapObject(position, stackObject);
				}
				return true;
			}
		}
	}

	public boolean canPop(ISPosition2D position, EMaterialType materialType) {
		StackMapObject stackObject = (StackMapObject) grid.getTile(position.getX(), position.getY()).getMapObject(EMapObjectType.STACK_OBJECT);

		return stackObject != null && stackObject.getMaterialType() == materialType && !stackObject.isEmpty();
	}

	public void addBuildingTo(ISPosition2D position, AbstractHexMapObject newBuilding) {
		addMapObject(position, newBuilding);
	}

}
