package dev.typetype.server.portability

import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

object PortabilityEngineFactory {
    fun create(root: Path, scope: CoroutineScope): PortabilityEngine {
        val adapters = listOf(
                TypeTypePortabilityAdapter(),
                PipePipePortabilityAdapter(),
                NewPipePortabilityAdapter(),
                InvidiousPortabilityAdapter(),
                PipedPortabilityAdapter(),
                LibreTubePortabilityAdapter(),
                ViewTubePortabilityAdapter(),
                FlowPortabilityAdapter(),
                GrayjayPortabilityAdapter(),
                YoutubeTakeoutPortabilityAdapter(),
                OpmlPortabilityAdapter(),
                MaterialiousPortabilityAdapter(),
                OpmlPortabilityAdapter(PortabilityFormat.SKY_TUBE, autoDetect = false),
                OpmlPortabilityAdapter(PortabilityFormat.YOUTUBE_LOCAL, autoDetect = false),
        )
        require(adapters.map { it.descriptor.format }.toSet() == PortabilityFormat.entries.toSet()) {
            "Every portability format must have an adapter"
        }
        return PortabilityEngine(
            registry = PortabilityRegistry(adapters),
            dataPort = TypeTypePortabilityDataPort(),
            store = PortabilityJobStore(root),
            scope = scope,
        )
    }
}
