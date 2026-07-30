# Jellystack 0.14.3 - Production follow-up design

The Home feed must always begin with a usable Spotlight after a refresh. Refreshing resets the vertical feed to its first item, and a changed Spotlight candidate set recreates pager state so an outdated page cannot leave only the indicators visible.

The Requests search field keeps focus while query state and results update. Request capabilities are refreshed from Seerr during an explicit refresh; removing Advanced Requests clears cached request profiles immediately.

The Requests status filter uses one full-width, accessible dropdown selector instead of a wrapping group of status chips. The current filter remains visible while the other values stay hidden until the selector is opened.

The Android App Bundle remains a local Play Console artifact and is not attached to the public GitHub release.
