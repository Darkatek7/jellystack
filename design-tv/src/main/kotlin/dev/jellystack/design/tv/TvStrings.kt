package dev.jellystack.design.tv

import dev.jellystack.core.preferences.AppLanguage
import java.util.Locale

/**
 * Duration, rating, and playback statistics labels. New strings go into small groups like this one:
 * the flat [TvStrings] constructor is at the JVM limit of 255 parameter slots (docs/tech-debt.md 3.7).
 */
internal data class TvMetadataStrings(
    val minutesLeft: String,
    val minutesShort: String,
    val rtCritics: String,
    val rtAudience: String,
    val frameRate: String,
    val dynamicRange: String,
    val buffer: String,
    val spotlightPosition: String,
    val captionsBadge: String,
)

/** Labels of the video player: skip prompts, the clock, chapters, and the up-next card. */
internal data class TvPlayerStrings(
    val skipIntro: String,
    val skipRecap: String,
    val skipPreview: String,
    val skipCommercial: String,
    val skipCredits: String,
    val playNextEpisode: String,
    val playNext: String,
    val endsAt: String,
    val chapters: String,
    val chapterNumber: String,
    val upNext: String,
    val watchCredits: String,
    val forcedTrack: String,
    val defaultTrack: String,
    val startFailedTitle: String,
    val startFailedMessage: String,
    val noPlayableEpisodeMessage: String,
    val videoScale: String,
    val videoScaleFit: String,
    val videoScaleZoom: String,
    val videoScaleStretch: String,
    val sync: TvPlayerSyncStrings,
    val noPlayableItemMessage: String,
)

/** Labels of the settings screens. */
internal data class TvSettingsStrings(
    val trailerPreviewDelay: String,
    val matchRefreshRate: String,
    val secondsShort: String,
    val decimalSeparator: String,
) {
    /** "1.5 s" or "3 s" in the language's number format. */
    fun seconds(millis: Int): String {
        val tenths = millis % MILLIS_PER_SECOND / MILLIS_PER_TENTH
        val whole = millis / MILLIS_PER_SECOND
        return secondsShort.format(if (tenths == 0) "$whole" else "$whole$decimalSeparator$tenths")
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000
        const val MILLIS_PER_TENTH = 100
    }
}

/**
 * Exit dialog, voice search, PIN pad, and countdown copy. The count helpers use the singular for exactly one, and
 * [locale] is the language of these strings for names the platform localizes (track languages).
 */
internal data class TvMessageStrings(
    val locale: Locale,
    val exitAppTitle: String,
    val exitAppMessage: String,
    val exitApp: String,
    val editName: String,
    val quickConnectScan: String,
    val itemListLoadFailed: String,
    val playAll: String,
    val filmography: String,
    val collectionLabel: String,
    val playlistLabel: String,
    val music: TvMusicStrings,
    val liveTv: TvLiveTvStrings,
    val cardBadges: TvCardBadgeStrings,
    val accountHasProfileFormat: String,
    private val voiceSearchUnavailable: String,
    private val voiceSearchNoSpeech: String,
    private val voiceSearchFailed: String,
    private val pinDigitsEnteredFormat: String,
    private val attemptRemainingFormat: String,
    private val attemptsRemainingFormat: String,
    private val playingInOneSecondFormat: String,
    private val playingInSecondsFormat: String,
    private val untitledRequestFormat: String,
    private val requestTitleFormat: String,
    val loadingDetails: String,
    /** The title of a card whose server item came without a name. */
    val untitled: String,
) {
    fun voiceSearchError(failure: TvVoiceSearchFailure): String =
        when (failure) {
            TvVoiceSearchFailure.RECOGNIZER_UNAVAILABLE -> voiceSearchUnavailable
            TvVoiceSearchFailure.NO_SPEECH -> voiceSearchNoSpeech
            TvVoiceSearchFailure.FAILED -> voiceSearchFailed
        }

    fun pinDigitsEntered(
        entered: Int,
        total: Int,
    ): String = pinDigitsEnteredFormat.format(entered, total)

    fun pinAttemptsRemaining(count: Int): String {
        val format = if (count == 1) attemptRemainingFormat else attemptsRemainingFormat
        return format.format(count)
    }

    fun playingIn(seconds: Int): String {
        val format = if (seconds == 1) playingInOneSecondFormat else playingInSecondsFormat
        return format.format(seconds)
    }

    fun untitledRequest(id: Int): String = untitledRequestFormat.format(id)

    fun requestTitle(title: String): String = requestTitleFormat.format(title)
}

/** Settings > Diagnostics (local only), plus the "that failed" line under the detail actions. */
internal data class TvDiagnosticsStrings(
    val categoryTitle: String,
    val categoryDescription: String,
    val actionFailed: String,
    val localOnlyNote: String,
    val appVersionLabel: String,
    val deviceLabel: String,
    val lastCrash: String,
    val noCrash: String,
    val clearCrash: String,
    val recentLog: String,
    val noLogLines: String,
    val refresh: String,
    private val androidVersionFormat: String,
    private val crashSummaryFormat: String,
) {
    fun androidVersion(
        release: String,
        sdk: Int,
    ): String = androidVersionFormat.format(release, sdk)

    fun crashSummary(
        time: String,
        thread: String,
        version: String,
    ): String = crashSummaryFormat.format(time, thread, version)
}

/** Settings > Home: which rows the home screen shows and in which order. */
internal data class TvHomeSettingsStrings(
    val categoryTitle: String,
    val categoryDescription: String,
    val intro: String,
    val shown: String,
    val hidden: String,
    val moveUp: String,
    val moveDown: String,
    val empty: String,
)

internal data class TvStrings(
    val home: String,
    val library: String,
    val search: String,
    val discover: String,
    val settings: String,
    val play: String,
    val continueLabel: String,
    val favorite: String,
    val watched: String,
    val loading: String,
    val retry: String,
    val signIn: TvSignInStrings,
    val cancel: String,
    val searchScreen: TvSearchStrings,
    val detailText: TvDetailLabelStrings,
    val preferences: TvPreferenceStrings,
    val homeRows: TvHomeRowStrings,
    val back: String,
    val close: String,
    val browse: TvBrowseStrings,
    val seerr: TvSeerrStrings,
    val all: String,
    val on: String,
    val off: String,
    val playbackUi: TvPlaybackStrings,
    val more: String,
    val none: String,
    val restart: String,
    val segments: TvSegmentStrings,
    val player: TvPlayerStrings,
    val profile: TvProfileStrings,
    val mediaTypeLabels: TvMediaTypeStrings,
    val emptyStates: TvEmptyStateStrings,
    val messages: TvMessageStrings,
    val metadata: TvMetadataStrings,
    val settingsLabels: TvSettingsStrings,
    val diagnostics: TvDiagnosticsStrings,
    val homeSettings: TvHomeSettingsStrings,
    private val itemSingular: String,
    private val itemPlural: String,
) {
    /** Locale of these strings, for names the platform localizes (track languages); the system locale may differ. */
    val locale: Locale
        get() = messages.locale

    /** Library screen labels; not a constructor field, the constructor is at the JVM limit of 255 parameters. */
    val libraryScreen: TvLibraryStrings
        get() = if (locale.language == "de") germanLibraryStrings else englishLibraryStrings

    fun itemCount(count: Long): String = "$count ${if (count == 1L) itemSingular else itemPlural}"

    companion object {
        fun current(language: AppLanguage): TvStrings {
            val german =
                language == AppLanguage.GERMAN ||
                    (language == AppLanguage.SYSTEM && Locale.getDefault().language == "de")
            return if (german) TvStringsGerman else English
        }

        internal val English =
            TvStrings(
                home = "Home",
                library = "Library",
                search = "Search",
                discover = "Discover",
                settings = "Settings",
                play = "Play",
                continueLabel = "Continue",
                favorite = "Favorite",
                watched = "Watched",
                loading = "Loading Jellystack",
                retry = "Try again",
                signIn =
                    TvSignInStrings(
                        quickConnect = "Quick Connect",
                        password = "Password",
                        connectJellyfin = "Connect Jellyfin",
                        serverUrl = "Server URL",
                        displayName = "Display name",
                        username = "Username",
                        connect = "Connect",
                        usePassword = "Use password",
                        newCode = "Create a new code",
                        quickConnectHelp =
                            "On another device, sign in to Jellyfin with the account for this TV, " +
                                "choose Quick Connect, and enter this code.",
                        enterCode = "Waiting for approval",
                        quickConnectDisabled = "Quick Connect is disabled on this server.",
                        quickConnectExpired = "The code expired. Create a new one.",
                        serverUnreachable = "The server could not be reached.",
                        quickConnectFailed = "Quick Connect failed. Check the server address.",
                        enterServerUrl = "Enter a complete http:// or https:// server URL.",
                        jellyfinConnectionFailed = "Could not connect to Jellyfin.",
                        tvTagline = "Your Jellyfin and Seerr library, designed for the big screen.",
                        email = "Email",
                        connected = "Connected",
                        remove = "Remove",
                        addJellyfin = "Add Jellyfin",
                        addSeerr = "Add Seerr",
                        connectSeerr = "Connect Seerr",
                        jellyfinAccount = "Jellyfin account",
                        seerrAccount = "Seerr account",
                        manageJellyfin = "Manage Jellyfin",
                        manageSeerr = "Manage Seerr",
                        connections = "Server connections",
                        removeServerConfirm = "Remove %s?",
                        removeServerMessage = "You will need to sign in again to use this server.",
                    ),
                cancel = "Cancel",
                searchScreen =
                    TvSearchStrings(
                        jellyfinSearchFailed = "Jellyfin search failed.",
                        searchHint = "Search Jellyfin and Seerr",
                        searching = "Searching…",
                        noResults = "No results",
                    ),
                detailText =
                    TvDetailLabelStrings(
                        overview = "Overview",
                        details = "Details",
                        episodes = "Episodes",
                        similar = "Similar",
                        trailer = "Trailer",
                        cast = "Cast",
                        noOverview = "No overview available.",
                        seasonNumber = "Season %d",
                        specials = "Specials",
                        detailUnavailable = "This item is unavailable.",
                        detailLoadFailed = "Details could not be loaded.",
                        trailerOpenFailed = "The trailer could not be opened.",
                    ),
                preferences =
                    TvPreferenceStrings(
                        appearancePlayback = "Playback and appearance",
                        settingsChooseCategory = "Choose a category",
                        settingsAppearance = "Appearance",
                        settingsAppearanceDescription = "Language and home screen",
                        settingsPlaybackCategory = "Playback",
                        settingsPlaybackDescription = "Quality, autoplay, seeking and trailers",
                        settingsAudioSubtitles = "Audio & Subtitles",
                        settingsAudioSubtitlesDescription = "Languages, subtitle mode and appearance",
                        settingsSegmentSkippingDescription = "Intro, recap, credits, previews and commercials",
                        settingsConnectionsDescription = "Jellyfin and Seerr servers",
                        quality = "Streaming quality",
                        audioLanguage = "Preferred audio",
                        subtitleLanguage = "Preferred subtitles",
                        autoplay = "Autoplay next episode",
                        resume = "Resume behavior",
                        playbackSpeed = "Playback speed",
                        statsForNerds = "Stats for Nerds",
                        trailerPreviews = "Trailer previews",
                        trailerPreviewSound = "Preview sound",
                        language = "Language",
                        subtitleMode = "Subtitle mode",
                        subtitleSize = "Subtitle size",
                        subtitleBackground = "Subtitle background",
                        seekBack = "Seek back",
                        seekForward = "Seek forward",
                        automatic = "Automatic",
                        systemDefault = "System default",
                        english = "English",
                        german = "German",
                        small = "Small",
                        medium = "Medium",
                        large = "Large",
                        translucent = "Translucent",
                        dark = "Dark",
                        forcedOnly = "Forced only",
                        preferredAlways = "Preferred language",
                        preferredWhenAudioDiffers = "Preferred when audio differs",
                        countdown = "Countdown",
                        immediate = "Immediately",
                        ask = "Always ask",
                    ),
                homeRows =
                    TvHomeRowStrings(
                        recentlyAdded = "Recently added",
                        lastThirtyDays = "Last 30 days",
                        latestAdditions = "Latest additions",
                        fromYourLibrary = "From your library",
                        continueWatching = "Continue watching",
                        nextUp = "Next up",
                        recentShows = "Recently added shows",
                        recentMovies = "Recently added movies",
                        homeSections = "Server home sections",
                        watchNextRow = "Show on TV home screen",
                    ),
                back = "Back",
                close = "Close",
                browse =
                    TvBrowseStrings(
                        allTitles = "All titles",
                        sort = "Sort",
                        filters = "Filters",
                        ascending = "Ascending",
                        descending = "Descending",
                        titleSort = "Title",
                        dateAddedSort = "Date added",
                        releaseYearSort = "Release year",
                        playedFilter = "Watched",
                        unplayed = "Unplayed",
                        favoritesOnly = "Favorites only",
                        clearFilters = "Clear filters",
                        genre = "Genre",
                        mediaType = "Media type",
                        applyLabel = "Apply",
                        libraryLoadFailed = "This library could not be loaded.",
                        loadMore = "Load more",
                        addToMyList = "Add to My List",
                        removeFromMyList = "Remove from My List",
                        markPlayed = "Mark played",
                        markUnplayed = "Mark unplayed",
                        myMedia = "My media",
                        myList = "My List",
                    ),
                seerr =
                    TvSeerrStrings(
                        connectSeerrPrompt = "Connect Seerr in Settings to discover and request media.",
                        requests = "Requests",
                        cannotRequest = "Your Seerr account cannot request this media type.",
                        request = "Request",
                        requestMoreSeasons = "Request more seasons",
                        version = "Version",
                        standard = "Standard",
                        seasons = "Seasons",
                        season = "Season",
                        requestProfile = "Request profile",
                        serverDefault = "Server default",
                        sendRequest = "Send request",
                        requesting = "Requesting…",
                        requestFailed = "The request could not be submitted.",
                        railTrending = "Trending",
                        railPopularMovies = "Popular movies",
                        railPopularShows = "Popular shows",
                        railUpcomingMovies = "Upcoming movies",
                        railUpcomingShows = "Upcoming shows",
                        requestPending = "Pending",
                        requestApproved = "Approved",
                        requestDeclined = "Declined",
                        requestFailedStatus = "Failed",
                        requestCompleted = "Completed",
                        requestUnknown = "Unknown",
                        availabilityAvailable = "Available",
                        availabilityProcessing = "Processing",
                        availabilityPartiallyAvailable = "Partially available",
                        availabilityUnavailable = "Unavailable",
                        discoverLoadFailed = "Discover could not be loaded.",
                        seerrSearchFailed = "Seerr search failed.",
                    ),
                all = "All",
                on = "On",
                off = "Off",
                playbackUi =
                    TvPlaybackStrings(
                        nextEpisode = "Next episode",
                        playNow = "Play now",
                        audio = "Audio",
                        subtitles = "Subtitles",
                        speed = "Speed",
                        stats = "Stats",
                        moreOptions = "More options",
                        syncPlay = "SyncPlay",
                        syncPlayNoPermission = "Your Jellyfin account cannot join SyncPlay groups.",
                        syncPlayUnauthorized = "Sign in to Jellyfin again to use SyncPlay.",
                        syncPlayNetworkError = "SyncPlay could not reach your Jellyfin server.",
                        syncPlayInvalidResponse = "Jellyfin returned an invalid SyncPlay response.",
                        preparingPlayback = "Preparing playback",
                        playbackFailedTitle = "Playback could not continue",
                        playbackFailedMessage =
                            "The player could not decode this stream. " +
                                "Try again to request a compatible version from Jellyfin.",
                        playback = "Playback",
                        pause = "Pause",
                        audioTrack = "Audio track %d",
                        subtitleTrack = "Subtitle track %d",
                        showStats = "Show Stats for Nerds",
                        hideStats = "Hide Stats for Nerds",
                        mode = "Mode",
                        container = "Container",
                        video = "Video",
                        resolution = "Resolution",
                        bitrate = "Bitrate",
                        range = "Range",
                        droppedFrames = "Dropped frames",
                        joinedGroup = "Joined: %s",
                        useCurrentItemAsQueue = "Use current item as queue",
                        leaveGroup = "Leave group",
                        createGroup = "Create Jellystack TV group",
                        joinGroup = "Join %s",
                        modeDirect = "Direct",
                        modeHls = "Transcode (HLS)",
                        modeLocal = "Local",
                        pausedLabel = "Paused",
                        resumeAskTitle = "Resume playback?",
                        continueFrom = "Continue from %s",
                    ),
                more = "More",
                none = "None",
                restart = "Restart",
                segments =
                    TvSegmentStrings(
                        segmentSkipping = "Segment skipping",
                        serverSegmentExplanation =
                            "Uses segment markers supplied by your Jellyfin server. " +
                                "Availability depends on your server and media.",
                        introSegments = "Intros",
                        recapSegments = "Recaps",
                        outroSegments = "Credits",
                        previewSegments = "Previews",
                        commercialSegments = "Commercials",
                        showSkipButton = "Show skip button",
                        skipAutomatically = "Skip automatically",
                    ),
                player =
                    TvPlayerStrings(
                        skipIntro = "Skip intro",
                        skipRecap = "Skip recap",
                        skipPreview = "Skip preview",
                        skipCommercial = "Skip commercial",
                        skipCredits = "Skip credits",
                        playNextEpisode = "Play next episode",
                        playNext = "Play next",
                        endsAt = "Ends at %s",
                        chapters = "Chapters",
                        chapterNumber = "Chapter %d",
                        upNext = "Up next",
                        watchCredits = "Watch credits",
                        forcedTrack = "Forced",
                        defaultTrack = "Default",
                        startFailedTitle = "Playback could not start",
                        startFailedMessage =
                            "Could not start playback. Check the connection to your server and try again.",
                        noPlayableEpisodeMessage = "This series has no playable episodes.",
                        videoScale = "Picture size",
                        videoScaleFit = "Fit",
                        videoScaleZoom = "Zoom (crop)",
                        videoScaleStretch = "Stretch",
                        sync = TvPlayerSyncStrings.ENGLISH,
                        noPlayableItemMessage = "This list has nothing that can be played.",
                    ),
                profile =
                    TvProfileStrings(
                        profiles = "Profiles",
                        chooseProfile = "Who's watching?",
                        addProfile = "Add profile",
                        manageProfiles = "Manage profiles",
                        removeProfile = "Remove profile",
                        removeProfileMessage =
                            "This removes local preferences, saves and PIN only. " +
                                "The server account is not deleted.",
                        enterProfilePin = "Enter profile PIN",
                        profileLocked = "Too many attempts. Try again shortly.",
                        reconnectProfile = "Reconnect profile",
                        switchingProfile = "Switching profile…",
                        settingUpProfile = "Setting up profile…",
                        profilePin = "Profile PIN",
                        setProfilePin = "Set a new PIN",
                        confirmProfilePin = "Confirm the new PIN",
                        removeProfilePin = "Remove PIN",
                        pinMismatch = "PINs did not match. Try again.",
                        menuKeyTip = "Tip: press ≡ Menu on any main screen to switch profile",
                    ),
                mediaTypeLabels = englishMediaTypeStrings,
                emptyStates = englishEmptyStateStrings,
                messages = englishMessageStrings,
                metadata =
                    TvMetadataStrings(
                        minutesLeft = "%d min left",
                        minutesShort = "%d min",
                        rtCritics = "RT Critics %.0f%%",
                        rtAudience = "RT Audience %.0f%%",
                        frameRate = "Frame rate",
                        dynamicRange = "Dynamic range",
                        buffer = "Buffer",
                        spotlightPosition = "Spotlight %1\$d of %2\$d",
                        captionsBadge = "CC",
                    ),
                settingsLabels =
                    TvSettingsStrings(
                        trailerPreviewDelay = "Preview delay",
                        matchRefreshRate = "Match refresh rate",
                        secondsShort = "%s s",
                        decimalSeparator = ".",
                    ),
                diagnostics = englishDiagnosticStrings,
                homeSettings = englishHomeSettingsStrings,
                itemSingular = "item",
                itemPlural = "items",
            )
    }
}

private val englishMessageStrings =
    TvMessageStrings(
        locale = Locale.ENGLISH,
        exitAppTitle = "Exit Jellystack?",
        exitAppMessage = "Do you want to close the app?",
        exitApp = "Exit",
        editName = "Edit name",
        quickConnectScan = "Scan to open Quick Connect on your phone",
        itemListLoadFailed = "This list could not be loaded.",
        playAll = "Play all",
        filmography = "Filmography",
        collectionLabel = "Collection",
        playlistLabel = "Playlist",
        music = TvMusicStrings.ENGLISH,
        liveTv = TvLiveTvStrings.ENGLISH,
        cardBadges = TvCardBadgeStrings.ENGLISH,
        accountHasProfileFormat =
            "%s already has a profile. To add another account, approve the code with that account.",
        voiceSearchUnavailable = "Voice search is not available on this device.",
        voiceSearchNoSpeech = "No speech was recognized. Try again.",
        voiceSearchFailed = "Voice search failed. Try again.",
        pinDigitsEnteredFormat = "%1\$d of %2\$d digits entered",
        attemptRemainingFormat = "%d attempt remaining",
        attemptsRemainingFormat = "%d attempts remaining",
        playingInOneSecondFormat = "Playing in %d second",
        playingInSecondsFormat = "Playing in %d seconds",
        untitledRequestFormat = "Request #%d",
        requestTitleFormat = "Request %s",
        loadingDetails = "Loading details…",
        untitled = "Untitled",
    )

private val englishHomeSettingsStrings =
    TvHomeSettingsStrings(
        categoryTitle = "Home",
        categoryDescription = "Show, hide and reorder the rows",
        intro = "Press OK to show or hide a row. Use the arrows to move it up or down.",
        shown = "Shown",
        hidden = "Hidden",
        moveUp = "Move up",
        moveDown = "Move down",
        empty = "The rows appear here once the home screen has loaded.",
    )

private val englishDiagnosticStrings =
    TvDiagnosticsStrings(
        categoryTitle = "Diagnostics",
        categoryDescription = "Version, last crash and recent log",
        actionFailed = "That did not work. Check the connection to your server and try again.",
        localOnlyNote = "This information stays on this device. Nothing is sent anywhere.",
        appVersionLabel = "App version",
        deviceLabel = "Device",
        lastCrash = "Last crash",
        noCrash = "No crash has been recorded.",
        clearCrash = "Clear crash report",
        recentLog = "Recent log (newest first)",
        noLogLines = "Nothing has been logged yet.",
        refresh = "Refresh",
        androidVersionFormat = "Android %1\$s (API %2\$d)",
        crashSummaryFormat = "%1\$s on thread %2\$s, version %3\$s",
    )
