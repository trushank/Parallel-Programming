import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import edu.rit.pj2.Job;
import edu.rit.pj2.LongVbl;
import edu.rit.pj2.Loop;
import edu.rit.pj2.Node;
import edu.rit.pj2.Rule;
import edu.rit.pj2.Task;
import edu.rit.pj2.TaskSpec;
import edu.rit.pj2.Tuple;
import edu.rit.util.LongRange;

/**
 * NumbrSatClu.java
 * This program calculates the numberSAT for a given input cnf file
 * The program is developed to run on a cluster parallel system
 */
/**
 * The Class NumberSatClu. Represents the central Job class Reads input file,
 * splits the chunks and finally displays output
 */
public class NumberSatClu extends Job {

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.rit.pj2.Job#main(java.lang.String[])
	 */
	@Override
	public void main(String[] args) {

		// Checking input args
		if (args.length != 2)
			useage();

		int variables; // number of variables
		int clauses; // Number of clauses
		final long[] neg; // store negative variables
		final long[] pos; // stores positive variables
		final Scanner src;
		int K = 0; // Number of worker tasks
		int CF = 20; // Chunk factor
		final long full; // to iterate to the size of variables

		try {
			src = new Scanner(new File(args[0])); // read file provided in
			// command line
			K = Integer.parseInt(args[1]);

			// checking format
			if (src.next().equals("p") && src.next().equals("cnf")) {
				variables = Integer.parseInt(src.next());
				clauses = Integer.parseInt(src.next());
				pos = new long[clauses];
				neg = new long[clauses];
				src.nextLine();

				// Set up a task group of K worker tasks.
				rule(new Rule().task(K, new TaskSpec(WorkerTask.class)
						.requires(new Node().cores(Node.ALL_CORES))));

				// Set up reduction task.
				rule(new Rule().atFinish().task(
						new TaskSpec(ReduceTask.class).runInJobProcess(true)));

				// Setting up the long arrays
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

				// iterate till length variables
				full = (1L << variables) - 1L;

				// Partition the iterations into chunks for the worker tasks.
				for (LongRange chunk : new LongRange(0, full).subranges(K * CF))
					putTuple(new ChunkTuple(chunk));

				// putting K input arrays into tuplespace
				for (int i = 0; i < K; i++) {
					putTuple(new InputTuple(pos, neg, full));
				}
			}

		} catch (FileNotFoundException e) {
		}

	}

	/**
	 * The Class WorkerTask.
	 * 
	 * @author Trushank
	 */
	public static class WorkerTask extends Task {

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.rit.pj2.Task#main(java.lang.String[])
		 */
		@Override
		public void main(String[] arg0) throws Exception {

			// Compute chunks of iterations.
			ChunkTuple template = new ChunkTuple();
			ChunkTuple chunk;
			final long[] pos;
			final long[] neg;
			final long full;

			InputTuple inp = (InputTuple) takeTuple(new InputTuple());
			pos = inp.pos;
			neg = inp.neg;
			full = inp.full;
			long finalCount = 0;

			while ((chunk = (ChunkTuple) tryToTakeTuple(template)) != null) {
				final LongVbl count = new LongVbl.Sum(0); // final result

				parallelFor(chunk.range.lb(), chunk.range.ub()).exec(
						new Loop() {
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
									// Checking if clause equates to greater
									// than 0
									if ((pos[i] & A) <= 0
											&& (neg[i] & oppA) <= 0) {
										isSAT = false;
										// if not don't check rest of the
										// clauses
										break;
									}

								}
								// if all clauses equate to greater than 1
								if (isSAT == true) {
									thrdCount.item++;
								}
							}
						});
				finalCount += count.item;
				count.item = 0;
			}
			putTuple(new ResultTuple(finalCount));

		}
	}

	/**
	 * The Class ChunkTuple. Stores info about the range of the chunk
	 * 
	 * @author Trushank
	 */
	private static class ChunkTuple extends Tuple {

		/** The range. */
		public LongRange range;

		/**
		 * Instantiates a new chunk tuple.
		 */
		public ChunkTuple() {
		}

		/**
		 * Instantiates a new chunk tuple.
		 * 
		 * @param range
		 *            the range
		 */
		public ChunkTuple(LongRange range) {
			this.range = range;
		}
	}

	/**
	 * The Class ResultTuple. Stores result generated by the task nodes
	 */
	private static class ResultTuple extends Tuple {

		/** The count. */
		public long count;

		/**
		 * Instantiates a new result tuple.
		 */
		public ResultTuple() {
		}

		/**
		 * Instantiates a new result tuple.
		 * 
		 * @param count
		 *            the count
		 */
		public ResultTuple(long count) {
			this.count = count;
		}
	}

	/**
	 * The Class InputTuple. Represents the input tuple passed to the worker
	 * tasks
	 */
	private static class InputTuple extends Tuple {

		/** The pos. */
		public long[] pos;

		/** The neg. */
		public long[] neg;

		/** The full. */
		long full;

		/**
		 * Instantiates a new input tuple.
		 */
		public InputTuple() {
		}

		/**
		 * Instantiates a new input tuple.
		 * 
		 * @param pos
		 *            the pos array
		 * @param neg
		 *            the neg array
		 * @param full
		 *            the full size of variables
		 */
		public InputTuple(long[] pos, long[] neg, long full) {
			this.pos = pos;
			this.neg = neg;
			this.full = full;
		}
	}

	/**
	 * The Class ReduceTask. Reads the result tuples from tuplespace, reduces
	 * them and prints output
	 */
	public static class ReduceTask extends Task {

		/**
		 * Reduce task main program.
		 * 
		 * @param args
		 *            the arguments
		 * @throws Exception
		 *             the exception
		 */
		public void main(String[] args) throws Exception {
			int K = inputTupleCount();
			long count = 0L;
			for (int i = 0; i < K; ++i)
				count += ((ResultTuple) getTuple(i)).count;
			System.out.printf("%d%n", count);
		}
	}

	/**
	 * usage: Prints usegae info Date: Sep 28, 2013.
	 * 
	 * @author: Trushank void
	 */
	private static void useage() {
		System.err
				.println("Usage: java pj2 jar=<jarfile> NumberSatClu <file> <K>");
		throw new IllegalArgumentException();
	}
}
