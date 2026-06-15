package com.privatesms.di

import com.privatesms.data.repository.BlocklistRepositoryImpl
import com.privatesms.data.repository.SmsRepositoryImpl
import com.privatesms.domain.repository.BlocklistRepository
import com.privatesms.domain.repository.SmsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSmsRepository(
        smsRepositoryImpl: SmsRepositoryImpl
    ): SmsRepository

    @Binds
    @Singleton
    abstract fun bindBlocklistRepository(
        blocklistRepositoryImpl: BlocklistRepositoryImpl
    ): BlocklistRepository
}
