package com.buffalo.inforetr;

import static org.junit.Assert.fail;

import java.util.Comparator;
import java.util.LinkedList;

import org.junit.Test;

import com.buffalo.utils.SortedLinkedList;

/**
 * 
 * @author Prasanth
 *
 */
public class TestSortedLinkedList
{
	SortedLinkedList<Integer> list = new SortedLinkedList<>(new Comparator<Integer>()
	{
		@Override
		public int compare(Integer o1, Integer o2)
		{
			return o1.intValue() - o2.intValue();
		}
	});
	
	@Test
	public void testAdd()
	{
		list.add(4);
		System.out.println(list);

		list.add(2);
		System.out.println(list);

		list.add(3);
		System.out.println(list);
	}
}
