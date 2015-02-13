import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import edu.rit.pj2.Task;

/**
 * NumberSatSeq.java
 * 
 * @author Trushank Date: Sep 12, 2013
 */

/**
 * 
 * NumberSatSeq
 * Sequential Program to compute the Number SAT for the given input
 * 
 * @author Trushank Date: Sep 12, 2013
 */
public class NumberSatSeq extends Task{

    static long totalSAT = 0; // store total number of SATs

    /**
     * 
     * main Date: Sep 12, 2013
     * 
     * @author: Trushank
     * @param args
     *            void
     * 
     */
    public void main(String args[]) {

	// usage
	if (args.length != 1)
	    usage();

	int variables; // stores no of variables
	int clauses; // stores no of clauses
	long[] neg; // neg variables
	long[] pos; // pos variables

	Scanner src; // input

	try {
	    src = new Scanner(new File(args[0]));
	    if (src.next().equals("p") && src.next().equals("cnf")) { // check
								      // format
		variables = Integer.parseInt(src.next());
		clauses = Integer.parseInt(src.next());
		pos = new long[clauses];
		neg = new long[clauses];
		src.nextLine();
		

		for (int i = 0; i < clauses; i++) {

		  while(src.hasNextInt()) {
			int temp;
			
			    temp = src.nextInt();
			
			if (temp == 0) {	//end of clause
			    break;
			} 
			else if (temp < 0) {	//neg variable
			    temp = temp * -1;
			    neg[i] = neg[i] | 1L << temp - 1;
			} 
			else {		//pos variable
			    pos[i] = pos[i] | 1L << temp - 1;
			}

		    }
		}
		long full = (1L << variables) - 1L;	//iterate till length variables
		for (long A = 0; A <= full; ++A) {
		    long oppA = A ^ full;		//inverse of mask
		    boolean isSAT = true;
		    for (int i = 0; i < pos.length; i++) {
			//check if clause equates to zero
			if ((pos[i] & A) <= 0
				&& (neg[i] & oppA) <= 0) {
			    isSAT = false;
			    break;
			}

		    }
		    //if no clause equates to zero
		    if (isSAT == true) {
			totalSAT++;
		    }
		}
		//print result
		System.out.println(totalSAT);
	    }
	} catch (FileNotFoundException e) {
	    e.printStackTrace();
	}

    }

    /**
     * 
    * usage: prints usage
    * Date: Sep 28, 2013
    * @author: Trushank void
    *
     */
    private static void usage() {
	System.err.println("Usage: java pj2 NumberSatSeq <file>");
	System.exit(0);
    }
}
