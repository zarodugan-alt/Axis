package axis.app.di

import android.content.Context
import axis.act.action.SystemActions
import axis.act.routine.RoutineEngine
import axis.act.routine.RoutineRepository
import axis.agent.AgentLoop
import axis.agent.LlmGateway
import axis.agent.http.AxisHttp
import axis.agent.http.UrlConnectionAxisHttp
import axis.agent.speech.TtsClient
import axis.agent.tool.ToolRegistry
import axis.agent.usage.UsageLedger
import axis.app.agent.SafetyGate
import axis.app.agent.ToolBelt
import axis.app.data.ProviderStore
import axis.app.data.SettingsStore
import axis.app.data.UsageRepository
import axis.app.speech.TtsSpeaker
import axis.app.system.AxisNotifications
import axis.kernel.events.EventBus
import axis.safety.AuditLog
import axis.safety.PolicyEngine
import axis.sense.DeviceStateRepository
import axis.sense.notify.NotificationInbox
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The object graph. Repositories carry `@Inject` constructors; this module
 * builds the pieces that need context or cross-module wiring: the network
 * seam, the gateway, the safety core, the routine engine and the agent loop.
 *
 * Note the explicit direction of every dependency — `:agent` and `:safety`
 * never see Android types, and `:app` is the only module that knows about
 * all of them.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ------------------------------------------------------------- kernel

    @Provides
    @Singleton
    fun provideEventBus(): EventBus = EventBus()

    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // -------------------------------------------------------------- agent

    @Provides
    @Singleton
    fun provideHttp(): AxisHttp = UrlConnectionAxisHttp()

    @Provides
    @Singleton
    fun provideUsageLedger(repository: UsageRepository): UsageLedger = UsageLedger(sink = repository)

    @Provides
    @Singleton
    fun provideGateway(http: AxisHttp, ledger: UsageLedger, store: ProviderStore): LlmGateway =
        LlmGateway(http = http, ledger = ledger, keyProvider = { id -> store.key(id) })

    @Provides
    @Singleton
    fun provideTtsClient(http: AxisHttp, ledger: UsageLedger, store: ProviderStore): TtsClient =
        TtsClient(http = http, ledger = ledger, keyProvider = { id -> store.key(id) })

    // --------------------------------------------------------------- sense

    @Provides
    @Singleton
    fun provideInbox(bus: EventBus): NotificationInbox = NotificationInbox(bus)

    @Provides
    @Singleton
    fun provideDeviceState(@ApplicationContext context: Context): DeviceStateRepository =
        DeviceStateRepository(context)

    // ---------------------------------------------------------------- act

    @Provides
    @Singleton
    fun provideSystemActions(@ApplicationContext context: Context): SystemActions = SystemActions(context)

    @Provides
    @Singleton
    fun provideRoutineRepository(@ApplicationContext context: Context): RoutineRepository =
        RoutineRepository(context)

    @Provides
    @Singleton
    fun provideNotifications(@ApplicationContext context: Context): AxisNotifications =
        AxisNotifications(context)

    @Provides
    @Singleton
    fun provideSpeaker(
        @ApplicationContext context: Context,
        client: TtsClient,
        store: ProviderStore
    ): TtsSpeaker = TtsSpeaker(context, client, store)

    @Provides
    @Singleton
    fun provideRoutineEngine(
        repo: RoutineRepository,
        actions: SystemActions,
        bus: EventBus,
        device: DeviceStateRepository,
        settings: SettingsStore,
        speaker: TtsSpeaker,
        notifications: AxisNotifications
    ): RoutineEngine = RoutineEngine(
        repo = repo,
        actions = actions,
        bus = bus,
        contextProvider = {
            val snapshot = device.sample()
            axis.act.routine.EngineContext(
                batteryPct = snapshot.batteryPct,
                charging = snapshot.charging,
                wifi = snapshot.wifi,
                foregroundApp = axis.sense.screen.AxisAccessibilityService.foregroundPackage.value,
                recentNotificationPkg = null
            )
        },
        killSwitch = { settings.killSwitchState.value },
        say = { text -> speaker.speak(text) },
        notify = { title, text -> notifications.postAgent(title, text) }
    )

    // ------------------------------------------------------------- safety

    @Provides
    @Singleton
    fun providePolicyEngine(settings: SettingsStore): PolicyEngine = PolicyEngine(
        policyProvider = { settings.policyState.value }
    )

    @Provides
    @Singleton
    fun provideAuditLog(): AuditLog = AuditLog()

    @Provides
    @Singleton
    fun provideSafetyGate(policy: PolicyEngine, audit: AuditLog, bus: EventBus): SafetyGate =
        SafetyGate(policy, audit, bus)

    // -------------------------------------------------------------- tools

    @Provides
    @Singleton
    fun provideToolBelt(
        apps: axis.app.drawer.AppRepository,
        device: DeviceStateRepository,
        actions: SystemActions,
        inbox: NotificationInbox,
        routines: RoutineEngine,
        speaker: TtsSpeaker,
        settings: SettingsStore
    ): ToolBelt = ToolBelt(apps, device, actions, inbox, routines, speaker, settings)

    @Provides
    @Singleton
    fun provideToolRegistry(belt: ToolBelt): ToolRegistry = ToolRegistry(belt.all())

    @Provides
    @Singleton
    fun provideAgentLoop(
        gateway: LlmGateway,
        registry: ToolRegistry,
        gate: SafetyGate
    ): AgentLoop = AgentLoop(gateway, registry, gate)
}
