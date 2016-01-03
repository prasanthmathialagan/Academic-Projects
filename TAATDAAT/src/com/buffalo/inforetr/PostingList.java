package com.buffalo.inforetr;

import java.util.Comparator;

import com.buffalo.inforetr.comparators.DocIDComparator;
import com.buffalo.inforetr.comparators.TFComparator;
import com.buffalo.utils.SortedLinkedList;

/**
 * 
 * @author Prasanth
 *
 */
public class PostingList
{
	private final String term;
	private final int size;
	private final SortedLinkedList<Posting>list;
	
	public PostingList(String term, int size, boolean sortByTf)
	{
		this.term = term;
		this.size = size;
		Comparator<Posting> comparator = sortByTf ? new TFComparator() : new DocIDComparator();
		this.list = new SortedLinkedList<>(comparator);
	}
	
	public int getSize()
	{
		return size;
	}

	public String getTerm()
	{
		return term;
	}
	
	public void addPosting(Posting p)
	{
		list.add(p);
	}

	public SortedLinkedList<Posting> getList()
	{
		return list;
	}

	@Override
	public String toString()
	{
		return "PostingList [term=" + term + ", size=" + size + ", list=" + list + "]";
	}
}
