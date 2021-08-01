package gameTestingContest;

import java.util.Scanner;

public class DebugUtil {

	public static void log(String s) {
		System.out.println(s); 
	}
	
	static void pressEnter() {
		if(! MyConfig.DEBUG_MODE) return ;
		System.out.println("Hit RETURN to continue.");
		new Scanner(System.in).nextLine();
	}
}
