package com.buffalo.inforetr.result;

import com.buffalo.utils.CommonUtils;

/**
 * 
 * @author Prasanth
 *
 */
public class TopKResult implements Result
{
	private final int k;
	private final String[] topKTerms;
	
	public TopKResult(int k, String[] topKTerms)
	{
		super();
		this.k = k;
		this.topKTerms = topKTerms;
	}

	@Override
	public String getFunctionName()
	{
		return "getTopK";
	}

	@Override
	public String getParamsAsString()
	{
		return k + "";
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
		sb.append("Result: ");
		sb.append(CommonUtils.getCommaSeparatedString(topKTerms));
		return sb.toString();
	}

	@Override
	public boolean isEmptyResult()
	{
		return false;
	}

	@Override
	public String getEmptyResultString()
	{
		return null;
	}

	@Override
	public Object getUserObject()
	{
		return null;
	}
}
