package axis.app.di

import axis.agent.LlmGateway
import axis.agent.http.AxisHttp
import axis.agent.http.UrlConnectionAxisHttp
import axis.agent.speech.TtsClient
import axis.agent.usage.UsageLedger
import axis.app.data.ProviderStore
import axis.app.data.UsageRepository
import axis.kernel.events.EventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Singleton bindings. Repositories carry `@Inject` constructors so they need
 * no entries here — this module covers what Hilt cannot build on its own:
 * the event bus, the network seam, and the agent gateway (whose key lookup
 * closes over [ProviderStore]).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEventBus(): EventBus = EventBus()

    @Provides
    @Singleton
    fun provideHttp(): AxisHttp = UrlConnectionAxisHttp()

    @Provides
    @Singleton
    fun provideUsageLedger(repository: UsageRepository): UsageLedger =
        UsageLedger(sink = repository)

    @Provides
    @Singleton
    fun provideGateway(
        http: AxisHttp,
        ledger: UsageLedger,
        store: ProviderStore
    ): LlmGateway = LlmGateway(
        http = http,
        ledger = ledger,
        keyProvider = { providerId -> store.key(providerId) }
    )

    @Provides
    @Singleton
    fun provideTtsClient(
        http: AxisHttp,
        ledger: UsageLedger,
        store: ProviderStore
    ): TtsClient = TtsClient(
        http = http,
        ledger = ledger,
        keyProvider = { providerId -> store.key(providerId) }
    )
}
