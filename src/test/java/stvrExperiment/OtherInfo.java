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
	
	
	float areaCoverage(String levelFile, String traceFile) throws IOException {
		var walkableTiles = LRFloorMap.firstFloorWalkableTiles(levelFile) ;
		
		var dummyAgent = new LabRecruitsTestAgent("dummy") ;
		dummyAgent.attachState(new XBelief()) ;
		var alg = new BaseSearchAlgorithm(dummyAgent) ;
		
		var rows = readCSV(',', traceFile) ;
		rows.remove(0) ;
		for (var R : rows) {
			Vec3 loc = new Vec3(Float.parseFloat(R[0]), Float.parseFloat(R[1]), Float.parseFloat(R[2])) ;
			alg.visitedLocations.add(loc) ;
		}
		int covered = (int) alg.getCoveredTiles2D().stream().filter(tile -> walkableTiles.contains(tile)).count() ;
		float areaCoverage = (float) covered / (float) walkableTiles.size() ;
		return areaCoverage ;
	}
	


    @Test	
	public void getAreaCovFromTraces_ATEST_DDO() throws IOException {
		var tracedir = Paths.get(STVRExperiment.dataDir, "all locations").toString() ;
		for (int k=0; k<ATEST_DDO_traces.length; k++) {
			var lev = all_levels[k] ;
			String levelFile = Paths.get(STVRExperiment.levelsDir, lev + ".csv").toString() ;
			var walkableTiles = LRFloorMap.firstFloorWalkableTiles(levelFile) ;
			
			var dummyAgent = new LabRecruitsTestAgent("dummy") ;
			dummyAgent.attachState(new XBelief()) ;
			var alg = new BaseSearchAlgorithm(dummyAgent) ;
			
			String traceFile = Paths.get(tracedir, ATEST_DDO_traces[k] + ".csv").toString() ;

			System.out.println("** " + lev + ", area-cov: " + areaCoverage(levelFile,traceFile)) ;
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
    
    @Test	
	public void getAreaCovFromTraces_RandomLarge() throws IOException {
    	var tracedir = Paths.get(STVRExperiment.dataDir, "all locations").toString() ;
    	String levelFile = Paths.get(STVRExperiment.levelsDir, "FBK_largerandom_R9_cleaned.csv").toString() ;
    	
    	for (int k=0; k<R9_traces.length; k++) {
			var walkableTiles = LRFloorMap.firstFloorWalkableTiles(levelFile) ;
			
			var dummyAgent = new LabRecruitsTestAgent("dummy") ;
			dummyAgent.attachState(new XBelief()) ;
			var alg = new BaseSearchAlgorithm(dummyAgent) ;
			
			String traceFile = Paths.get(tracedir, R9_traces[k] + ".csv").toString() ;

			System.out.println("** " + R9_traces[k] + ", area-cov: " + areaCoverage(levelFile,traceFile)) ;
		}
    	
    }
}
