package edu.buffalo.algo.sorting;

/**
 * 
 * @author Prasanth
 *
 */
public final class Utils
{
	/**
	 * 		Swaps the elements specified at indices <em>src</em> and <em>dest</em> in the given array.
	 * 
	 * @param arr
	 * @param src
	 * @param dest
	 */
	public static void swap(int[] arr, int src, int dest)
	{
		int temp = arr[src];
		arr[src] = arr[dest];
		arr[dest] = temp;
	}
}
