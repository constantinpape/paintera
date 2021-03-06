package org.janelia.saalfeldlab.paintera.state

import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import java.util.function.DoublePredicate

class ThresholdingSourceStatePreferencePaneNode(private val state: ThresholdingSourceState<*, *>) {

	val node: Node
		get() = SourceState.defaultPreferencePaneNode(state.compositeProperty()).let { if (it is VBox) it else VBox(it) }
				.also { it.children.add(createNode()) }

	private fun createNode(): Node {
		val checkBox = CheckBox("From Raw Source")
				.also { it.tooltip = Tooltip("When selected, use min and max from underlying raw source for threshold, use min and max below otherwise.") }
				.also { state.controlSeparatelyProperty().addListener { _, _, new -> it.isSelected = !new } }
				.also { it.selectedProperty().addListener { _, _, new -> state.controlSeparatelyProperty().value = !new } }
				.also { it.isSelected = !state.controlSeparatelyProperty().get() }
		val min = NumberField
				.doubleField(state.minProperty().get(), DoublePredicate { true }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
				.also { it.valueProperty().addListener { _, _, new -> state.minProperty().set(new.toDouble()) } }
		val max = NumberField
				.doubleField(state.maxProperty().get(), DoublePredicate { true }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
				.also { it.valueProperty().addListener { _, _, new -> state.maxProperty().set(new.toDouble()) } }

		val foreground = ColorPicker(state.colorProperty().get()).also { it.valueProperty().bindBidirectional(state.colorProperty()) }
		val background = ColorPicker(state.backgroundColorProperty().get()).also { it.valueProperty().bindBidirectional(state.backgroundColorProperty()) }

		val minMax = GridPane()
		minMax.add(Label("min"), 0, 0)
		minMax.add(Label("max"), 0, 1)
		minMax.add(Label("foreground"), 0, 2)
		minMax.add(Label("background"), 0, 3)

		minMax.add(min.textField(), 1, 0)
		minMax.add(max.textField(), 1, 1)
		minMax.add(foreground, 1, 2)
		minMax.add(background, 1, 3)

		GridPane.setHgrow(min.textField(), Priority.ALWAYS)
		GridPane.setHgrow(max.textField(), Priority.ALWAYS)
		GridPane.setHgrow(foreground, Priority.ALWAYS)
		GridPane.setHgrow(background, Priority.ALWAYS)


		val helpDialog = PainteraAlerts
				.alert(Alert.AlertType.INFORMATION, true)
				.also { it.initModality(Modality.NONE) }
				.also { it.headerText = "Threshold" }
				.also { it.contentText = "TODO" /* TODO */ }

		val tpGraphics = HBox(
				Label("Threshold"),
				Region().also { HBox.setHgrow(it, Priority.ALWAYS) }.also { it.minWidth = 0.0 },
				Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
				.also { it.alignment = Pos.CENTER }

		return TitledPanes
				.createCollapsed(null, VBox(checkBox, minMax))
				.also { with (TPE) { it.graphicsOnly(tpGraphics) } }
				.also { it.alignment = Pos.CENTER_RIGHT }
				.also { it.tooltip = null /* TODO */ }
	}

}
