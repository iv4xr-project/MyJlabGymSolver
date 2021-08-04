package gameTestingContest;

import static nl.uu.cs.aplib.agents.PrologReasoner.predicate;
import static nl.uu.cs.aplib.agents.PrologReasoner.rule;

import java.util.*;
import java.util.stream.Collectors;

import alice.tuprolog.InvalidTheoryException;
import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.spatial.LineIntersectable;
import eu.iv4xr.framework.spatial.Obstacle;
import eu.iv4xr.framework.spatial.Vec3;
import nl.uu.cs.aplib.agents.PrologReasoner.PredicateName;
import nl.uu.cs.aplib.agents.PrologReasoner.Rule;
import nl.uu.cs.aplib.utils.Pair;
import world.BeliefState;
import world.LabEntity;

import static nl.uu.cs.aplib.agents.PrologReasoner.* ;

public class XBelief extends BeliefState {
	
		
	public XBelief() throws InvalidTheoryException {
		super() ;
		// add prolog, and reasoning rules:
		this.attachProlog() ;
		this.prolog().add(
				neigborRule,
				roomReachableRule1, roomReachableRule2, roomReachableRule3
				) ;
	}

	// Below we define predicates and relations capturing LR-logic:
	
	static PredicateName isRoom = predicate("room") ;
	static PredicateName isButton = predicate("button") ;
	static PredicateName isDoor = predicate("door") ;
	static PredicateName inRoom = predicate("inRoom") ;
	static PredicateName wiredTo = predicate("wiredTo") ;
	static PredicateName notWiredTo = predicate("notWiredTo") ;
	
	static PredicateName neighbor = predicate("neighbor") ;
	
	/**
	 * A rule defining when two rooms are neighboring, namely when they share a door.
	 */
	static Rule neigborRule = rule(neighbor.on("R1","R2"))
			.impBy(isRoom.on("R1"))
			.and(isRoom.on("R2"))
			.and("(R1 \\== R2)") // ok have to use this operator
			.and(isDoor.on("D"))
			.and(isRoom.on("R1","D"))
			.and(isRoom.on("R2","D"))
			;
	static PredicateName roomReachable = predicate("roomReachable") ;
	
	// Below are three rules defining when a room R2 is reachable from a room R1, through
	// k number of edges/doors.
	
	static Rule roomReachableRule1 = rule(roomReachable.on("R1","R2","1"))
			.impBy(neighbor.on("R1","R2"))
			;
	
	static Rule roomReachableRule2 = rule(roomReachable.on("R1","R2","K+1"))
			.impBy(neighbor.on("R1","R"))
			.and(roomReachable.on("R","R2","K"))
			.and("(R1 \\== R2)")
			.and("K > 0")
			;
	static Rule roomReachableRule3 = rule(roomReachable.on("R1","R2","K+1"))
			.impBy(roomReachable.on("R1","R2","K"))
			.and("K > 0")
			;
	
	/**
	 * Execute a prolog query, with var_ as the query variable. Returing a single solution,
	 * or null if there is none.
	 */
	String pQuery(String var_, String q) {
		return prolog().query(q).str_(var_) ;
	}
	
	/**
	 * Test if a prolog query q is successful (has some result).
	 */
	boolean pTest(String q) {
		return prolog().query(q) != null ;
	}
	
	List<String> pQueryAll(String var_, String q) {
		return prolog()
			   . queryAll(q).stream()
			   . map(Q -> Q.str_(var_))
			   . collect(Collectors.toList()) ;
	}
	
	
	// end logic
	

	
	public void registerButton(String button) throws InvalidTheoryException {
		if (pTest(isButton.on(button))) {
			// button is already registered
			return;
		}
		// else this is a new button: 
		prolog().facts(isButton.on(button)) ;
		
		// now check in which room it can be placed:

		Vec3 agentOriginalPosition = worldmodel.position;
		Map<String, Boolean> originalDoorBlockingState = getDoorsBlockingState();
		fakelyMakeAlldoorsBlocking(null);

		var rooms = pQueryAll("R", isRoom.on("R"));
		boolean added = false;
		for (var roomx : rooms) {
			String bt0 = pQuery("B", and(inRoom.on(roomx, "B"), isButton.on("B")));
			if (bt0 != null) {
				// the button can be added if there is another button the the room that is
				// reachable
				// from it:

				WorldEntity b0 = worldmodel.getElement(bt0);
				worldmodel.position = b0.position.copy();
				worldmodel.position.y = agentOriginalPosition.y;

				if (buttonIsReachable(button)) {
					// add the button:
					prolog().facts(inRoom.on(roomx, button));
					added = true;
					break;
				}
			}
		}
		if (!added) {
			// button is in a new room, create the room too:
			String newRoom = "room" + rooms.size();
			prolog().facts(isRoom.on(newRoom), inRoom.on(newRoom, button));
			
			WorldEntity b_ = worldmodel.getElement(button);
			worldmodel.position = b_.position.copy();
			worldmodel.position.y = agentOriginalPosition.y;
			
			// check which doors are connected to the new room:
			var doors = pQueryAll("D", isDoor.on("D"));
			for(var d0 : doors) {
				if(doorIsReachable(d0)) {
					prolog().facts(inRoom.on(newRoom,d0)) ;
				}
			}	
		}

		// restore the doors' state:
		worldmodel.position = agentOriginalPosition;
		restoreDoorsBlockingState(originalDoorBlockingState, null);

		if (added) {
			DebugUtil.log(">>>>> registering " + button);
		}
	}
	
	
	public void registerDoor(String door) throws InvalidTheoryException {
		if (pTest(isDoor.on(door))) {
			// door is already registered
			return;
		}
		// new door. Add it:
		prolog().facts(isDoor.on(door));

		// Now check which rooms it connects:

		Vec3 agentOriginalPosition = worldmodel.position;
		Map<String, Boolean> originalDoorBlockingState = getDoorsBlockingState();
		fakelyMakeAlldoorsBlocking(door);

		var rooms = pQueryAll("R", isRoom.on("R"));
		int numbersOfAdded = 0; // the number of rooms the door has been added to. Max 2.
		for (var roomx : rooms) {
			String bt0 = pQuery("B", and(inRoom.on(roomx, "B"), isButton.on("B")));
			if (bt0 != null) {
				// the door can be added if there is a button the the room that is
				// reachable from it:

				WorldEntity b0 = worldmodel.getElement(bt0);
				worldmodel.position = b0.position.copy();
				worldmodel.position.y = agentOriginalPosition.y;

				if (doorIsReachable(door)) {
					// add the door to this room:
					prolog().facts(inRoom.on(roomx, door));
					numbersOfAdded++;
					if (numbersOfAdded == 2) {
						// a door can only connect at most two rooms!
						break;
					}
				}
			}
		}

		// restore the doors' state:
		worldmodel.position = agentOriginalPosition;
		restoreDoorsBlockingState(originalDoorBlockingState,door);
		
		if (numbersOfAdded > 0) {
			DebugUtil.log(">>>>> registering " + door);
		}
	}
	
	
	public void registerConnection(String button, String door) throws InvalidTheoryException {
		if(pTest(wiredTo.on(button,door))) {
			// the connection is already registered
			return ;
		}
		boolean wasReportedAsUnconnected = pTest(notWiredTo.on(button,door)) ;
		
		prolog().facts(wiredTo.on(button,door)) ;
		if (wasReportedAsUnconnected) {
			prolog().removeFacts(notWiredTo.on(button,door)) ;
		}
		
		DebugUtil.log(">>>>> registering connection " + button + " -> " 
		   + door 
		   + (wasReportedAsUnconnected ? " (conflict: was NON-connection!)" : "")) ;
	}

	public void registerNONConnection(String button, String door) throws InvalidTheoryException {
		if(pTest(notWiredTo.on(button,door))) {
			// the non-connection is already registered
			return ;
		}
		
		if(pTest(wiredTo.on(button,door))) {
		    // was previously reported as connected. Ignoring the new non-connection report
			DebugUtil.log(">>>>> IGNORING reported NON-connection " + button 
					  + " X " + door + " because it is already marked as connected.") ;
			return ;
		}
		
		prolog().facts(notWiredTo.on(button,door)) ;
		DebugUtil.log(">>>>> registering NON-connection " + button + " X " + door) ;
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
	
	public boolean isDoor(String id) {
		LabEntity e = worldmodel.getElement(id) ;
		return e.type.equals(LabEntity.DOOR) ;
	}
	
	public boolean isButton(String id) {
		LabEntity e = worldmodel.getElement(id) ;
		return e.type.equals(LabEntity.SWITCH) ;
	}
	
	public boolean isReachable(String entityId) {
		if (isDoor(entityId)) {
			return doorIsReachable(entityId) ;
		}
		return buttonIsReachable(entityId) ;
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
		restoreObstacleState(door, ! isOpen) ;
		return path != null ;		
	}
	
	boolean buttonIsReachable(String button) {
		LabEntity b = worldmodel.getElement(button) ;
		var path = findPathTo(b.getFloorPosition(),true) ;
		return path != null ;
	}
	
	String getCurrentRoom() {
		Map<String, Boolean> originalDoorBlockingState = getDoorsBlockingState();
		fakelyMakeAlldoorsBlocking(null);
		var rooms = pQueryAll("R", isRoom.on("R"));
		int numbersOfAdded = 0; // the number of rooms the door has been added to. Max 2.
		String currentRoom = null ;
		for (var roomx : rooms) {
			String bt0 = pQuery("B", and(inRoom.on(roomx, "B"), isButton.on("B")));
			if (bt0 != null) {
				if (buttonIsReachable(bt0)) {
					currentRoom = roomx ;
					break ;
				}
			}
			
		}
		restoreDoorsBlockingState(originalDoorBlockingState, null);
		return currentRoom ;
	}
	
	boolean isLockedInCurrentRoom() {
		String room = getCurrentRoom() ;
		if(room == null) return false ;
		var doors = pQueryAll("D", and(isDoor.on("D"), inRoom.on(room,"D"))) ;
		boolean anyOpen = doors.stream().anyMatch(D -> isOpen(D)) ;
		return ! anyOpen ;
	}
	
	/*.
	 * Given a door or button find closed doors such that for each D of these doors, if it is open would make 
	 * the entity reachable.
	 * This assumes the entity is not reachable. If it is, an empty list is returned.
	 */
	public List<String> getEnablingDoors(String entity) {
		
		List<String> enablingCandidates = new LinkedList<>() ;
		
		if (isReachable(entity)) {
			return enablingCandidates ;
		}
		
		List<String> candidates ;
		
		if (isDoor(entity)) {
			// get doors guarding the neighboring room:
			candidates = pQueryAll("D",
					and(inRoom.on("R",entity),
						neighbor.on("R","R2"),
					    isDoor.on("D"),
						inRoom.on("R2","D"),
						not(inRoom.on("R","D")))
				) ;
		}
		else {
			// get the doors guarding the room  of the entity
			candidates =  pQueryAll("D",
					and(inRoom.on("R",entity),
						isDoor.on("D"),
						inRoom.on("R2","D"))
					) ;
		}
		
		for(String D : candidates) {
			boolean isOpen = isOpen(D) ;
			fakelyUnblockDoor(D) ;
			if(isReachable(entity)) {
				enablingCandidates.add(D) ;
			}
			restoreObstacleState(D, ! isOpen) ;
		}
		
		return enablingCandidates ;
	}
	
	Set<Pair<String,String>> getConnections() {
		Set<Pair<String,String>> cons = new HashSet<>() ;
		for(WorldEntity B : this.knownButtons()) {
			var doors = pQueryAll("D", and(isDoor.on("D"), wiredTo.on(B.id,"D"))) ;
			for(var D : doors) {
				cons.add(new Pair(B.id,D)) ;
			}
		}
		return cons ;
	}

	
}
