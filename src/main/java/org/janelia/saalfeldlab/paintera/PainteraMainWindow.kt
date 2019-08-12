package org.janelia.saalfeldlab.paintera

import bdv.viewer.ViewerOptions
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.scene.Parent
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.event.MouseTracker
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.serialization.*
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.scijava.plugin.Plugin
import java.lang.reflect.Type
import java.util.function.BiConsumer
import java.util.function.Supplier

typealias PropertiesListener = BiConsumer<Properties2?, Properties2?>

class PainteraMainWindow {

    val baseView = PainteraBaseView(
            PainteraBaseView.reasonableNumFetcherThreads(),
            ViewerOptions.options().screenScales(ScreenScalesConfig.defaultScreenScalesCopy()))

    private lateinit var paneWithStatus: BorderPaneWithStatusBars2

    val keyTracker = KeyTracker()

    val mouseTracker = MouseTracker()

    val projectDirectory = ProjectDirectory()

    private lateinit var defaultHandlers: PainteraDefaultHandlers2

	private lateinit var properties: Properties2

	fun getPane(): Parent = paneWithStatus.pane

	fun getProperties() = properties

	private fun initProperties(properties: Properties2) {
		this.properties = properties
		this.paneWithStatus = BorderPaneWithStatusBars2(this.baseView, this.properties)
		this.defaultHandlers = PainteraDefaultHandlers2(
				baseView,
				keyTracker,
				mouseTracker,
				paneWithStatus,
				Supplier { projectDirectory.actualDirectory.absolutePath },
				this.properties)
		properties.navigationConfig.bindNavigationToConfig(defaultHandlers.navigation())
	}

	private fun initProperties(json: JsonObject?, gson: Gson) {
		val properties = json?.let { gson.fromJson(it, Properties2::class.java) }
		initProperties(properties ?: Properties2())
	}

	fun deserialize() {
		val indexToState = mutableMapOf<Int, SourceState<*, *>>()
		val builder = GsonHelpers
				.builderWithAllRequiredDeserializers(
						StatefulSerializer.Arguments(baseView),
						{ projectDirectory.actualDirectory.absolutePath },
						{ indexToState[it] })
		val gson = builder.create()
		val json = projectDirectory
				.actualDirectory
				?.let { N5FSReader(it.absolutePath).getAttribute("/", PAINTERA_KEY, JsonElement::class.java) }
				?.takeIf { it.isJsonObject }
				?.let { it.asJsonObject }
		deserialize(json, gson, indexToState)
	}

	fun save() {
		val builder = GsonHelpers.builderWithAllRequiredSerializers(baseView) { projectDirectory.actualDirectory.absolutePath }
		N5FSWriter(projectDirectory.actualDirectory.absolutePath, builder).setAttribute("/", PAINTERA_KEY, this)
	}

	private fun deserialize(json: JsonObject?, gson: Gson, indexToState: MutableMap<Int, SourceState<*, *>>) {
		initProperties(json, gson)
		baseView.orthogonalViews().grid().manage(properties.gridConstraints)
		json
				?.takeIf { it.has(SOURCES_KEY) }
				?.get(SOURCES_KEY)
				?.takeIf { it.isJsonObject }
				?.asJsonObject
				?.let { SourceInfoSerializer.populate(
						{ baseView.addState(it) },
						{ baseView.sourceInfo().currentSourceIndexProperty().set(it) },
						it.asJsonObject,
						{ k, v -> indexToState.put(k, v) },
						gson)
				}
	}

	companion object{

		private const val PAINTERA_KEY = "paintera"

		private const val SOURCES_KEY = "sourceInfo"

	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	private class Serializer : PainteraSerialization.PainteraSerializer<PainteraMainWindow> {
		override fun serialize(mainWindow: PainteraMainWindow, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
			val map = context.serialize(mainWindow.properties).asJsonObject
			map.add(SOURCES_KEY, context.serialize(mainWindow.baseView.sourceInfo()))
			return map
		}

		override fun getTargetClass() = PainteraMainWindow::class.java

	}


}
