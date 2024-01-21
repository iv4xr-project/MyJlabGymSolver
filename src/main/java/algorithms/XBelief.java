package algorithms;

import java.util.*;
import java.util.stream.Collectors;

import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.spatial.LineIntersectable;
import eu.iv4xr.framework.spatial.Obstacle;
import eu.iv4xr.framework.spatial.Vec3;
import nl.uu.cs.aplib.utils.Pair;
import world.BeliefState;
import world.LabEntity;

public class XBelief extends BeliefState {
	
		
	
	
	public enum LinkStatus { LINKED, NOCONNECTON, UNKNOWN }
	
	public Rooms rooms ;
	
	/**
	 * Mapping pairs (button,door) to the connectivity between them.
	 */
	public Map<Pair<String,String>,LinkStatus> connectionsModel = new HashMap<>() ;
	
	
	public XBelief() {
		super() ;
		rooms = new Rooms(this) ;
	}
	
	public void registerButton(String button) {
		boolean added = false ;
		for(WorldEntity d : this.knownDoors()) {
			Pair<String,String> con = new Pair(button, d.id) ;
			LinkStatus st = connectionsModel.get(con) ;
			if (st==null) {
				connectionsModel.put(con, LinkStatus.UNKNOWN) ;
				added = true ;
			}	
		}
		rooms.registerButton(button);
		if(added) {
		   DebugUtil.log(">>>>> registering " + button) ;
		}
	}
	
	public void registerDoor(String door) {
		boolean added = false ;
		for(WorldEntity b : this.knownButtons()) {
			Pair<String,String> con = new Pair(b.id, door) ;
			LinkStatus st = connectionsModel.get(con) ;
			if (st==null) {
				connectionsModel.put(con,LinkStatus.UNKNOWN) ;
				added = true ;
			}
		}
		rooms.registerDoor(door);
		if(added) {
			DebugUtil.log(">>>>> registering " + door) ;
		}
	}
	

	
	public void registerConnection(String button, String door) {
		var link = new Pair(button,door) ;
		LinkStatus currentStatus = connectionsModel.get(link) ;
		connectionsModel.put(link, LinkStatus.LINKED) ;
		DebugUtil.log(">>>>> registering connection " + button + " -> " 
		   + door 
		   + (currentStatus == LinkStatus.NOCONNECTON ? " (conflict: was NON-connection!)" : "")) ;
		
	}

	public void registerNONConnection(String button, String door) {
		var link = new Pair(button,door) ;
		LinkStatus currentStatus = connectionsModel.get(link) ;
		if(currentStatus == LinkStatus.LINKED) {
			DebugUtil.log(">>>>> IGNORING reported NON-connection " + button 
					  + " X " + door + " because it is already marked as connected.") ;
			return ;
		}
		connectionsModel.put(link, LinkStatus.NOCONNECTON) ;
		DebugUtil.log(">>>>> registering NON-connection " 
		          + button + " X " + door
		          + (currentStatus == LinkStatus.LINKED ? " (conflict: was LINKED!)" : "")) ;
	}
	
	public List<String> getConnectedButtons(String door) {
		return connectionsModel.entrySet().stream()
		   .filter(L -> L.getKey().snd.equals(door) && L.getValue() == LinkStatus.LINKED)
		   .map(L -> L.getKey().fst)
		   .collect(Collectors.toList()) ;
	}
	
	public List<String> getUnexploredButtons(String door) {
		return connectionsModel.entrySet().stream()
				   .filter(L -> L.getKey().snd.equals(door) && L.getValue() == LinkStatus.UNKNOWN)
				   .map(L -> L.getKey().fst)
				   .collect(Collectors.toList()) ;
	}
	
	
	void fakelyUnblockDoor(String door) {
		for(Obstacle<LineIntersectable> o : pathfinder.obstacles) {
			LabEntity e = (LabEntity) o.obstacle ;
			if (e.id == door) {
				o.isBlocking = false ;
				return ;
			}
		}
	}
	
	void restoreObstacleState(String door, boolean originalState) {
		for(Obstacle<LineIntersectable> o : pathfinder.obstacles) {
			LabEntity e = (LabEntity) o.obstacle ;
			if (e.id == door) {
				o.isBlocking = originalState ;
				return ;
			}
		}
	}
	
	void fakelyMakeAlldoorsBlocking(String exception) {
		for(Obstacle<LineIntersectable> o : pathfinder.obstacles) {
			LabEntity e = (LabEntity) o.obstacle ;
			if (e.type == "Door") {
				o.isBlocking = true ;
				if(e.id != exception) {
					e.extent.x += 1f ;
					e.extent.z += 1f ;				
				}
			}
		}
	}
	
	Map<String,Boolean> getDoorsBlockingState() {
		Map<String,Boolean> map = new HashMap<>() ;
		for(Obstacle<LineIntersectable> o : pathfinder.obstacles) {
			LabEntity e = (LabEntity) o.obstacle ;
			if (e.type == "Door") {
				map.put(e.id,o.isBlocking) ;
			}
		}
		return map ;
	}
	
	void restoreDoorsBlockingState(Map<String,Boolean> originalState, String exception) {
		for(Obstacle<LineIntersectable> o : pathfinder.obstacles) {
			LabEntity e = (LabEntity) o.obstacle ;
			if (e.type == "Door") {
				o.isBlocking = originalState.get(e.id) ;
				if(e.id != exception) {
					e.extent.x -= 1f ;
					e.extent.z -= 1f ;				
				}
			}
		}
	}
	
	
	
	public boolean doorIsReachable(String door) {
		LabEntity d = worldmodel.getElement(door) ;
		Boolean isOpen = d.getBooleanProperty("isOpen") ;
		//if(isOpen) {
		//	return findPathTo(d.getFloorPosition(),true) != null ;
		//}
		// pretend that the door is open:
		fakelyUnblockDoor(door) ;
		var entity_location = d.getFloorPosition() ;
		var entity_sqcenter = new Vec3((float) Math.floor((double) entity_location.x - 0.5f) + 1f,
	    		entity_location.y,
	    		(float) Math.floor((double) entity_location.z - 0.5f) + 1f) ;
		var path = findPathTo(entity_sqcenter,true) ;
		// restore the original state:
		restoreObstacleState(door,! isOpen) ;
		return path != null ;		
	}
	
	boolean buttonIsReachable(String button) {
		LabEntity b = worldmodel.getElement(button) ;
		var path = findPathTo(b.getFloorPosition(),true) ;
		return path != null ;
	}
	
	/*.
	 * Given an unreachable door d, find a closed door d2 that if it is open would make
	 * d reachable.
	 */
	String findAEnablingClosedDoor(String entity) {
		LabEntity e = worldmodel.getElement(entity) ;
		boolean isDoor = e.type.equals("Door") ;
		for(WorldEntity d2 : knownDoors()) {
			if (d2.id.equals(entity)) continue ;
			if(!isOpen(d2) && doorIsReachable(d2.id)) {
				d2.properties.put("isOpen",true) ;
				if( isDoor ? doorIsReachable(entity) : buttonIsReachable(entity)) {
					d2.properties.put("isOpen",false) ;
					return d2.id ;
				}
				d2.properties.put("isOpen",false) ;
			}
		}
		return null ;
	}
	
	/**
	 * Return the set of known connections between buttons and doors.
	 */
	public Set<Pair<String,String>> getConnections() {
		return connectionsModel.entrySet().stream()
		   .filter(E -> E.getValue() == LinkStatus.LINKED)
		   .map(E -> E.getKey())
		   .collect(Collectors.toSet());
	}
	
}
