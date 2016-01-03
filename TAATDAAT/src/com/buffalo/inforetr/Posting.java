package com.buffalo.inforetr;

/**
 * @author Prasanth
 *
 */
public class Posting
{
	private final long docId;
	private final int frequency;
	
	public Posting(long docId, int frequency)
	{
		this.docId = docId;
		this.frequency = frequency;
	}

	public long getDocId()
	{
		return docId;
	}

	public int getFrequency()
	{
		return frequency;
	}

	@Override
	public String toString()
	{
		return "Posting [docId=" + docId + ", frequency=" + frequency + "]";
	}
}
