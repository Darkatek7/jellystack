package dev.jellystack.core.livetv

import dev.jellystack.network.jellyfin.JellyfinLiveTvChannelDto
import dev.jellystack.network.jellyfin.JellyfinLiveTvProgramDto
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveTvModelsTest {
    @Test
    fun channelUsesTheTunerNumberAndFallsBackToItWhenTheNameIsMissing() {
        val named = JellyfinLiveTvChannelDto("c1", name = "Example One", channelNumber = "12.1", imageTags = mapOf("Primary" to "t"))
        val unnamed = JellyfinLiveTvChannelDto("c2", name = " ", number = "13")

        assertEquals(LiveTvChannel("c1", "12.1", "Example One", "t"), named.toDomain())
        assertEquals(LiveTvChannel("c2", "13", "13", null), unnamed.toDomain())
    }

    @Test
    fun programReadsServerDatesWithSevenFractionDigits() {
        val program =
            JellyfinLiveTvProgramDto(
                id = "p1",
                channelId = "c1",
                name = "Example News",
                episodeTitle = "Evening",
                startDate = "2026-11-01T17:30:00.0000000Z",
                endDate = "2026-11-01T18:30:00.0000000Z",
            ).toDomain()

        assertEquals(Instant.parse("2026-11-01T17:30:00Z"), program?.start)
        assertEquals(Instant.parse("2026-11-01T18:30:00Z"), program?.end)
        assertEquals("Evening", program?.subtitle)
    }

    @Test
    fun guideEntriesWithoutUsableTimesOrChannelAreDropped() {
        fun dto(
            channelId: String? = "c1",
            start: String? = "2026-11-01T17:00:00Z",
            end: String? = "2026-11-01T18:00:00Z",
        ) = JellyfinLiveTvProgramDto("p", channelId = channelId, name = "Example", startDate = start, endDate = end)

        assertNull(dto(channelId = null).toDomain())
        assertNull(dto(start = null).toDomain())
        assertNull(dto(start = "not a date").toDomain())
        assertNull(dto(end = "2026-11-01T17:00:00Z").toDomain())
    }

    @Test
    fun nowIsTheProgramRunningAtTheMomentAndNextFollowsIt() {
        val programs = listOf(program("a", 17, 18), program("b", 18, 19), program("c", 19, 21))

        val nowNext = programs.nowAndNext(at(18, 30))

        assertEquals("b", nowNext.now?.id)
        assertEquals("c", nowNext.next?.id)
        assertEquals(at(19, 0), nowNext.changesAt)
    }

    @Test
    fun aProgramStartsAtItsStartAndIsOverAtItsEnd() {
        val programs = listOf(program("a", 17, 18), program("b", 18, 19))

        assertEquals("a", programs.nowAndNext(at(17, 0)).now?.id)
        assertEquals("a", programs.nowAndNext(at(17, 59)).now?.id)
        assertEquals("b", programs.nowAndNext(at(18, 0)).now?.id)
        assertNull(programs.nowAndNext(at(19, 0)).now)
        assertNull(programs.nowAndNext(at(19, 0)).next)
    }

    @Test
    fun inAGapNothingRunsAndTheNextProgramIsTheFirstToStart() {
        val programs = listOf(program("a", 17, 18), program("b", 19, 20))

        val nowNext = programs.nowAndNext(at(18, 30))

        assertNull(nowNext.now)
        assertEquals("b", nowNext.next?.id)
        assertEquals(at(19, 0), nowNext.changesAt)
    }

    @Test
    fun beforeTheGuideStartsNothingRunsAndTheFirstProgramIsNext() {
        val nowNext = listOf(program("a", 17, 18), program("b", 18, 19)).nowAndNext(at(16, 0))

        assertNull(nowNext.now)
        assertEquals("a", nowNext.next?.id)
    }

    @Test
    fun ofOverlappingProgramsTheOneThatStartedLastRuns() {
        val programs = listOf(program("long", 17, 20), program("late", 18, 19))

        assertEquals("late", programs.nowAndNext(at(18, 30)).now?.id)
    }

    @Test
    fun theLastProgramHasNoNext() {
        val nowNext = listOf(program("a", 17, 18)).nowAndNext(at(17, 30))

        assertEquals("a", nowNext.now?.id)
        assertNull(nowNext.next)
    }

    @Test
    fun progressGrowsFromZeroToOneAndStaysInsideThatRange() {
        val program = program("a", 17, 19)

        assertEquals(0f, program.progressAt(at(16, 0)))
        assertEquals(0.25f, program.progressAt(at(17, 30)))
        assertEquals(1f, program.progressAt(at(20, 0)))
    }

    private fun at(
        hour: Int,
        minute: Int,
    ) = Instant.parse("2026-11-01T${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:00Z")

    private fun program(
        id: String,
        startHour: Int,
        endHour: Int,
    ) = LiveTvProgram(id, "c1", "Program $id", null, null, at(startHour, 0), at(endHour, 0))
}
