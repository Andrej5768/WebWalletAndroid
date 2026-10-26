package org.andrewdev.webwallet.core.auth.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import org.andrewdev.webwallet.core.auth.SessionCleanup
import org.andrewdev.webwallet.core.auth.SessionManager
import org.andrewdev.webwallet.core.network.AccessTokenProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    abstract fun accessTokenProvider(manager: SessionManager): AccessTokenProvider

    /** Empty until a data module contributes its clean-up, so the set exists in every graph. */
    @Multibinds
    abstract fun sessionCleanups(): Set<SessionCleanup>
}
