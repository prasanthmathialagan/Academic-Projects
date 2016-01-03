package com.buffalo.inforetr.comparators;

import java.util.Comparator;

import com.buffalo.inforetr.Posting;

/*
 *  Increasing DocIDs comparator
 */
public class DocIDComparator implements Comparator<Posting>
{
	@Override
	public int compare(Posting o1, Posting o2)
	{
		return Long.compare(o1.getDocId(), o2.getDocId());
	}
}
