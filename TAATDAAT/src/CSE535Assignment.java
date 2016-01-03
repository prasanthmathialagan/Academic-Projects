import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.buffalo.inforetr.IndexHandler;
import com.buffalo.inforetr.result.Result;
import com.buffalo.utils.CommonUtils;
import com.buffalo.utils.ConsoleLogger;
import com.buffalo.utils.Level;
import com.buffalo.utils.Logger;

/**
 * @author Prasanth
 *
 */
public final class CSE535Assignment
{
	private static final Logger logger = ConsoleLogger.getLogger();
	
	// java CSE535Assignment term.idx output.log 10 query_file.txt
	public static void main(String[] args) throws IOException
	{
		// TODO : Remove
		logger.setLevel(Level.TRACE);
		
		long start = System.currentTimeMillis();

		if(logger.isTraceEnabled())
			logger.trace("Arguments : " + CommonUtils.getCommaSeparatedString(args));
		
		if(args.length != 4)
		{
			System.err.println("Not enough arguments. Usage : java CSE535Assignment <index_file> <output_file> <K> <query_file>");
			System.exit(1);
		}

		String indexFile = args[0];
		String logFile = args[1];
		int k = Integer.parseInt(args[2]);
		String queryFile = args[3];

		try
		{
			long startIdx = System.currentTimeMillis();
			IndexHandler.loadIndices(indexFile);
			long endIdx = System.currentTimeMillis();
			if(logger.isDebugEnabled())
				logger.debug("Time taken to load index = " + (endIdx - startIdx) + " ms");
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
			System.exit(1);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			System.exit(1);
		}
		
		FileWriter writer = new FileWriter(logFile, false);
		
		// top k terms
		long startKTerms = System.currentTimeMillis();
		writeResult(writer, IndexHandler.getTopKTerms(k));
		long endKTerms = System.currentTimeMillis();
		if(logger.isDebugEnabled())
			logger.debug("Time taken to get top " + k + " terms = " + (endKTerms - startKTerms) + " ms");
		
		BufferedReader queryFileReader = new BufferedReader(new FileReader(queryFile));
		String queryTerms = queryFileReader.readLine();
		while(queryTerms != null)
		{
			String[] terms = queryTerms.split(" ");
			
			long startQuery = System.currentTimeMillis();
			
			// postings
			for (String term : terms)
				writeResult(writer, IndexHandler.getPostings(term));
			
			// termAtATimeQueryAnd
			writeResult(writer, IndexHandler.doTermAtATimeQueryAnd(terms));
			
			// termAtATimeQueryOr
			writeResult(writer, IndexHandler.doTermAtATimeQueryOr(terms));
			
			// docAtATimeQueryAnd
			writeResult(writer, IndexHandler.doDocAtATimeQueryAnd2(terms));
			
			// docAtATimeQueryOr
			writeResult(writer, IndexHandler.doDocAtATimeQueryOr2(terms));
			
			long endQuery = System.currentTimeMillis();
			
			if(logger.isDebugEnabled())
				logger.debug("Time taken to process \""+ queryTerms +"\" = " +(endQuery - startQuery) + " ms");
			
			queryTerms = queryFileReader.readLine();
		}
		
		queryFileReader.close();
		
		writer.flush();
		writer.close();
		
		long end = System.currentTimeMillis();
		
		if(logger.isDebugEnabled())
			logger.debug("Total time taken for the execution = " + (end - start) + " ms");
	}
	
	private static void writeResult(FileWriter writer, Result result) throws IOException
	{
		writer.write("FUNCTION: " + result.getFunctionName() + " " + result.getParamsAsString());
		newLine(writer);
		if (result.isEmptyResult())
			writer.write(result.getEmptyResultString());
		else
			writer.write(result.getResultString());
		writer.flush();

		newLine(writer);
	}

	private static void newLine(FileWriter writer) throws IOException
	{
		writer.append('\n');
	}
}
