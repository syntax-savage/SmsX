package com.privatesms.di

import android.content.Context
import com.privatesms.data.db.DatabaseManager
import com.privatesms.domain.repository.BlocklistRepository
import com.privatesms.domain.repository.SmsRepository
import com.privatesms.domain.usecase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideGetConversationsUseCase(smsRepository: SmsRepository): GetConversationsUseCase {
        return GetConversationsUseCase(smsRepository)
    }

    @Provides
    @Singleton
    fun provideGetMessagesUseCase(smsRepository: SmsRepository): GetMessagesUseCase {
        return GetMessagesUseCase(smsRepository)
    }

    @Provides
    @Singleton
    fun provideSendSmsUseCase(smsRepository: SmsRepository): SendSmsUseCase {
        return SendSmsUseCase(smsRepository)
    }

    @Provides
    @Singleton
    fun provideDeleteConversationUseCase(smsRepository: SmsRepository): DeleteConversationUseCase {
        return DeleteConversationUseCase(smsRepository)
    }

    @Provides
    @Singleton
    fun provideBlockNumberUseCase(blocklistRepository: BlocklistRepository): BlockNumberUseCase {
        return BlockNumberUseCase(blocklistRepository)
    }

    @Provides
    @Singleton
    fun provideScheduleMessageUseCase(smsRepository: SmsRepository): ScheduleMessageUseCase {
        return ScheduleMessageUseCase(smsRepository)
    }

    @Provides
    @Singleton
    fun provideBackupRestoreUseCase(
        @ApplicationContext context: Context,
        databaseManager: DatabaseManager
    ): BackupRestoreUseCase {
        return BackupRestoreUseCase(context, databaseManager)
    }
}
