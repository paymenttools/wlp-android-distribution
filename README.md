# Whitelabel Pay SDK

## SDK Installation

### 1. Get your Github token.
To generate a Github token, navigate to your Github profile -> Settings -> Developer settings -> Personal access token -> 
Generate new token (classic). Check the read:packages scope. Click Generate token. 

### 2. Create / update the github.properties file.
Create the github.properties file in your project at root level. Add the following 2 properties:
```properties
gpr.usr=YOUR_GITHUB_ACCOUNT
gpr.key=YOUR_GITHUB_TOKEN
```
Make sure to add the file to .gitignore.

### 3. Configure access to the distribution repository.

#### 3.1. Using the Kotlin DSL structure
In the settings.gradle.kts file add the following lines in the dependencyResolutionManagement block:

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
            credentials{
                username = githubProperties.getProperty("gpr.usr")
                password = githubProperties.getProperty("gpr.key")
            }
        }
    }
}
```

Add the dependency for the library in the app-level build.gradle.kts file:
```kotlin
implementation("com.paymenttools:paymenttoolssdk:x.y.x")
```

#### 3.2. Using Groovy DSL
In the settings.gradle file add the following lines in the dependencyResolutionManagement block:

```groovy
dependencyResolutionManagement {
    ...
    repositories {
        ...
        maven {
            url = "https://maven.pkg.github.com/paymenttools/wlp-android-distribution"
            credentials{
                username githubProperties.getProperty("gpr.usr")
                password githubProperties.getProperty("gpr.key")
            }
        }
    }
}
```

Add the dependency for the library in the app-level build.gradle.kts file:
```groovy
implementation "com.paymenttools:paymenttoolssdk:x.y.x"
```

## SDK Usage

### 1. Initialization

In order to make use of the SDK, an instance of WhitelabelPayImplementation needs to be created.

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
    merchantId = MERCHANT_ID,
    notificationId = NOTIFICATION_ID,
    environment = WhitelabelPayEnvironment.INTEGRATION,
    showErrorLogs = true
)
```

- `MERCHANT_ID` use `rew` as merchant id;
- `NOTIFICATION_ID` represents a UUID value converted to a string and without dashes.

### 2. SDK State
The WhitelabelPay SDK offers a mechanism to check the status of the SDK instance within the
WhitelabelPay system:

```kotlin
val state = sdk.deviceState()
```

or observe the state changes:

```kotlin
sdk.observeDeviceState()
    .collect { state ->
        when (state) {
            State.INACTIVE -> { }
            State.ONBOARDING -> { }
            State.ACTIVE -> { }
        }
    }
```

### 3. Tokens

#### Enrolment token
The enrolment token is used to register a card (once or multiple times) within the WhitelabelPay
system. The sdk provides a function to get the enrolment token:
```kotlin
try {
    val enrolmentToken = sdk.getEnrolmentToken()
} catch (e: Exception) {
    Timber.e("get onboarding token failed: ", e)
}
```

The enrolment token:
- always starts with ***01***;
- is unique for the device;
- is stored on device;
- is not reset for new sdk instances;
- can be used multiple times when enrolling new or existing cards (bank accounts).

In order to use the token with the OneScan format, the SDK offers an utility function to get
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

A payment token(s):
- always start with ***02***;
- is consumable, meaning that once scanned it must be scanned again;
- once read, the token is deleted from local storage (device);
- can be `null` if no tokens are stored on device and the device is offline.

Fetching a payment token when there is internet connectivity:
1. Will trigger an API call that returns a list of 5 payment tokens
2. Save the token list in the local storage
3. Return one token from storage
4. Delete the token from storage

Fetching a payment token when there is NO internet connectivity:
1. Return one token from the local storage (if there is no token left, *null* will be returned)
2. Delete the token from storage

In order to use the token with the OneScan format, the SDK offers an utility function to get the
string representation of the token:

```kotlin
    val tokenString = paymentToken.stringRepresentation()
```

### 4. Data synchronization

The SDK offers an utility function to synchronize all SDK data available on device (sdk state, card
data and payment tokens):

```kotlin
viewModelScope.launch {
    try {
        val state = sdk.sync()
    } catch (e: Exception) {
        Timber.e("sync failed: ", e)
    }
}
```

### 5. Registered cards operations (payment means)

The SDK offers the functionality to retrieve and manage the already registered cards,
called **payment means** within WhitelabelPay.

#### - Retrieve payment means
```kotlin
try {
    val paymentMeansList = sdk.getPaymentMeansList()
} catch (e: Exception) {
    Timber.e("get payment means list failed: ", e)
}
```

#### - Deactivate an active payment mean
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

#### - Reactivate an inactive payment mean
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

#### - Delete a payment mean
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

### 6. Sign Off

Sign off deactivates all payment means and removes locally stored payment means and payment tokens.
The state of the SDK is set to ONBOARDING.

```kotlin
viewModelScope.launch {
   wlpSdk.signOffFromWhiteLabelPay()
}
```

### 7. Reset

The reset functionality purges ***ALL*** SDK data from the device, resets the SDK state to INACTIVE

```kotlin
viewModelScope.launch {
   wlpSdk.reset()
}
```

### 8. Exclude WhitelabelPay data from automatic backup

If the Application using WhitelabelPay has automatic backup enabled, in order to make sure the data
generated by the SDK and stored in the Encrypted Shared Preferences file is not persisted in the
end-user's Google account, the aforementioned shared preferences file must be excluded from
automatic backup.

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

Create an XML resource file (e.g., `res/xml/full_backup_exclude.xml`) that specifies which files or
directories should be excluded from backups. In this XML file, you can specify that
the shared preferences file should not be backed up. Here's an example of such an XML file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="wlp-sdk-prefs-bundle-id.xml" />
</full-backup-content>
```

where `bundle-id` is the value specified at step: 1. Setup for `bundleId` parameter.
