package edu.buffalo.algo.sorting;

/**
 * 
 * @author Prasanth
 *
 */
public class InsertionSort
{
	public static void sort(int[] arr)
	{
		for (int i = 1; i < arr.length; i++)
		{
			int element = arr[i];
			int j = i - 1;
			int k = i;
			
			while(j >= 0 && arr[j] > element)
			{
				Utils.swap(arr, k, j);
				j--;
				k--;
			}
		}
	}
}
