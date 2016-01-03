package com.buffalo.utils;

import java.io.PrintStream;

/**
 * 
 * @author Prasanth
 *
 */
public class ConsoleLogger implements Logger
{
	private static final ConsoleLogger logger = new ConsoleLogger();
	
	private PrintStream stream = System.out;
	
	private volatile Level level = Level.INFO;

	private ConsoleLogger()
	{
		
	}
	
	public static ConsoleLogger getLogger()
	{
		return logger;
	}
	
	@Override
	public boolean isTraceEnabled()
	{
		return level == Level.TRACE;
	}

	@Override
	public boolean isDebugEnabled()
	{
		return level == Level.DEBUG ||  isTraceEnabled();
	}

	@Override
	public boolean isInfoEnabled()
	{
		return level == Level.INFO || isDebugEnabled() || isTraceEnabled();
	}

	@Override
	public void trace(Object o)
	{
		if(!isTraceEnabled())
			return;
		
		write(o);
	}

	private void write(Object o)
	{
		stream.println(o);
	}

	@Override
	public void debug(Object o)
	{
		if(!isDebugEnabled())
			return;

		write(o);
	}

	@Override
	public void info(Object o)
	{
		if(!isInfoEnabled())
			return;
		
		write(o);
	}

	@Override
	public Level getLevel()
	{
		return level;
	}

	@Override
	public synchronized void setLevel(Level level)
	{
		this.level = level;
	}
}
