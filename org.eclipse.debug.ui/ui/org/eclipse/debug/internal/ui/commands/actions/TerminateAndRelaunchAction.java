/*******************************************************************************
 * Copyright (c) 2004, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.commands.actions;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.internal.ui.DebugPluginImages;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.internal.ui.actions.ActionMessages;
import org.eclipse.debug.internal.ui.actions.RelaunchActionDelegate;
import org.eclipse.debug.internal.ui.commands.provisional.ITerminateCommand;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchConfigurationManager;
import org.eclipse.debug.internal.ui.viewers.provisional.IAsynchronousRequestMonitor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPart;

/**
 * Action which terminates a launch and then re-launches it.
 */
public class TerminateAndRelaunchAction extends DebugCommandAction {
	
	class RequestMonitor extends ActionRequestMonitor {
		
		private ILaunch fLaunch;

		public RequestMonitor(ILaunch launch) {
			fLaunch = launch;
		}
		
		public void done() {
			super.done();
	        DebugUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
	            public void run() {
	                // Must be run in the UI thread since the launch can require
	                // prompting to proceed
	                RelaunchActionDelegate.relaunch(fLaunch.getLaunchConfiguration(), fLaunch.getLaunchMode());
	            }
	        });			
		}
		
	}

	protected IAsynchronousRequestMonitor createStatusMonitor(Object target) {
		ILaunch launch = RelaunchActionDelegate.getLaunch(target);
		return new RequestMonitor(launch);
	}

	protected Class getCommandType() {
		return ITerminateCommand.class;
	}

	public void contextActivated(ISelection context, IWorkbenchPart part) {
		if (context instanceof IStructuredSelection) {
			Object[] elements = ((IStructuredSelection)context).toArray();
			for (int i = 0; i < elements.length; i++) {
				if (!canRelaunch(elements[i])) {
					setEnabled(false);
					return;
				}
			} 
		}
		super.contextActivated(context, part);
	}

    protected boolean canRelaunch(Object element) {
    	ILaunch launch = RelaunchActionDelegate.getLaunch(element);
    	return launch != null && LaunchConfigurationManager.isVisible(launch.getLaunchConfiguration());
    }

    public String getActionDefinitionId() {
        return ActionMessages.TerminateAndRelaunchAction_0;
    }

    public String getHelpContextId() {
        return "terminate_and_relaunch_action_context"; //$NON-NLS-1$
    }

    public String getId() {
        return "org.eclipse.debug.ui.debugview.popupMenu.TerminateAndRelaunch"; //$NON-NLS-1$
    }

    public String getText() {
        return ActionMessages.TerminateAndRelaunchAction_3;
    }

    public String getToolTipText() {
        return ActionMessages.TerminateAndRelaunchAction_4;
    }

    public ImageDescriptor getDisabledImageDescriptor() {
        return DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_TERMINATE_AND_RELAUNCH);
    }

    public ImageDescriptor getHoverImageDescriptor() {
        return DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_TERMINATE_AND_RELAUNCH);
    }

    public ImageDescriptor getImageDescriptor() {
        return DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_TERMINATE_AND_RELAUNCH);
    }
}