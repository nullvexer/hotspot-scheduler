package com.iranjan.hotspotscheduler.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.iranjan.hotspotscheduler.data.database.AppDatabase
import com.iranjan.hotspotscheduler.data.database.RoutineDao
import com.iranjan.hotspotscheduler.data.database.ExecutionRecordDao
import com.iranjan.hotspotscheduler.data.repository.RoutineRepository
import com.iranjan.hotspotscheduler.data.repository.RoutineRepositoryImpl
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferences
import com.iranjan.hotspotscheduler.data.datastore.AutomationPreferencesImpl
import com.iranjan.hotspotscheduler.data.encrypted.CredentialVault
import com.iranjan.hotspotscheduler.data.encrypted.CredentialVaultImpl
import com.iranjan.hotspotscheduler.data.encrypted.PasswordVault
import com.iranjan.hotspotscheduler.data.encrypted.PasswordVaultImpl
import com.iranjan.hotspotscheduler.domain.scheduler.DesiredStateResolver
import com.iranjan.hotspotscheduler.domain.scheduler.RoutineEvaluator
import com.iranjan.hotspotscheduler.domain.scheduler.OperationOrder
import com.iranjan.hotspotscheduler.automation.session.AutomationCoordinator
import com.iranjan.hotspotscheduler.automation.session.DefaultSessionQueue
import com.iranjan.hotspotscheduler.automation.session.SessionQueue
import com.iranjan.hotspotscheduler.automation.session.EmergencyStop
import com.iranjan.hotspotscheduler.automation.transaction.TransactionExecutor
import com.iranjan.hotspotscheduler.automation.transaction.TransactionExecutorImpl
import com.iranjan.hotspotscheduler.automation.verification.VerificationEngine
import com.iranjan.hotspotscheduler.automation.verification.VerificationEngineImpl
import com.iranjan.hotspotscheduler.automation.recovery.ReconciliationEngine
import com.iranjan.hotspotscheduler.automation.recovery.ReconciliationEngineImpl
import com.iranjan.hotspotscheduler.automation.recovery.CrashRecovery
import com.iranjan.hotspotscheduler.platform.alarm.AlarmScheduler
import com.iranjan.hotspotscheduler.platform.screen.ScreenSession
import com.iranjan.hotspotscheduler.platform.screen.ScreenSessionImpl
import com.iranjan.hotspotscheduler.platform.keyguard.KeyguardEngine
import com.iranjan.hotspotscheduler.platform.keyguard.KeyguardEngineImpl
import com.iranjan.hotspotscheduler.platform.keyguard.PinPadResolver
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntime
import com.iranjan.hotspotscheduler.platform.accessibility.AccessibilityRuntimeImpl
import com.iranjan.hotspotscheduler.automation.strategies.OperationStrategyResolver
import com.iranjan.hotspotscheduler.automation.strategies.SettingsNavigator
import com.iranjan.hotspotscheduler.automation.strategies.SamsungSettingsNavigationStrategy
import com.iranjan.hotspotscheduler.automation.strategies.NavigationStrategy
import com.iranjan.hotspotscheduler.automation.strategies.SwitchFinder
import com.iranjan.hotspotscheduler.automation.strategies.SamsungSettingsHotspotStrategy
import com.iranjan.hotspotscheduler.automation.strategies.SamsungSettingsDataStrategy
import com.iranjan.hotspotscheduler.automation.strategies.SamsungQuickSettingsHotspotStrategy
import com.iranjan.hotspotscheduler.automation.strategies.SamsungQuickSettingsDataStrategy
import com.iranjan.hotspotscheduler.automation.strategies.QuickSettingsNavigator
import com.iranjan.hotspotscheduler.automation.strategies.NetworkOperationStrategy
import com.iranjan.hotspotscheduler.platform.screen.WakeEngine
import com.iranjan.hotspotscheduler.platform.permissions.CapabilityProbe
import com.iranjan.hotspotscheduler.automation.diagnostics.AutomationDiagnostics
import com.iranjan.hotspotscheduler.automation.logger.AutomationLogger
import com.iranjan.hotspotscheduler.automation.logger.AutomationLoggerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "hotspot_scheduler.db").build()

    @Provides
    fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideExecutionRecordDao(db: AppDatabase): ExecutionRecordDao = db.executionRecordDao()
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("automation_prefs") }
        )

    @Provides
    @Singleton
    fun provideCredentialDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        val deContext = context.createDeviceProtectedStorageContext()
        return PreferenceDataStoreFactory.create(
            produceFile = { deContext.preferencesDataStoreFile("credential_vault") }
        )
    }

    @Provides
    @Singleton
    fun providePasswordDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        val deContext = context.createDeviceProtectedStorageContext()
        return PreferenceDataStoreFactory.create(
            produceFile = { deContext.preferencesDataStoreFile("password_vault") }
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository

    @Binds
    abstract fun bindAutomationPreferences(impl: AutomationPreferencesImpl): AutomationPreferences

    @Binds
    abstract fun bindCredentialVault(impl: CredentialVaultImpl): CredentialVault

    @Binds
    abstract fun bindPasswordVault(impl: PasswordVaultImpl): PasswordVault
}

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideDesiredStateResolver(
        routineRepository: RoutineRepository,
        preferences: AutomationPreferences
    ): DesiredStateResolver = DesiredStateResolver(routineRepository, preferences)

    @Provides
    @Singleton
    fun provideOperationOrder(): OperationOrder = OperationOrder
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformBindingsModule {

    @Binds
    abstract fun bindScreenSession(impl: ScreenSessionImpl): ScreenSession

    @Binds
    abstract fun bindKeyguardEngine(impl: KeyguardEngineImpl): KeyguardEngine

    @Binds
    abstract fun bindAccessibilityRuntime(impl: AccessibilityRuntimeImpl): AccessibilityRuntime

    @Binds
    abstract fun bindAlarmScheduler(impl: AlarmScheduler): com.iranjan.hotspotscheduler.platform.alarm.AlarmScheduler

    @Binds
    abstract fun bindNavigationStrategy(impl: SamsungSettingsNavigationStrategy): NavigationStrategy

    @Binds
    abstract fun bindReconciliationEngine(impl: ReconciliationEngineImpl): ReconciliationEngine
}

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {

    @Provides
    @Singleton
    fun provideWakeEngine(@ApplicationContext context: Context): WakeEngine = WakeEngine(context)

    @Provides
    @Singleton
    fun providePinPadResolver(accessibility: AccessibilityRuntime): PinPadResolver = PinPadResolver(accessibility)

    @Provides
    @Singleton
    fun provideSettingsNavigator(@ApplicationContext context: Context): SettingsNavigator = SettingsNavigator(context)

    @Provides
    @Singleton
    fun provideSwitchFinder(accessibility: AccessibilityRuntime, prefs: AutomationPreferences): SwitchFinder =
        SwitchFinder(accessibility, prefs)

    @Provides
    @Singleton
    fun provideCapabilityProbe(
        context: Context,
        accessibility: AccessibilityRuntime,
        wakeEngine: WakeEngine,
        keyguardEngine: KeyguardEngine,
        strategyResolver: OperationStrategyResolver,
        prefs: AutomationPreferences,
        credentialVault: CredentialVault
    ): CapabilityProbe = CapabilityProbe(context, accessibility, wakeEngine, keyguardEngine, strategyResolver, prefs, credentialVault)
}

@Module
@InstallIn(SingletonComponent::class)
object AutomationModule {

    @Provides
    @Singleton
    fun provideSessionQueue(): SessionQueue = DefaultSessionQueue()

    @Provides
    @Singleton
    fun provideHotspotStrategies(
        samsungSettings: SamsungSettingsHotspotStrategy,
        samsungQS: SamsungQuickSettingsHotspotStrategy
    ): List<NetworkOperationStrategy> = listOf(samsungSettings, samsungQS)

    @Provides
    @Singleton
    fun provideDataStrategies(
        samsungData: SamsungSettingsDataStrategy,
        samsungQS: SamsungQuickSettingsDataStrategy
    ): List<NetworkOperationStrategy> = listOf(samsungData, samsungQS)

    @Provides
    @Singleton
    fun provideQuickSettingsNavigator(accessibility: AccessibilityRuntime): QuickSettingsNavigator =
        QuickSettingsNavigator(accessibility)

    @Provides
    @Singleton
    fun provideOperationStrategyResolver(
        hotspotStrategies: List<NetworkOperationStrategy>,
        dataStrategies: List<NetworkOperationStrategy>,
        accessibility: AccessibilityRuntime
    ): OperationStrategyResolver = OperationStrategyResolver(hotspotStrategies, dataStrategies, accessibility)

    @Provides
    @Singleton
    fun provideTransactionExecutor(
        screenSession: ScreenSession,
        keyguardEngine: KeyguardEngine,
        strategyResolver: OperationStrategyResolver,
        verificationEngine: VerificationEngine,
        logger: AutomationLogger,
        emergencyStop: EmergencyStop
    ): TransactionExecutor = TransactionExecutorImpl(screenSession, keyguardEngine, strategyResolver, verificationEngine, logger, emergencyStop)

    @Provides
    @Singleton
    fun provideVerificationEngine(strategyResolver: OperationStrategyResolver): VerificationEngine =
        VerificationEngineImpl(strategyResolver)

    @Provides
    @Singleton
    fun provideCrashRecovery(
        reconciliationEngine: ReconciliationEngineImpl,
        logger: AutomationLogger
    ): CrashRecovery = CrashRecovery(reconciliationEngine, logger)

    @Provides
    @Singleton
    fun provideAutomationLogger(): AutomationLogger = AutomationLoggerImpl()

    @Provides
    @Singleton
    fun provideEmergencyStop(logger: AutomationLogger): EmergencyStop = EmergencyStop(logger)

    @Provides
    @Singleton
    fun provideAutomationDiagnostics(
        capabilityProbe: CapabilityProbe,
        crashRecovery: CrashRecovery,
        logger: AutomationLogger,
        accessibility: AccessibilityRuntime,
        screenSession: ScreenSession,
        keyguardEngine: KeyguardEngine,
        strategyResolver: OperationStrategyResolver
    ): AutomationDiagnostics = AutomationDiagnostics(capabilityProbe, crashRecovery, logger, accessibility, screenSession, keyguardEngine, strategyResolver)

    @Provides
    @Singleton
    fun provideAutomationCoordinator(
        sessionQueue: SessionQueue,
        transactionExecutor: TransactionExecutor,
        alarmScheduler: AlarmScheduler,
        preferences: AutomationPreferences
    ): AutomationCoordinator = AutomationCoordinator(sessionQueue, transactionExecutor, alarmScheduler, preferences)
}