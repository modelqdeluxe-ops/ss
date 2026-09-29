// Proyecto Gradle mínimo para la app del dueño con Google Play Billing (lo arma y lo usa scripts/pagos.sh)
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "rumentis"
