package com.d4rk.cleaner.app.clean.contacts.domain.usecases

import android.util.Log
import com.d4rk.android.libs.apptoolkit.core.domain.model.network.DataState
import com.d4rk.cleaner.app.clean.contacts.data.ContactsRepository
import com.d4rk.cleaner.app.clean.contacts.domain.data.model.RawContactInfo
import com.d4rk.cleaner.core.domain.model.network.Errors
import com.d4rk.cleaner.core.utils.extensions.toError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val TAG = "GetDuplicateContactsUseCase"

class GetDuplicateContactsUseCase(private val repository: ContactsRepository) {
    operator fun invoke(): Flow<DataState<List<List<RawContactInfo>>, Errors>> = flow {
        emit(DataState.Loading())
        runCatching { repository.findDuplicates() }
            .onSuccess { emit(DataState.Success(it)) }
            .onFailure {
                Log.e(TAG, "Error fetching duplicate contacts", it)
                emit(DataState.Error(error = it.toError()))
            }
    }
}
