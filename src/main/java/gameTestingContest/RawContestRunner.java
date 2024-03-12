package gameTestingContest;

import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;

import agents.LabRecruitsTestAgent;
import algorithms.XBelief;
import environments.LabRecruitsConfig;
import environments.LabRecruitsEnvironment;
import environments.SocketReaderWriter;
import game.LabRecruitsTestServer;
import game.Platform;
import leveldefUtil.LRconnectionLogic;
import nl.uu.cs.aplib.utils.Pair;

/**
 * A simple runner of your test-algorithm/AI (which is expected to be
 * implemented in the class {@link gameTestingContest.MyTestingAI}). You can
 * invoke the main method of this class, which will then create an instance of
 * your MyTestingAI, and run it. The resulting findings will be printed to the
 * console.
 * 
 * You have to configure several things in the fields
 * {@link #labRecruitesExeRootDir}, {@link #levelsDir}, and {@link levelName}.
 */
public class RawContestRunner {

    /**
     * Specify here the path to the "root" directory where the Lab Recruits
     * executable is placed. E.g. if it is in the directory
     * bar/foo/gym/Windows/bin/LabRecruits.exe, then the root directory is
     * bar/foo/gym.
     */
    static String labRecruitesExeRootDir = System.getProperty("user.dir") ;

    /**
     * Specify here the name of the level that you want to load. if the name is
     * "xyz", then there should be a text-file named xyz.csv that contains the
     * definition of this level. This csv file will be loaded to Lab Recruits, which
     * in turn will generate the corresponding game content.
     */
    //static String levelName = "BM2021_diff3_R4_2_2" ;
    // static String levelName = "BM2021_diff2_R7_2_2" ;    
    //static String levelName = "buttons_doors_1" ;    
    static String levelName = "samira_8room" ;   
    //static String levelName = "BM2021_diff1_R3_1_1_H" ;    
    
    

    
    /**
     * Specify hefre the path to the directory where the level-file referred to by
     * {@link #levelName} above is stored.
     */
    //static String levelsDir = labRecruitesExeRootDir + "/src/test/resources/levels/contest";
    static String levelsDir = labRecruitesExeRootDir + "/src/test/resources/levels";

    static LabRecruitsTestServer labRecruitsBinding;

    // a convenience method to launch Lab Recruits:
    static void launchLabRcruits() {
        var useGraphics = true; // set this to false if you want to run the game without graphics
        SocketReaderWriter.debug = false;
        labRecruitsBinding = new LabRecruitsTestServer(useGraphics,
                Platform.PathToLabRecruitsExecutable(labRecruitesExeRootDir));
        labRecruitsBinding.waitForGameToLoad();
    }
    
    // a function used to create an instance of MyTestingAI. By default this
    // will just call the constructor of MyTestingAI. But through this field
    // we can change it to something for the purpose of testing this Runner.
    static Supplier<MyTestingAI> mkAnInstanceOfMyTestingAI = () -> new MyTestingAI() ;
    
    /**
     * Invoke this main method to run your MyTestingAI on a game-level you specified
     * above.
     */
    public static void main(String[] args) throws Exception {
        // launch an instance of Lab Recruits
        launchLabRcruits();
        // specify the level file, and where to find it:
        var config = new LabRecruitsConfig(levelName, levelsDir);
        
        // let's now instantiate your test-algorithm/AI:
        MyTestingAI myTestingAI = mkAnInstanceOfMyTestingAI.get() ;
        
        var agentId_ = MyConfig.agentId ;
        if (agentId_ == null) agentId_ = "agent0" ;
        final String agentId = agentId_ ;
        
        myTestingAI.agentConstructor = dummy -> {
        	// create an instance of LabRecruitsEnvironment; it will bind to the
            // Lab Recruits instance you launched above. It will also load the
            // level specified in the passed LR-config:
        	LabRecruitsEnvironment env = new LabRecruitsEnvironment(config);
        	LabRecruitsTestAgent agent = new LabRecruitsTestAgent(agentId) // matches the ID in the CSV file
    				.attachState(new XBelief())
    				.attachEnvironment(env);
    		return agent ;
        } ;

        
        Set<Pair<String, String>> report = myTestingAI.exploreLRLogic();
        // printing the findings:
        System.out.println("** The level has the following logic:");
        for (Pair<String, String> connection : report) {
            System.out.println("   Button " + connection.fst + " toggles " + connection.snd);
        }
        String levelfile = Paths.get(levelsDir, levelName + ".csv").toString() ;
        var referenceLogic = LRconnectionLogic.parseConnections(levelfile) ;
        System.out.println(LRconnectionLogic.compareConnection(referenceLogic, report)) ;
        labRecruitsBinding.close();
    }

}
