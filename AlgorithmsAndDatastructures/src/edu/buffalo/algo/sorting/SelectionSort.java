package edu.buffalo.algo.sorting;

/**
 * 
 * @author Prasanth
 *
 */
// Selecting the smallest element in each iteration
public final class SelectionSort
{
	public static void sort(int[] arr)
	{
		for (int i = 0; i < arr.length - 1; i++)
		{
			int min = arr[i];
			int index = i;
			
			for (int j = i + 1; j < arr.length; j++)
			{
				if(arr[j] <= min)
				{
					min = arr[j];
					index = j;
				}
			}
			
			// Swapping the elements
			Utils.swap(arr, index, i);
		}
	}
}
