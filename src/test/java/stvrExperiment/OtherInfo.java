package stvrExperiment;

import static leveldefUtil.LRFloorMap.* ;

import java.io.IOException;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

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
			, "durk_1"
			,"FBK_largerandom_R9_cleaned"
	} ;
	
	@Test
	public void calcAreaSize() throws IOException {
		for (int k=0; k<all_levels.length; k++) {
		   var lev = all_levels[k] ;
		   String levelFile = Paths.get(STVRExperiment.levelsDir, lev+ ".csv").toString() ;
		   int area = numberOfFirstFloorWalkableTiles(levelFile) ;
		   System.out.println("** " + lev + " area = " + area + " m2") ;
		}
		
	}

}
