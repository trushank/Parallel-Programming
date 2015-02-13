//******************************************************************************
//
// File:    MaximumCliqueClu.java
// Authors:  Dhaval Powar, Trushank Dand
// Version: 7-Nov-2013
//******************************************************************************
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import edu.rit.pj2.Task;
import edu.rit.pj2.Job;
import edu.rit.pj2.Tuple;
import edu.rit.pj2.Vbl;
import edu.rit.pj2.LongParallelForLoop;
import edu.rit.pj2.LongLoop;
import edu.rit.pj2.TaskSpec;
import edu.rit.pj2.Rule;
import edu.rit.util.LongRange;
import edu.rit.pj2.Node;

/**
 * Program MaximumCliqueClu is a parallel program  that calculates 
 * the size and total number of maximum cliques in a random graph
 *
 * Usage: java pj2 MaximumCliqueClu <graphFile> <workerTasks>
 * <graphFile> = A file containing the random graph
 * <workerTasks> = Number of nodes on the cluster to distribute load
 */
public class MaximumCliqueClu extends Job 
{
	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public void main(String[] args) throws Exception 
	{
	 try 
	 {	
		if(args.length != 2) usage();
		Scanner s = new Scanner(new File(args[0]));
		
		int V = s.nextInt();
		int K = Integer.parseInt(args[1]);
		long [] vertex = new long[V];

		//Parse vertices
		for(int i = 0; i < V; ++i) vertex[i] = s.nextLong();
		s.close();

		// Put K copies of graph tuple for worker tasks to take.
		for(int i=0;i<K;i++)
      	putTuple (new Graph (V, vertex));

		// Different combinations of vertices to check for maximum clique
		long vertexCombinations = (1L << V) - 1L;

		// Partition the iterations into chunks for the worker tasks.
      	for (LongRange chunk : new LongRange (0L, vertexCombinations)
         .subranges (K))
            putTuple (new ChunkTuple (chunk));

		// Set up a group of K MaxCliqueSolvers.
      	rule (new Rule()
         .task (K, new TaskSpec (MaximumCliqueSolver.class)
            .requires (new Node() .cores (Node.ALL_CORES))));
		
		// Set up reduction tasks.
      	rule (new Rule()
         .atFinish()
         .task (new TaskSpec (OverallReduction.class)
            //.args (""+V)
            .runInJobProcess (true)));
	  } 
	  catch (FileNotFoundException e) { System.out.println("Graph file not found");}
    }

   /**
    * Tuple with the input graph. Sent from the job main program to all
    * the worker tasks.
    */
   private static class Graph extends Tuple
   {
      // Number of vertices.
      public int V;

      // Vertex array; each element is a vertex bitset having all outgoing edges
      // from this edge.
      public long[] vertex;

      // Construct a new graph tuple.
      public Graph() {}

      // Construct a new graph tuple with the given information.
      public Graph (int V, long[] vertex)
         {
         	this.V = V;
         	this.vertex = vertex;
         }
    }


	/**
	 * This inner class maintains chunks of iteration that each node needs to do
	 */
	private static class ChunkTuple extends Tuple
	{
		public LongRange range;

		ChunkTuple() {};
		ChunkTuple(LongRange range) {this.range = range;}
	}
	
	/**
	 * This inner class reduces the local maximum cliques into a global variable
	 */
	private static class Clique extends Tuple implements Vbl
	{
		//Local variables that keeps track of maximum clique
		public long maxClique;
		public int maxCliqueSize;
		public long maxCliqueCount;

		public Clique(){}

		public Clique(long maxClique, int maxCliqueSize, long maxCliqueCount)
		{
			this.maxClique = maxClique;
			this.maxCliqueSize = maxCliqueSize;
			this.maxCliqueCount = maxCliqueCount;
		}

		/**
		 * Returns a deep copy of the Clique object
		 *
		 * @return new clique object
		 */
		public Object clone()
		{
			return new Clique(maxClique,maxCliqueSize,maxCliqueCount);
		}

		/**
		 * Sets the arguments values to this clique object
		 *
		 * @param Clique object to copy from
		 */
		public void set(Vbl vbl)
		{
			Clique c = (Clique) vbl;
			this.maxClique = c.maxClique;
			this.maxCliqueSize = c.maxCliqueSize;
			this.maxCliqueCount = c.maxCliqueCount;
		}

		/**
		 * Reduces the given object to this clique object to get the maximum clique
		 *
		 * @param Clique object to reduce from
		 */
		public void reduce(Vbl vbl)
		{
			Clique c = (Clique) vbl;

			if(c.maxCliqueSize > this.maxCliqueSize)
			{
				this.maxCliqueSize = c.maxCliqueSize;
				this.maxCliqueCount = c.maxCliqueCount;
				this.maxClique = c.maxClique;
			}
			else if (c.maxCliqueSize == this.maxCliqueSize)
				{
					this.maxCliqueCount += c.maxCliqueCount;
					this.maxClique = Math.min(this.maxClique,c.maxClique); 
				}
		}

		/**
		 * Method that keeps track of maximum clique on each worker node.
		 *
		 * @param The vertex combination
		 */
		public void reduce(long i)
		{
			int size = Long.bitCount(i);

			if(size > this.maxCliqueSize)
			{
				this.maxCliqueSize = size;
				this.maxCliqueCount = 1;
				this.maxClique = i;
			}
			else if (size== this.maxCliqueSize)
				{
					++this.maxCliqueCount;
				}
		}

		public void clear()
		{
			this.maxClique = 0L;
			this.maxCliqueSize = 0;
			this.maxCliqueCount = 0L;
		}
	}

	/**
    * Worker task class.
    */
   public static class MaximumCliqueSolver extends Task
   {
      // Number of vertices.
      int V;

      // Vertex array.
      long[] vertex;

      // Maximum clique.
      Clique clique;
      Clique chunkClique;

      /**
       * Worker task main program.
       */
      public void main(String[] args) throws Exception
      {
         // Read graph tuple.
         Graph g = (Graph) takeTuple (new Graph());
         this.V = g.V;
         this.vertex = g.vertex;

         // Set up shared global reduction variables.
         clique = new Clique();
         chunkClique = new Clique();

         // Compute chunks of iterations.
         ChunkTuple template = new ChunkTuple();
         ChunkTuple chunk;
         while ((chunk = (ChunkTuple) tryToTakeTuple (template)) != null)
         {
            // Check all subsets of vertices to find maximum cliques.
            chunkClique.clear();
            parallelFor (chunk.range.lb(), chunk.range.ub()) .exec (new LongLoop()
               {
               Clique thrClique;

               public void start()
               {
                  thrClique = threadLocal (chunkClique);
               }
               public void run (long i)
               {
                	boolean satisfiesClique = true;
					long temp, currentVertex;

					// For every vertex
					for(int j = 0; j < V; j++)
					{
						currentVertex = 1L << j;
						// If vertex is part of the combination
						if((i & currentVertex) != 0)
						{
							// Determines if a vertex has edges to every other edge in a combination
							temp = i & vertex[j];
							if(Long.bitCount(temp) != Long.bitCount(i))
							{
								satisfiesClique=false;
								break;
							}
						}
					}

					//If a clique is found, compare it with the previous max clique
					if(satisfiesClique == true)
					{
						thrClique.reduce(i);
					}

                }
               });

               // Reduce the result of each chunk worked on.
               clique.reduce (chunkClique);
            }

         // Send results to reduction task.
         putTuple (clique);
       }
    }

   /**
    * Reduction task class.
    */
   public static class OverallReduction extends Task
   {
      /**
       * Reduction main program.
       */
      public void main (String[] args) throws Exception
      {
         // Reduce all worker tasks' results together.
         Clique total = new Clique (0L, 0, 0L);
         int K = inputTupleCount();
         for (int i = 0; i < K; ++ i)
            total.reduce ((Clique) getTuple (i));

         // Print results
		long shifter = total.maxClique;
		StringBuffer sb = new StringBuffer();
		sb.append("Vertices {   ");
		for(int l = 0; l < 64; l++)
		{
			if((shifter & 1L) != 0)
			{
				sb.append(l);
				sb.append("   ");
			}
			shifter >>>= 1;
		}
		sb.append("} form a maximum Clique.");
		System.out.println(sb.toString());
		System.out.println("Maximum Clique Size  : "+total.maxCliqueSize);
		System.out.println("Maximum Clique Count : "+total.maxCliqueCount);
      }
    }

	/**
	 * Usage message
	 */
	public static void usage()
	{
		System.err.println("Usage: java pj2 MaximumCliqueClu <File> <workerTasks>");
		System.err.println("<File> = Randomly genereated graph file");
		System.err.println("<workerTasks> = Number of nodes to distribute load on");
		System.exit(1);
	}
}
