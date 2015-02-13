import java.io.File;
import edu.rit.pj2.LongVbl;
import edu.rit.pj2.Loop;
import edu.rit.pj2.Task;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * NumberSatSeq.java
 * Parallel Program to compute the Number SAT for the given input
 * @author Trushank Date: Sep 12, 2013
 */

/**
 * 
 * NumberSatSmp
 * 
 * @author Trushank Date: Sep 12, 2013
 */
public class NumberSatSmp extends Task {

    /**
     * main args Commandline arguments
     * 
     * @return void
     */
    public void main(String args[]) {

	// Check for command-line argument
	if (args.length != 1)
	    usage();

	int variables; // number of variables
	int clauses; // Number of clauses
	final long[] neg; // store negative variables
	final long[] pos; // stores positive variables
	final Scanner src;

	try {
	    src = new Scanner(new File(args[0])); // read file provided in
						  // command line
	    if (src.next().equals("p") && src.next().equals("cnf")) { // checking
								      // format
		variables = Integer.parseInt(src.next());
		clauses = Integer.parseInt(src.next());
		pos = new long[clauses];
		neg = new long[clauses];
		src.nextLine();
		for (int i = 0; i < clauses; i++) {

		    while (src.hasNextInt()) {
			int temp;
			temp = src.nextInt();
			if (temp == 0) { // end of clause
			    break;
			} else if (temp < 0) { // neg number
			    temp = temp * -1;
			    neg[i] = neg[i] | 1L << temp - 1;
			} else { // pos number
			    pos[i] = pos[i] | 1L << temp - 1;
			}

		    }
		}

		final long full = (1L << variables) - 1L; // iterate till length
							  // variables
		final LongVbl count = new LongVbl.Sum(0); // final result

		parallelFor(0, full).exec(new Loop() {
		    LongVbl thrdCount; // local result

		    public void start() {
			thrdCount = (LongVbl) threadLocal(count); // assigning
								  // local count
								  // with final
		    }

		    public void run(long A) {

			long oppA = A ^ full; // Inverse of mask
			boolean isSAT = true;

			for (int i = 0; i < pos.length; i++) {
			    // Checking if clause equates to greater than 0
			    if ((pos[i] & A) <= 0
				    && (neg[i] & oppA) <= 0) {
				isSAT = false;
				// if not don't check rest of the clauses
				break;
			    }

			}
			// if all clauses equate to greater than 1
			if (isSAT == true) {
			    thrdCount.item++;
			}
		    }
		});
		// Print output
		System.out.println(count.item);
	    }
	} catch (FileNotFoundException e) {
	    e.printStackTrace();
	}

    }

    /**
     * 
     * usage: Prints usgae info Date: Sep 28, 2013
     * 
     * @author: Trushank void
     * 
     */
    private static void usage() {
	System.err.println("Usage: java pj2 NumberSatSeq <file>");
	System.exit(0);
    }
}
