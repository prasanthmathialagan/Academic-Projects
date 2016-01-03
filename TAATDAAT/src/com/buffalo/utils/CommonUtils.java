package com.buffalo.utils;

import java.util.List;

/**
 * 
 * @author Prasanth
 *
 */
public final class CommonUtils
{
	public static<T> String getCommaSeparatedString(T[] arr)
	{
		StringBuilder sb = new StringBuilder();
		
		if(arr != null && arr.length != 0)
		{
			sb.append(arr[0]);
			for (int i = 1; i < arr.length; i++)
				sb.append(", ").append(arr[i]);
		}
		
		return sb.toString();
	}
	
	public static<T> String getCommaSeparatedString(List<T> list)
	{
		StringBuilder sb = new StringBuilder();

		if(list != null && !list.isEmpty())
		{
			sb.append(list.get(0));
			for (int i = 1; i < list.size(); i++)
				sb.append(", ").append(list.get(i));
		}

		return sb.toString();
	}
}
