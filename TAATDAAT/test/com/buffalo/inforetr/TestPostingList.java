package com.buffalo.inforetr;

import org.junit.BeforeClass;
import org.junit.Test;

public class TestPostingList
{
	private static PostingList listByTF;
	private static PostingList listByDocID;
	
	@BeforeClass
	public static void beforeClass()
	{
		listByTF = new PostingList("A", 5, true);
		listByDocID = new PostingList("A", 5, false);
	}
	
	@Test
	public void testAddPosting()
	{
		Posting p = new Posting(10000, 1);
		Posting q = new Posting(10010, 33);
		Posting r = new Posting(10008, 12);
		Posting s = new Posting(10003, 22);
		Posting t = new Posting(10005, 10);
		
		listByDocID.addPosting(p);
		listByDocID.addPosting(q);
		listByDocID.addPosting(r);
		listByDocID.addPosting(s);
		listByDocID.addPosting(t);
		
		listByTF.addPosting(p);
		listByTF.addPosting(q);
		listByTF.addPosting(r);
		listByTF.addPosting(s);
		listByTF.addPosting(t);

		System.out.println("By DocID : " + listByDocID);
		System.out.println("By TF : " + listByTF);
	}

}
