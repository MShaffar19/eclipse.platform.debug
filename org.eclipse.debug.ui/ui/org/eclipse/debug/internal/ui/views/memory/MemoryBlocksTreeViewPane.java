/*******************************************************************************
 * Copyright (c) 2004, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
 
package org.eclipse.debug.internal.ui.views.memory;

import java.util.ArrayList;
import java.util.Hashtable;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.IMemoryBlockListener;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IMemoryBlockRetrieval;
import org.eclipse.debug.internal.ui.DebugPluginImages;
import org.eclipse.debug.internal.ui.DebugUIMessages;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.internal.ui.memory.provisional.MemoryViewPresentationContext;
import org.eclipse.debug.internal.ui.viewers.AsynchronousTreeViewer;
import org.eclipse.debug.internal.ui.views.variables.ViewerState;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.contexts.DebugContextEvent;
import org.eclipse.debug.ui.contexts.IDebugContextListener;
import org.eclipse.debug.ui.memory.IMemoryRendering;
import org.eclipse.debug.ui.memory.IMemoryRenderingContainer;
import org.eclipse.debug.ui.memory.IMemoryRenderingSite;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;


/**
 * Tree viewer for memory blocks
 */
public class MemoryBlocksTreeViewPane implements ISelectionListener, ISelectionChangedListener,  IMemoryViewPane, IMemoryRenderingContainer{
	
	public static final String PANE_ID = DebugUIPlugin.getUniqueIdentifier() + ".MemoryView.MemoryBlocksTreeViewPane"; //$NON-NLS-1$
	
	private IViewPart fParent;
	private MemoryViewTreeViewer fTreeViewer;
	protected IMemoryBlockRetrieval fRetrieval;
	private ViewPaneSelectionProvider fSelectionProvider;
	private AddMemoryBlockAction fAddMemoryBlockAction;
	private IAction fRemoveMemoryBlockAction;
	private IAction fRemoveAllMemoryBlocksAction;
	private String fPaneId;
	private boolean fVisible = true;
	private TreeViewPaneContextListener fDebugContextListener;
	private ViewPaneEventHandler fEvtHandler;
	private String fLabel;
	
	class TreeViewerRemoveMemoryBlocksAction extends Action
	{
		TreeViewerRemoveMemoryBlocksAction()
		{
			super();
			setText(DebugUIMessages.RemoveMemoryBlockAction_title); 

			setToolTipText(DebugUIMessages.RemoveMemoryBlockAction_tooltip); 
			setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_REMOVE_MEMORY));	
			setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_LCL_REMOVE_MEMORY));
			setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_REMOVE_MEMORY));
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IDebugUIConstants.PLUGIN_ID + ".RemoveMemoryBlockAction_context"); //$NON-NLS-1$
			setEnabled(true);
		}
		
		/* (non-Javadoc)
		 * @see org.eclipse.jface.action.IAction#run()
		 */
		public void run() {
			ISelection selected = fTreeViewer.getSelection();
			
			if (selected != null && selected instanceof IStructuredSelection)
			{
				Object[] selectedMemBlks = ((IStructuredSelection)selected).toArray();
				ArrayList memoryBlocks = new ArrayList();
				for (int i=0; i<selectedMemBlks.length; i++)
				{
					if (selectedMemBlks[i] instanceof IMemoryBlock)
						memoryBlocks.add(selectedMemBlks[i]);
				}
				
				DebugPlugin.getDefault().getMemoryBlockManager().removeMemoryBlocks((IMemoryBlock[])memoryBlocks.toArray(new IMemoryBlock[memoryBlocks.size()]));
			}
		}
	}
	
	class TreeViewerRemoveAllMemoryBlocksAction extends Action
	{
		TreeViewerRemoveAllMemoryBlocksAction()
		{
			super();
			setText(DebugUIMessages.MemoryBlocksTreeViewPane_2); 

			setToolTipText(DebugUIMessages.MemoryBlocksTreeViewPane_2); 
			setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_REMOVE_ALL));	
			setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_REMOVE_ALL));
			setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_REMOVE_ALL));
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IDebugUIConstants.PLUGIN_ID + ".RemoveAllMemoryBlocksAction_context"); //$NON-NLS-1$
		}
		
		/* (non-Javadoc)
		 * @see org.eclipse.jface.action.IAction#run()
		 */
		public void run() {
			
			IWorkbenchWindow window= DebugUIPlugin.getActiveWorkbenchWindow();
			if (window == null) {
				return;
			}
			boolean proceed = MessageDialog.openQuestion(window.getShell(), DebugUIMessages.MemoryBlocksTreeViewPane_0, DebugUIMessages.MemoryBlocksTreeViewPane_1); // 
			if (proceed) {
				IMemoryBlock[] memoryBlocks = DebugPlugin.getDefault().getMemoryBlockManager().getMemoryBlocks(fRetrieval);
				DebugPlugin.getDefault().getMemoryBlockManager().removeMemoryBlocks(memoryBlocks);
			}
		}		
	}
	
	class ViewPaneEventHandler implements IMemoryBlockListener, IDebugEventSetListener
	{
		private boolean fDisposed = false;
		
		public ViewPaneEventHandler()
		{
			DebugPlugin.getDefault().getMemoryBlockManager().addListener(this);
			DebugPlugin.getDefault().addDebugEventListener(this);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
		 */
		public void dispose() {
			fDisposed = true;
			DebugPlugin.getDefault().getMemoryBlockManager().removeListener(this);
			DebugPlugin.getDefault().removeDebugEventListener(this);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.debug.internal.core.memory.IMemoryBlockListener#MemoryBlockAdded(org.eclipse.debug.core.model.IMemoryBlock)
		 */
		public void memoryBlocksAdded(final IMemoryBlock[] memory) {
			// if the content provider is disposed, do not handle event
			if (fDisposed)
				return;
			updateActionsEnablement();
		}

		/* (non-Javadoc)
		 * @see org.eclipse.debug.internal.core.memory.IMemoryBlockListener#MemoryBlockRemoved(org.eclipse.debug.core.model.IMemoryBlock)
		 */
		public void memoryBlocksRemoved(final IMemoryBlock[] memory) {
			if (fDisposed)
				return;
			updateActionsEnablement();
		}

		/* (non-Javadoc)
		 * @see org.eclipse.debug.internal.ui.views.memory.BasicDebugViewContentProvider#doHandleDebugEvent(org.eclipse.debug.core.DebugEvent)
		 */
		protected void doHandleDebugEvent(DebugEvent event) {
			
			// if the view is disposed, do not handle event
			if (fDisposed)
				return;
			
			if (event.getKind() == DebugEvent.TERMINATE)
			{
				// should only handle the terminate event if the target is terminated
				if (event.getSource() instanceof IDebugTarget)
				{
					IMemoryBlockRetrieval srcRetrieval = getMemoryBlockRetrieval(event.getSource());
					if (srcRetrieval == fRetrieval)
					{
						// #setInput must be done on the UI thread
						UIJob job = new UIJob("setInput"){ //$NON-NLS-1$
							public IStatus runInUIThread(IProgressMonitor monitor) {
								
								// if viewpane is disposed, do not handle event							
								if (fTreeViewer.getContentProvider() == null)
									return  Status.OK_STATUS;
								
								fTreeViewer.setInput(null);
								return Status.OK_STATUS;
							}};
							
						job.setSystem(true);
						job.schedule();
					}
				}
			}
		}

		public void handleDebugEvents(DebugEvent[] events) {
			for (int i=0; i<events.length; i++)
			{
				doHandleDebugEvent(events[i]);
			}
		}
	}
	
	class MemoryViewerState extends ViewerState implements Cloneable
	{
		private Hashtable fPathMap = new Hashtable(); 
		private AsynchronousTreeViewer fViewer;
		
		public MemoryViewerState() {}
		public MemoryViewerState(AsynchronousTreeViewer viewer) {
			super(viewer);
			fViewer = viewer;
		}

		protected IPath encodeElement(TreeItem item) throws DebugException {
			if (fViewer != null)
			{
				IPath path = super.encodeElement(item);
				TreePath[] paths = fViewer.getTreePaths(item.getData());
				if (paths.length > 0)
				{
					fPathMap.put(path, paths[0]);
				}
				return path;
			}
			
			return null;
		}

		protected TreePath decodePath(IPath path, AsynchronousTreeViewer viewer) throws DebugException {
			return (TreePath)fPathMap.get(path);
		}
		
		/* (non-Javadoc)
		 * @see org.eclipse.debug.internal.ui.views.AbstractViewerState#clone()
		 */
		public Object clone() {
			MemoryViewerState clone = (MemoryViewerState) super.clone();
			clone.fViewer = fViewer;
			clone.fPathMap = fPathMap;
			return clone;
		}
	}
	
	class TreeViewPaneContextListener implements IDebugContextListener
	{
		public void contextActivated(ISelection selection) {
			
			if (selection.isEmpty() && fRetrieval != null)
			{
				fRetrieval = null;
				if (fTreeViewer != null)
					fTreeViewer.setInput(fRetrieval);
				updateActionsEnablement();
				return;
			}
			
			if (selection instanceof IStructuredSelection)
			{		
				Object obj = ((IStructuredSelection)selection).getFirstElement();
				if (obj instanceof IAdaptable)
				{
					IAdaptable context = (IAdaptable)obj;
					IMemoryBlockRetrieval retrieval = getMemoryBlockRetrieval(context);
					if (retrieval != null && fTreeViewer != null && retrieval != fRetrieval)
					{
						// set new setting
						fRetrieval = retrieval;
						fTreeViewer.setInput(fRetrieval);
					}
					updateActionsEnablement();
				}
			}
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
	
	public MemoryBlocksTreeViewPane(IViewPart parent)
	{
		fParent = parent;
		fSelectionProvider = new ViewPaneSelectionProvider();
	}
	
	public Control createViewPane(Composite parent, String paneId, String label)
	{
		fPaneId = paneId;
		int style = SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.VIRTUAL;

		fLabel = label;
		
		IMemoryRenderingSite site = getMemoryRenderingSite();
		MemoryViewPresentationContext presentationContext = new MemoryViewPresentationContext(site, this, null);
		fTreeViewer = new MemoryViewTreeViewer(parent, style, presentationContext);
		
		IAdaptable context = DebugUITools.getDebugContext();
		IMemoryBlockRetrieval retrieval = getMemoryBlockRetrieval(context);
		fTreeViewer.setInput(retrieval);
		fRetrieval = retrieval;
		
		fParent.getViewSite().getSelectionProvider().addSelectionChangedListener(this);
		fParent.getViewSite().getPage().addSelectionListener(this);
		
		fDebugContextListener = new TreeViewPaneContextListener();
		DebugUITools.getDebugContextManager().getContextService(fParent.getSite().getWorkbenchWindow()).addDebugContextListener(fDebugContextListener);
		
		fTreeViewer.addSelectionChangedListener(new ISelectionChangedListener() {

			public void selectionChanged(SelectionChangedEvent event) {
				ISelection treeSelected = event.getSelection();
				fSelectionProvider.setSelection(treeSelected);
			}});

		populateViewPane();
		fEvtHandler = new ViewPaneEventHandler();
		
		// create context menu
		MenuManager mgr = createContextMenuManager();
		Menu menu = mgr.createContextMenu(fTreeViewer.getControl());
		fTreeViewer.getControl().setMenu(menu);
		
		GridData data = new GridData();
		data.grabExcessHorizontalSpace = true;
		data.grabExcessVerticalSpace = true;
		data.horizontalAlignment = SWT.FILL;
		data.verticalAlignment = SWT.FILL;
		fTreeViewer.getControl().setLayoutData(data);
		
		updateActionsEnablement();
		
		return fTreeViewer.getControl();
	}
	
	
	/**
	 * 
	 */
	private void populateViewPane() {
		
		Object context = DebugUITools.getDebugContext();
		fRetrieval = MemoryViewUtil.getMemoryBlockRetrieval(context);
		
		ISelection selection = null;
		if (fParent.getSite().getSelectionProvider() != null)
			selection = fParent.getSite().getSelectionProvider().getSelection();
		
		IMemoryBlock memoryBlock = null;
		
		if (selection == null)
		{
			return;
		}
		
		// get memory block from selection if selection is not null
		memoryBlock = getMemoryBlock(selection);
		
		if (memoryBlock == null)
		{
			IMemoryBlock [] memoryBlocks = DebugPlugin.getDefault().getMemoryBlockManager().getMemoryBlocks(fRetrieval);
			if (memoryBlocks.length > 0)
				memoryBlock = memoryBlocks[0];
		}
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

	protected MenuManager createContextMenuManager() {
		MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				manager.add(fAddMemoryBlockAction);
				manager.add(fRemoveMemoryBlockAction);
				manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			}
		});

		// register a context menu manager, use its pane id as the menu id
		fParent.getSite().registerContextMenu(getId(), menuMgr, fSelectionProvider);
		return menuMgr;
	}
	
	public void dispose()
	{
		fParent.getViewSite().getSelectionProvider().removeSelectionChangedListener(this);
		fParent.getViewSite().getPage().removeSelectionListener(this); 
		fAddMemoryBlockAction.dispose();
		DebugUITools.getDebugContextManager().getContextService(fParent.getSite().getWorkbenchWindow()).removeDebugContextListener(fDebugContextListener);
		fEvtHandler.dispose();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.ISelectionListener#selectionChanged(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection)
	 */
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		


		if (selection instanceof IStructuredSelection)
		{
			Object obj = ((IStructuredSelection)selection).getFirstElement();
		
			if (obj instanceof IMemoryBlock)
			{
				// if the selection event comes from this view
				if (part == fParent)
				{
					// do not change selection if the selection is already correct
					ISelection treeSel = fTreeViewer.getSelection();
					if (treeSel instanceof IStructuredSelection)
					{
						if (((IStructuredSelection)treeSel).getFirstElement() == obj)
							return;
					}
					// remove itself as selection listener when handling selection changed event
					removeSelctionListener(this);
					fTreeViewer.setSelection(selection);
					// remove itself as selection listener when handling selection changed event
					addSelectionListener(this);
				}
			}
		}
		
	}

	private IMemoryBlockRetrieval getMemoryBlockRetrieval(Object obj) {
		IAdaptable adaptable = (IAdaptable)obj;
		IMemoryBlockRetrieval retrieval = null;
		if (adaptable != null)
		{
			retrieval = (IMemoryBlockRetrieval)adaptable.getAdapter(IMemoryBlockRetrieval.class);
			if(retrieval == null && obj instanceof IDebugElement)
			{
				IDebugTarget debugTarget = ((IDebugElement)obj).getDebugTarget();
				if (debugTarget != null)
					retrieval = debugTarget;
			}
		}
		return retrieval;
	}
	
	public String getId()
	{
		return fPaneId;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IMemoryViewPane#getActions()
	 */
	public IAction[] getActions() {
		
		if (fAddMemoryBlockAction == null)
			fAddMemoryBlockAction = new RetargetAddMemoryBlockAction((IMemoryRenderingSite)fParent);
		
		if (fRemoveMemoryBlockAction == null)
		{
			fRemoveMemoryBlockAction = new TreeViewerRemoveMemoryBlocksAction();
		}
		
		if (fRemoveAllMemoryBlocksAction == null)
		{
			fRemoveAllMemoryBlocksAction = new TreeViewerRemoveAllMemoryBlocksAction();
		}
		
		updateActionsEnablement();
		
		return new IAction[]{fAddMemoryBlockAction, fRemoveMemoryBlockAction, fRemoveAllMemoryBlocksAction};
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IMemoryViewPane#addSelectionListener(org.eclipse.jface.viewers.ISelectionChangedListener)
	 */
	public void addSelectionListener(ISelectionChangedListener listener)
	{
		if (fSelectionProvider == null)
			fSelectionProvider = new ViewPaneSelectionProvider();
		
		fSelectionProvider.addSelectionChangedListener(listener);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IMemoryViewPane#removeSelctionListener(org.eclipse.jface.viewers.ISelectionChangedListener)
	 */
	public void removeSelctionListener(ISelectionChangedListener listener)
	{
		if (fSelectionProvider == null)
			return;
		
		fSelectionProvider.removeSelectionChangedListener(listener);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IMemoryViewPane#getSelectionProvider()
	 */
	public ISelectionProvider getSelectionProvider() {
		return fSelectionProvider;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IMemoryViewPane#restoreViewPane()
	 */
	public void restoreViewPane() {
		populateViewPane();
		updateActionsEnablement();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IMemoryViewPane#getControl()
	 */
	public Control getControl() {
		return fTreeViewer.getControl();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IMemoryViewPane#setVisible(boolean)
	 */
	public void setVisible(boolean visible) {
		if (fVisible != visible)
		{
			fVisible = visible;
			
			if(fVisible)
			{
				fTreeViewer.refresh();
				fTreeViewer.getControl().setFocus();
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.memory.IMemoryViewPane#isVisible()
	 */
	public boolean isVisible() {
		return fVisible;
	}
	
	private void updateActionsEnablement()
	{
		if (fRemoveMemoryBlockAction == null)
			return;
		
		if (fRemoveAllMemoryBlocksAction == null)
			return;
		
		if (fRetrieval != null)
		{
			IMemoryBlock[] memBlocks = DebugPlugin.getDefault().getMemoryBlockManager().getMemoryBlocks(fRetrieval);
			if(memBlocks.length > 0)
			{
				fRemoveMemoryBlockAction.setEnabled(true);
				fRemoveAllMemoryBlocksAction.setEnabled(true);
			}
			else
			{
				fRemoveMemoryBlockAction.setEnabled(false);
				fRemoveAllMemoryBlocksAction.setEnabled(false);				
			}
		}
		else
		{
			fRemoveMemoryBlockAction.setEnabled(false);
			fRemoveAllMemoryBlocksAction.setEnabled(false);
		}
	}

	public void selectionChanged(SelectionChangedEvent event) {
		// only handle selection changed from parent's selection provider
		if (event.getSource() == fParent.getSite().getSelectionProvider())
		{
			MemoryBlocksTreeViewPane.this.selectionChanged(fParent, event.getSelection());
		}
	}
	
	public StructuredViewer getViewer()
	{
		return fTreeViewer;
	}

	public IMemoryRenderingSite getMemoryRenderingSite() {
		if (fParent instanceof IMemoryRenderingSite)
			return (IMemoryRenderingSite)fParent;
		return null;
	}

	public void addMemoryRendering(IMemoryRendering rendering) {
		// do nothing
	}

	public void removeMemoryRendering(IMemoryRendering rendering) {
		// do nothing
	}

	public IMemoryRendering[] getRenderings() {
		return new IMemoryRendering[0];
	}

	public IMemoryRendering getActiveRendering() {
		return null;
	}

	public String getLabel() {
		return fLabel;
	}
}
