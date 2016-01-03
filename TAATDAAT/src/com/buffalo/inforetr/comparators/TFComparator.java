package com.buffalo.inforetr.comparators;

import java.util.Comparator;

import com.buffalo.inforetr.Posting;

/*
 * 	Decreasing TF comparator
 */
public class TFComparator implements Comparator<Posting>
{
	@Override
	public int compare(Posting o1, Posting o2)
	{
		return Integer.compare(o2.getFrequency(), o1.getFrequency());
	}
}
