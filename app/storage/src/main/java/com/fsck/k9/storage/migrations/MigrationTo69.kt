package com.fsck.k9.storage.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.fsck.k9.controller.MessagingControllerCommands.PendingSetFlag
import com.fsck.k9.controller.PendingCommandSerializer
import com.fsck.k9.mail.Flag
import com.squareup.moshi.Moshi

internal class MigrationTo69(private val db: SQLiteDatabase) {
    private val serializer: PendingCommandSerializer = PendingCommandSerializer.getInstance()

    fun createPendingDelete() {
        val pendingSetFlagsToConvert = mutableListOf<PendingSetFlag>()

        db.rawQuery("SELECT id, command, data FROM pending_commands WHERE command = 'set_flag'", null).use { cursor ->
            while (cursor.moveToNext()) {
                val databaseId = cursor.getLong(0)
                val commandName = cursor.getString(1)
                val data = cursor.getString(2)

                val pendingSetFlag = serializer.unserialize(databaseId, commandName, data) as PendingSetFlag
                if (pendingSetFlag.flag == Flag.DELETED && pendingSetFlag.newState) {
                    pendingSetFlagsToConvert.add(pendingSetFlag)
                }
            }
        }

        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(LegacyPendingDelete::class.java)

        for (pendingSetFlag in pendingSetFlagsToConvert) {
            val pendingDelete = LegacyPendingDelete.create(pendingSetFlag.folder, pendingSetFlag.uids)
            val contentValues = ContentValues().apply {
                put("command", "delete")
                put("data", adapter.toJson(pendingDelete))
            }

            db.update("pending_commands", contentValues, "id = ?", arrayOf(pendingSetFlag.databaseId.toString()))
        }
    }
}
