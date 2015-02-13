import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;



// TODO: Auto-generated Javadoc
/**
 * The Class MaxCliqueSeq.
 */
public class MaxCliqueSeq {

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
	try {
		Long start=System.currentTimeMillis();
		Scanner fileIn=new Scanner(new File("graph.txt"));
		ArrayList<Long> tempList=new ArrayList<Long>();
		long[] graph;
		while(fileIn.hasNextLong()){
		tempList.add(fileIn.nextLong());
		}
		graph=new long[tempList.size()];
		for(int i=0;i<tempList.size();i++){
			graph[i]=tempList.get(i);
		}
		
		long full = (1L << graph.length) - 1L;
		long cliqueSize=0;
		for(long i=1;i<full;i++){
			boolean isClique=true;
			//((A & (1 << i)) != 0)
			int len=Long.toBinaryString(i).length();
			for(int j=0;j<len;j++){
				if((i & (1 << j)) != 0){
					long test=i&graph[j];
					//System.out.println(Long.toBinaryString(test)+" "+j);
					if(Long.bitCount(test)!=Long.bitCount(i)){
						isClique=false;
						break;
					}
				}
			}
			if(isClique && Long.bitCount(i)>cliqueSize){
			cliqueSize=Long.bitCount(i);
			//System.out.println(i+ " "+cliqueSize);
			}
		}
		System.out.println(cliqueSize);
		System.out.println("Time: "+(System.currentTimeMillis()-start));
	} catch (FileNotFoundException e) {
		e.printStackTrace();
	}
	
	}

}
