/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.launchVariables;


import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.ui.launchVariables.AbstractVariableComponent;
import org.eclipse.debug.ui.launchVariables.IVariableComponentContainer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

/**
 * Visual component to edit the resource type variable
 * value.
 * @since 3.0
 */
public class ResourceComponent extends AbstractVariableComponent {
	protected Button selectedResourceButton;
	protected Button specificResourceButton;
	protected TreeViewer resourceList;
	private IResource selectedResource;
	private boolean selectedResourceVariable= true;
	
	/**
	 * Creates the component
	 */
	public ResourceComponent() {
		super();
	}

	/**
	 * @see IVariableComponent#createContents(Composite, String, IVariableComponentContainer)
	 */
	public void createContents(Composite parent, String varTag, IVariableComponentContainer componentContainer) {
		super.createContents(parent, varTag, componentContainer); // Creates the main group and sets the page
		
		createSelectedResourceOption();
		createSpecificResourceOption();
		createResourceList();
	}

	/**
	 * Creates the list of resources.
	 */
	protected void createResourceList() {
		Tree tree = new Tree(mainGroup, SWT.SINGLE | SWT.BORDER);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.heightHint = tree.getItemHeight() * 8;
		tree.setLayoutData(data);
		tree.setFont(mainGroup.getFont());
		tree.setEnabled(false);
		
		resourceList = new TreeViewer(tree);
		resourceList.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				selectedResource= (IResource) ((IStructuredSelection)event.getSelection()).getFirstElement();
				validate();
			}
		});
		resourceList.setContentProvider(new WorkbenchContentProvider());
		resourceList.setLabelProvider(new WorkbenchLabelProvider());
		resourceList.setInput(ResourcesPlugin.getWorkspace().getRoot());
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.launchVariables.IVariableComponent#setEnabled(boolean)
	 */
	public void setEnabled(boolean enabled) {
		mainGroup.setEnabled(enabled);
		resourceList.getTree().setEnabled(specificResourceButton.getSelection());
	}
	
	/**
	 * Creates the option button for using the selected
	 * resource.
	 */
	private void createSelectedResourceOption() {
		selectedResourceButton = new Button(mainGroup, SWT.RADIO);
		selectedResourceButton.setText(LaunchVariableMessages.getString("ResourceComponent.0")); //$NON-NLS-1$
		GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		selectedResourceButton.setLayoutData(data);
		selectedResourceButton.setFont(mainGroup.getFont());
		selectedResourceButton.setSelection(true);
	}
	
	/**
	 * Creates the option button for using a specific
	 * resource.
	 */
	private void createSpecificResourceOption() {
		specificResourceButton = new Button(mainGroup, SWT.RADIO);
		specificResourceButton.setText(LaunchVariableMessages.getString("ResourceComponent.1")); //$NON-NLS-1$
		GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		specificResourceButton.setLayoutData(data);
		specificResourceButton.setFont(mainGroup.getFont());
		specificResourceButton.setSelection(false);
		
		specificResourceButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				updateResourceListEnablement();
				getContainer().updateValidState();
				selectedResourceVariable= !specificResourceButton.getSelection();
			}
		});
	}
	
	/* (non-Javadoc)
	 * Method declared on IVariableComponent.
	 */
	public String getVariableValue() {
		if (selectedResourceVariable) {
			return null;
		}
		
		if (resourceList != null) {
			if (selectedResource != null) {
				return selectedResource.getFullPath().toString();
			}
		}
		
		return null;
	}
	
	/**
	 * Updates the enablement of the resource list if needed
	 */
	private void updateResourceListEnablement() {
		if (specificResourceButton != null && resourceList != null) {
			Tree tree= resourceList.getTree();
			tree.setEnabled(specificResourceButton.getSelection());
			if (tree.getItemCount() > 0) {
				tree.setSelection(new TreeItem[]{tree.getItems()[0]});
				selectedResource= (IResource)tree.getSelection()[0].getData();
			}
			
			validate();
		}
	}
	
	/* (non-Javadoc)
	 * Method declared on IVariableComponent.
	 */
	public void setVariableValue(String varValue) {
		if (varValue == null || varValue.length() == 0) {
			if (selectedResourceButton != null) {
				selectedResourceButton.setSelection(true);
			}
			if (specificResourceButton != null) {
				specificResourceButton.setSelection(false);
			}
			if (resourceList != null) {
				resourceList.getTree().setEnabled(false);
			}
		} else {
			if (selectedResourceButton != null) {
				selectedResourceButton.setSelection(false);
			}
			if (specificResourceButton != null) {
				specificResourceButton.setSelection(true);
			}
			if (resourceList != null) {
				resourceList.getTree().setEnabled(true);
				IResource member = ResourcesPlugin.getWorkspace().getRoot().findMember(varValue);
				if (member != null) {
					resourceList.setSelection(new StructuredSelection(member), true);
				} else {
					resourceList.setSelection(StructuredSelection.EMPTY);
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * Method declared on IVariableComponent.
	 */
	public void validate() {
		getContainer().setErrorMessage(null);
		setIsValid(true);
		if (specificResourceButton != null && specificResourceButton.getSelection()) {
			validateResourceListSelection();
		}
		getContainer().updateValidState();
	}

	/**
	 * Validates the resource selection list. If no resource is selected, the
	 * component is updated with an error message and isValid is set
	 * <code>false</code>
	 */
	private void validateResourceListSelection() {
		if (resourceList == null) {
			return;
		}
		if (resourceList.getSelection().isEmpty()) {
			getContainer().setErrorMessage(LaunchVariableMessages.getString("ResourceComponent.2")); //$NON-NLS-1$
			setIsValid(false);
		}
	}
}
