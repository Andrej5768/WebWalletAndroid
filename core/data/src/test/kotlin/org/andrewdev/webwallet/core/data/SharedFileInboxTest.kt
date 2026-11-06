package org.andrewdev.webwallet.core.data

import org.andrewdev.webwallet.core.model.UploadFile
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SharedFileInboxTest {

    @Test
    fun `a shared file is taken once and a newer one replaces it`() {
        val inbox = SharedFileInbox()
        val first = UploadFile("a.pdf", "application/pdf", byteArrayOf(1))
        val second = UploadFile("b.pdf", "application/pdf", byteArrayOf(2))
        inbox.put(first)
        inbox.put(second)
        assertSame(second, inbox.take())
        assertNull(inbox.take())
    }
}
