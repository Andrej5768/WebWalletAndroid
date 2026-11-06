package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.andrewdev.webwallet.core.model.UploadFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A file shared into the app from another one ("Share" or "Open with"), waiting for the imports screen. The grant
 * to read it ends with the sharing activity, so the bytes are read at once and held here until they are taken.
 */
@Singleton
class SharedFileInbox @Inject constructor() {
    private val file = MutableStateFlow<UploadFile?>(null)

    val pending: StateFlow<UploadFile?> = file.asStateFlow()

    fun put(upload: UploadFile) {
        file.value = upload
    }

    /** The waiting file, once: the screen that takes it is the only one to handle it. */
    fun take(): UploadFile? = file.getAndUpdateToNull()

    private fun MutableStateFlow<UploadFile?>.getAndUpdateToNull(): UploadFile? {
        while (true) {
            val current = value ?: return null
            if (compareAndSet(current, null)) return current
        }
    }
}
