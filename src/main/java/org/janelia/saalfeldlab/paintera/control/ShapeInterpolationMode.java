package org.janelia.saalfeldlab.paintera.control;

import java.lang.invoke.MethodHandles;
import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions;
import org.janelia.saalfeldlab.paintera.control.actions.LabelAction;
import org.janelia.saalfeldlab.paintera.control.actions.NavigationAction;
import org.janelia.saalfeldlab.paintera.control.actions.PaintAction;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class ShapeInterpolationMode
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final AllowedActions allowedActionsInShapeInterpolationMode;
	static
	{
		allowedActionsInShapeInterpolationMode = new AllowedActions(
			NavigationAction.of(NavigationAction.Drag, NavigationAction.Zoom, NavigationAction.Scroll),
			LabelAction.none(),
			PaintAction.of(PaintAction.Paint, PaintAction.SetBrush)
		);
	}

	private final ObjectProperty<ViewerPanelFX> activeViewer = new SimpleObjectProperty<>();

	private final SelectedIds selectedIds;

	private AllowedActions lastAllowedActions;

	public ShapeInterpolationMode(final SelectedIds selectedIds)
	{
		this.selectedIds = selectedIds;
	}

	public ObjectProperty<ViewerPanelFX> activeViewerProperty()
	{
		return this.activeViewer;
	}

	public EventHandler<Event> modeHandler(final PainteraBaseView paintera, final KeyTracker keyTracker)
	{
		final DelegateEventHandlers.AnyHandler filter = DelegateEventHandlers.handleAny();
		filter.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"enter shape interpolation mode",
						e -> enterMode(paintera, (ViewerPanelFX) e.getTarget()),
						e -> e.getTarget() instanceof ViewerPanelFX &&
							this.activeViewer.get() == null &&
							selectedIds.isLastSelectionValid() &&
							keyTracker.areOnlyTheseKeysDown(KeyCode.S)
					)
			);
		filter.addEventHandler(
				KeyEvent.KEY_PRESSED,
				EventFX.KEY_PRESSED(
						"exit shape interpolation mode",
						e -> exitMode(paintera),
						e -> this.activeViewer.get() != null &&
							keyTracker.areOnlyTheseKeysDown(KeyCode.ESCAPE)
					)
			);
		return filter;
	}

	public void enterMode(final PainteraBaseView paintera, final ViewerPanelFX viewer)
	{
		LOG.info("Entering shape interpolation mode");
		assert this.activeViewer.get() == null;
		activeViewer.set(viewer);
		setDisableOtherViewers(true);

		this.lastAllowedActions = paintera.allowedActionsProperty().get();
		paintera.allowedActionsProperty().set(allowedActionsInShapeInterpolationMode);

		// ...
	}

	public void exitMode(final PainteraBaseView paintera)
	{
		LOG.info("Exiting shape interpolation mode");
		assert this.activeViewer.get() != null;
		setDisableOtherViewers(false);

		paintera.allowedActionsProperty().set(this.lastAllowedActions);
		this.lastAllowedActions = null;

		// ...

		this.activeViewer.set(null);
	}

	private void setDisableOtherViewers(final boolean disable)
	{
		final Parent parent = this.activeViewer.get().getParent();
		for (final Node child : parent.getChildrenUnmodifiable())
		{
			if (child instanceof ViewerPanelFX && child != this.activeViewer.get())
			{
				final ViewerPanelFX viewer = (ViewerPanelFX) child;
				viewer.setDisable(disable);
				if (disable)
				{
					final ColorAdjust grayedOutEffect = new ColorAdjust();
					grayedOutEffect.setContrast(-0.2);
					grayedOutEffect.setBrightness(-0.5);
					viewer.setEffect(grayedOutEffect);
				}
				else
				{
					viewer.setEffect(null);
				}
			}
		}
	}
}
