package com.konodiary.app

import com.konodiary.app.core.contracts.AnalysisController
import com.konodiary.app.core.contracts.RecordingRepository
import com.konodiary.app.core.contracts.SegmentPlayer
import com.konodiary.app.core.contracts.SegmentRepository
import com.konodiary.app.core.contracts.SongRepository
import com.konodiary.app.core.contracts.SongSearchService

/** Manual dependency container (no DI framework — prototype). */
interface AppContainer {
    val recordingRepository: RecordingRepository
    val segmentRepository: SegmentRepository
    val songRepository: SongRepository
    val analysisController: AnalysisController
    val segmentPlayer: SegmentPlayer
    val songSearchService: SongSearchService
}
