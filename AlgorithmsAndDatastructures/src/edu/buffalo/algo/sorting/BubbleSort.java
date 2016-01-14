package edu.buffalo.algo.sorting;

/**
 * @author Prasanth
 *
 */
public class BubbleSort
{
	public static void sort(int[] arr)
	{
		int i = arr.length - 1;

		while (i > 0)
		{
			for (int j = 0; j <= i - 1; j++)
			{
				if (arr[j] > arr[j + 1])
					Utils.swap(arr, j, j + 1);
			}
			i--;
		}
	}
}
