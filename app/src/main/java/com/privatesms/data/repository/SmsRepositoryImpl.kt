package com.privatesms.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.privatesms.data.contacts.ContactsRepository
import com.privatesms.data.db.DatabaseManager
import com.privatesms.data.model.ConversationEntity
import com.privatesms.data.model.MessageEntity
import com.privatesms.data.sms.SmsSender
import com.privatesms.domain.model.Conversation
import com.privatesms.domain.model.Message
import com.privatesms.domain.repository.SmsRepository
import com.privatesms.service.ScheduledSmsReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseManager: DatabaseManager,
    private val contactsRepository: ContactsRepository,
    private val smsSender: SmsSender
) : SmsRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getConversations(archived: Boolean, isPrivate: Boolean): Flow<List<Conversation>> {
        return databaseManager.isUnlocked.flatMapLatest { unlocked ->
            if (unlocked) {
                databaseManager.getDatabase().conversationDao().getConversations(archived, isPrivate)
                    .map { entities ->
                        entities.map { entity ->
                            val contact = contactsRepository.getContactByAddress(entity.address)
                            Conversation(
                                threadId = entity.threadId,
                                address = entity.address,
                                snippet = entity.snippet,
                                date = entity.date,
                                read = entity.read,
                                archived = entity.archived,
                                isPrivate = entity.isPrivate,
                                messageCount = entity.messageCount,
                                isSpam = entity.isSpam,
                                contactName = contact?.name,
                                contactPhotoUri = contact?.photoUri
                            )
                        }
                    }
            } else {
                flowOf(emptyList())
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMessagesForThread(threadId: Long): Flow<List<Message>> {
        return databaseManager.isUnlocked.flatMapLatest { unlocked ->
            if (unlocked) {
                databaseManager.getDatabase().messageDao().getMessagesForThread(threadId)
                    .map { entities ->
                        if (entities.isEmpty()) return@map emptyList()
                        
                        // Optimize contact resolving: query details only once per unique address in this thread
                        val uniqueAddresses = entities.map { it.address }.distinct()
                        val contactMap = uniqueAddresses.associateWith { address ->
                            contactsRepository.getContactByAddress(address)
                        }

                        entities.map { entity ->
                            val contact = contactMap[entity.address]
                            Message(
                                id = entity.id,
                                threadId = entity.threadId,
                                address = entity.address,
                                body = entity.body,
                                type = entity.type,
                                date = entity.date,
                                dateSent = entity.dateSent,
                                read = entity.read,
                                status = entity.status,
                                isScheduled = entity.isScheduled,
                                scheduledTime = entity.scheduledTime,
                                isEncrypted = entity.isEncrypted,
                                attachmentPath = entity.attachmentPath,
                                contactName = contact?.name,
                                contactPhotoUri = contact?.photoUri
                            )
                        }
                    }
            } else {
                flowOf(emptyList())
            }
        }
    }

    override suspend fun sendSms(address: String, body: String, subscriptionId: Int?) {
        val db = databaseManager.getDatabase()
        val threadId = address.hashCode().toLong()
        val now = System.currentTimeMillis()

        val msg = MessageEntity(
            threadId = threadId,
            address = address,
            body = body,
            type = MessageEntity.TYPE_SENT,
            date = now,
            dateSent = now,
            read = true,
            status = MessageEntity.STATUS_NONE,
            isScheduled = false
        )
        val id = db.messageDao().insertMessage(msg)

        val existing = db.conversationDao().getConversationByThreadId(threadId)
        val count = (existing?.messageCount ?: 0) + 1
        db.conversationDao().insertConversation(
            ConversationEntity(
                threadId = threadId,
                address = address,
                snippet = body,
                date = now,
                read = true,
                archived = existing?.archived ?: false,
                isPrivate = existing?.isPrivate ?: false,
                messageCount = count,
                isSpam = existing?.isSpam ?: false
            )
        )

        smsSender.sendSms(address, body, id, subscriptionId)
    }

    override suspend fun deleteMessage(messageId: Long) {
        val db = databaseManager.getDatabase()
        db.messageDao().deleteMessageById(messageId)
    }

    override suspend fun deleteConversation(threadId: Long) {
        val db = databaseManager.getDatabase()
        db.messageDao().deleteMessagesForThread(threadId)
        db.conversationDao().deleteConversation(threadId)
    }

    override suspend fun archiveConversation(threadId: Long, archived: Boolean) {
        val db = databaseManager.getDatabase()
        val conv = db.conversationDao().getConversationByThreadId(threadId) ?: return
        db.conversationDao().updateConversation(conv.copy(archived = archived))
    }

    override suspend fun markConversationPrivate(threadId: Long, isPrivate: Boolean) {
        val db = databaseManager.getDatabase()
        val conv = db.conversationDao().getConversationByThreadId(threadId) ?: return
        db.conversationDao().updateConversation(conv.copy(isPrivate = isPrivate))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun searchMessages(query: String): Flow<List<Message>> {
        return databaseManager.isUnlocked.flatMapLatest { unlocked ->
            if (unlocked) {
                databaseManager.getDatabase().messageDao().searchMessages(query)
                    .map { entities ->
                        entities.map { entity ->
                            val contact = contactsRepository.getContactByAddress(entity.address)
                            Message(
                                id = entity.id,
                                threadId = entity.threadId,
                                address = entity.address,
                                body = entity.body,
                                type = entity.type,
                                date = entity.date,
                                dateSent = entity.dateSent,
                                read = entity.read,
                                status = entity.status,
                                isScheduled = entity.isScheduled,
                                scheduledTime = entity.scheduledTime,
                                isEncrypted = entity.isEncrypted,
                                attachmentPath = entity.attachmentPath,
                                contactName = contact?.name,
                                contactPhotoUri = contact?.photoUri
                            )
                        }
                    }
            } else {
                flowOf(emptyList())
            }
        }
    }

    override suspend fun setReadStatus(threadId: Long, read: Boolean) {
        val db = databaseManager.getDatabase()
        db.conversationDao().updateReadStatus(threadId, read)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getScheduledMessages(): Flow<List<Message>> {
        return databaseManager.isUnlocked.flatMapLatest { unlocked ->
            if (unlocked) {
                databaseManager.getDatabase().messageDao().getScheduledMessages()
                    .map { entities ->
                        entities.map { entity ->
                            val contact = contactsRepository.getContactByAddress(entity.address)
                            Message(
                                id = entity.id,
                                threadId = entity.threadId,
                                address = entity.address,
                                body = entity.body,
                                type = entity.type,
                                date = entity.date,
                                dateSent = entity.dateSent,
                                read = entity.read,
                                status = entity.status,
                                isScheduled = entity.isScheduled,
                                scheduledTime = entity.scheduledTime,
                                isEncrypted = entity.isEncrypted,
                                attachmentPath = entity.attachmentPath,
                                contactName = contact?.name,
                                contactPhotoUri = contact?.photoUri
                            )
                        }
                    }
            } else {
                flowOf(emptyList())
            }
        }
    }

    override suspend fun scheduleSms(address: String, body: String, scheduledTime: Long) {
        val db = databaseManager.getDatabase()
        val threadId = address.hashCode().toLong()
        val now = System.currentTimeMillis()

        val msg = MessageEntity(
            threadId = threadId,
            address = address,
            body = body,
            type = MessageEntity.TYPE_SENT,
            date = now,
            dateSent = now,
            read = true,
            status = MessageEntity.STATUS_NONE,
            isScheduled = true,
            scheduledTime = scheduledTime
        )
        val id = db.messageDao().insertMessage(msg)
        
        scheduleAlarm(id, scheduledTime)
    }

    override suspend fun cancelScheduledSms(messageId: Long) {
        val db = databaseManager.getDatabase()
        db.messageDao().deleteMessageById(messageId)
        cancelAlarm(messageId)
    }

    private fun scheduleAlarm(messageId: Long, timeMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduledSmsReceiver::class.java).apply {
            putExtra("message_id", messageId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
        }
    }

    private fun cancelAlarm(messageId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduledSmsReceiver::class.java).apply {
            putExtra("message_id", messageId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    override suspend fun reschedulePendingAlarms() {
        val db = databaseManager.getDatabase()
        val now = System.currentTimeMillis()
        val scheduled = db.messageDao().getPendingScheduledMessagesSync(Long.MAX_VALUE)
        scheduled.forEach { msg ->
            if (msg.scheduledTime != null && msg.scheduledTime > now) {
                scheduleAlarm(msg.id, msg.scheduledTime)
            } else if (msg.scheduledTime != null && msg.scheduledTime <= now) {
                // If the scheduled time passed while the phone was off, dispatch it now
                smsSender.sendSms(msg.address, msg.body, msg.id, null)
                db.messageDao().updateMessage(msg.copy(
                    isScheduled = false,
                    status = MessageEntity.STATUS_SENT
                ))
            }
        }
    }
}
