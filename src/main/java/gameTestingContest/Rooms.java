package gameTestingContest;

import java.util.*;

import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.spatial.Vec3;

/**
 * A model of how a level is built from rooms. Currently we don't represent the connectivity
 * between rooms --> Samira :D
 * 
 * NOTE:
 * 
 * The algorithm to identify rooms used here is a bit naive. It assumes that the sub nav-graph H
 * seen so-far is always enough to accurately determine the reachability between any two nodes
 * in that subgraph. This assumption is broken for example when there is a closed between two
 * nodes in H. So, in H they appear to be unreachable from each other. But in the total graph
 * there might be a path that connect them. 
 */
public class Rooms {
	
	/**
	 * A class representing a single room.
	 */
	public static class Room {

		String id;
		List<String> buttons = new LinkedList<>();
		List<String> doors = new LinkedList<>();
		List<String> doorsNotInThisRoom = new LinkedList<>();
		
		/**
		 * A pointer to the BeliefState to which this "Room" is being kept track.
		 */
		XBelief enclosingBelief;

		public Room(String id, XBelief belief) {
			this.id = id;
			enclosingBelief = belief;
		}

		/**
		 * Register a button to this room, if it belongs to it. It returns true if the button
		 * is already in the room, or if it is added. Else it returns  false.
		 */
		public boolean registerButton(String button) {
			if (buttons.contains(button))
				return true;
			if (buttons.isEmpty()) {
				buttons.add(button);
				return true ;
			}
			Vec3 agentOriginalPosition = enclosingBelief.worldmodel.position;
			Map<String, Boolean> originalDoorBlockingState = enclosingBelief.getDoorsBlockingState() ;

			WorldEntity b0 = enclosingBelief.worldmodel.getElement(buttons.get(0));
			enclosingBelief.worldmodel.position = b0.position.copy();
			enclosingBelief.worldmodel.position.y = agentOriginalPosition.y ;
			boolean added = false ; 
			enclosingBelief.fakelyMakeAlldoorsBlocking(null);
			if (enclosingBelief.buttonIsReachable(button)) {
				buttons.add(button);
				added = true ;
			}
			// restore the state:
			enclosingBelief.worldmodel.position = agentOriginalPosition;
			enclosingBelief.restoreDoorsBlockingState(originalDoorBlockingState,null);
			
			return added ;
		}

		/**
		 * Register a door to a room. The room should contains at least one button. It returns true if
		 * the door is already in the room, or if it is added to the room. Else false.
		 */
		public boolean registerDoor(String door) {
			if (doors.contains(door))
				return true ;
			if (buttons.isEmpty())
				return false ;
			Vec3 agentOriginalPosition = enclosingBelief.worldmodel.position;
			Map<String, Boolean> originalDoorBlockingState = enclosingBelief.getDoorsBlockingState() ;

			WorldEntity b0 = enclosingBelief.worldmodel.getElement(buttons.get(0));
			enclosingBelief.worldmodel.position = b0.position.copy();
			enclosingBelief.worldmodel.position.y = agentOriginalPosition.y ;
			enclosingBelief.fakelyMakeAlldoorsBlocking(door);
			boolean added = false ;
			if (enclosingBelief.doorIsReachable(door)) {
				doors.add(door);
				added = true ;
			}
			else {
				// adding this somehow screws the logc. Probly has something to do with
				// seen area so far that may make a door in a room appears unreachable 
				// at first.
				// Disabling it:
				//doorsNotInThisRoom.add(door) ;
			}
			//for(String btn : buttons) {
			//	System.out.println("   > " + btn) ;
			//}

			// restore the state:
			enclosingBelief.worldmodel.position = agentOriginalPosition;
			enclosingBelief.restoreDoorsBlockingState(originalDoorBlockingState,door);
			
			return added ;
		}

	}
	
	
	
	public Set<Room> rooms = new HashSet<>() ;
	
	/**
	 * A pointer to the BeliefState to which the "Rooms" are being kept track.
	 */
	XBelief enclosingBelief;
	
	public Rooms(XBelief belief) {
		enclosingBelief = belief ;
	}
	
	public void registerButton(String button) {
		for(Room R : rooms) {
			if (R.buttons.contains(button)) return ;
		}		
		for(Room R : rooms) {
			boolean registered = R.registerButton(button) ;
			if (registered) return ;
		}
		Room newRoom = new Room("R" + rooms.size(),enclosingBelief) ;
		newRoom.registerButton(button) ;
		rooms.add(newRoom) ;
	}
	
	public void registerDoor(String door) {
		//System.out.println("@@@ #rooms = " + rooms.size()) ;
		for(Room R : rooms) {
			if (R.doors.contains(door) || R.doorsNotInThisRoom.contains(door)) continue ;
			R.registerDoor(door) ;
		}
	}
	
	/**
	 * Get the room where the agent is currently located.
	 */
	public Room getCurrentRoom() {
		Map<String, Boolean> originalDoorBlockingState = enclosingBelief.getDoorsBlockingState() ;
		enclosingBelief.fakelyMakeAlldoorsBlocking(null);
		Room found = null ;
		for(Room R : rooms) {
			if(enclosingBelief.buttonIsReachable(R.buttons.get(0))) {
				found = R ;
				break ;
			}
		}
		enclosingBelief.restoreDoorsBlockingState(originalDoorBlockingState,null);
		return found ;
	}
	
	public List<String> getDoorsOfCurrentRoom() {
		Room R = getCurrentRoom() ;
		if (R==null) return null ;
		else return R.doors ;
	}
	
	/**
	 * Return true is all doors of the room the agent is currently in is closed.
	 */
	boolean isLockedInCurrentRoopm() {
		Room R = getCurrentRoom() ;
		if (R == null) return false ;
		for(String door : R.doors) {
			if (enclosingBelief.isOpen(door)) return false ;
		}
		return true ;
	}


}
