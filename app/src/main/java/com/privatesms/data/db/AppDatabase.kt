package com.privatesms.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.privatesms.data.model.BlockedNumberEntity
import com.privatesms.data.model.ConversationEntity
import com.privatesms.data.model.MessageEntity

@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        BlockedNumberEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun blocklistDao(): BlocklistDao
}
