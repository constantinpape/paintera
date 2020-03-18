package org.janelia.saalfeldlab.paintera.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.InvalidationListener
import javafx.beans.property.ObjectProperty
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ChoiceBox
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.util.logging.LogUtils

class LoggingConfigNode(private val config: LoggingConfig) {

    private val unmodifiableLoggerLevels = config.unmodifiableLoggerLevels

    val node: Node
        get() {
            val rootLevelChoiceBox = logLevelChoiceBox(config.rootLoggerLevelProperty())
            val loggerLevelGrid = GridPane()
            loggerLevelGrid.columnConstraints.setAll(
                ColumnConstraints().also{ it.hgrow = Priority.ALWAYS },
                ColumnConstraints())
            unmodifiableLoggerLevels.addListener(MapChangeListener { loggerLevelGrid.setupLevelConfig(rootLevelChoiceBox) })
            loggerLevelGrid.setupLevelConfig(rootLevelChoiceBox)

            val contents = VBox(
                makeEnableToggleBox(),
                Separator(Orientation.HORIZONTAL),
                loggerLevelGrid)

            val helpDialog = PainteraAlerts
                .alert(Alert.AlertType.INFORMATION, true)
                .also { it.initModality(Modality.NONE) }
                .also { it.headerText = "Configure Paintera logging." }

            val tpGraphics = HBox(
                Label("Logging"),
                Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
                Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
                .also { it.alignment = Pos.CENTER }

            return with (TitledPaneExtensions) {
                TitledPanes.createCollapsed(null, contents)
                    .also { it.graphicsOnly(tpGraphics) }
                    .also { it.alignment = Pos.CENTER_RIGHT }
            }
        }

    private fun makeEnableToggleBox(): Node {
        val isEnabledCheckBox = CheckBox("Enable logging")
            .also { it.selectedProperty().bindBidirectional(config.loggingEnabledProperty) }
        val isLoggingToConsoleEnabled = CheckBox("Log to console")
            .also { it.selectedProperty().bindBidirectional(config.loggingToConsoleEnabledProperty) }
            .also { it.disableProperty().bind(config.loggingEnabledProperty.not()) }
        val isLoggingToFileEnabled = CheckBox("Log to file")
            .also { it.selectedProperty().bindBidirectional(config.loggingToFileEnabledProperty) }
            .also { it.disableProperty().bind(config.loggingEnabledProperty.not()) }

        return VBox(
            isEnabledCheckBox,
            isLoggingToConsoleEnabled,
            isLoggingToFileEnabled)
    }

    private fun logLevelChoiceBox(logLevelProperty: ObjectProperty<Level>?): ChoiceBox<Level> {
        val choiceBox = ChoiceBox(FXCollections.observableList(LogUtils.Logback.Levels.levels))
        choiceBox.value = LoggingConfig.defaultLogLevel
        logLevelProperty?.let { choiceBox.valueProperty().bindBidirectional(it) }
        return choiceBox
    }

    private fun GridPane.setupLevelConfig(rootLoggerLevelChoiceBox: ChoiceBox<Level>) {

        children.clear()

        add(Labels.withTooltip(Logger.ROOT_LOGGER_NAME, "Root logger"), 0, 0)
        add(rootLoggerLevelChoiceBox, 1, 0)

        val keys = unmodifiableLoggerLevels.keys
        val sortedKeys = keys.sorted()

        sortedKeys.forEachIndexed { index, name ->
            unmodifiableLoggerLevels[name]?.let { level ->
                val removeButton = Buttons.withTooltip(null, "Unset level setting for logger `$name'.") {
                    config.unsetLogLevelFor(name)
                }
                removeButton.graphic = FontAwesome[FontAwesomeIcon.MINUS, 2.0]
                index.let { it + 1 }.let { row ->
                    add(Labels.withTooltip(name), 0, row)
                    add(logLevelChoiceBox(level), 1, row)
                    add(removeButton, 2, row)
                }
            }
        }
        val newLoggerField = TextField("")
        val newLoggerChoiceBox = logLevelChoiceBox(null)
        val newLoggerButton = Buttons
            .withTooltip(null) { config.setLogLevelFor(newLoggerField.text, newLoggerChoiceBox.value) }
            .also {  it.graphic = FontAwesome[FontAwesomeIcon.PLUS, 2.0] }
        val listener = InvalidationListener {
            val name = newLoggerField.text
            val isRootLoggerName = LogUtils.rootLogger.name == name
            val isExistingLogger = name in keys
            val isValidLoggerName = !isExistingLogger && !isRootLoggerName && newLoggerField.text?.isNotEmpty() == true
            newLoggerButton.isDisable = !isValidLoggerName

            when {
                isValidLoggerName -> newLoggerButton.tooltip = Tooltip("Add level setting for logger `$name'.")
                isRootLoggerName -> newLoggerButton.tooltip = Tooltip("Cannot add `$name' because the name is reserved for the root logger.")
                isExistingLogger -> newLoggerButton.tooltip = Tooltip("Cannot add `$name' because it is already configured.")
                else -> newLoggerButton.tooltip = Tooltip("Add level setting for logger (specify logger name)")
            }

        }

        sortedKeys.size.let { it + 1 }.let { row ->
            newLoggerField.textProperty().addListener(listener)
            listener.invalidated(newLoggerField.textProperty())
            add(newLoggerField, 0, row)
            add(newLoggerChoiceBox, 1, row)
            add(newLoggerButton, 2, row)
        }


    }

}
