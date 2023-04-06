package org.kde.kdeconnect.Plugins

class StubTextPlugin(private val description: String) : Plugin() {
    override fun getDisplayName() = description

    override fun getDescription() = description

    override fun getSupportedPacketTypes(): Array<String> {
        throw UnsupportedOperationException("StubTextPlugin is used only with displayName and description")
    }

    override fun getOutgoingPacketTypes(): Array<String> {
        throw UnsupportedOperationException("StubTextPlugin is used only with displayName and description")
    }
}