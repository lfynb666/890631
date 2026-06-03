package com.example.opencode.data

data class ConnectionSettings(
    val host: String = "",
    val port: String = "4096",
    val password: String = "",
) {
    val baseUrl: String
        get() {
            val trimmedHost = host.trim()
            val schemeHost = when {
                trimmedHost.startsWith("http://") || trimmedHost.startsWith("https://") -> trimmedHost
                else -> "http://$trimmedHost"
            }.trimEnd('/')

            return if (port.isBlank() || schemeHost.substringAfter("://").contains(":")) {
                schemeHost
            } else {
                "$schemeHost:${port.trim()}"
            }
        }

    val isConnectable: Boolean
        get() {
            val schemeHost = host.trim()
                .let {
                    when {
                        it.startsWith("http://") || it.startsWith("https://") -> it
                        else -> "http://$it"
                    }
                }
                .trimEnd('/')
            val hasPort = schemeHost.substringAfter("://").contains(":")
            val portNumber = port.trim().toIntOrNull()
            return host.isNotBlank() &&
                (hasPort || (portNumber != null && portNumber in 1..65535))
        }
}
