package com.buffalo.inforetr.result;

import com.buffalo.inforetr.PostingList;

public class DocIDPostingsResult implements Result
{
	private final String term;
	private final PostingList list;
	private final boolean isEmptyResult;
	
	public DocIDPostingsResult(String term, PostingList list, boolean isEmptyResult)
	{
		this.term = term;
		this.list = list;
		this.isEmptyResult = isEmptyResult;
	}

	@Override
	public String getFunctionName()
	{
		return "getPostingsFromDocIDIdx";
	}

	@Override
	public String getParamsAsString()
	{
		return term;
	}

	@Override
	public String getAdditionalInfo()
	{
		return null;
	}

	@Override
	public String getResultString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Ordered by doc IDs: ");

		if(!isEmptyResult)
		{
			sb.append(list.getList().get(0).getDocId());
			for (int i = 1; i < list.getList().size(); i++)
				sb.append(", ").append(list.getList().get(i).getDocId());
		}
		
		return sb.toString();
	}

	@Override
	public boolean isEmptyResult()
	{
		return isEmptyResult;
	}

	@Override
	public String getEmptyResultString()
	{
		return "term not found";
	}

	@Override
	public Object getUserObject()
	{
		return list;
	}
}
