package com.buffalo.inforetr;

import java.io.File;

import org.junit.BeforeClass;
import org.junit.Test;

import com.buffalo.inforetr.result.Result;
import com.buffalo.utils.ConsoleLogger;
import com.buffalo.utils.Level;

public class TestIndexHandler
{

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		ConsoleLogger.getLogger().setLevel(Level.TRACE);
		
		long start = System.currentTimeMillis();
		IndexHandler.loadIndices("resources" + File.separator + "small.idx");
		long end = System.currentTimeMillis();
		System.out.println("Time taken to load indices = " + (end - start) + " ms");
	}

//	@Test
	public void testGetTopKTerms()
	{
		long start = System.currentTimeMillis();
		Result result = IndexHandler.getTopKTerms(100);
		long end = System.currentTimeMillis();
		System.out.println(result.getResultString());
		System.out.println("Time taken to find top k terms = " + (end - start) + " ms");
	}
	
//	@Test
	public void testPostingsByDocID()
	{
		long start = System.currentTimeMillis();
//		Result result = IndexHandler.getPostingsFromDocIDIdx("Atlantic");
//		Result result = IndexHandler.getPostingsFromTFIdx("Atlanticasdfwrsfa");
		Result result = IndexHandler.getPostings("Atlanticsfaserwer");
		long end = System.currentTimeMillis();
		if(result.isEmptyResult())
		{
			System.out.println(result.getEmptyResultString());
		}
		else
		{
			System.out.println(result.getResultString());
		}
		System.out.println("Time taken to find postings = " + (end - start) + " ms");
	}

	@Test
	public void testDoDocAtATimeQueryAnd()
	{
		long start = System.currentTimeMillis();
		try
		{
//			IndexHandler.doDocAtATimeQueryAnd("hard", "hand");
			IndexHandler.doDocAtATimeQueryAnd2("hard", "hand", "hope");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		long end = System.currentTimeMillis();
		System.out.println("testDoDocAtATimeQueryAnd2 : Time taken for processing = " + (end - start) + " ms");
	}
	
	
//	@Test
	public void testDoDocAtATimeQueryOr()
	{
		long start = System.currentTimeMillis();
		try
		{
//			IndexHandler.doDocAtATimeQueryOr("hard", "hand");
			IndexHandler.doDocAtATimeQueryOr2("move", "lower", "expect","from","said");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		long end = System.currentTimeMillis();
		System.out.println("Time taken for processing = " + (end - start) + " ms");
	}
	
//	@Test
	public void testDoTermAtATimeQueryAnd()
	{
		long start = System.currentTimeMillis();
		try
		{
//			IndexHandler.doDocAtATimeQueryOr("hard", "hand");
			IndexHandler.doTermAtATimeQueryAnd("move", "lower", "expect","from","said");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		long end = System.currentTimeMillis();
		System.out.println("Time taken for processing = " + (end - start) + " ms");
	}
	
//	@Test
	public void testDoTermAtATimeQueryOr()
	{
		long start = System.currentTimeMillis();
		try
		{
//			IndexHandler.doDocAtATimeQueryOr("hard", "hand");
			IndexHandler.doTermAtATimeQueryOr("move", "lower", "expect","from","said");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		long end = System.currentTimeMillis();
		System.out.println("Time taken for processing = " + (end - start) + " ms");
	}
}
