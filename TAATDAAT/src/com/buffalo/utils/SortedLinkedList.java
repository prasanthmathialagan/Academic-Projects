package com.buffalo.utils;

import java.util.Comparator;
import java.util.LinkedList;

/**
 * 
 * @author Prasanth
 *
 * @param <T>
 */
public class SortedLinkedList<T>
{
	private final LinkedList<T> list = new LinkedList<>();
	
	private final Comparator<T> comparator;
	
	public SortedLinkedList(Comparator<T> comparator)
	{
		this.comparator = comparator;
	}
	
	/**
	 *  Does not support insertion of null
	 *  
	 * @param t
	 */
	public void add(T t)
	{
		if(t == null)
			throw new NullPointerException("Element cannot be null");
		
		int i = 0;
		for (T p : list)
		{
			int result = comparator.compare(t, p);
			if(result < 0)
			{
				list.add(i, t);
				return;
			}
			i++;
		}
		
		list.add(t);
	}
	
	public boolean contains(T t)
	{
		return list.contains(t);
	}
	
	public T get(int index)
	{
		return list.get(index);
	}
	
	public int size()
	{
		return list.size();
	}
	
	public LinkedList<T> getLinkedList()
	{
		return list;
	}
	
	@Override
	public String toString()
	{
		return list.toString();
	}
}
