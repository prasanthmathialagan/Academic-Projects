package com.buffalo.inforetr.result;

/**
 * @author Prasanth
 *
 */
public interface Result
{
	String getFunctionName();
	
	String getParamsAsString();
	
	String getAdditionalInfo();
	
	String getResultString();
	
	boolean isEmptyResult();
	
	String getEmptyResultString();
	
	Object getUserObject();
}
