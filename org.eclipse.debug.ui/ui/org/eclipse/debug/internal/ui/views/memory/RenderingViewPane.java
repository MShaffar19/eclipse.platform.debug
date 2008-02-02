/*******************************************************************************
 * Copyright (c) 2004, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     WindRiver - Bug 192028 [Memory View] Memory view does not 
 *                 display memory blocks that do not reference IDebugTarget
 *     ARM - Bug 192028 [Memory View] Memory view does not 
 *                 display memory blocks that do not reference IDebugTarget
 *     WindRiver - Bug 216509 [Memory View] typo, s/isMeomryBlockRemoved/isMemoryBlockRemoved
 *******************************************************************************/
package org.eclipse.debug.internal.ui.views.memory;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IMemoryBlockRetrieval;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.views.memory.renderings.CreateRendering;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.contexts.DebugContextEvent;
import org.eclipse.debug.ui.memory.IMemoryRendering;
import org.eclipse.debug.ui.memory.IMemoryRenderingContainer;
import org.eclipse.debug.ui.memory.IMemoryRenderingSite;
import org.eclipse.debug.ui.memory.IMemoryRenderingSynchronizationService;
import org.eclipse.debug.ui.memory.IResettableMemoryRendering;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;



/**
 * Represents a rendering view pane in the Memory View.
 * This hosts the memory view tabs in the view.
 * @since 3.1
 *
 */
public class RenderingViewPane extends AbstractMemoryViewPane implements IMemoryRenderingContainer{

	public static final String RENDERING_VIEW_PANE_ID = DebugUIPlugin.getUniqueIdentifier() + ".MemoryView.RenderingViewPane"; //$NON-NLS-1$
	
	private Hashtable fTabFolderForMemoryBlock = new Hashtable();
	private Hashtable fMemoryBlockFromTabFolder = new Hashtable();

	private AddMemoryRenderingAction fAddMemoryRenderingAction;

	private IAction fRemoveMemoryRenderingAction;
	private ViewPaneRenderingMgr fRenderingMgr;
	
	private IMemoryRenderingSite fRenderingSite;
	private Set fAddedRenderings = new HashSet();
	private Set fAddedMemoryBlocks = new HashSet();

	private boolean fIsDisposed = false;
	
	/**
	 * @param parent is the view hosting this view pane
	 * @param paneId is the identifier assigned by the Memory View
	 * 
	 * Pane id is assigned with the following format.  
	 * Rendering view pane created has its id assigned to 
	 * org.eclipse.debug.ui.MemoryView.RenderingViewPane.#.  
	 * # is a number indicating the order of which the rendering view
	 * pane is created.  First rendering view pane created will have its
	 * id assigned to org.eclipse.debug.ui.MemoryView.RenderingViewPane.1.
	 * Second rendering view pane created will have its id assigned to
	 * org.eclipse.debug.ui.MemoryView.RenderingViewPane.2. and so on.
	 * View pane are created from left to right by the Memory View.
	 * 
	 */
	public RenderingViewPane(IViewPart parent) {
		super(parent);
		
		if (parent instanceof IMemoryRenderingSite)
			fRenderingSite = (IMemoryRenderingSite)parent;
		else
		{
			DebugUIPlugin.logErrorMessage("Parent for the rendering view pane is invalid."); //$NON-NLS-1$
		}	
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.core.memory.IMemoryBlockListener#MemoryBlockAdded(org.eclipse.debug.core.model.IMemoryBlock)
	 */
	public void memoryBlocksAdded(final IMemoryBlock[] memoryBlocks) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				
				if (isDisposed())
					return;
						
				// check condition before doing anything
				if (memoryBlocks == null || memoryBlocks.length <= 0)
					return;
				
				for (int i=0; i<memoryBlocks.length; i++)
				{
					IMemoryBlock memory = memoryBlocks[i];
				
					if (!fTabFolderForMemoryBlock.containsKey(memory))
					{
						createFolderForMemoryBlock(memory);						
					}	
					fAddedMemoryBlocks.add(memory);
					updateToolBarActionsEnablement();
				}
			}});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.core.memory.IMemoryBlockListener#MemoryBlockRemoved(org.eclipse.debug.core.model.IMemoryBlock)
	 */
	public void memoryBlocksRemoved(final IMemoryBlock[] memoryBlocks) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				for (int j=0; j<memoryBlocks.length; j++)
				{
					IMemoryBlock mbRemoved = memoryBlocks[j];
					if (fTabFolderForMemoryBlock == null)
					{
						return;
					}
					
					// get all renderings from this memory block and remove them from the view
					IMemoryRendering[] renderings = fRenderingMgr.getRenderingsFromMemoryBlock(mbRemoved);
					
					for (int k=0; k<renderings.length; k++)
					{
						removeMemoryRendering(renderings[k]);
					}
					
					// remove a the tab folder if the memory block is removed
					TabFolder tabFolder =
						(TabFolder) fTabFolderForMemoryBlock.get(mbRemoved);
					
					if (tabFolder == null)
						continue;
					
					fTabFolderForMemoryBlock.remove(mbRemoved);
					fMemoryBlockFromTabFolder.remove(tabFolder);
					IMemoryBlockRetrieval retrieve = MemoryViewUtil.getMemoryBlockRetrieval(mbRemoved);
					if (retrieve != null)
					{
						if (fTabFolderForDebugView.contains(tabFolder))
						{					
							fTabFolderForDebugView.remove(retrieve);
						}
					}
					
					if (!tabFolder.isDisposed()) {						
						// dispose all view tabs belonging to the tab folder
						TabItem[] items = tabFolder.getItems();
						
						for (int i=0; i<items.length; i++)
						{	
							disposeTab(items[i]);
						}
						
						// dispose the tab folder
						tabFolder.dispose();						
						
						// if this is the top control
						if (tabFolder == fStackLayout.topControl)
						{	
							
							// if memory view is visible and have a selection
							// follow memory view's selection
							
							ISelection selection = DebugUIPlugin.getActiveWorkbenchWindow().getSelectionService().getSelection(IDebugUIConstants.ID_MEMORY_VIEW);
							IMemoryBlock mbToSelect = getMemoryBlock(selection);
							
							if (mbToSelect != null)
							{									
								// memory view may not have got the event and is still displaying
								// the deleted memory block
								if (mbToSelect != mbRemoved)
									handleMemoryBlockSelection(null, mbToSelect);
								else if ((MemoryViewUtil.getMemoryBlockManager().getMemoryBlocks(retrieve).length > 0))
								{
									mbToSelect = MemoryViewUtil.getMemoryBlockManager().getMemoryBlocks(retrieve)[0];
									handleMemoryBlockSelection(null, mbToSelect);										
								}
								else
								{
									emptyFolder();
								}
							}
							else if (MemoryViewUtil.getMemoryBlockManager().getMemoryBlocks(retrieve).length > 0)
							{	// get to the next folder
								mbToSelect = MemoryViewUtil.getMemoryBlockManager().getMemoryBlocks(retrieve)[0];
								handleMemoryBlockSelection(null, mbToSelect);
							}
							else
							{
								emptyFolder();
			
							}
						}
						
						// if not the top control
						// no need to do anything
					}
					
					fAddedMemoryBlocks.remove(mbRemoved);
					updateToolBarActionsEnablement();
				}
			}
		});

	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.ISelectionListener#selectionChanged(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection)
	 */
	public void selectionChanged(final IWorkbenchPart part, final ISelection selection) {
		if (fIsDisposed)
			return;
		
		// do not schedule job if any of these conditions are true
		if(part == RenderingViewPane.this)
			return;
		
		if (!(selection instanceof IStructuredSelection))
			return;
		
		if (selection == AbstractMemoryViewPane.EMPTY)
			return;
		
		UIJob job = new UIJob("RenderingViewPane selectionChanged"){ //$NON-NLS-1$

			public IStatus runInUIThread(IProgressMonitor monitor) {
				try {

					if (isDisposed())
						return Status.OK_STATUS;
					
					if (selection.isEmpty())
					{
						// if the event comes from Memory View
						// pick empty tab folder as the memory view is no longer displaying anything
						if (part.getSite().getId().equals(IDebugUIConstants.ID_MEMORY_VIEW))
						{
							if (part == getMemoryRenderingSite().getSite().getPart())
							{
								IMemoryViewTab lastViewTab = getTopMemoryTab();
								
								if (lastViewTab != null)
									lastViewTab.setEnabled(false);
								
								emptyFolder();
							}
						}
						
						// do not do anything if there is no selection
						// In the case when a debug adpater fires a debug event incorrectly, Launch View sets
						// selection to nothing.  If the view tab is disabled, it erases all the "delta" information
						// in the content.  This may not be desirable as it will cause memory to show up as
						// unchanged when it's actually changed.  Do not disable the view tab until there is a 
						// valid selection.
						
						return Status.OK_STATUS;
					}
					
					// back up current view tab
					IMemoryViewTab lastViewTab = getTopMemoryTab();
					
					if (!(selection instanceof IStructuredSelection))
						return Status.OK_STATUS;

					Object elem = ((IStructuredSelection)selection).getFirstElement();
					
					if (elem instanceof IMemoryBlock)
					{	
						// if the selection event comes from this view
						if (part == getMemoryRenderingSite())
						{
							// find the folder associated with the given IMemoryBlockRetrieval
							IMemoryBlock memBlock = (IMemoryBlock)elem;
							
							// should never get here... added code for safety
							if (fTabFolderForMemoryBlock == null)
							{
								if (lastViewTab != null)
									lastViewTab.setEnabled(false);
								
								emptyFolder();
								return Status.OK_STATUS;		
							}
			
							handleMemoryBlockSelection(lastViewTab, memBlock);
						}
					}
				}
				catch(SWTException se)
				{
					DebugUIPlugin.log(se);
				}
				return Status.OK_STATUS;
			}};
		job.setSystem(true);
		job.schedule();
	}
	
	public void handleMemoryBlockSelection(final IMemoryViewTab lastViewTab, final IMemoryBlock memBlock) {
		// Do not check if the debug target of mb is removed
		// We should not get into this method if the debug target of the memory block is terminated
		// Memory Block Manager gets the terminate event and would have removed all memory blocks
		// associated with the debug target
		// Therefore, we will never try to set a selection to a memory block whose target is terminated

		// check current memory block
		TabFolder currentFolder = (TabFolder) fStackLayout.topControl;
		if (currentFolder != null && !currentFolder.isDisposed()) {
			IMemoryBlock currentBlk = (IMemoryBlock) fMemoryBlockFromTabFolder.get(currentFolder);
			if (currentBlk != null) {
				if (currentBlk == memBlock)
					return;
			}
		}

		if (getTopMemoryTab() != null) {
			if (getTopMemoryTab().getRendering().getMemoryBlock() == memBlock) {
				return;
			}
		}

		// if we've got a tabfolder to go with the IMemoryBlock, display
		// it
		if (fTabFolderForMemoryBlock.containsKey(memBlock)) {
			if (fStackLayout.topControl != (TabFolder) fTabFolderForMemoryBlock.get(memBlock)) {
				setTabFolder((TabFolder) fTabFolderForMemoryBlock.get(memBlock));
				fViewPaneCanvas.layout();
			}
		} else { // otherwise, add a new one
			TabFolder folder = new TabFolder(fViewPaneCanvas, SWT.NULL);

			fTabFolderForMemoryBlock.put(memBlock, folder);
			fMemoryBlockFromTabFolder.put(folder, memBlock);
			setTabFolder((TabFolder) fTabFolderForMemoryBlock.get(memBlock));
			fViewPaneCanvas.layout();
			fAddedMemoryBlocks.add(memBlock);
		}

		// restore view tabs
		IMemoryRendering[] renderings = fRenderingMgr.getRenderingsFromMemoryBlock(memBlock);
		TabFolder toDisplay = (TabFolder) fStackLayout.topControl;

		if (toDisplay.getItemCount() == 0) {
			restoreViewTabs(renderings);
		}

		// disable last view tab as it becomes hidden
		IMemoryViewTab newViewTab = getTopMemoryTab();

		if (lastViewTab != null && lastViewTab != newViewTab) {
			lastViewTab.setEnabled(false);
		}

		if (newViewTab != null) {
			// if new view tab is not already enabled, enable it
			if (!newViewTab.isEnabled()) {
				// if the view tab is visible, enable it
				if (fVisible) {
					newViewTab.setEnabled(fVisible);
				}
			}
		}

		IMemoryViewTab viewTab = getTopMemoryTab();
		if (viewTab != null)
			setRenderingSelection(viewTab.getRendering());

		if (viewTab == null && renderings.length == 0) {
			// do not ever want to put it on the empty folder
			if (toDisplay != fEmptyTabFolder) {
				TabItem newItem = new TabItem(toDisplay, SWT.NULL);
				CreateRendering rendering = new CreateRendering(getInstance());
				rendering.init(getInstance(), memBlock);
				MemoryViewTab createTab = new MemoryViewTab(newItem,rendering, getInstance());
				setRenderingSelection(createTab.getRendering());
			}
		}

		//set toolbar actions enabled/disabled
		updateToolBarActionsEnablement();
	}

	public void memoryBlockRenderingAdded(final IMemoryRendering rendering) {

		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				
				if (isDisposed())
					return;
				
				if (fAddedRenderings.contains(rendering))
					return;

				IMemoryBlock memoryblk = rendering.getMemoryBlock();

				TabFolder tabFolder = (TabFolder) fTabFolderForMemoryBlock.get(memoryblk);
				
				if (tabFolder == null)
				{
					tabFolder = createFolderForMemoryBlock(memoryblk);
				}

				if (tabFolder.getItemCount() >= 1) {
					TabItem[] items = tabFolder.getItems();
					for (int i=0; i<items.length; i++)
					{
						// remove "Create rendering tab"
						TabItem item = items[i];
						if (item.getData() instanceof MemoryViewTab) {
							MemoryViewTab viewTab = (MemoryViewTab) item.getData();
							if (viewTab.getRendering() instanceof CreateRendering) {
								disposeTab(item);
							}
						}
					}
				}
				
				if (tabFolder == fStackLayout.topControl)
				{
					// disable current view tab
					if (getTopMemoryTab() != null) {
						deactivateRendering(getTopMemoryTab());
						getTopMemoryTab().setEnabled(false);
					}						
				}
				fAddedRenderings.add(rendering);
				TabItem tab = new TabItem(tabFolder, SWT.NULL);

				MemoryViewTab viewTab = new MemoryViewTab(tab, rendering,getInstance());
				tabFolder.setSelection(tabFolder.indexOf(tab));
				
				if (tabFolder == fStackLayout.topControl)
				{
					setRenderingSelection(viewTab.getRendering());

					// disable top view tab if the view pane is not visible
					IMemoryViewTab top = getTopMemoryTab();
					if (top != null)
						top.setEnabled(fVisible);
				}
				else
				{
					deactivateRendering(viewTab);
					viewTab.setEnabled(false);
				}

				updateToolBarActionsEnablement();
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.core.memory.IMemoryRenderingListener#MemoryBlockRenderingRemoved(org.eclipse.debug.internal.core.memory.IMemoryRendering)
	 */
	public void memoryBlockRenderingRemoved(final IMemoryRendering rendering) {
		final IMemoryBlock memory = rendering.getMemoryBlock();
		
		// need to run the following code on the UI Thread to avoid invalid thread access exception
		Display.getDefault().asyncExec(new Runnable()
		{
			public void run()
			{
				if (!fAddedRenderings.contains(rendering))
					return;
				
				fAddedRenderings.remove(rendering);
				
				TabFolder tabFolder = (TabFolder) fStackLayout.topControl;
				
				if (tabFolder.isDisposed())
					return;
				
				TabItem[] tabs = tabFolder.getItems();
				boolean foundTab = false;
				for (int i = 0; i < tabs.length; i++)
				{
					IMemoryViewTab viewTab = (IMemoryViewTab) tabs[i].getData();
					
					if (tabs[i].isDisposed())
						continue;
					

					if (viewTab.getRendering().getMemoryBlock() == memory)
					{
						if (viewTab.getRendering() == rendering)
						{
							foundTab = true;
							disposeTab(tabs[i]);
							break;
						}
						
					}
				}

				// if a tab is not found in the current top control
				// this deletion is a result of a debug target termination
				// find memory from other folder and dispose the view tab
				if (!foundTab)
				{
					Enumeration enumeration = fTabFolderForMemoryBlock.elements();
					while (enumeration.hasMoreElements())
					{
						TabFolder otherTabFolder = (TabFolder) enumeration.nextElement();
						tabs = otherTabFolder.getItems();
						IMemoryViewTab viewTab = null;
						for (int i = 0; i < tabs.length; i++)
						{
							viewTab = (IMemoryViewTab) tabs[i].getData();
							if (viewTab.getRendering().getMemoryBlock() == memory)
							{
								if (viewTab.getRendering() == rendering)
								{
									foundTab = true;
									disposeTab(tabs[i]);
									break;
								}
							}
						}
					}
				}
				IMemoryViewTab top = getTopMemoryTab();
				
				// update selection
				if (top != null)
					setRenderingSelection(top.getRendering());
				else
				{
					if (tabFolder != fEmptyTabFolder)
					{
						IDebugTarget target = memory.getDebugTarget();
						
						// do not create if the target is already terminated or if the memory block is removed
						if (target != null && !target.isDisconnected() && !target.isTerminated() && !isMemoryBlockRemoved(memory))
						{
							TabItem newItem = new TabItem(tabFolder, SWT.NULL);
							CreateRendering createRendering = new CreateRendering(getInstance());
							createRendering.init(getInstance(), memory);
				 		
							MemoryViewTab viewTab = new MemoryViewTab(newItem, createRendering, getInstance());
							tabFolder.setSelection(0);
							setRenderingSelection(viewTab.getRendering());
						}
					}
				}
					
				updateToolBarActionsEnablement();
			}
		});
		
	}
	
	/**
	 * @param memoryBlock
	 * @return if this memory block is removed
	 */
	private boolean isMemoryBlockRemoved(IMemoryBlock memoryBlock)
	{
		IMemoryBlockRetrieval retrieval = MemoryViewUtil.getMemoryBlockRetrieval(memoryBlock);		
		boolean removed = true;
		if (retrieval != null)
		{
			IMemoryBlock[] memoryBlocks = DebugPlugin.getDefault().getMemoryBlockManager().getMemoryBlocks(retrieval);
			for (int i=0; i<memoryBlocks.length; i++)
			{
				if (memoryBlocks[i] == memoryBlock)
					removed = false;
			}
		}
		
		return removed;
	}
	
	
	/**
	 * @param viewTab
	 */
	protected void setRenderingSelection(IMemoryRendering rendering) {

	 	if (rendering != null)
	 	{	
	 		fSelectionProvider.setSelection(new StructuredSelection(rendering));
	 	}
	}
	
	private void restoreViewTabs(IMemoryRendering[] renderings)
	{
		for (int i=0; i<renderings.length; i++)
		{
			memoryBlockRenderingAdded(renderings[i]);
		}
	}
	
	private void handleDebugElementSelection(final IMemoryViewTab lastViewTab, final IAdaptable element)
	{	
		// get current memory block retrieval and debug target
		IMemoryBlockRetrieval currentRetrieve = null;
		
		// get tab folder
		TabFolder tabFolder = (TabFolder) fStackLayout.topControl;
		
		// get memory block
		IMemoryBlock currentBlock = (IMemoryBlock)fMemoryBlockFromTabFolder.get(tabFolder);
		
		if (currentBlock != null)
		{	
			currentRetrieve = MemoryViewUtil.getMemoryBlockRetrieval(currentBlock);
			
			// backup current retrieve and tab folder
			if (currentRetrieve != null && tabFolder != null)
			{
				fTabFolderForDebugView.put(currentRetrieve, tabFolder);
			}
		}
		
		// find the folder associated with the given IMemoryBlockRetrieval
		IMemoryBlockRetrieval retrieve = MemoryViewUtil.getMemoryBlockRetrieval(element);

		// if debug target has changed
		// switch to that tab folder
		if (retrieve != null && retrieve != currentRetrieve)
		{	
			TabFolder folder = (TabFolder)fTabFolderForDebugView.get(retrieve);
			
			if (folder != null)
			{	
				setTabFolder(folder);
				fTabFolderForDebugView.put(retrieve, folder);
				fViewPaneCanvas.layout();
			}
			else
			{	
				// find out if there is any memory block for this debug target
				// and set up tab folder for the memory blocks
				IMemoryBlock blocks[] = MemoryViewUtil.getMemoryBlockManager().getMemoryBlocks(retrieve);
				
				if (blocks.length > 0)
				{	
					handleMemoryBlockSelection(null, blocks[0]);
				}
				else
				{	
					emptyFolder();
					fTabFolderForDebugView.put(retrieve, fEmptyTabFolder);
					fViewPaneCanvas.layout();
				}
			}
		}
		
		// disable last view tab as it becomes hidden
		IMemoryViewTab newViewTab = getTopMemoryTab();

		if (lastViewTab != null && lastViewTab != newViewTab)
		{
			lastViewTab.setEnabled(false);
		}

		if (newViewTab != null)
		{
			// if new view tab is not already enabled, enable it
			if (!newViewTab.isEnabled())
			{
				// if the view tab is visible, enable it
				if (fVisible)
				{
					newViewTab.setEnabled(fVisible);
				}					
			}
			
			// should only change selection if the new view tab is different
			if (lastViewTab != newViewTab)
				setRenderingSelection(newViewTab.getRendering());
		}	
		//set toolbar actions enabled/disabled
		updateToolBarActionsEnablement();
	}
	
	protected void addListeners() {
		super.addListeners();
		
		// must directly listen for selection events from parent's selection provider
		// to ensure that we get the selection event from the tree viewer pane even
		// if the view does not have focuse
		fParent.getSite().getSelectionProvider().addSelectionChangedListener(this);
	}
	protected void removeListeners() {
		super.removeListeners();
		fParent.getSite().getSelectionProvider().removeSelectionChangedListener(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.swt.events.SelectionListener#widgetSelected(org.eclipse.swt.events.SelectionEvent)
	 */
	public void widgetSelected(SelectionEvent e) {
		
		if (getTopMemoryTab() == null)
			return;
		
		IMemoryRendering rendering = getTopMemoryTab().getRendering();
		
		if (rendering != null)
		{
			fSelectionProvider.setSelection(new StructuredSelection(rendering));
		}
		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.swt.events.SelectionListener#widgetDefaultSelected(org.eclipse.swt.events.SelectionEvent)
	 */
	public void widgetDefaultSelected(SelectionEvent e) {
	}
	
	public Object getCurrentSelection() {
		if (getTopMemoryTab() != null)
			if (getTopMemoryTab().getRendering() != null)
				return getTopMemoryTab().getRendering();
		return null;
	}

	/* (non-Javadoc)
	 * @see com.ibm.debug.extended.ui.IMemoryView#getAllViewTabs()
	 */
	public IMemoryViewTab[] getAllViewTabs() {
		
		// otherwise, find the view tab to display
		TabFolder folder = (TabFolder) fStackLayout.topControl;
		TabItem[] items = folder.getItems();
		
		IMemoryViewTab[] viewTabs = new IMemoryViewTab[folder.getItemCount()];
		
		for(int i=0; i<items.length; i++){
			viewTabs[i] = (IMemoryViewTab)items[i].getData();
		}
		
		return viewTabs;
	}

	/* (non-Javadoc)
	 * @see com.ibm.debug.extended.ui.IMemoryView#moveToTop(com.ibm.debug.extended.ui.IMemoryViewTab)
	 */
	public void moveToTop(IMemoryViewTab viewTab) {
		
		IMemoryViewTab lastViewTab = getTopMemoryTab();
		
		if (viewTab == lastViewTab)
			return;
		
		// otherwise, find the view tab to display
		TabFolder folder = (TabFolder) fStackLayout.topControl;
		TabItem[] items = folder.getItems();

		for (int i = 0; i < items.length; i++) {
			IMemoryViewTab tab =
				(IMemoryViewTab) items[i].getData();
			if (viewTab == tab) {

				boolean isEnabled = lastViewTab.isEnabled();

				// switch to that viewTab
				lastViewTab.setEnabled(false);
				folder.setSelection(i);
				
				setRenderingSelection(tab.getRendering());
				
				getTopMemoryTab().setEnabled(isEnabled && fVisible);
				break;
			}
		}
	}

	public void restoreViewPane() {
		
		// get current selection from memory view
		
		ISelection selection = null;
		if (fParent.getSite().getSelectionProvider() != null)
			selection = fParent.getSite().getSelectionProvider().getSelection();
		
		IMemoryBlock memoryBlock = null;
		if (selection != null)
		{
			memoryBlock = getMemoryBlock(selection);
		}
		
		if (memoryBlock == null)
		{	
			// get selection from this view
			selection = fSelectionProvider.getSelection();
			
			if (MemoryViewUtil.isValidSelection(selection))
			{	
				Object elem = ((IStructuredSelection)selection).getFirstElement();

				if (!(elem instanceof IMemoryBlock))
					return;
				
				memoryBlock = (IMemoryBlock)elem;								
			}
		}
		
		if (memoryBlock == null)
		{
			// get a memory block from current debug context
			IAdaptable context = DebugUITools.getDebugContext();
			if (context != null)
			{
				IMemoryBlockRetrieval retrieval = MemoryViewUtil.getMemoryBlockRetrieval(context);

				if (retrieval != null)
				{
					IMemoryBlock[] blocks = DebugPlugin.getDefault().getMemoryBlockManager().getMemoryBlocks(retrieval);
					if (blocks.length > 0)
						memoryBlock = blocks[0];
				}
			}
		}
		
		if (memoryBlock != null)
		{	
			if (!fTabFolderForMemoryBlock.containsKey(memoryBlock))
			{
				// create tab folder if a tab folder does not already exist
				// for the memory block
				TabFolder folder = new TabFolder(fViewPaneCanvas, SWT.NULL);
				
				fTabFolderForMemoryBlock.put(memoryBlock, folder);
				fMemoryBlockFromTabFolder.put(folder, memoryBlock);
				setTabFolder((TabFolder)fTabFolderForMemoryBlock.get(memoryBlock));
				IMemoryBlockRetrieval retrieval = MemoryViewUtil.getMemoryBlockRetrieval(memoryBlock);
				if (retrieval != null)
					fTabFolderForDebugView.put(retrieval, fTabFolderForMemoryBlock.get(memoryBlock));
				else
					DebugUIPlugin.logErrorMessage("Memory block retrieval for memory block is null."); //$NON-NLS-1$
				
				fViewPaneCanvas.layout();
				fAddedMemoryBlocks.add(memoryBlock);
			}
			
			if (fTabFolderForMemoryBlock.containsKey(memoryBlock))
			{
				TabFolder toDisplay = (TabFolder)fTabFolderForMemoryBlock.get(memoryBlock);
				
				if (toDisplay != null)
				{
					setTabFolder(toDisplay);
					IMemoryBlockRetrieval retrieval = MemoryViewUtil.getMemoryBlockRetrieval(memoryBlock);
					
					if (retrieval != null)
						fTabFolderForDebugView.put(retrieval, toDisplay);
					else
						DebugUIPlugin.logErrorMessage("Memory block retrieval is null for memory block."); //$NON-NLS-1$
					
					fViewPaneCanvas.layout();
					
					// restore view tabs
					IMemoryRendering[] renderings = fRenderingMgr.getRenderingsFromMemoryBlock(memoryBlock);
					
					if (toDisplay.getItemCount() == 0 || (getTopMemoryTab().getRendering() instanceof CreateRendering))
					{
						restoreViewTabs(renderings);
					}
				}
			}
			
			// disable current storag block
		
			IMemoryViewTab top = getTopMemoryTab();
		
			if (top != null)
			{
				top.setEnabled(fVisible);
			}
			else
			{
				TabFolder folder = (TabFolder)fStackLayout.topControl;
				if (folder != fEmptyTabFolder)
				{
					TabItem newItem = new TabItem(folder, SWT.NULL);
					CreateRendering rendering = new CreateRendering(this);
					rendering.init(getInstance(), memoryBlock);
					new MemoryViewTab(newItem, rendering, this);
					folder.setSelection(0);
					setRenderingSelection(rendering);
				}
			}
		}
	}
	
	public void dispose() {
		fIsDisposed = true;
		super.dispose();
		fAddMemoryRenderingAction.dispose();
		
		fTabFolderForMemoryBlock.clear();
		fTabFolderForMemoryBlock = null;
		
		fMemoryBlockFromTabFolder.clear();
		fMemoryBlockFromTabFolder = null;
		
		fRenderingMgr.dispose();
		fRenderingMgr = null;
		
		fAddedMemoryBlocks.clear();
		fAddedRenderings.clear();
	}	
	
	public Control createViewPane(Composite parent, String paneId, String label) {
		Control control =  super.createViewPane(parent, paneId, label);
		fRenderingMgr = new ViewPaneRenderingMgr(this);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, IDebugUIConstants.PLUGIN_ID + ".MemoryRenderingView_context"); //$NON-NLS-1$
		return control;
	}
	
	public IAction[] getActions() {
		ArrayList actions = new ArrayList();
		
		if (fAddMemoryRenderingAction == null)
			fAddMemoryRenderingAction = new AddMemoryRenderingAction(this);
		actions.add(fAddMemoryRenderingAction);
		
		if (fRemoveMemoryRenderingAction == null)
			fRemoveMemoryRenderingAction = new RemoveMemoryRenderingAction(this);
		
		fRemoveMemoryRenderingAction.setEnabled(false);
		actions.add(fRemoveMemoryRenderingAction);
		
		return (IAction[])actions.toArray(new IAction[actions.size()]);
	}
	
	// enable/disable toolbar action 
	protected void updateToolBarActionsEnablement()
	{
		Object context = DebugUITools.getDebugContext();
		if (context != null)
		{	
			ISelection selection = fSelectionProvider.getSelection();
			if (selection != null && !selection.isEmpty() && selection instanceof IStructuredSelection)
			{
				Object sel = ((IStructuredSelection)selection).getFirstElement();
				if (sel instanceof CreateRendering)
					fRemoveMemoryRenderingAction.setEnabled(false);
				else
					fRemoveMemoryRenderingAction.setEnabled(true);						
			}
			else
			{
				fRemoveMemoryRenderingAction.setEnabled(false);
			}
		}
		else
		{	
			fRemoveMemoryRenderingAction.setEnabled(false);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.AbstractMemoryViewPane#emptyFolder()
	 */
	protected void emptyFolder() {
		super.emptyFolder();
		updateToolBarActionsEnablement();
		fSelectionProvider.setSelection(AbstractMemoryViewPane.EMPTY);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IRenderingViewPane#addMemoryRendering(org.eclipse.debug.internal.ui.views.memory.IMemoryRendering)
	 */
	public void addMemoryRendering(IMemoryRendering rendering) {
		
		if (rendering == null)
			return;

		memoryBlockRenderingAdded(rendering);
		fRenderingMgr.addMemoryBlockRendering(rendering);
		
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IRenderingViewPane#removeMemoryRendering(org.eclipse.debug.internal.ui.views.memory.IMemoryRendering)
	 */
	public void removeMemoryRendering(IMemoryRendering rendering) {
		
		if (rendering == null)
			return;
		
		memoryBlockRenderingRemoved(rendering);
		
		if (fRenderingMgr != null)
			fRenderingMgr.removeMemoryBlockRendering(rendering);
		
	}
	
	private RenderingViewPane getInstance()
	{
		return this;
	}
	
	private IMemoryBlock getMemoryBlock(ISelection selection)
	{
		if (!(selection instanceof IStructuredSelection))
			return null;

		//only single selection of PICLDebugElements is allowed for this action
		if (selection.isEmpty() || ((IStructuredSelection)selection).size() > 1)
		{
			return null;
		}

		Object elem = ((IStructuredSelection)selection).getFirstElement();
		
		if (elem instanceof IMemoryBlock)
			return (IMemoryBlock)elem;
		else if (elem instanceof IMemoryRendering)
			return ((IMemoryRendering)elem).getMemoryBlock();
		else
			return null;
	}
	
	private void deactivateRendering(IMemoryViewTab viewTab)
	{
		if (viewTab == null)
			return;

		if (!viewTab.isDisposed())
		{		
			viewTab.getRendering().deactivated();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IRenderingViewPane#getMemoryRenderingSite()
	 */
	public IMemoryRenderingSite getMemoryRenderingSite() {
		return fRenderingSite;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.memory.IMemoryRenderingContainer#getId()
	 */
	public String getId() {
		return getPaneId();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.memory.IMemoryRenderingContainer#getRenderings()
	 */
	public IMemoryRendering[] getRenderings() {
		return fRenderingMgr.getRenderings();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.memory.IMemoryRenderingContainer#getActiveRendering()
	 */
	public IMemoryRendering getActiveRendering() {
		if (getTopMemoryTab() == null)
			return null;
		return getTopMemoryTab().getRendering();
	}
	
	/**
	 * Reset the memory renderings within this view pane.
	 * @param memoryBlock - reset renderings associated with the given memory block
	 * @param resetVisible - reset what's currently visible if the parameter is true.
	 * Otherwise, the view pane will reset all renderings associated with the given 
	 * memory block.
	 */
	public void resetRenderings(IMemoryBlock memoryBlock, boolean resetVisible)
	{
		// if we only reset what's visible and the view pane is not visible
		// do nothing.
		if (resetVisible && !isVisible())
			return;
		
		if(resetVisible)
		{
			IMemoryRendering rendering = getActiveRendering();
			if (rendering != null)
			{
				if (rendering.getMemoryBlock() == memoryBlock)
				{
					if (rendering instanceof IResettableMemoryRendering)
					{
						IResettableMemoryRendering resettableRendering = (IResettableMemoryRendering)rendering;
						try {
							resettableRendering.resetRendering();
						} catch (DebugException e) {
							// do not pop up error message
							// error message is annoying where there are multiple rendering
							// panes and renderings to reset
						}
					}
				}
			}
		}
		else
		{
			// get all renderings associated with the given memory block
			IMemoryRendering[] renderings = fRenderingMgr.getRenderingsFromMemoryBlock(memoryBlock);
			
			// back up current synchronization provider
			IMemoryRendering originalProvider = null;
			IMemoryRenderingSynchronizationService service = getMemoryRenderingSite().getSynchronizationService();
			if (service != null)
				originalProvider = service.getSynchronizationProvider();
			
			for (int i=0; i<renderings.length; i++)
			{
				if (renderings[i] instanceof IResettableMemoryRendering)
				{
					try {
						
						// This is done to allow user to select multiple memory monitors and 
						// reset their renderings.
						// In this case, a hidden rendering will not be the sync provider to the sync
						// service.  When the reset happens, the top visible address and selected
						// address is not updated in the sync service.  When the rendering later
						// becomes visible, the rendering gets the sync info from the sync service
						// and will try to sync up with old information, giving user the impression
						// that the rendering was never reset.  By forcing the rendering that we
						// are trying to reset as the synchronization provider, we ensure that
						// the rendering is able to update its sync info even though the rendering
						// is currently hidden.
						if (service != null)
							service.setSynchronizationProvider(renderings[i]);
						((IResettableMemoryRendering)renderings[i]).resetRendering();
					} catch (DebugException e) {
						// do not pop up error message
						// error message is annoying where there are multiple rendering
						// panes and renderings to reset
					}
				}
			}
			
			// restore synchronization provider
			if (service != null)
				service.setSynchronizationProvider(originalProvider);
		}
	}
	
	private boolean isDisposed()
	{
		return fIsDisposed;
	}

	public void contextActivated(final ISelection selection) {
		
		UIJob job = new UIJob("contextActivated"){ //$NON-NLS-1$
			public IStatus runInUIThread(IProgressMonitor monitor) 
			{
				if (isDisposed())
					return Status.OK_STATUS;
				
				IMemoryViewTab lastViewTab = getTopMemoryTab();
				
				if (MemoryViewUtil.isValidSelection(selection))
				{
					if (!(selection instanceof IStructuredSelection))
						return Status.OK_STATUS;

					Object elem = ((IStructuredSelection)selection).getFirstElement();
					
					if (elem instanceof IAdaptable)
					{	
						handleDebugElementSelection(lastViewTab, (IAdaptable)elem);
					}
				}
				else
				{
					if (lastViewTab != null)
						lastViewTab.setEnabled(false);
					
					if (fStackLayout.topControl != fEmptyTabFolder)
						emptyFolder();
					
				}
				return Status.OK_STATUS;
			}};
		job.setSystem(true);
		job.schedule();
	}

	/**
	 * @param memory
	 */
	private TabFolder createFolderForMemoryBlock(IMemoryBlock memory) {
		
		
			TabFolder folder =  new TabFolder(fViewPaneCanvas, SWT.NULL);
			
			fTabFolderForMemoryBlock.put(memory, folder);
			fMemoryBlockFromTabFolder.put(folder, memory);
			
			IMemoryBlockRetrieval retrieval = MemoryViewUtil.getMemoryBlockRetrieval(memory);
			if (retrieval != null)
			{
				fTabFolderForDebugView.put(retrieval, folder);
			}
			else {
				DebugUIPlugin.logErrorMessage("Memory block retrieval for memory block is null"); //$NON-NLS-1$
			}
			
			// check renderings, only create if there is no rendering
			IMemoryRendering[] renderings = fRenderingMgr.getRenderingsFromMemoryBlock(memory);
			if (renderings.length == 0)
			{
				TabItem newItem = new TabItem(folder, SWT.NULL);
				CreateRendering rendering = new CreateRendering(getInstance());
				rendering.init(getInstance(), memory);
				new MemoryViewTab(newItem, rendering, getInstance());
				folder.setSelection(0);
			}
			
			return folder;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.contexts.provisional.IDebugContextListener#contextEvent(org.eclipse.debug.internal.ui.contexts.provisional.DebugContextEvent)
	 */
	public void debugContextChanged(DebugContextEvent event) {
		if ((event.getFlags() & DebugContextEvent.ACTIVATED) > 0) {
			contextActivated(event.getContext());
		}
		
	}
}
