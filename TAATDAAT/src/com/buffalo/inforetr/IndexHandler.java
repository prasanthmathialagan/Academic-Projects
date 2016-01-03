package com.buffalo.inforetr;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.buffalo.inforetr.comparators.DocIDComparator;
import com.buffalo.inforetr.result.DAATResult;
import com.buffalo.inforetr.result.DocIDPostingsResult;
import com.buffalo.inforetr.result.PostingsResult;
import com.buffalo.inforetr.result.Result;
import com.buffalo.inforetr.result.TAATResult;
import com.buffalo.inforetr.result.TFPostingsResult;
import com.buffalo.inforetr.result.TopKResult;
import com.buffalo.utils.ConsoleLogger;
import com.buffalo.utils.Logger;

/**
 * 
 * @author Prasanth
 *
 */
public final class IndexHandler
{
	private static final Logger logger = ConsoleLogger.getLogger();
	
	private static Pattern idxFileLinePattern = Pattern.compile("(.+)\\\\c(\\d+)\\\\m\\[(.+)\\]");
	
	private static final HashMap<String, PostingList> posSortedByDocIDIdx = new HashMap<>();
	
	private static final HashMap<String, PostingList>posDesSortedByTFIdx = new HashMap<>();
	
	/**
	 * 
	 * @param fileName
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static void loadIndices(String fileName) throws FileNotFoundException, IOException
	{
		try(BufferedReader reader = new BufferedReader(new FileReader(fileName)))
		{
			String line = reader.readLine();
			while(line != null)
			{
				Matcher matcher = idxFileLinePattern.matcher(line);
				boolean matches = matcher.matches();
				if(matches)
				{
					String term = matcher.group(1);
					int size = Integer.parseInt(matcher.group(2));
					PostingList listSortedByDocID = new PostingList(term, size, false);
					PostingList listSortedByTF = new PostingList(term, size, true);

					String postingsString = matcher.group(3);
					String[] split = postingsString.split(",");
					for (String s : split)
					{
						String[] postSplit = s.split("/");
						long docId = Long.parseLong(postSplit[0].trim());
						int frequency = Integer.parseInt(postSplit[1].trim());
						
						Posting posting = new Posting(docId, frequency);
						listSortedByDocID.addPosting(posting);
						listSortedByTF.addPosting(posting);
					}
					
					posSortedByDocIDIdx.put(term, listSortedByDocID);
					posDesSortedByTFIdx.put(term, listSortedByTF);
				}
				else
				{
					throw new RuntimeException("Line does not match the pattern : " +line);
				}
				
				line = reader.readLine();
			}
			
			if(logger.isDebugEnabled())
				logger.debug("Index size = " + posSortedByDocIDIdx.size());
		}
	}
	
	// O(n)
	public static Result getTopKTerms(int k)
	{
		int size = k > posSortedByDocIDIdx.size() ? posSortedByDocIDIdx.size() : k;
		int[] arr = new int[size];
		String[] terms = new String[size];

		int currentSize = 0;
		for (Entry<String, PostingList> entry : posSortedByDocIDIdx.entrySet())
		{
			String term = entry.getKey();
			int value = entry.getValue().getSize();
			currentSize = insert(currentSize, term, value, arr, terms, size);
		}
		
		Result result = new TopKResult(k, terms);
		return result;
	}
	
	private static int insert(int currentSize, String term, int value, int[] arr, String[] terms, int maxSize)
	{
		if(currentSize < maxSize)
		{
			for (int i = 0; i < currentSize; i++)
			{
				if (value >= arr[i])
				{
					for (int j = currentSize; j > i; j--)
					{
						arr[j] = arr[j - 1];
						terms[j] = terms[j - 1];
					}
					
					arr[i] = value;
					terms[i] = term;
					return currentSize + 1;
				}
			}
			
			arr[currentSize] = value;
			terms[currentSize] = term;
			return currentSize + 1;
		}
		
		for (int i = 0; i < currentSize; i++)
		{
			if (value >= arr[i])
			{
				for (int j = currentSize - 1; j > i; j--)
				{
					arr[j] = arr[j - 1];
					terms[j] = terms[j - 1];
				}

				arr[i] = value;
				terms[i] = term;

				return currentSize;
			}
		}

		return currentSize;
	}
	
	/**
	 * 
	 * @param term
	 * @return
	 */
	public static Result getPostings(String term)
	{
		Result docIDResult = getPostingsFromDocIDIdx(term);
		if(docIDResult.isEmptyResult())
			return new PostingsResult(term, null, null, true);
		
		Result tfResult = getPostingsFromTFIdx(term);
		return new PostingsResult(term, (DocIDPostingsResult)docIDResult, (TFPostingsResult)tfResult, false);
	}
	
	/**
	 * 
	 * @param term
	 * @return
	 */
	public static Result getPostingsFromDocIDIdx(String term)
	{
		PostingList list = getPostingsListFromDocIDIdx(term);
		DocIDPostingsResult result = new DocIDPostingsResult(term, list, list == null);
		return result;
	}

	private static PostingList getPostingsListFromDocIDIdx(String term)
	{
		return posSortedByDocIDIdx.get(term);
	}
	
	/**
	 * 
	 * @param term
	 * @return
	 */
	public static Result getPostingsFromTFIdx(String term)
	{
		PostingList list = getPostingsListFromTFIdx(term);
		TFPostingsResult result = new TFPostingsResult(term, list, list == null);
		return result;
	}

	private static PostingList getPostingsListFromTFIdx(String term)
	{
		return posDesSortedByTFIdx.get(term);
	}

	/**
	 * 
	 * @param terms
	 * @return
	 */
	public static Result doDocAtATimeQueryAnd2(String... terms)
	{
		String functionName = "docAtATimeQueryAnd";
		
		long startT = System.currentTimeMillis();
		
		List<PostingList> postingsForTerms = new ArrayList<>();
		
		for (String term : terms)
		{
			PostingList list = getPostingsListFromDocIDIdx(term);
			if(list == null)
				return new DAATResult(functionName, terms, true);
			postingsForTerms.add(list);
		}
		
		List<Long> docIDs = new ArrayList<>();
		int[] termPtrs = new int[postingsForTerms.size()];
		int comparisons = 0;

		boolean postingsEnded = false; // To indicate if postings ended for any of the terms.
		
		PostingList term1PosList = postingsForTerms.get(0);

		/* Loop until one of the following conditions is met. 
		 *		-	if the pointer for the first term reached the end of the postings list.
		 *		- 	if the pointer for any of the other terms reached the end of the postings list.
		 */
		while(!postingsEnded && termPtrs[0] < term1PosList.getSize())
		{
			long term1DocId = term1PosList.getList().get(termPtrs[0]).getDocId();
			
			for (int i = 1; i < postingsForTerms.size(); i++)
			{
				// To indicate that we have to start from the first term again. This condition is set if the current term doc id becomes greater than that of the first term.
				boolean breakToFirst = false;   

				PostingList iTermPosList = postingsForTerms.get(i);

				// If the pointer for any of the terms reached the end of the postings list.
				if(termPtrs[i] == iTermPosList.getSize())
				{
					postingsEnded = true;
					breakToFirst = true;
				}
				
				// Traverse through the postings list of the term
				while(termPtrs[i] < iTermPosList.getSize())
				{
					long iTermDocID = iTermPosList.getList().get(termPtrs[i]).getDocId();
					comparisons++;
					
					// If the doc id of the ith term is less than that of the first term, then move the pointer
					if(iTermDocID < term1DocId)
					{
						termPtrs[i]++;
						continue;
					}
					
					// If the doc id of the ith term is greater than that of the first term, then resume from the first term.
					else if(iTermDocID > term1DocId)
					{
						breakToFirst = true;
						break;
					}
					else // if the doc ids are same
					{
						// if all the terms contain the same doc id, then it is a match and we have to resume from the first term
						if(i == postingsForTerms.size() - 1)
						{
							docIDs.add(iTermDocID);
							breakToFirst = true;
						}
						
						termPtrs[i]++;
						break;
					}
				}
				
				if(breakToFirst)
				{
					termPtrs[0]++;
					break;
				}
			}
		}
		
		long endT = System.currentTimeMillis();
		
		double elapsed = (endT - startT)/1000.0;
		
		DAATResult result = new DAATResult(functionName, terms, false);
		result.setComparisons(comparisons);
		result.setDocIDs(docIDs);
		result.setElapsedTime(elapsed);
		
		return result;
	}
	
	/**
	 * 
	 * @param terms
	 * @return
	 */
	public static Result doDocAtATimeQueryOr2(String... terms)
	{
		String functionName = "docAtATimeQueryOr";
		
		long start = System.currentTimeMillis();
		
		List<PostingList> postingsForTerms = new ArrayList<>();
		
		for (String term : terms)
		{
			PostingList list = getPostingsListFromDocIDIdx(term);
			if(list != null)
				postingsForTerms.add(list);
		}
		
		// If postings cannot be found for any terms, empty result is returned.
		if(postingsForTerms.isEmpty())
			return new DAATResult(functionName, terms, true);

		List<Long> docIDs = new ArrayList<>();
		int[] termPtrs = new int[postingsForTerms.size()];
		int comparisons = 0;
		int postingsTraversed = 0; // To track the number of postings traversed.
		
		// Loop until the postings lists for all the terms are traversed.
		while(postingsTraversed < postingsForTerms.size())
		{
			long minDocID = Long.MAX_VALUE;
			List<Integer> minTermsList = null;
			
			for (int i = 0; i < postingsForTerms.size(); i++)
			{
				PostingList postings = postingsForTerms.get(i);
				if(postings.getSize() == termPtrs[i])
					continue;

				long docId = postings.getList().get(termPtrs[i]).getDocId();
				
				comparisons++;
				
				// If the docId is less than the min doc id, it is made the min doc id and added to a list for processing duplicates.
				if(docId < minDocID)
				{
					minTermsList = new ArrayList<>();
					minTermsList.add(i);
					minDocID = docId;
				}
				else if(docId == minDocID)
				{
					minTermsList.add(i);
				}
			}
			
			// Adding the minimum document id found. In no way, Long.MAX_VALUE will be added to this list.
			docIDs.add(minDocID);
			
			// Move the pointers for the terms for which the minimum doc id is found
			for (Integer index : minTermsList)
			{
				termPtrs[index]++;
				
				// If the pointer for any term reaches the end of the postings list, postings traversed will be incremented by one.
				if(termPtrs[index] == postingsForTerms.get(index).getSize())
					postingsTraversed++;
			}
		}
	
		long end = System.currentTimeMillis();
		
		double elapsed = (end - start)/1000.0;
		
		DAATResult result = new DAATResult(functionName, terms, false);
		result.setComparisons(comparisons);
		result.setDocIDs(docIDs);
		result.setElapsedTime(elapsed);
		
		return result;
	}
	
	/**
	 * 
	 * @param terms
	 * @return
	 */
	public static Result doTermAtATimeQueryAnd(String... terms)
	{
		String functionName = "termAtATimeQueryAnd";
		
		long start = System.currentTimeMillis();
		
		List<PostingList> postingsForTerms = new ArrayList<>();

		for (String term : terms)
		{
			PostingList list = getPostingsListFromDocIDIdx(term);
			if(list == null)
				return new TAATResult(functionName, terms, true);
			postingsForTerms.add(list);
		}
		
		AtomicInteger counter = new AtomicInteger();
		List<Posting> tempResult = null;
		// Accumulating the temporary result
		for (PostingList postingList : postingsForTerms)
			tempResult = compareAnd(tempResult, postingList.getList().getLinkedList(), counter);
		
		long end = System.currentTimeMillis();
		
		double elapsed = (end - start)/1000.0;
		
		// Sorting the result based on DocIDs
		Collections.sort(tempResult, new DocIDComparator());
		
		List<Long>docIDs = new ArrayList<>();
		for (Posting posting : tempResult)
			docIDs.add(posting.getDocId());
		
		// Optimization code starts
		
		// Sort based on increasing size of postings list
		Collections.sort(postingsForTerms, new Comparator<PostingList>()
		{
			@Override
			public int compare(PostingList o1, PostingList o2)
			{
				return Integer.compare(o1.getSize(), o2.getSize());
			}
		});
		
		AtomicInteger optimCounter = new AtomicInteger();
		List<Posting> optimTempResult = null;
		for (PostingList postingList : postingsForTerms)
			optimTempResult = compareAnd(optimTempResult, postingList.getList().getLinkedList(), optimCounter);
		// Optimization code ends
		
		TAATResult result = new TAATResult(functionName, terms, false);
		result.setComparisons(counter.get());
		result.setOptimizedComparisons(optimCounter.get());
		result.setDocIDs(docIDs);
		result.setElapsedTime(elapsed);
		
		return result;
	}
	
	private static List<Posting> compareAnd(List<Posting> oldTempResult, List<Posting> postings, AtomicInteger counter)
	{
		List<Posting> newTempResult = new ArrayList<>();

		// Adding the postings for the first term to the temp result
		if(oldTempResult == null)
		{
			newTempResult.addAll(postings);
			return newTempResult;
		}
		
		// Comparing the postings between the term and temporary result
		for (Posting oldPosting : oldTempResult)
		{
			for (Posting newPosting : postings)
			{
				counter.incrementAndGet();
				
				if(oldPosting.getDocId() == newPosting.getDocId())
				{
					newTempResult.add(newPosting);
					break;
				}
			}
		}
		
		return newTempResult;
	}
	
	public static Result doTermAtATimeQueryOr(String... terms)
	{
		String functionName = "termAtATimeQueryOr";

		long start = System.currentTimeMillis();

		List<PostingList> postingsForTerms = new ArrayList<>();
		
		for (String term : terms)
		{
			PostingList list = getPostingsListFromDocIDIdx(term);
			if(list != null)
				postingsForTerms.add(list);
		}
		
		if(postingsForTerms.isEmpty())
			return new TAATResult(functionName, terms, true);
		
		AtomicInteger counter = new AtomicInteger();
		
		List<Posting> tempResult = null;
		// Accumulating the temporary result
		for (PostingList postingList : postingsForTerms)
			tempResult = compareOr(tempResult, postingList.getList().getLinkedList(), counter);
		
		long end = System.currentTimeMillis();
		
		double elapsed = (end - start)/1000.0;
		
		// Sorting the result based on DocIDs
		Collections.sort(tempResult, new DocIDComparator());
		
		List<Long>docIDs = new ArrayList<>();
		for (Posting posting : tempResult)
			docIDs.add(posting.getDocId());
		
		// Optimization code starts

		// Sort based on decreasing size of postings list
		Collections.sort(postingsForTerms, new Comparator<PostingList>()
		{
			@Override
			public int compare(PostingList o1, PostingList o2)
			{
				return Integer.compare(o2.getSize(), o1.getSize());
			}
		});

		AtomicInteger optimCounter = new AtomicInteger();
		List<Posting> optimTempResult = null;
		for (PostingList postingList : postingsForTerms)
			optimTempResult = compareOr(optimTempResult, postingList.getList().getLinkedList(), optimCounter);
		// Optimization code ends
		
		TAATResult result = new TAATResult(functionName, terms, false);
		result.setComparisons(counter.get());
		result.setOptimizedComparisons(optimCounter.get());
		result.setDocIDs(docIDs);
		result.setElapsedTime(elapsed);
		
		return result;
	}
	
	private static List<Posting> compareOr(List<Posting> oldTempResult, List<Posting> postings, AtomicInteger counter)
	{
		List<Posting> newTempResult = new ArrayList<>();
		
		// Adding the postings for the first term to the temp result
		if(oldTempResult == null)
		{
			newTempResult.addAll(postings);
			return newTempResult;
		}
		else // Creating a new temporary result with all the old results
		{
			newTempResult.addAll(oldTempResult);
		}

		// Comparing the postings between the term and temporary result
		for (Posting newPosting : postings)
		{
			boolean alreadyPresent = false;
			for (Posting oldPosting : oldTempResult)
			{
				counter.incrementAndGet();
				if(oldPosting.getDocId() == newPosting.getDocId())
				{
					alreadyPresent = true;
					break;
				}
			}
			
			counter.incrementAndGet();
			// If the element is not present in the old temporary result, we add it to the new temporary result.
			if(!alreadyPresent)
				newTempResult.add(newPosting);
		}
		
		return newTempResult;
	}
}
