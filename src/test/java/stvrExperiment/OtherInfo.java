package stvrExperiment;

import static leveldefUtil.LRFloorMap.* ;

import java.io.IOException;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import agents.LabRecruitsTestAgent;
import algorithms.BaseSearchAlgorithm;
import algorithms.XBelief;
import eu.iv4xr.framework.spatial.Vec3;
import leveldefUtil.LRFloorMap;
import leveldefUtil.LRconnectionLogic;
import world.BeliefState;

import static nl.uu.cs.aplib.utils.CSVUtility.* ;

public class OtherInfo {
	
	
	static String[] all_levels = {
			"BM2021_diff1_R3_1_1_H"  
			,"BM2021_diff1_R4_1_1"    
			,"BM2021_diff1_R4_1_1_M" 
			,"BM2021_diff2_R5_2_2_M"  
			,"BM2021_diff2_R7_2_2"    
			,"BM2021_diff3_R4_2_2"    
			,"BM2021_diff3_R4_2_2_M"  
			,"BM2021_diff3_R7_3_3"
			,"sanctuary_1"
			,"durk_1"
			,"FBK_largerandom_R9_cleaned"
	} ;
	
	
	/**
	 * Run this to calculate the total area size of the levels in STVR.
	 */
	//@Test
	public void calcAreaSize() throws IOException {
		for (int k=0; k<all_levels.length; k++) {
		   var lev = all_levels[k] ;
		   String levelFile = Paths.get(STVRExperiment.levelsDir, lev+ ".csv").toString() ;
		   int area = numberOfFirstFloorWalkableTiles(levelFile) ;
		   System.out.println("** " + lev + " area = " + area + " m2") ;
		}
		
	}
	
	static String[] ATEST_DDO_traces = {
			"BM2021_diff1_R3_1_1_HpositionTrace"  
			,"BM2021_diff1_R4_1_1positionTrace"    
			,"BM2021_diff1_R4_1_1_MpositionTrace" 
			,"BM2021_diff2_R5_2_2_MpositionTrace"  
			,"BM2021_diff2_R7_2_2positionTrace"    
			,"BM2021_diff3_R4_2_2positionTrace"    
			,"BM2021_diff3_R4_2_2_MpositionTrace"  
			,"BM2021_diff3_R7_3_3positionTrace"
			,"ddo_the_sanctuary_withdoorspositionTrace"
			,"durk_LR_map1positionTraceViewDis"
	} ;
	
	
	float areaCoverage(BaseSearchAlgorithm dummyAlg, String levelFile, String traceFile) throws IOException {
		var walkableTiles = LRFloorMap.firstFloorWalkableTiles(levelFile) ;
				
		var rows = readCSV(',', traceFile) ;
		rows.remove(0) ;
		for (var R : rows) {
			Vec3 loc = new Vec3(Float.parseFloat(R[0]), Float.parseFloat(R[1]), Float.parseFloat(R[2])) ;
			dummyAlg.visitedLocations.add(loc) ;
		}
		int covered = (int) dummyAlg.getCoveredTiles2D().stream().filter(tile -> walkableTiles.contains(tile)).count() ;
		float areaCoverage = (float) covered / (float) walkableTiles.size() ;
		return areaCoverage ;
	}
	


	/**
	 * Run this to calculate the physical coverage of onlineSeacrh on ATEST and DDO levels.
	 * This is calculated using traces Samira produced during her runs.
	 */
    @Test	
	public void getAreaCovFromTraces_ATEST_DDO() throws IOException {
		var tracedir = Paths.get(STVRExperiment.dataDir, "all locations").toString() ;
		var dummyAgent = new LabRecruitsTestAgent("dummy") ;
		dummyAgent.attachState(new XBelief()) ;
		var dummyAlg = new BaseSearchAlgorithm(dummyAgent) ;
		for (int k=0; k<ATEST_DDO_traces.length; k++) {
			var lev = all_levels[k] ;
			String levelFile = Paths.get(STVRExperiment.levelsDir, lev + ".csv").toString() ;
			String traceFile = Paths.get(tracedir, ATEST_DDO_traces[k] + ".csv").toString() ;
			dummyAlg.visitedLocations.clear();
			System.out.println("** " + lev + ", area-cov: " + areaCoverage(dummyAlg,levelFile,traceFile)) ;
		}
	}
    
	static String[] R9_traces = {
			"FBK-door26"
			,"FBK-door5"
			,"FBK-door39"
			,"FBK-door33"
			,"FBK-door16"
			,"FBK-door37"
			,"FBK-door34"
			,"FBK-door22"
			,"FBK-door38"			
	} ;
	
	/**
	 * Run this to calculate the physical coverage of randomLarge.
	 * This is calculated using traces Samira produced during her runs.
	 */  
    @Test	
	public void getAreaCovFromTraces_RandomLarge() throws IOException {
    	var tracedir = Paths.get(STVRExperiment.dataDir, "all locations").toString() ;
    	String levelFile = Paths.get(STVRExperiment.levelsDir, "FBK_largerandom_R9_cleaned.csv").toString() ;
    	
    	var dummyAgent = new LabRecruitsTestAgent("dummy") ;
		dummyAgent.attachState(new XBelief()) ;
		var dummyAlg1 = new BaseSearchAlgorithm(dummyAgent) ;
		var dummyAlg2 = new BaseSearchAlgorithm(dummyAgent) ;
		
    	
    	for (int k=0; k<R9_traces.length; k++) {
			var walkableTiles = LRFloorMap.firstFloorWalkableTiles(levelFile) ;	
			String traceFile = Paths.get(tracedir, R9_traces[k] + ".csv").toString() ;
			dummyAlg1.visitedLocations.clear();
			System.out.println("** " + R9_traces[k] + ", area-cov: " + areaCoverage(dummyAlg1,levelFile,traceFile)) ;
			areaCoverage(dummyAlg2,levelFile,traceFile) ;
		}
    	// calculate the total coverage of all traces together:
    	var walkableTiles = LRFloorMap.firstFloorWalkableTiles(levelFile) ;
		int covered = (int) dummyAlg2.getCoveredTiles2D().stream().filter(tile -> walkableTiles.contains(tile)).count() ;
		float totAreaCoverage = (float) covered / (float) walkableTiles.size() ;
		System.out.println("** R9 tot area-cov: " + totAreaCoverage) ;
    	
    }
}
