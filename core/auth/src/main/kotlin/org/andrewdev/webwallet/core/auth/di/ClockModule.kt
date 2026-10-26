package org.andrewdev.webwallet.core.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.andrewdev.webwallet.core.common.KyivClock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    /** The one clock of the app; tests and previews build their own fixed one. */
    @Provides
    @Singleton
    fun kyivClock(): KyivClock = KyivClock()
}
