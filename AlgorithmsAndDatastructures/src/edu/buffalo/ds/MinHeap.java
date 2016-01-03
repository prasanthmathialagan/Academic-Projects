package edu.buffalo.ds;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Prasanth
 *
 */
public class MinHeap
{
	private List<Integer> list = new ArrayList<Integer>();

	// Initializers
	{
		list.add(0);
	}
	
	public void addElement(Integer e)
	{
		if(e == null)
			throw new NullPointerException("Element cannot be null");
		
		list.add(e);
		balanceFromBottom();
	}

	private void balanceFromBottom()
	{
		int parentIndex = getSize() / 2;
		int childIndex = getSize();
		while(parentIndex > 0)
		{
			Integer parent = list.get(parentIndex);
			Integer child = list.get(childIndex);
			if(parent.intValue() <= child.intValue()) // If the parent is lesser
				break;
			
			// If the parent is greater, flip the parent and child
			list.set(parentIndex, child);
			list.set(childIndex, parent);
			
			// Make the current parent as the child and parent's parent as the parent
			childIndex = parentIndex;
			parentIndex = parentIndex / 2;
		}
	}
	
	public Integer remove()
	{
		if(getSize() == 0)
			throw new RuntimeException("Cannot remove the root from the ");
		
		// Get the root element
		Integer root = list.get(1);

		// Remove the leaf element and set it as the root
		Integer leaf = list.remove(getSize());
		list.set(1, leaf);

		balanceFromTop();
		
		return root;
	}

	private void balanceFromTop()
	{
		// Balance
		int parentIndex = 1;
		while(getSize() >= getLeftChildIndex(parentIndex))
		{
			Integer parent = list.get(parentIndex);
			
			int leftChildIndex = getLeftChildIndex(parentIndex);
			Integer leftChild = list.get(leftChildIndex);
			int rightChildIndex = getRightChildIndex(parentIndex);
			Integer rightChild = getSize() >= rightChildIndex ? list.get(rightChildIndex) : Integer.MAX_VALUE;
			
			if(parent > leftChild && leftChild < rightChild)
			{
				// Move the left child to the top
				list.set(parentIndex, leftChild);
				list.set(leftChildIndex, parent);
				parentIndex = leftChildIndex;
			}
			else if(parent > rightChild && rightChild < leftChild)
			{
				// Move the right child to the top
				list.set(parentIndex, rightChild);
				list.set(rightChildIndex, parent);
				parentIndex = rightChildIndex;
			}
			else // No action needed
			{
				break;
			}
		}
	}

	private int getRightChildIndex(int parentIndex)
	{
		return getLeftChildIndex(parentIndex) + 1;
	}

	private int getLeftChildIndex(int parentIndex)
	{
		return parentIndex*2;
	}
	
	public int getSize()
	{
		return list.size() - 1;
	}
	
	public Integer getRootElement()
	{
		if(getSize() == 0)
			return null;
		
		return list.get(1);
	}

	@Override
	public String toString()
	{
		if(getSize() == 0)
			return "[]";
		
		return list.subList(1, getSize()).toString();
	}
}
