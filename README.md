# Module Whitelabel Pay SDK

## What's new

This is the WhitelabelPay SDK version 1.1.10.
This release focuses on improving the SDK's functionality and performance.
It brings a new parameter to the SDK configuration: `coldStart` that stops any api calls when the SDK is initialized.
Also, a new error type was introduced: `InvalidEnrolmentInstance`.
It is used to signal an invalid enrolment detected by the backend.

### New features

- The `coldStart` parameter was added to the `WhitelabelPayConfigurations` class.
  The default value is `false`.
  When set to `true`, the SDK will not perform any API calls during initialization.
  Calling the `sync()` or `startMonitoringUpdates()` functions will reset the
  parameter value to `false`.

- Added `InvalidEnrolmentInstance` error to the WhitelabelPay SDK.
  This error will be thrown when the backend does not accept/authorize requests from 
  a device that has been previously enrolled.
  This will happen when a device identifier is deleted from the server because it was reported
  stolen or fraud detected, or when an unsolvable technical error stops the key verification from
  working.
  This is an unrecoverable state for a device, assuming the error is permanent.
  The only way forward is to reset the SDK and start fresh.
  A full reset is not something to be
  taken lightly or done without the users’ knowledge, so we strongly suggest gaining consent from
  the user.

### Breaking changes

- The `setReferenceId` is now a suspend function. 
  It performs a sync operation on updating the reference id.

## SDK Installation

### 1. Repository access

On first use you need "read" access to the GitHub repository
that hosts [SDK distribution packages](https://github.com/paymenttools/wlp-android-distribution).

### 2. Get your GitHub token.

To generate a GitHub token, navigate to your GitHub profile → Settings → Developer settings →
Personal access token → Generate new token (classic).
Select the `read:packages` scope.
Click Generate token.<br/>
More details can be
found [here](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens#creating-a-personal-access-token-classic).

### 3. Create/update the github.properties file.

Create the github.properties file in your project at root level. Add the following two properties:

```properties
gpr.usr=YOUR_GITHUB_ACCOUNT
gpr.key=YOUR_GITHUB_TOKEN
```

Make sure to add the file to .gitignore.

### 4. Configure access to the distribution repository.

#### 4.1. Using the Kotlin DSL structure

Open your project's `settings.gradle.kts` file add the following lines in the dependencyResolutionManagement block:

```kotlin
val githubProperties = Properties().apply {
    rootDir.resolve("github.properties").inputStream().use { load(it) }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        maven {
            url = uri("https://maven.pkg.github.com/paymenttools/wlp-android-distribution")
            credentials {
                username = githubProperties.getProperty("gpr.usr")
                password = githubProperties.getProperty("gpr.key")
            }
        }
    }
}
```

Add the dependency for the library in the app-level `build.gradle.kts` file of your project:

```kotlin
implementation("com.paymenttools:paymenttoolssdk:x.y.x")
```

#### 4.2. Using Groovy DSL

Open your project's `settings.gradle` file add the following lines in the dependencyResolutionManagement block:

```groovy
dependencyResolutionManagement {
    ...
    repositories {
        ...
        maven {
            url = "https://maven.pkg.github.com/paymenttools/wlp-android-distribution"
            credentials {
                username githubProperties.getProperty("gpr.usr")
                password githubProperties.getProperty("gpr.key")
            }
        }
    }
}
```

Add the dependency for the library in the app-level `build.gradle` file of your project:

```groovy
implementation "com.paymenttools:paymenttoolssdk:x.y.x"
```

## SDK Usage

### 1. Initialization

To make use of the SDK, an instance of WhitelabelPayImplementation needs to be created.

```kotlin
    val wlpSdk = WhitelabelPayImplementation(
        context = context,
        configs = WhitelabelPayConfigurations(...)
    )
```

#### WhitelabelPayConfigurations

There are several key points in creating the configuration object to consider:

```kotlin
    val configs = WhitelabelPayConfigurations(
        bundleId = BuildConfig.APPLICATION_ID,
        tenantId = TENANT_ID,
        referenceId = REFERENCE_ID,
        environment = WhitelabelPayEnvironment.INTEGRATION,
        shouldLog = true
    )
```

- `TENANT_ID` tenant identifier provided by Payment Tools. Should be set to `rew`.
- `REFERENCE_ID` **Unique** notification identifier for the device.
- `shouldLog` is a boolean value that enables or disables logging.
  The logs are printed in the logcat with the tag `WLP-SDK`.
  The logs are also recorded in a file that can be exported using the `exportLogs` function.

### 2. SubjectId

The `subjectId` is a unique identifier for the enrolled device in the WhitelabelPay system.
It is associated with the `referenceId` value provided by the app implementing the SDK.
The `subjectId` is generated by the WhitelabelPay system after a successful enrolment and can be
retrieved using the following property of the SDK:

```kotlin
    val subjectId = sdk.subjectId
```

If the device is not enrolled, the `subjectId` will be `null`.

### 3. SDK State

The WhitelabelPay SDK offers two ways to check the status of the SDK instance within the
WhitelabelPay system:

```kotlin
    val state = sdk.deviceState()
```

or observe the state changes by observing the `state` StateFlow property:

```kotlin
    sdk.state.collect { state ->
        when (state) {
            State.INACTIVE -> {}
            State.ONBOARDING -> {}
            State.ACTIVE -> {}
        }
    }
```

### 4. Tokens

#### Enrolment token

The enrolment token is used to register a card (once or multiple times) within the WhitelabelPay
system. The sdk provides a function to get the enrolment token:

```kotlin
    try {
        val enrolmentToken = sdk.getEnrolmentToken()
    } catch (e: WhitelabelPayError.MissingReferenceId) {
        Timber.e("get onboarding token failed: ", e)
    }
```

The enrolment token:

- always starts with ***01***;
- it is unique for the device;
- it is not reset for new sdk instances;
- it can be used multiple times when enrolling new or existing cards (bank accounts).

To use the token with the OneScan format, the SDK offers a utility function to get
the string representation of the token:

```kotlin
    val tokenString = enrolmentToken.stringRepresentation()
```

#### Payment token

The payment token is used to make payments within the WhitelabelPay system.
The sdk provides a function to get a payment token (if available):

```kotlin
    try {
        val paymentToken = sdk.getPaymentToken()
    } catch (e: Exception) {
        Timber.e("get payment token failed: ", e)
    }
```

A payment token:

- always start with ***02***;
- it is consumable, meaning that once used to perform a payment; it is no longer valid;
- it has an expiration period of 10 minutes since its generation;
- has an offline limit: once enough tokens are read while offline, no more tokens will be
  available.

Fetching a payment token when there is internet connectivity:

1. It will trigger an API call that returns the data associated with the active card
2. A token will be generated and returned

Fetching a payment token when there is **NO** internet connectivity:

1. A limit is imposed on the number of available tokens while offline
2. A token will be generated and returned while the limit is not reached
3. If the limit is reached, an error will be thrown
4. If the internet connection restores before reaching the limit, the limit resets to its initial value

To use the token with the OneScan format, the SDK offers a utility function to get the
string representation of the token:

```kotlin
    val tokenString = paymentToken.stringRepresentation()
```

#### Observe `token` StateFlow

The SDK offers a StateFlow property to observe the token changes:

```kotlin
    sdk.token
    .collect { token ->
        when (token) {
            is Token.EnrolmentToken -> {
                // handle enrolment token
            }
            is Token.PaymentToken -> {
                // handle payment token
            }
            is Token.Empty -> {
                //handle default value
            }
        }
    }
```

### 5. Data synchronization

The SDK offers a function to synchronize all SDK data available on the device (sdk state and card data):

```kotlin
    viewModelScope.launch {
        try {
            val state = sdk.sync()
        } catch (e: Exception) {
            Timber.e("sync failed: ", e)
        }
    }
```

There's also an overloaded version of the function that accepts a boolean parameter to emit or not
a new token through the `token` StateFlow property:

```kotlin
    viewModelScope.launch {
        try {
            val state = sdk.sync(updateToken = true)
        } catch (e: Exception) {
            Timber.e("sync failed: ", e)
        }
    }
```

### 6. Monitoring SDK state and token changes

The SDK offers two functions to start and stop monitoring the SDK state and token changes.

#### `startMonitoringUpdates` function

The `startMonitoringUpdates` function starts monitoring the SDK state and token changes.
The function triggers two parallel processes: one to update the token every 30 seconds and one to
update the SDK state every 5 seconds when enrolling a new card and every 10 seconds when making
a payment.
The function offers a callback to handle possible errors when *getting tokens*.

An implementation example would look like this:

```kotlin
fun startObservingChanges() {
    sdk.startMonitoringUpdates(
        onError = { error: WhitelabelPayError ->
            // handle error in your UI
            setErrorMessage(error.message())

            // delete the previous token from the UI
            if (error is WhitelabelPayError.TokenSignatureFailure ||
                error is WhitelabelPayError.GetPaymentMeansError ||
                error is WhitelabelPayError.GetPaymentTokenError ||
                error is WhitelabelPayError.NetworkConnectivityFail ||
                error is WhitelabelPayError.RequestDataSignatureFailure 
            ) {
                setToken(null)
                updateCodeImage(null)
            }

            if (error is WhitelabelPayError.InvalidEnrolmentInstance) {
                // todo: ask for user permission to reset/signoff and trigger an re-enrolment
            }
        }
    )
}
```

#### `stopMonitoringUpdates` function

The `stopMonitoringUpdates` function stops the monitoring of the SDK state and token changes by
stopping the two parallel processes started by the `startMonitoringUpdates` function.
The function should be called when the screen where the token is shown is destroyed.

An implementation example would look like this:

```kotlin
fun stopObservingChanges() {
    sdk.stopMonitoringUpdates()
}
```

or in a viewmodel:

```kotlin
override fun onCleared() {
    super.onCleared()
    stopObservingChanges()
}
```

### 7. Registered cards operations (payment means)

The SDK offers the functionality to retrieve and manage the already registered cards,
called **payment means** within WhitelabelPay.

#### — Retrieve payment means

```kotlin
    try {
        val paymentMeansList = sdk.getPaymentMeansList()
    } catch (e: Exception) {
        Timber.e("get payment means list failed: ", e)
    }
```

#### — Deactivate an active payment mean

```kotlin
    viewModelScope.launch {
        try {
            sdk.deactivatePaymentMeans(
                paymentMeanId = paymentMean.Id,
                onDeactivationSuccess = { }
            )
        } catch (e: Exception) {
            Timber.e("deactivation of payment mean failed: ", e)
        }
    }
```

#### — Reactivate an inactive payment mean

```kotlin
    viewModelScope.launch {
        try {
            sdk.reactivatePaymentMean(
                paymentMeanId = paymentMean.Id,
                onReactivationSuccess = { }
            )
        } catch (e: Exception) {
            Timber.e("reactivation of payment mean failed: ", e)
        }
    }
```

#### — Delete a payment mean

```kotlin
    viewModelScope.launch {
        try {
            sdk.deletePaymentMean(
                paymentMeanId = paymentMean.Id,
                onDeletionSuccess = { }
            )
        } catch (e: Exception) {
            Timber.e("deleting payment mean failed: ", e)
        }
    }
```

### 8. Sign Off

The *signOff* functionality purges ***ALL*** SDK data from the device, as well as deactivates all
payment means on the backend and changes the mandate status.
The state of the SDK is set to ONBOARDING.

```kotlin
    viewModelScope.launch {
        sdk.signOff(
            onSignOffSuccess = { }
        )
    }
```

### 9. Reset

The *reset* functionality purges ***ALL*** SDK data from the device, resets the SDK state to INACTIVE.

```kotlin
    viewModelScope.launch {
        wlpSdk.reset()
    }
```

### 10. Logging

The SDK offers the functionality to log the main actions that are performed by the SDK.
Enabling the logging can be done by setting the `shouldLog` parameter to `true` when initializing
the WhitelabelPayConfigurations object.
The logs are printed in the logcat with the tag `WLP-SDK`.

The SDK offers the possibility to export the logs to a file by using the `exportLogs` function.
Here is an example of how to export the logs to the external Downloads directory:

```kotlin
/**
 * Copies the log file to the external Downloads directory.
 * Returns the copied file or null if anything fails.
 */
private fun exportLogFileToDownloads(): File? {
    val logFile = sdk.exportLogs()
    if (logFile == null || !logFile.exists()) return null
  
    val downloadsDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!downloadsDir.exists()) downloadsDir.mkdirs()
  
    val exportedFile = File(downloadsDir, "wlp_sdk_logs_${LocalDate.now()}.txt")
    return try {
        logFile.copyTo(exportedFile, overwrite = true)
        exportedFile
    } catch (e: IOException) {
        Timber.tag("SettingsViewModel").e(e, "Failed to export logs")
        null
    }
}
```

### 11. Passing push notifications to WhitelabelPay SDK

The WhitelabelPay SDK offers the functionality to handle push notifications and to react to four
different events:
- enrolment successful;
- enrolment failed;
- payment successful;
- payment failed.

The reaction to these events would be a sync action.
In all cases, except for **enrolment failed**, 
a new payment token will be generated and emitted to the observers of the `token` StateFlow property.
Also in the case of **enrolment successful**, the SDK state will be set to `ACTIVE`.

The SDK offers the `handleNotification` function to handle the push notifications.
It accepts a string parameter that represents the type of the notification. 
The notification type should be retrieved from the push notification payload:

```kotlin
     val eventType = message.data["type"]
     try {
          sdk.handleNotification(eventType = eventType)
     } catch (e: WhitelabelPayError.WrongNotificationType) {
          Timber.e(e)
     }
```

### 12. Exclude WhitelabelPay data from automatic backup

If the Application using WhitelabelPay has automatic backup enabled, make sure the data
generated by the SDK is not persisted in the end-user's Google account.

Here are the steps for achieving this:

#### Changes in the `AndroidManifest.xml` file:

Set the `android:allowBackup` attribute to `"true"` for the `<application>` element. This ensures
that the app's data will be backed up by default.

In the app's `AndroidManifest.xml`, explicitly exclude the shared preferences file from being
backed up by adding the `android:fullBackupContent` attribute to the `<application>` element and
specifying an XML resource that excludes the shared preferences file.

```xml
<application
        android:allowBackup="true"
        android:fullBackupContent="@xml/full_backup_exclude">
    <!-- ... -->
</application>
```

#### Create an XML resource:

Create an XML resource file (for example, `res/xml/full_backup_exclude.xml`) that specifies which files or
directories should be excluded from backups. In this XML file, you can specify that
the shared preferences file shouldn't be backed up. Here is an example of such an XML file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="wlp-sdk-prefs-bundle-id.xml"/>
</full-backup-content>
```

where `bundle-id` is the value specified at the step: 1. Setup for `bundleId` parameter.
