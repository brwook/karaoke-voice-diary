package com.konodiary.app

import android.content.Context
import com.konodiary.app.audio.analysis.DefaultAnalysisController
import com.konodiary.app.audio.analysis.EnergyAudioAnalyzer
import com.konodiary.app.audio.player.ExoSegmentPlayer
import com.konodiary.app.core.contracts.AnalysisController
import com.konodiary.app.core.contracts.RecordingRepository
import com.konodiary.app.core.contracts.SegmentPlayer
import com.konodiary.app.core.contracts.SegmentRepository
import com.konodiary.app.core.contracts.SongRepository
import com.konodiary.app.data.db.KonoDatabase
import com.konodiary.app.data.repository.DefaultRecordingRepository
import com.konodiary.app.data.repository.DefaultSegmentRepository
import com.konodiary.app.data.repository.DefaultSongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wires concrete implementations together. Constructed once in [KonoApp.onCreate]
 * on the main thread (ExoPlayer requires a Looper thread).
 */
class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext
    private val db: KonoDatabase = KonoDatabase.getInstance(appContext)

    // Long-lived scope for background analysis work.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val recordingRepository: RecordingRepository = DefaultRecordingRepository(db)
    override val segmentRepository: SegmentRepository = DefaultSegmentRepository(db)
    override val songRepository: SongRepository = DefaultSongRepository(db)

    override val analysisController: AnalysisController = DefaultAnalysisController(
        scope = appScope,
        recordingRepository = recordingRepository,
        audioAnalyzer = EnergyAudioAnalyzer(appContext),
    )

    override val segmentPlayer: SegmentPlayer = ExoSegmentPlayer(appContext)

    init {
        // A process kill mid-analysis strands rows at ANALYZING (no retry in the
        // UI); recover them on startup. Racing a just-started analysis is harmless:
        // its own saveAnalysisResult/FAILED transition wins afterwards.
        appScope.launch { recordingRepository.resetStaleAnalyzing() }
    }
}
