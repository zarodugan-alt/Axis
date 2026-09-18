package axis.app.di

import axis.kernel.events.EventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Singleton bindings. Repositories carry `@Inject` constructors so they
 * need no entries here — this module only covers types we don't own the
 * construction of (today: just the event bus).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEventBus(): EventBus = EventBus()
}
