# Datenschutzerklärung für Jellystack

Stand: 30. Juli 2026

Jellystack ist ein Client für selbst gehostete Medienserver und betreibt keinen eigenen Jellystack-Cloud-Dienst.

Die App verbindet sich direkt vom Gerät des Nutzers mit den selbst eingerichteten Jellyfin- und Seerr-Servern. Der Entwickler erhält keine Medien, Zugangsdaten, Bibliotheksinhalte, Wiedergabeverläufe, Anfragen oder Serveradressen.

Serverkonfiguration, Zugriffstokens, Sitzungsdaten, Einstellungen, noch nicht synchronisierter Wiedergabefortschritt und Download-Metadaten werden lokal gespeichert. Authentifizierungsgeheimnisse werden, soweit von der Plattform unterstützt, im geschützten Anmeldedatenspeicher abgelegt.

Jellystack enthält keine Werbung, Analyse-SDKs, Tracking oder automatische Log-Uploads. Google Play Services werden ausschließlich für die optionale Cast-Funktion verwendet.

## Konten und Löschung

Jellystack erstellt und betreibt keine Benutzerkonten. Die App meldet sich ausschließlich bei bestehenden Konten auf den vom Nutzer gewählten Jellyfin- und Seerr-Servern an. Diese Konten und die serverseitigen Daten werden vom jeweiligen Server und dessen Administrator verwaltet, nicht vom Jellystack-Entwickler.

Alle lokal gespeicherten Jellystack-Daten können über Androids Funktion **Speicherinhalt löschen** oder durch Deinstallation der App entfernt werden. Für die Löschung eines Jellyfin- oder Seerr-Kontos oder anderer serverseitiger Daten ist der Administrator des jeweiligen Servers zuständig.

Für Cast kann Android erst nach Auswahl der Funktion den Zugriff auf Geräte in der Nähe (Android 13+) beziehungsweise den Standort (Android 12 und älter) anfragen. Während einer aktiven Cast-Sitzung kann die Benachrichtigungsberechtigung für Wiedergabesteuerungen angefragt werden. Cast funktioniert auch bei Ablehnung weiter.

Offline-Medien verbleiben im app-spezifischen Speicher. Jellystack fordert keinen allgemeinen Zugriff auf Fotos, Medien, USB-Speicher, Telefonstatus oder Geräteidentität an.

HTTP bleibt für lokale Server möglich, wird aber vor der ersten Anmeldung deutlich als unverschlüsselt gekennzeichnet. HTTPS wird empfohlen.

Quellcode und öffentliche Fehlerverfolgung: <https://github.com/Darkatek7/jellystack>

Sicherheitslücken bitte vertraulich über den Security-Bereich des Repositories melden.
