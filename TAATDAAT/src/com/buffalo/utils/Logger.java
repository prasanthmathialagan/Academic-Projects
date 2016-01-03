package com.buffalo.utils;

/**
 * 
 * @author Prasanth
 *
 */
public interface Logger
{
	boolean isTraceEnabled();
	boolean isDebugEnabled();
	boolean isInfoEnabled();
	
	void trace(Object o);
	void debug(Object o);
	void info(Object o);
	
	Level getLevel();
	void setLevel(Level level);
}
