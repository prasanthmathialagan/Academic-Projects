package com.buffalo.inforetr.result;

/**
 * 
 * @author Prasanth
 *
 */
public class PostingsResult implements Result
{
	private final String term;
	private final DocIDPostingsResult docIDResult;
	private final TFPostingsResult tfResult;
	private final boolean isEmptyResult;
	
	public PostingsResult(String term, DocIDPostingsResult docIDResult, TFPostingsResult tfResult,
			boolean isEmptyResult)
	{
		super();
		this.term = term;
		this.docIDResult = docIDResult;
		this.tfResult = tfResult;
		this.isEmptyResult = isEmptyResult;
	}

	@Override
	public String getFunctionName()
	{
		return "getPostings";
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
		
		if(!isEmptyResult)
		{
			sb.append(docIDResult.getResultString());
			sb.append("\n");
			sb.append(tfResult.getResultString());
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
		return null;
	}

}
