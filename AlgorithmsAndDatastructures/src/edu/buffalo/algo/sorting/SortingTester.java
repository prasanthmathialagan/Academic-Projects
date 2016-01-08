package edu.buffalo.algo.sorting;

import java.util.Arrays;

/**
 * 
 * @author Prasanth
 *
 */
public final class SortingTester
{
	public static void main(String[] args)
	{
		int[] arr = {3, 1, 7, 4, 2, 8, 5, 3};
		System.out.println("Before sorting : " + Arrays.toString(arr));
//		SelectionSort.sort(arr);
//		InsertionSort.sort(arr);
		BubbleSort.sort(arr);
		System.out.println("After sorting : " + Arrays.toString(arr));
	}
}
