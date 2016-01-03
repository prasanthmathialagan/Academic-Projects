package edu.buffalo.ds;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestMinHeap
{
	private static MinHeap heap;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		heap = new MinHeap();
		
		heap.addElement(1);
		heap.addElement(6);
		heap.addElement(7);
		heap.addElement(12);
		heap.addElement(10);
		heap.addElement(13);
		heap.addElement(15);
		heap.addElement(17);
		
		System.out.println(heap);
	}
	
	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
		System.out.println(heap);
	}

	@Test
	public void testAddElement()
	{
		heap.addElement(4);
		heap.addElement(11);
	}

	@Test
	public void testRemove()
	{
//		fail("Not yet implemented");
		
		System.out.println(heap.remove());
		System.out.println(heap);
		
		System.out.println(heap.remove());
	}
}
