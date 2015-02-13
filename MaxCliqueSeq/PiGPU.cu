//******************************************************************************
//
// File:    PiGPU.cu
// Author:  Alan Kaminsky
// Version: 22-Oct-2013
//
// This source file is copyright (C) 2013 by Parallel Crypto LLC. All rights
// reserved. For further information, contact the author, Alan Kaminsky, at
// alan.kaminsky@parallelcrypto.com.
//
// This source file is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation; either version 3 of the License, or (at your option) any
// later version.
//
// This source file is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
// details.
//
// A copy of the GNU General Public License is provided in the file gpl.txt. You
// may also obtain a copy of the GNU General Public License on the World Wide
// Web at http://www.gnu.org/licenses/gpl.html.
//
//******************************************************************************

/**
 * Program PiGPU computes an approximation of pi in parallel on the GPU by
 * generating N random (x,y) points in the unit square and counting how many
 * fall within a distance of 1 from the origin.
 *
 * Usage: PiGPU <seed> <N>
 * <seed> = Pseudorandom number generator seed
 * <N> = Number of points, N >= 1
 */

#include <stdlib.h>
#include <stdio.h>
#include <cuda_runtime_api.h>

#include "Util.cu"
#include "Random.cu"

//------------------------------------------------------------------------------
// DEVICE FUNCTIONS

// Number of threads per block.
#define NT 1024

// Overall counter variable in global memory.
__device__ unsigned long long int devCount;

// Per-thread counter variables in shared memory.
__shared__ unsigned long long int shrCount [NT];

/**
 * Device kernel to compute random points.
 *
 * Called with a one-dimensional grid of one-dimensional blocks, NB blocks, NT
 * threads per block.
 *
 * @param  seed  Pseudorandom number generator seed.
 * @param  N     Number of points.
 */
__global__ void computeRandomPoints
	(unsigned long long int seed,
	 unsigned long long int N,unsigned long long int *devpopulation)
	{
	int x, size, rank;
	unsigned long long int len, lb, ub, count;
	prng_t prng;
	
	
	

	// Determine number of threads and this thread's rank.
	x = threadIdx.x;
	size = gridDim.x*NT;
	rank = blockIdx.x*NT + x;

	// Determine iterations for this thread.
	len = (N + size - 1)/size;
	lb = rank*len;
	ub = min (lb + len, N) - 1;

	// Initialize per-thread prng and count.
	prngSetSeed (&prng, seed + rank);
	count = 0;
	int sizeOfArray=(sizeof(&devpopulation) / sizeof(devpopulation[0]));
	unsigned long long int max=devpopulation[sizeOfArray-1];
	
	// Compute random points.
	for (unsigned long long int i = lb; i <= ub; ++ i)
		{
		int x = prngNextInt (&prng,max);
		int y = prngNextInt (&prng,max);
		
		if (x==y) y++;
		
		int xGrp=0;
		int yGrp=0;
		
			for(int i=0;i<sizeOfArray;i++){
				
				if(i==0){
					if(x>=0 && x<devpopulation[i]){
						xGrp=0;
					}
					if(y>=0 && y<devpopulation[i]){
					yGrp=0;
					}
				}
				else{
					if(x>=devpopulation[i-1] && x<devpopulation[i]){
					xGrp=i;
					}
					if(y>=devpopulation[i-1] && y<devpopulation[i]){
					yGrp=i;
					}
				}
			}
			if(xGrp!=yGrp) count++;
		}
// Shared memory parallel reduction within thread block.
	shrCount[x] = count;
	
	__syncthreads();
	for (int i = NT/2; i > 0; i >>= 1)
		{
		if (x < i)
			shrCount[x] += shrCount[x+i];
		__syncthreads();
		}

	// Atomic reduction into overall counter.
	if (x == 0)
		atomicAdd (&devCount, shrCount[0]);
	}

//------------------------------------------------------------------------------
// HOST FUNCTIONS

/**
 * Print a usage message and exit.
 */
static void usage()
	{
	fprintf (stderr, "Usage: PiGPU <seed> <N>\n");
	fprintf (stderr, "<seed> = Pseudorandom number generator seed\n");
	fprintf (stderr, "<trials> = Number of trials>= 1\n");
	exit (1);
	}

/**
 * Main program.
 */
int main
	(int argc,
	 char *argv[])
	{
	unsigned long long int seed, trials, t1, t2, hostCount;
	unsigned long long int *population,*devpopulation;
	int dev, NB;
   size_t populationBytes;
	// Parse command line arguments.
	if (argc < 4) usage();
	progname = argv[0];
	if (sscanf (argv[1], "%llu", &seed) != 1) usage();
	if (sscanf (argv[2], "%llu", &trials) != 1) usage();
	
	
populationBytes= (argc-3)*sizeof(unsigned long long int);
population=(unsigned long long int*) malloc (populationBytes);
 if (population == NULL) die ("Cannot allocate population");
 for(int i=3;i<argc;i++){
	if (sscanf (argv[i], "%llu", &population[i-3]) != 1 || population[i-3] < 1) usage();
	
	if(i!=3){
	population[i-3]+=population[i-4];
	}
	
}


	// Set CUDA device and determine number of multiprocessors (thread blocks).
	dev = setCudaDevice();
	checkCuda
		(cudaDeviceGetAttribute (&NB, cudaDevAttrMultiProcessorCount, dev),
		 "Cannot get number of multiprocessors");
	printf ("NB = %d, NT = %d, threads = %d\n", NB, NT, NB*NT);

	// Allocate storage on host and device.
   checkCuda (cudaMalloc (&devpopulation, populationBytes),
      "Cannot allocate devpopulation");
	  
	  // Copy population array to device.
   checkCuda (cudaMemcpy (devpopulation, population, populationBytes, cudaMemcpyHostToDevice),
      "Cannot upload devpopulation");
	  
	// Initialize overall counter.
	hostCount = 0;
	checkCuda
		(cudaMemcpyToSymbol (devCount, &hostCount, sizeof(hostCount)),
		 "Cannot initialize devCount");

	// Compute random points in parallel on the GPU. Measure computation time.
	t1 = currentTimeMillis();
	computeRandomPoints <<< NB, NT >>> (seed, trials,devpopulation);
	cudaThreadSynchronize();
	checkCuda
		(cudaGetLastError(),
		 "Cannot launch computeRandomPoints() kernel");
	t2 = currentTimeMillis();

	// Get overall counter from GPU.
	checkCuda
		(cudaMemcpyFromSymbol (&hostCount, devCount, sizeof(hostCount)),
		 "Cannot copy devCount to hostCount");

	// Print results.
	printf ("pi = 100*%llu/%llu = %lf\n", hostCount, trials, 100*((double)hostCount/(double)trials));
	printf ("%llu msec\n", t2 - t1);
	}
