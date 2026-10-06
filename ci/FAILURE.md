# Build failure — run 51

commit: b692a57ce4e72ff7f3e88bee29ff51c697700ad8

## Errors
```
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/HotspotApp.kt:17:65 Unresolved reference: EntryPointAccessors
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/HotspotApp.kt:18:61 Unresolved reference: EntryPointAccessors
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/HotspotApp.kt:19:57 Unresolved reference: EntryPointAccessors
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/accessibility/NodeDumper.kt:21:27 Unresolved reference: boundsInScreen
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/accessibility/NodeDumper.kt:39:45 Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type String?
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/accessibility/ToggleHostActivity.kt:13:42 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:12:42 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:45:9 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:53:32 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:55:9 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:108:36 Suspend function 'dumpCurrentUi' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:111:53 Unresolved reference: error
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:120:37 Suspend function 'detectKeyguard' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:123:36 Unresolved reference: error
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:127:41 Suspend function 'resolveHotspot' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:128:31 Suspend function 'readState' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:133:41 Suspend function 'resolveMobileData' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:134:31 Suspend function 'readState' should be called only from a coroutine or another suspend function
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:145:97 Unresolved reference: error
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:158:105 Unresolved reference: error
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/diagnostics/AutomationDiagnostics.kt:172:87 Unresolved reference: error
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/CrashRecovery.kt:18:58 Unresolved reference: withLock
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/CrashRecovery.kt:23:46 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/CrashRecovery.kt:27:15 Unresolved reference: withLock
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:36:33 Unresolved reference: withLock
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:40:13 'return' is not allowed here
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:57:95 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:58:99 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:63:50 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:70:39 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:74:103 Unresolved reference: detail
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:81:17 'return' is not allowed here
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:90:43 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:92:13 'return' is not allowed here
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:99:9 'return' is not allowed here
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:106:55 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:108:67 Classifier 'MasterDisabled' does not have a companion object, and thus must be initialized here
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:127:48 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:128:53 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/recovery/ReconciliationEngineImpl.kt:129:71 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:27:96 Unresolved reference: withLock
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:29:41 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:36:59 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:37:52 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:38:57 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:39:75 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:41:34 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:53:22 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:54:29 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:61:33 Unresolved reference: withLock
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:69:22 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:70:29 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:73:67 Unresolved reference: withLock
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:80:22 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationCoordinator.kt:81:29 Suspension functions can be called only within coroutine body
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationSession.kt:90:17 Using 'receiveOrNull(): E?' is an error. Deprecated in favor of 'receiveCatching'. Please note that the provided replacement does not rethrow channel's close cause as 'receiveOrNull' did, for the detailed replacement please refer to the 'receiveOrNull' documentation
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/AutomationSession.kt:93:26 Return type of 'cancelAll' is not a subtype of the return type of the overridden member 'public abstract suspend fun cancelAll(): Unit defined in com.iranjan.hotspotscheduler.automation.session.SessionQueue'
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/EmergencyStop.kt:18:15 Unresolved reference: withLock
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/EmergencyStop.kt:25:15 Unresolved reference: withLock
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/session/EmergencyStop.kt:34:15 Unresolved reference: withLock
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/OperationStrategyResolver.kt:5:42 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/OperationStrategyResolver.kt:59:17 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/OperationStrategyResolver.kt:63:9 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/QuickSettingsNavigator.kt:7:42 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/QuickSettingsNavigator.kt:21:9 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/QuickSettingsNavigator.kt:40:71 Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type String?
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/QuickSettingsNavigator.kt:41:58 Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type String?
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/QuickSettingsNavigator.kt:52:23 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/QuickSettingsNavigator.kt:58:55 Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type String?
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/QuickSettingsNavigator.kt:65:9 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsDataStrategy.kt:7:42 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsDataStrategy.kt:24:57 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsDataStrategy.kt:25:57 Unresolved reference: value
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsDataStrategy.kt:26:42 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsDataStrategy.kt:31:9 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsDataStrategy.kt:38:27 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsDataStrategy.kt:41:31 Unresolved reference: value
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsDataStrategy.kt:69:57 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsDataStrategy.kt:70:57 Unresolved reference: value
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsDataStrategy.kt:71:42 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsHotspotStrategy.kt:7:42 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsHotspotStrategy.kt:24:57 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsHotspotStrategy.kt:25:57 Unresolved reference: value
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsHotspotStrategy.kt:26:42 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsHotspotStrategy.kt:31:9 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsHotspotStrategy.kt:38:27 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsHotspotStrategy.kt:41:31 Unresolved reference: value
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsHotspotStrategy.kt:70:57 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsHotspotStrategy.kt:71:57 Unresolved reference: value
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungQuickSettingsHotspotStrategy.kt:72:42 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:11:42 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:27:58 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:28:16 Type mismatch: inferred type is Boolean? but Result<Boolean> was expected
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:28:38 Unresolved reference: value
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:39:56 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:43:58 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:45:33 Unresolved reference: value
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:48:56 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:52:9 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:54:41 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:58:13 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:63:9 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:77:58 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:78:43 Unresolved reference: value
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:79:42 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsDataStrategy.kt:90:23 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:10:42 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:26:58 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:27:16 Type mismatch: inferred type is Boolean? but Result<Boolean> was expected
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:27:38 Unresolved reference: value
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:38:56 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:42:58 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:44:33 Unresolved reference: value
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:52:56 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:56:9 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:58:41 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:62:13 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:67:9 Unresolved reference: AttemptLog
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:81:58 Not enough information to infer type variable T
e: file:///home/runner/work/hotspot-scheduler/hotspot-scheduler/app/src/main/java/com/iranjan/hotspotscheduler/automation/strategies/SamsungSettingsHotspotStrategy.kt:82:43 Unresolved reference: value
(no matching lines)
```

## Tail of build log
```
	at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:37)
	at org.gradle.internal.execution.steps.CancelExecutionStep.execute(CancelExecutionStep.java:41)
	at org.gradle.internal.execution.steps.TimeoutStep.executeWithoutTimeout(TimeoutStep.java:74)
	at org.gradle.internal.execution.steps.TimeoutStep.execute(TimeoutStep.java:55)
	at org.gradle.internal.execution.steps.CreateOutputsStep.execute(CreateOutputsStep.java:50)
	at org.gradle.internal.execution.steps.CreateOutputsStep.execute(CreateOutputsStep.java:28)
	at org.gradle.internal.execution.steps.CaptureStateAfterExecutionStep.executeDelegateBroadcastingChanges(CaptureStateAfterExecutionStep.java:100)
	at org.gradle.internal.execution.steps.CaptureStateAfterExecutionStep.execute(CaptureStateAfterExecutionStep.java:72)
	at org.gradle.internal.execution.steps.CaptureStateAfterExecutionStep.execute(CaptureStateAfterExecutionStep.java:50)
	at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:40)
	at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:29)
	at org.gradle.internal.execution.steps.BuildCacheStep.executeWithoutCache(BuildCacheStep.java:166)
	at org.gradle.internal.execution.steps.BuildCacheStep.lambda$execute$1(BuildCacheStep.java:70)
	at org.gradle.internal.Either$Right.fold(Either.java:175)
	at org.gradle.internal.execution.caching.CachingState.fold(CachingState.java:59)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:68)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:46)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:36)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:25)
	at org.gradle.internal.execution.steps.RecordOutputsStep.execute(RecordOutputsStep.java:36)
	at org.gradle.internal.execution.steps.RecordOutputsStep.execute(RecordOutputsStep.java:22)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.executeBecause(SkipUpToDateStep.java:91)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.lambda$execute$2(SkipUpToDateStep.java:55)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:55)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:37)
	at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:65)
	at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:36)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:37)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:27)
	at org.gradle.internal.execution.steps.ResolveCachingStateStep.execute(ResolveCachingStateStep.java:76)
	at org.gradle.internal.execution.steps.ResolveCachingStateStep.execute(ResolveCachingStateStep.java:37)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:94)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:49)
	at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.execute(CaptureStateBeforeExecutionStep.java:71)
	at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.execute(CaptureStateBeforeExecutionStep.java:45)
	at org.gradle.internal.execution.steps.SkipEmptyWorkStep.executeWithNonEmptySources(SkipEmptyWorkStep.java:177)
	at org.gradle.internal.execution.steps.SkipEmptyWorkStep.execute(SkipEmptyWorkStep.java:86)
	at org.gradle.internal.execution.steps.SkipEmptyWorkStep.execute(SkipEmptyWorkStep.java:53)
	at org.gradle.internal.execution.steps.RemoveUntrackedExecutionStateStep.execute(RemoveUntrackedExecutionStateStep.java:32)
	at org.gradle.internal.execution.steps.RemoveUntrackedExecutionStateStep.execute(RemoveUntrackedExecutionStateStep.java:21)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:36)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:23)
	at org.gradle.internal.execution.steps.CleanupStaleOutputsStep.execute(CleanupStaleOutputsStep.java:75)
	at org.gradle.internal.execution.steps.CleanupStaleOutputsStep.execute(CleanupStaleOutputsStep.java:41)
	at org.gradle.internal.execution.steps.AssignWorkspaceStep.lambda$execute$0(AssignWorkspaceStep.java:32)
	at org.gradle.api.internal.tasks.execution.TaskExecution$4.withWorkspace(TaskExecution.java:287)
	at org.gradle.internal.execution.steps.AssignWorkspaceStep.execute(AssignWorkspaceStep.java:30)
	at org.gradle.internal.execution.steps.AssignWorkspaceStep.execute(AssignWorkspaceStep.java:21)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:37)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:27)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:47)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:34)
	at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:64)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:146)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:135)
	at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
	at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:73)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:42)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:337)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:324)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:317)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:463)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:380)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.ManagedExecutorImpl$1.run(ManagedExecutorImpl.java:49)
Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: Compilation error. See log for more details
	at org.jetbrains.kotlin.gradle.tasks.TasksUtilsKt.throwExceptionIfCompilationFailed(tasksUtils.kt:22)
	at org.jetbrains.kotlin.compilerRunner.GradleKotlinCompilerWork.run(GradleKotlinCompilerWork.kt:144)
	at org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction.execute(GradleCompilerRunnerWithWorkers.kt:76)
	at org.gradle.workers.internal.DefaultWorkerServer.execute(DefaultWorkerServer.java:63)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create(NoIsolationWorkerFactory.java:66)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create(NoIsolationWorkerFactory.java:62)
	at org.gradle.internal.classloader.ClassLoaderUtils.executeInClassloader(ClassLoaderUtils.java:100)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1.lambda$execute$0(NoIsolationWorkerFactory.java:62)
	at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:44)
	at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:41)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:73)
	at org.gradle.workers.internal.AbstractWorker.executeWrappedInBuildOperation(AbstractWorker.java:41)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1.execute(NoIsolationWorkerFactory.java:59)
	at org.gradle.workers.internal.DefaultWorkerExecutor.lambda$submitWork$0(DefaultWorkerExecutor.java:169)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runExecution(DefaultConditionalExecutionQueue.java:187)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.access$700(DefaultConditionalExecutionQueue.java:120)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner$1.run(DefaultConditionalExecutionQueue.java:162)
	at org.gradle.internal.Factories$1.create(Factories.java:31)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:249)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:109)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:114)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runBatch(DefaultConditionalExecutionQueue.java:157)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.run(DefaultConditionalExecutionQueue.java:126)
	... 2 more


* Get more help at https://help.gradle.org

BUILD FAILED in 2m 45s
26 actionable tasks: 26 executed
```
