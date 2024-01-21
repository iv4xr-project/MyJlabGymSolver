package algorithms;

import java.util.Scanner;

import gameTestingContest.MyConfig;

public class DebugUtil {

	public static void log(String s) {
		System.out.println(s); 
	}
	
	public static void pressEnter() {
		if(! MyConfig.DEBUG_MODE) return ;
		System.out.println("Hit RETURN to continue.");
		new Scanner(System.in).nextLine();
	}
}
