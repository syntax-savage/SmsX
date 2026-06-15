package com.privatesms.domain.usecase

import com.privatesms.domain.repository.SmsRepository
import javax.inject.Inject

class SendSmsUseCase @Inject constructor(
    private val smsRepository: SmsRepository
) {
    suspend operator fun invoke(address: String, body: String, subscriptionId: Int? = null) {
        smsRepository.sendSms(address, body, subscriptionId)
    }
}
