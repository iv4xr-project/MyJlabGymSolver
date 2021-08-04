package gameTestingContest;

import static nl.uu.cs.aplib.agents.PrologReasoner.* ;

import java.util.stream.Collectors;

import alice.tuprolog.InvalidTheoryException;
import alice.tuprolog.Prolog;
import alice.tuprolog.SolveInfo;
import alice.tuprolog.Term;

public class CobaProlog {
	
	static public void main(String[] args) throws InvalidTheoryException {
	
		XBelief belief = new XBelief() ;
		belief.attachProlog() ;
		var Proom = predicate("room") ;
		var Pbutton = predicate("button") ;
		var Pdoor = predicate("door") ;
		var PinRoom = predicate("inRoom") ;
		var PwiredTo = predicate("wiredTo") ;
		var PnotWiredTo = predicate("notWiredTo") ;
		
		var prolog = belief.prolog() ;
		prolog.facts(Proom.on("r0")) ;
		prolog.facts(Proom.on("r1")) ;
		prolog.facts(Proom.on("r2")) ;
		prolog.facts(Pbutton.on("button0")) ;
		prolog.facts(Pbutton.on("button1")) ;
		prolog.facts(Pbutton.on("button2")) ;
		prolog.facts(Pbutton.on("button3")) ;
		prolog.facts(Pdoor.on("door0")) ;
		prolog.facts(Pdoor.on("door1")) ;
		prolog.facts(Pdoor.on("door2")) ;
		
		prolog.facts(PinRoom.on("r0","button0")) ;
		prolog.facts(PinRoom.on("r0","button1")) ;
		prolog.facts(PinRoom.on("r0","button3")) ;
		prolog.facts(PinRoom.on("r0","door0")) ;
		
		prolog.facts(PinRoom.on("r1","button2")) ;
		prolog.facts(PinRoom.on("r1","door0")) ;
		prolog.facts(PinRoom.on("r1","door1")) ;
		
		prolog.facts(PinRoom.on("r2","door1")) ;
		prolog.facts(PinRoom.on("r2","door2")) ;

		prolog.facts(PwiredTo.on("button1","door0")) ;
		prolog.facts(PwiredTo.on("button0","door1")) ;
		prolog.facts(PnotWiredTo.on("button3","door0")) ;
		prolog.facts(PwiredTo.on("button2","door1")) ;
		
		
		var Pneighbor = predicate("neighbor") ;
		
		Rule neigborRule = rule(Pneighbor.on("R1","R2"))
				.impBy(Proom.on("R1"))
				.and(Proom.on("R2"))
				.and("(R1 \\== R2)") // ok have to use this operator
				.and(Pdoor.on("D"))
				.and(PinRoom.on("R1","D"))
				.and(PinRoom.on("R2","D"))
				;
		
		var ProomReachable = predicate("roomReachable") ;
		
		Rule roomReachableRule1 = rule(ProomReachable.on("R1","R2","1"))
				.impBy(Pneighbor.on("R1","R2"))
				;
		
		Rule roomReachableRule2 = rule(ProomReachable.on("R1","R2","K+1"))
				.impBy(Pneighbor.on("R1","R"))
				.and(ProomReachable.on("R","R2","K"))
				.and("(R1 \\== R2)")
				.and("K > 0")
				;
		Rule roomReachableRule3 = rule(ProomReachable.on("R1","R2","K+1"))
				.impBy(ProomReachable.on("R1","R2","K"))
				.and("K > 0")
				;
		
		prolog.add(neigborRule,roomReachableRule1,roomReachableRule2,roomReachableRule3) ;
		
		System.out.println("Button in r0: " +  prolog.query(
				and(PinRoom.on("r0","X"), 
					Pbutton.on("X")))
		        .str_("X")) ;
		
		System.out.println("Button in r0 to open door1: " +  prolog.query(
				and(PwiredTo.on("B","door1"),
					PinRoom.on("r0","B")))
		        .str_("B")) ;
		
		System.out.println("Button with no confirmed wiring or non-wiring to open door0: " 
		      +  prolog.queryAll(
		    		  and(Pbutton.on("B"), 
		    			  not(PwiredTo.on("B","door0")),
		    			  not(PnotWiredTo.on("B","door0"))))
		      .stream().map(Q -> Q.str_("B"))
		      .collect(Collectors.toList())) ;
		
		System.out.println("Neighbors of r1: " +  
		        prolog.queryAll(
				Pneighbor.on("r1","R"))
		        .stream().map(Q -> Q.str_("R"))
		        .collect(Collectors.toList())
		) ;
		
		System.out.println("Reachable from r0: " +  
		        prolog.queryAll(
		        ProomReachable.on("r0","R","1+1"))
		        .stream().map(Q -> Q.str_("R"))
		        .collect(Collectors.toList())
		) ;
		
		
		System.out.println(prolog.query(Pbutton.on("button10")).info.isSuccess()) ;
		
	}
	
	

}
