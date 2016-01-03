package com.buffalo.inforetr.result;

import java.util.List;

import com.buffalo.utils.CommonUtils;

/**
 * 
 * @author Prasanth
 *
 */
public class DAATResult implements Result
{
	private final String functionName;
	private final String[] terms;
	private int comparisons;
	private double elapsedTime;
	private List<Long> docIDs;
	private final boolean isEmptyResult;
	
	public DAATResult(String functionName, String[] terms, boolean isEmptyResult)
	{
		this.functionName = functionName;
		this.terms = terms;
		this.isEmptyResult = isEmptyResult;
	}
	
	@Override
	public String getFunctionName()
	{
		return functionName;
	}

	@Override
	public String getParamsAsString()
	{
		return CommonUtils.getCommaSeparatedString(terms);
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

		if(!isEmptyResult)
		{
			sb.append(docIDs.size()).append(" documents are found").append('\n');
			sb.append(comparisons).append(" comparisons are made").append('\n');
			sb.append(elapsedTime).append(" seconds are used").append('\n');
			sb.append("Result: ");
			sb.append(CommonUtils.getCommaSeparatedString(docIDs));
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
		return "terms not found";
	}

	@Override
	public Object getUserObject()
	{
		return null;
	}

	public void setComparisons(int comparisons)
	{
		this.comparisons = comparisons;
	}

	public void setElapsedTime(double elapsedTime)
	{
		this.elapsedTime = elapsedTime;
	}

	public void setDocIDs(List<Long> docIDs)
	{
		this.docIDs = docIDs;
	}
}
